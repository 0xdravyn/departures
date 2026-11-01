package app.departures.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The configuration. Content (where you are, calendars, the message, markets) lives in the app's private
 * files/config.json, written by the setup page served from the TV (SetupServer) or imported over ADB
 * (ImportActivity, config.example.json). Settings (which views, how long, animation, quiet hours) live in private
 * preferences, set on the TV (SettingsActivity) or from the same page. Every field has a default so a missing or
 * partial file still gives a working board. Calendar addresses are the only secret and are never logged or
 * sent back to the page.
 */
public final class Config {
    public static final class Calendar { public final String label, url; Calendar(String l, String u) { label = l; url = u; } }
    public static final class Market { public final String label, source, symbol; Market(String l, String s, String y) { label = l; source = s; symbol = y; } }

    static final String PREFS = "settings";
    static final int MAX_CALENDARS = 3, MAX_MARKETS = 5, MESSAGE_LINES = 6;
    /** The public defaults when the config has no "markets" key at all; an empty list in the config turns the face off. */
    static final List<Market> DEFAULT_MARKETS = List.of(new Market("S&P 500", "yahoo", "^GSPC"), new Market("NVIDIA", "yahoo", "NVDA"), new Market("GOOGLE", "yahoo", "GOOGL"), new Market("BITCOIN", "coingecko", "bitcoin"), new Market("GOLD", "yahoo", "GC=F"));

    // ----- content (config.json) -----
    public boolean present;
    public String locationName = "";
    public double latitude = Double.NaN, longitude = Double.NaN;
    public boolean imperial, clock24;
    public final List<Calendar> calendars = new ArrayList<>();
    /** Today's bottom rows: the next calendar event, or two lines of the owner's own. */
    public boolean todayMessage;
    public String todayLine1 = "", todayLine2 = "";
    /** The Message view: up to six lines of 22, with colour chips as {r} {o} {y} {g} {b} {v} {w}. */
    public final List<String> message = new ArrayList<>();
    public final List<Market> markets = new ArrayList<>();
    /** How far ahead the next-event rows look: 0 shows today only, 1 (default) falls back to tomorrow's first event once today is done. */
    public int lookaheadDays = 1;

    // ----- settings (preferences) -----
    public boolean todayOn = true, keepWatchingOn = true, marketsOn = true, messageOn = true;
    public int todaySeconds = 60, keepWatchingSeconds = 30, marketsSeconds = 60, messageSeconds = 30;
    public boolean quietEnabled = true;
    public int quietFromMin = 23 * 60, quietToMin = 7 * 60;   // minutes after midnight
    public float quietDim = 0.3f;
    /** Overall brightness of the board outside quiet hours, 0.4 to 1. */
    public float brightness = 1f;
    public boolean flips = true;

    static File file(Context ctx) { return new File(ctx.getFilesDir(), "config.json"); }

