package app.departures.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The configuration, read from the app's private files/config.json (see ImportActivity and config.example.json).
 * Every field has a default so a missing or partial file still gives a working board; calendar addresses are
 * the only secret and are never logged.
 */
public final class Config {
    public static final class Calendar { public final String label, url; Calendar(String l, String u) { label = l; url = u; } }

    public String locationName = "";
    public double latitude = Double.NaN, longitude = Double.NaN;
    public boolean imperial;
    public boolean clock24;
    public int quietFromMin = 23 * 60, quietToMin = 7 * 60;   // minutes after midnight
    public float quietDim = 0.3f;
    public final List<Calendar> calendars = new ArrayList<>();
    public int nowSeconds = 60, keepWatchingSeconds = 30, marketsSeconds = 60;
    public static final class Market { public final String label, source, symbol; Market(String l, String s, String y) { label = l; source = s; symbol = y; } }
    public final List<Market> markets = new ArrayList<>();
    public boolean present;
    /** -1 cycles the faces; 0, 1 or 2 pins one (FaceCycle.NOW, KEEP_WATCHING, MARKETS). Set on the TV. */
    public int pinnedFace = -1;
    public boolean quietEnabled = true;
    static final String PREFS = "settings";
    /** The public defaults when the config has no "markets" key at all; an empty list in the config turns the face off. */
    static final List<Market> DEFAULT_MARKETS = List.of(new Market("S&P 500", "yahoo", "^GSPC"), new Market("NVIDIA", "yahoo", "NVDA"), new Market("BITCOIN", "coingecko", "bitcoin"), new Market("GOLD", "yahoo", "GC=F"));
    /** How far ahead the next-event rows look: 0 shows today only, 1 (default) falls back to tomorrow's first event once today is done. */
    public int lookaheadDays = 1;

    public static Config load(Context ctx) {
        Config c = new Config();
        File f = new File(ctx.getFilesDir(), "config.json");
        if (!f.exists()) return c;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            JSONObject loc = o.optJSONObject("location");
            if (loc != null) { c.locationName = loc.optString("name", ""); c.latitude = loc.optDouble("latitude", Double.NaN); c.longitude = loc.optDouble("longitude", Double.NaN); }
            c.imperial = "imperial".equalsIgnoreCase(o.optString("units", "metric"));
            c.clock24 = "24h".equalsIgnoreCase(o.optString("clock", "12h"));
            JSONObject q = o.optJSONObject("quiet_hours");
            if (q != null) { c.quietFromMin = minutes(q.optString("from", "23:00"), c.quietFromMin); c.quietToMin = minutes(q.optString("to", "07:00"), c.quietToMin); c.quietDim = (float) q.optDouble("dim", 0.3); }
            JSONArray cal = o.optJSONArray("calendars");
            if (cal != null) for (int i = 0; i < cal.length(); i++) {
                JSONObject e = cal.optJSONObject(i);
                if (e == null) continue;
                String url = e.optString("url", "");
                if (url.startsWith("https://")) c.calendars.add(new Calendar(e.optString("label", ""), url));
            }
            JSONObject faces = o.optJSONObject("faces");
            if (faces != null) { c.nowSeconds = Math.max(10, faces.optInt("now_seconds", 60)); c.keepWatchingSeconds = Math.max(10, faces.optInt("keep_watching_seconds", faces.optInt("up_next_seconds", 30))); c.marketsSeconds = Math.max(10, faces.optInt("markets_seconds", 60)); }
            JSONArray mk = o.optJSONArray("markets");
            if (mk != null) for (int i = 0; i < mk.length() && c.markets.size() < 4; i++) { Market m = market(mk.opt(i)); if (m != null) c.markets.add(m); }
            if (mk == null) c.markets.addAll(DEFAULT_MARKETS);
            JSONObject calo = o.optJSONObject("calendar");
            if (calo != null) c.lookaheadDays = Math.max(0, Math.min(14, calo.optInt("lookahead_days", 1)));
            c.present = true;
        } catch (Exception e) {
            android.util.Log.w("Departures", "config.json unreadable: " + e.getClass().getSimpleName());
        }
        // Choices made on the TV itself (SettingsActivity) win over config.json; both the app and the screensaver read them.
        android.content.SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        c.pinnedFace = p.getInt("pinned_face", -1);
        c.nowSeconds = p.getInt("now_seconds", c.nowSeconds);
        c.keepWatchingSeconds = p.getInt("keep_watching_seconds", c.keepWatchingSeconds);
        c.marketsSeconds = p.getInt("markets_seconds", c.marketsSeconds);
        if (p.contains("clock24")) c.clock24 = p.getBoolean("clock24", c.clock24);
        if (p.contains("imperial")) c.imperial = p.getBoolean("imperial", c.imperial);
        c.quietEnabled = p.getBoolean("quiet_enabled", true);
        return c;
    }

    /**
     * One market entry. The simple form is a string: a common name ("S&P 500", "gold", "bitcoin", "ETH"), a stock
     * ticker ("NVDA", "GLXY"), or a CoinGecko id in lower case ("purr-2"). The long form is an object with label,
     * symbol and source ("yahoo" or "coingecko") for anything the guess gets wrong.
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
        String lower = s.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("coingecko:")) { String id = s.substring(10).trim().toLowerCase(java.util.Locale.US); return new Market(id.toUpperCase(java.util.Locale.US), "coingecko", id); }
        if (lower.startsWith("yahoo:")) { String sym = s.substring(6).trim(); return new Market(sym.toUpperCase(java.util.Locale.US), "yahoo", sym); }
        switch (s.toUpperCase(java.util.Locale.US).replace("&", "AND").replace(" ", "")) {
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
        if (s.equals(lower) && lower.matches("[a-z0-9-]+")) return new Market(s.toUpperCase(java.util.Locale.US), "coingecko", lower);
        return new Market(s.toUpperCase(java.util.Locale.US), "yahoo", s.toUpperCase(java.util.Locale.US));
    }

    public boolean hasLocation() { return !Double.isNaN(latitude) && !Double.isNaN(longitude); }

    /** True inside quiet hours, which may wrap past midnight. */
    public boolean quiet(int minuteOfDay) {
        if (!quietEnabled) return false;
        return quietFromMin <= quietToMin ? (minuteOfDay >= quietFromMin && minuteOfDay < quietToMin)
                                          : (minuteOfDay >= quietFromMin || minuteOfDay < quietToMin);
    }

    private static int minutes(String hhmm, int dflt) {
        try { String[] p = hhmm.trim().split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); } catch (Exception e) { return dflt; }
    }
}
