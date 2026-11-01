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
 * Quotes for the Markets face, up to five, from two keyless sources: Yahoo Finance's public chart endpoint for
 * stocks, indices and futures (unofficial, delayed, may change without notice) and CoinGecko's simple price API
 * for crypto. Fetched every 10 minutes while the board is on screen; the last good set is cached on disk so a
 * failed fetch keeps yesterday's numbers rather than an empty face. Informational only, never for trading.
 */
final class Markets {
    /** state: "OPEN" during a regular session, "CLOSED" otherwise (pre- and after-hours count as closed), null for crypto. */
    static final class Quote { String label, key; double price; double changePct; boolean ok; String state; }
    private static final long FRESH_MS = 10 * 60_000L;

    static List<Quote> load(Context ctx, Config cfg, boolean allowNetwork) { return load(ctx, cfg, allowNetwork, false); }

    /** force skips the 10-minute cache: used while the Markets face is on screen so a change can be watched. */
    static List<Quote> load(Context ctx, Config cfg, boolean allowNetwork, boolean force) {
        if (cfg.markets.isEmpty()) return new ArrayList<>();
        File cache = new File(ctx.getFilesDir(), "markets.json");
        List<Quote> cached = read(cache, cfg);
        if (!force && cached != null && System.currentTimeMillis() - cache.lastModified() < FRESH_MS) return cached;
        if (!allowNetwork) return cached == null ? new ArrayList<>() : cached;
        List<Quote> out = new ArrayList<>();
        JSONObject gecko = geckoPrices(cfg);
        boolean any = false;
        for (Config.Market m : cfg.markets) {
            Quote q = new Quote(); q.label = m.label; q.key = m.source + ":" + m.symbol;
            if (m.source.equals("coingecko")) {
                JSONObject c = gecko == null ? null : gecko.optJSONObject(m.symbol.toLowerCase());
                if (c != null && c.has("usd")) { q.price = c.optDouble("usd"); q.changePct = c.optDouble("usd_24h_change", 0); q.ok = true; }
            } else {
                double[] p = yahoo(m.symbol);
                if (p != null) { q.price = p[0]; q.changePct = p[1]; q.ok = true; q.state = p[2] == 1 ? "OPEN" : "CLOSED"; }
            }
            if (!q.ok && cached != null) for (Quote old : cached) if (q.key.equals(old.key) && old.ok) { q = old; break; }
            any |= q.ok;
            out.add(q);
        }
        if (any) {
            try {
                JSONArray arr = new JSONArray();
                for (Quote q : out) arr.put(new JSONObject().put("label", q.label).put("key", q.key).put("price", q.price).put("change", q.changePct).put("ok", q.ok).put("state", q.state == null ? JSONObject.NULL : q.state));
                Files.write(cache.toPath(), arr.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** One instrument, looked up afresh with its name: what the setup page shows under each symbol. */
    static final class Lookup { boolean ok; String name = ""; double price, changePct; }
    static Lookup lookup(Config.Market m) {
        Lookup l = new Lookup();
        try {
            if (m.source.equals("coingecko")) {
                String body = Net.get("https://api.coingecko.com/api/v3/coins/" + java.net.URLEncoder.encode(m.symbol.toLowerCase(), "UTF-8") + "?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false&sparkline=false", null, 8000);
                if (body == null) return l;
                JSONObject o = new JSONObject(body);
                JSONObject md = o.getJSONObject("market_data");
                l.name = o.optString("name", m.symbol); l.price = md.getJSONObject("current_price").getDouble("usd"); l.changePct = md.optDouble("price_change_percentage_24h", 0); l.ok = true;
            } else {
                String[] name = new String[1];
                double[] p = yahoo(m.symbol, name);
                if (p == null) return l;
                l.name = name[0] == null || name[0].isEmpty() ? m.symbol : name[0]; l.price = p[0]; l.changePct = p[1]; l.ok = true;
            }
        } catch (Exception e) { l.ok = false; }
        return l;
    }

    private static double[] yahoo(String symbol) { return yahoo(symbol, null); }

    /** {price, change % versus the previous close, 1 if the regular session is open}, or null; name[0] gets the instrument's name when asked. */
    private static double[] yahoo(String symbol, String[] name) {
        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + java.net.URLEncoder.encode(symbol, "UTF-8") + "?range=5d&interval=1d";
            String body = Net.get(url, null, 8000);
            if (body == null) return null;
            JSONObject meta = new JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta");
            if (name != null) name[0] = meta.optString("shortName", meta.optString("longName", ""));
            double price = meta.getDouble("regularMarketPrice");
            double prev = meta.optDouble("regularMarketPreviousClose", Double.NaN);
            if (Double.isNaN(prev)) {
                // fall back to the last two daily closes in the series
                JSONArray closes = new JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close");
                List<Double> vals = new ArrayList<>();
                for (int i = 0; i < closes.length(); i++) if (!closes.isNull(i)) vals.add(closes.getDouble(i));
                prev = vals.size() >= 2 ? vals.get(vals.size() - 2) : meta.optDouble("chartPreviousClose", price);
            }
            boolean open = "REGULAR".equals(meta.optString("marketState", ""));
            return new double[]{price, prev > 0 ? (price - prev) / prev * 100 : 0, open ? 1 : 0};
        } catch (Exception e) {
            android.util.Log.w("Departures", "quote failed for " + symbol + ": " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static JSONObject geckoPrices(Config cfg) {
        StringBuilder ids = new StringBuilder();
        for (Config.Market m : cfg.markets) if (m.source.equals("coingecko")) { if (ids.length() > 0) ids.append(','); ids.append(m.symbol.toLowerCase()); }
        if (ids.length() == 0) return null;
        try {
            String body = Net.get("https://api.coingecko.com/api/v3/simple/price?ids=" + ids + "&vs_currencies=usd&include_24hr_change=true", null, 8000);
            return body == null ? null : new JSONObject(body);
        } catch (Exception e) { return null; }
    }

    private static List<Quote> read(File cache, Config cfg) {
        if (!cache.exists()) return null;
        try {
            JSONArray arr = new JSONArray(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8));
            List<Quote> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) { JSONObject o = arr.getJSONObject(i); Quote q = new Quote(); q.label = o.getString("label"); q.key = o.optString("key", ""); q.price = o.getDouble("price"); q.changePct = o.getDouble("change"); q.ok = o.optBoolean("ok", true); q.state = o.isNull("state") ? null : o.optString("state"); out.add(q); }
            // the cache only counts for the same list of assets (source and symbol, not the label: a renamed or
            // re-pointed entry must fetch afresh)
            if (out.size() != cfg.markets.size()) return null;
            for (int i = 0; i < out.size(); i++) { Config.Market mk = cfg.markets.get(i); if (!out.get(i).key.equals(mk.source + ":" + mk.symbol)) return null; out.get(i).label = mk.label; }
            return out;
        } catch (Exception e) { return null; }
    }

    /** "7,580", "204.64", "0.1176": enough digits to read, never more than fits. */
    static String price(double p) {
        if (p >= 100000) return String.format(java.util.Locale.US, "%,.0f", p);
        if (p >= 1000) return String.format(java.util.Locale.US, "%,.0f", p);
        if (p >= 100) return String.format(java.util.Locale.US, "%.1f", p);
        if (p >= 1) return String.format(java.util.Locale.US, "%.2f", p);
        return String.format(java.util.Locale.US, "%.4f", p);
    }

    static String change(double pct) {
        String s = String.format(java.util.Locale.US, "%+.1f%%", pct);
        return s.replace("+0.0%", "0.0%").replace("-0.0%", "0.0%");
    }
}