    public static Config load(Context ctx) {
        Config c = new Config();
        File f = file(ctx);
        if (!f.exists()) { c.markets.addAll(DEFAULT_MARKETS); return overlay(ctx, c); }
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            JSONObject loc = o.optJSONObject("location");
            if (loc != null) { c.locationName = loc.optString("name", ""); c.latitude = loc.optDouble("latitude", Double.NaN); c.longitude = loc.optDouble("longitude", Double.NaN); }
            c.imperial = "imperial".equalsIgnoreCase(o.optString("units", "metric"));
            c.clock24 = "24h".equalsIgnoreCase(o.optString("clock", "12h"));
            JSONObject q = o.optJSONObject("quiet_hours");
            if (q != null) { c.quietFromMin = minutes(q.optString("from", "23:00"), c.quietFromMin); c.quietToMin = minutes(q.optString("to", "07:00"), c.quietToMin); c.quietDim = (float) q.optDouble("dim", 0.3); }
            JSONArray cal = o.optJSONArray("calendars");
            if (cal != null) for (int i = 0; i < cal.length() && c.calendars.size() < MAX_CALENDARS; i++) {
                JSONObject e = cal.optJSONObject(i);
                if (e == null) continue;
                String url = e.optString("url", "");
                if (url.startsWith("https://")) c.calendars.add(new Calendar(e.optString("label", ""), url));
            }
            JSONObject today = o.optJSONObject("today");
            if (today != null) {
                c.todayMessage = "message".equalsIgnoreCase(today.optString("mode", "calendars"));
                JSONArray lines = today.optJSONArray("lines");
                if (lines != null) { c.todayLine1 = lines.optString(0, ""); c.todayLine2 = lines.optString(1, ""); }
            }
            JSONArray msg = o.optJSONArray("message");
            if (msg != null) for (int i = 0; i < msg.length() && c.message.size() < MESSAGE_LINES; i++) c.message.add(msg.optString(i, ""));
            JSONObject faces = o.optJSONObject("faces");
            if (faces != null) { c.todaySeconds = Math.max(10, faces.optInt("today_seconds", faces.optInt("now_seconds", 60))); c.keepWatchingSeconds = Math.max(10, faces.optInt("keep_watching_seconds", 30)); c.marketsSeconds = Math.max(10, faces.optInt("markets_seconds", 60)); c.messageSeconds = Math.max(10, faces.optInt("message_seconds", 30)); }
            JSONArray mk = o.optJSONArray("markets");
            if (mk != null) for (int i = 0; i < mk.length() && c.markets.size() < MAX_MARKETS; i++) { Market m = market(mk.opt(i)); if (m != null) c.markets.add(m); }
            if (mk == null) c.markets.addAll(DEFAULT_MARKETS);
            JSONObject calo = o.optJSONObject("calendar");
            if (calo != null) c.lookaheadDays = Math.max(0, Math.min(14, calo.optInt("lookahead_days", 1)));
            c.present = true;
        } catch (Exception e) {
            android.util.Log.w("Departures", "config.json unreadable: " + e.getClass().getSimpleName());
        }
        return overlay(ctx, c);
    }

    /** Choices made on the TV or the setup page win over config.json; both the app and the screensaver read them. */
    private static Config overlay(Context ctx, Config c) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        c.todayOn = p.getBoolean("today_on", true); c.keepWatchingOn = p.getBoolean("keep_watching_on", true); c.marketsOn = p.getBoolean("markets_on", true); c.messageOn = p.getBoolean("message_on", true);
        c.todaySeconds = p.getInt("today_seconds", c.todaySeconds);
        c.keepWatchingSeconds = p.getInt("keep_watching_seconds", c.keepWatchingSeconds);
        c.marketsSeconds = p.getInt("markets_seconds", c.marketsSeconds);
        c.messageSeconds = p.getInt("message_seconds", c.messageSeconds);
        if (p.contains("clock24")) c.clock24 = p.getBoolean("clock24", c.clock24);
        if (p.contains("imperial")) c.imperial = p.getBoolean("imperial", c.imperial);
        c.quietEnabled = p.getBoolean("quiet_enabled", true);
        c.quietFromMin = p.getInt("quiet_from", c.quietFromMin); c.quietToMin = p.getInt("quiet_to", c.quietToMin);
        c.brightness = Math.max(0.4f, Math.min(1f, p.getFloat("brightness", 1f)));
        c.flips = p.getBoolean("flips", true);
        return c;
    }

    /**
     * Writes the content the setup page sent. A calendar entry without a url keeps the address of the saved entry
     * it came from ("saved": its index in the last state), so the page never has to hold or resend a secret. Caches that depend on the content
     * are dropped so the board refreshes at once.
     */
    static synchronized void saveContent(Context ctx, JSONObject in) throws Exception {
        Config old = load(ctx);
        JSONObject out = new JSONObject();
        JSONObject loc = in.optJSONObject("location");
        if (loc != null && !Double.isNaN(loc.optDouble("latitude", Double.NaN))) out.put("location", new JSONObject().put("name", loc.optString("name", "")).put("latitude", loc.optDouble("latitude")).put("longitude", loc.optDouble("longitude")));
        out.put("units", in.optBoolean("imperial", false) ? "imperial" : "metric");
        out.put("clock", in.optBoolean("clock24", false) ? "24h" : "12h");
        JSONArray cals = new JSONArray();
        JSONArray cin = in.optJSONArray("calendars");
        if (cin != null) for (int i = 0; i < cin.length() && cals.length() < MAX_CALENDARS; i++) {
            JSONObject e = cin.optJSONObject(i); if (e == null) continue;
            String url = e.optString("url", "").trim();
            int saved = e.optInt("saved", -1);
            if (url.isEmpty() && saved >= 0 && saved < old.calendars.size()) url = old.calendars.get(saved).url;
            if (!url.startsWith("https://")) continue;
            cals.put(new JSONObject().put("label", e.optString("label", "").trim()).put("url", url));
        }
        out.put("calendars", cals);
        JSONObject today = in.optJSONObject("today");
        JSONArray tl = new JSONArray();
        if (today != null) { JSONArray lines = today.optJSONArray("lines"); for (int i = 0; i < 2; i++) tl.put(lines == null ? "" : clip(lines.optString(i, ""), 44)); }   // cells are limited on the board; a chip is three characters
        else { tl.put(""); tl.put(""); }
        out.put("today", new JSONObject().put("mode", today != null && "message".equals(today.optString("mode")) ? "message" : "calendars").put("lines", tl));
        JSONArray msg = new JSONArray();
        JSONArray min = in.optJSONArray("message");
        for (int i = 0; i < MESSAGE_LINES; i++) msg.put(min == null ? "" : clip(min.optString(i, ""), 44));
        out.put("message", msg);
        JSONArray mk = new JSONArray();
        JSONArray mkin = in.optJSONArray("markets");
        if (mkin != null) for (int i = 0; i < mkin.length() && mk.length() < MAX_MARKETS; i++) {
            Object entry = mkin.opt(i);
            String sym = entry instanceof JSONObject ? ((JSONObject) entry).optString("symbol", "").trim() : String.valueOf(entry).trim();
            String label = entry instanceof JSONObject ? ((JSONObject) entry).optString("label", "").trim() : "";
            Market m = market(sym); if (m == null) continue;
            mk.put(new JSONObject().put("symbol", m.symbol).put("source", m.source).put("label", label.isEmpty() ? m.label : label));
        }
        out.put("markets", mk);
        out.put("calendar", new JSONObject().put("lookahead_days", old.lookaheadDays));
        Files.write(file(ctx).toPath(), out.toString(2).getBytes(StandardCharsets.UTF_8));
        dropCaches(ctx);
        android.util.Log.i("Departures", "content saved from the setup page");
    }

    /** Back to a TV with nothing of yours on it: config.json and every cache gone. Settings stay. */
    static synchronized void resetContent(Context ctx) { file(ctx).delete(); dropCaches(ctx); }

    /** Every setting back to its default. Content and the pairing code stay. */
    static synchronized void resetSettings(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String code = p.getString("pair_code", null), prev = p.getString("previous_screensaver", null);
        SharedPreferences.Editor e = p.edit().clear();
        if (code != null) e.putString("pair_code", code);
        if (prev != null) e.putString("previous_screensaver", prev);
        e.apply();
    }

    static void dropCaches(Context ctx) {
        File dir = ctx.getFilesDir();
        for (String n : new String[]{"weather.json", "markets.json"}) new File(dir, n).delete();
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) if (f.getName().startsWith("cal-")) f.delete();
    }

    /** The four-digit code the setup page asks for, shown on the TV. Made once, kept until "New code". */
    static String pairCode(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String code = p.getString("pair_code", null);
        if (code == null || code.length() != 4) code = newPairCode(ctx);
        return code;
    }

    static String newPairCode(Context ctx) {
        String code = String.format(Locale.US, "%04d", new java.security.SecureRandom().nextInt(10000));
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pair_code", code).apply();
        return code;
    }

    /**
     * One market entry. The simple form is a string: a Yahoo Finance symbol ("NVDA", "^GSPC", "GC=F"), a CoinGecko
     * id in lower case ("bitcoin"), or one of a few common names ("S&P 500", "gold"). The long form is an object
     * with label, symbol and source ("yahoo" or "coingecko") for anything the guess gets wrong.
     */
    static Market market(Object entry) {
        if (entry instanceof JSONObject) {
            JSONObject m = (JSONObject) entry;
            String sym = m.optString("symbol", ""); if (sym.isEmpty()) return null;
            String src = m.optString("source", "yahoo").equals("coingecko") ? "coingecko" : "yahoo";
            return new Market(m.optString("label", sym), src, sym);
        }
        if (!(entry instanceof String)) return null;
        String s = ((String) entry).trim(); if (s.isEmpty()) return null;
        String lower = s.toLowerCase(Locale.US);
        if (lower.startsWith("coingecko:")) { String id = s.substring(10).trim().toLowerCase(Locale.US); return new Market(id.toUpperCase(Locale.US), "coingecko", id); }
        if (lower.startsWith("yahoo:")) { String sym = s.substring(6).trim(); return new Market(sym.toUpperCase(Locale.US), "yahoo", sym); }
        switch (s.toUpperCase(Locale.US).replace("&", "AND").replace(" ", "")) {
            case "SANDP500": case "SP500": case "SPX": return new Market("S&P 500", "yahoo", "^GSPC");
            case "NASDAQ": case "NASDAQ100": return new Market("NASDAQ", "yahoo", "^IXIC");
            case "DOW": case "DOWJONES": case "DJIA": return new Market("DOW", "yahoo", "^DJI");
            case "GOLD": return new Market("GOLD", "yahoo", "GC=F");
            case "SILVER": return new Market("SILVER", "yahoo", "SI=F");
            case "OIL": case "CRUDE": return new Market("OIL", "yahoo", "CL=F");
            case "BTC": case "BITCOIN": return new Market("BITCOIN", "coingecko", "bitcoin");
            case "ETH": case "ETHEREUM": return new Market("ETHEREUM", "coingecko", "ethereum");
            case "SOL": case "SOLANA": return new Market("SOLANA", "coingecko", "solana");
            case "HYPE": case "HYPERLIQUID": return new Market("HYPE", "coingecko", "hyperliquid");
            case "XRP": return new Market("XRP", "coingecko", "ripple");
            case "DOGE": case "DOGECOIN": return new Market("DOGE", "coingecko", "dogecoin");
            case "NVIDIA": return new Market("NVIDIA", "yahoo", "NVDA");
            case "GOOGLE": case "ALPHABET": return new Market("GOOGLE", "yahoo", "GOOGL");
            case "APPLE": return new Market("APPLE", "yahoo", "AAPL");
            case "TESLA": return new Market("TESLA", "yahoo", "TSLA");
            default: break;
        }
        // A lower-case word is a CoinGecko id ("purr-2"); anything else is a Yahoo symbol (NVDA, ^GSPC, GC=F).
        if (s.equals(lower) && lower.matches("[a-z0-9-]+")) return new Market(s.toUpperCase(Locale.US), "coingecko", lower);
        return new Market(s.toUpperCase(Locale.US), "yahoo", s.toUpperCase(Locale.US));
    }

    public boolean viewOn(int face) { return face == FaceCycle.TODAY ? todayOn : face == FaceCycle.KEEP_WATCHING ? keepWatchingOn : face == FaceCycle.MARKETS ? marketsOn : messageOn; }
    public int viewsOn() { return (todayOn ? 1 : 0) + (keepWatchingOn ? 1 : 0) + (marketsOn ? 1 : 0) + (messageOn ? 1 : 0); }
    public boolean hasLocation() { return !Double.isNaN(latitude) && !Double.isNaN(longitude); }
    public boolean hasMessage() { for (String l : message) if (l != null && !l.trim().isEmpty()) return true; return false; }

    /** True inside quiet hours, which may wrap past midnight. */
    public boolean quiet(int minuteOfDay) {
        if (!quietEnabled) return false;
        return quietFromMin <= quietToMin ? (minuteOfDay >= quietFromMin && minuteOfDay < quietToMin)
                                          : (minuteOfDay >= quietFromMin || minuteOfDay < quietToMin);
    }

    static int minutes(String hhmm, int dflt) {
        try { String[] p = hhmm.trim().split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); } catch (Exception e) { return dflt; }
    }

    private static String clip(String s, int max) { return s == null ? "" : s.length() <= max ? s : s.substring(0, max); }
}
