package app.departures.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/**
 * The setup page, served by the TV itself on the local network (http://<tv>:8787) while Departures is on
 * screen (as the app or the screensaver). One HTML file from the assets, a small JSON API behind it. Every API call
 * carries the four-digit code shown on the TV; the TV checks it on each request, the page only shows the answer.
 * Calendar addresses go in and never come back out. No dependencies: a plain socket and a hand-written HTTP/1.0.
 */
final class SetupServer {
    static final int PORT = 8787;
    private static final int MAX_BODY = 64 * 1024;
    private static SetupServer instance;
    /** Set by whatever shows the board; called on the main thread after the page changed content or settings. */
    static volatile Runnable onChange;

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ServerSocket socket;
    private int refs;
    /** Wrong codes: after five, every API request waits out a pause that doubles each time, up to ten minutes. */
    private int wrongCodes;
    private long pausedUntil;

    private SetupServer(Context ctx) { app = ctx.getApplicationContext(); }

    static synchronized SetupServer get(Context ctx) { if (instance == null) instance = new SetupServer(ctx); return instance; }

    /** The board came on screen: serve now and keep serving. */
    synchronized void retain() { refs++; start(); }

    /** The board went away. The socket stays open as long as the process lives (the TV reclaims it when it likes), so a page already open keeps working. */
    synchronized void release() { refs = Math.max(0, refs - 1); }

    private synchronized void start() {
        if (socket != null) return;
        try {
            socket = new ServerSocket(PORT, 8);
            socket.setReuseAddress(true);
            Thread t = new Thread(this::accept, "departures-setup");
            t.setDaemon(true); t.start();
            android.util.Log.i("Departures", "setup page on port " + PORT);
        } catch (Exception e) {
            android.util.Log.w("Departures", "setup page could not start: " + e.getClass().getSimpleName());
            socket = null;
        }
    }

    private synchronized void stop() {
        if (socket == null) return;
        try { socket.close(); } catch (Exception ignored) {}
        socket = null;
        android.util.Log.i("Departures", "setup page off");
    }

    private void accept() {
        ServerSocket s = socket;
        while (s != null && !s.isClosed()) {
            try { Socket c = s.accept(); c.setSoTimeout(10_000); new Thread(() -> serve(c), "departures-setup-req").start(); }
            catch (Exception e) { break; }
        }
    }

    // ----- one request -----

    private void serve(Socket c) {
        try (Socket sock = c; InputStream in = sock.getInputStream(); OutputStream out = sock.getOutputStream()) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String line = r.readLine();
            if (line == null) return;
            String[] req = line.split(" ");
            if (req.length < 2) { send(out, 400, "text/plain", "bad request"); return; }
            String method = req[0], target = req[1];
            int len = 0; String code = null; String h;
            while ((h = r.readLine()) != null && !h.isEmpty()) {
                int k = h.indexOf(':'); if (k < 0) continue;
                String name = h.substring(0, k).trim().toLowerCase(Locale.US), val = h.substring(k + 1).trim();
                if (name.equals("content-length")) len = Integer.parseInt(val);
                if (name.equals("x-code")) code = val;
            }
            if (len > MAX_BODY) { send(out, 413, "text/plain", "too large"); return; }
            char[] buf = new char[len]; int got = 0;
            while (got < len) { int n = r.read(buf, got, len - got); if (n < 0) break; got += n; }
            String body = new String(buf, 0, got);
            String path = target, query = "";
            int q = target.indexOf('?'); if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            if (code == null) code = param(query, "code");
            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) { sendAsset(out, "setup.html", "text/html; charset=utf-8"); return; }
            if (method.equals("GET") && path.equals("/logo.svg")) { sendAsset(out, "logo.svg", "image/svg+xml"); return; }
            if (!path.startsWith("/api/")) { send(out, 404, "text/plain", "not found"); return; }
            if (paused()) { send(out, 429, "application/json", "{\"error\":\"wait\"}"); return; }
            if (!codeOk(code)) { wrongCode(); send(out, 403, "application/json", "{\"error\":\"code\"}"); return; }
            synchronized (this) { wrongCodes = 0; }
            JSONObject res = api(method, path.substring(5), query, body);
            send(out, res.optInt("_status", 200), "application/json", res.toString());
        } catch (Exception e) {
            android.util.Log.w("Departures", "setup request failed: " + e.getClass().getSimpleName());
        }
    }

    private synchronized boolean paused() { return System.currentTimeMillis() < pausedUntil; }

    private synchronized void wrongCode() {
        wrongCodes++;
        if (wrongCodes >= 5) pausedUntil = System.currentTimeMillis() + Math.min(600_000L, 30_000L << Math.min(5, wrongCodes - 5));
    }

    private boolean codeOk(String code) {
        if (code == null || code.length() != 4) return false;
        String want = Config.pairCode(app);
        int diff = 0; for (int i = 0; i < 4; i++) diff |= code.charAt(i) ^ want.charAt(i);
        return diff == 0;
    }

    private JSONObject api(String method, String name, String query, String body) throws Exception {
        JSONObject in = body.isEmpty() ? new JSONObject() : new JSONObject(body);
        switch (name) {
            case "state": return state();
            case "content": if (!method.equals("POST")) break; Config.saveContent(app, in); changed(); return state();
            case "settings": if (!method.equals("POST")) break; saveSettings(in); changed(); return state();
            case "reset-content": if (!method.equals("POST")) break; Config.resetContent(app); changed(); return state();
            case "reset-settings": if (!method.equals("POST")) break; Config.resetSettings(app); changed(); return state();
            case "geocode": return geocode(param(query, "q"));
            case "calendar-test": if (!method.equals("POST")) break; return calendarTest(in.optString("url", ""), in.optInt("index", -1));
            case "market": return market(in.optString("symbol", ""));
            case "screensaver": {
                if (!method.equals("POST")) break;
                String err = null;
                if (in.has("enable")) err = Screensaver.enable(app, in.optBoolean("enable"));
                if (err == null && in.has("minutes")) err = Screensaver.setMinutes(app, in.optInt("minutes", 2));
                JSONObject s = state();
                if (err != null) { s.put("error", err); s.put("_status", 409); }
                return s;
            }
            default: break;
        }
        return new JSONObject().put("error", "unknown").put("_status", 404);
    }

    /** Everything the page shows. Calendar addresses are reported as present or absent, never by value. */
    private JSONObject state() throws Exception {
        Config c = Config.load(app);
        JSONObject o = new JSONObject();
        o.put("tv", tvName(app)).put("address", addressLine()).put("paired", true);
        JSONObject content = new JSONObject().put("present", c.present);
        if (c.hasLocation()) content.put("location", new JSONObject().put("name", c.locationName).put("latitude", c.latitude).put("longitude", c.longitude));
        content.put("clock24", c.clock24).put("imperial", c.imperial);
        content.put("today", new JSONObject().put("mode", c.todayMessage ? "message" : "calendars").put("lines", new JSONArray().put(c.todayLine1).put(c.todayLine2)));
        JSONArray cals = new JSONArray();
        for (Config.Calendar cal : c.calendars) cals.put(new JSONObject().put("label", cal.label).put("set", true));
        content.put("calendars", cals);
        JSONArray mk = new JSONArray();
        for (Config.Market m : c.markets) mk.put(new JSONObject().put("symbol", m.symbol).put("label", m.label));
        content.put("markets", mk);
        JSONArray msg = new JSONArray();
        for (int i = 0; i < Config.MESSAGE_LINES; i++) msg.put(i < c.message.size() ? c.message.get(i) : "");
        content.put("message", msg);
        o.put("content", content);
        JSONObject views = new JSONObject()
                .put("today", new JSONObject().put("on", c.todayOn).put("seconds", c.todaySeconds))
                .put("keep_watching", new JSONObject().put("on", c.keepWatchingOn).put("seconds", c.keepWatchingSeconds))
                .put("markets", new JSONObject().put("on", c.marketsOn).put("seconds", c.marketsSeconds))
                .put("message", new JSONObject().put("on", c.messageOn).put("seconds", c.messageSeconds));
        JSONObject settings = new JSONObject().put("views", views).put("flips", c.flips).put("brightness", c.brightness).put("clockSeconds", c.clockSeconds).put("scatter", c.scatter)
                .put("quiet", new JSONObject().put("enabled", c.quietEnabled).put("from", hhmm(c.quietFromMin)).put("to", hhmm(c.quietToMin)))
                .put("screensaver", new JSONObject().put("ours", Screensaver.isOurs(app)).put("current", Screensaver.currentName(app)).put("minutes", Screensaver.minutes(app))
                        .put("canSecure", Screensaver.canSecure(app)).put("canSystem", Screensaver.canSystem(app)).put("grantSecure", Screensaver.GRANT_SECURE).put("grantSystem", Screensaver.GRANT_SYSTEM));
        o.put("settings", settings);
        o.put("watchNext", app.checkSelfPermission("android.permission.READ_TV_LISTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED);
        return o;
    }

    private void saveSettings(JSONObject in) {
        android.content.SharedPreferences.Editor e = app.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).edit();
        JSONObject views = in.optJSONObject("views");
        if (views != null) {
            int on = 0;
            for (String k : new String[]{"today", "keep_watching", "markets", "message"}) { JSONObject v = views.optJSONObject(k); if (v != null && v.optBoolean("on", true)) on++; }
            for (String k : new String[]{"today", "keep_watching", "markets", "message"}) {
                JSONObject v = views.optJSONObject(k); if (v == null) continue;
                boolean isOn = v.optBoolean("on", true) || (on == 0 && k.equals("today"));   // at least one view stays on
                e.putBoolean(k + "_on", isOn).putInt(k + "_seconds", Math.max(10, Math.min(3600, v.optInt("seconds", 60))));
            }
        }
        if (in.has("flips")) e.putBoolean("flips", in.optBoolean("flips", true));
        if (in.has("clockSeconds")) e.putBoolean("clock_seconds", in.optBoolean("clockSeconds", false));
        if (in.has("scatter")) e.putBoolean("scatter", in.optBoolean("scatter", true));
        if (in.has("brightness")) e.putFloat("brightness", (float) Math.max(0.4, Math.min(1.0, in.optDouble("brightness", 1.0))));
        JSONObject quiet = in.optJSONObject("quiet");
        if (quiet != null) {
            e.putBoolean("quiet_enabled", quiet.optBoolean("enabled", true));
            e.putInt("quiet_from", Config.minutes(quiet.optString("from", "23:00"), 23 * 60));
            e.putInt("quiet_to", Config.minutes(quiet.optString("to", "07:00"), 7 * 60));
        }
        e.apply();
    }

    private JSONObject geocode(String q) throws Exception {
        JSONObject o = new JSONObject().put("results", new JSONArray());
        if (q == null || q.trim().length() < 2) return o;
        String body = Net.get("https://geocoding-api.open-meteo.com/v1/search?name=" + java.net.URLEncoder.encode(q.trim(), "UTF-8") + "&count=6&language=en&format=json", null, 6000);
        if (body == null) return o.put("error", "The TV could not reach the geocoder");
        JSONArray res = new JSONObject(body).optJSONArray("results");
        JSONArray out = new JSONArray();
        if (res != null) for (int i = 0; i < res.length(); i++) {
            JSONObject r = res.getJSONObject(i);
            out.put(new JSONObject().put("name", r.optString("name")).put("admin1", r.optString("admin1", "")).put("country", r.optString("country", ""))
                    .put("latitude", r.optDouble("latitude")).put("longitude", r.optDouble("longitude")).put("timezone", r.optString("timezone", "")));
        }
        return o.put("results", out);
    }

    /** Fetches a calendar once and counts the next day's events. The address is used and forgotten, never logged. */
    private JSONObject calendarTest(String url, int index) throws Exception {
        JSONObject o = new JSONObject();
        if (url.isEmpty() && index >= 0) { Config c = Config.load(app); if (index < c.calendars.size()) url = c.calendars.get(index).url; }
        if (!url.startsWith("https://")) return o.put("ok", false).put("message", "Paste the address that starts with https://");
        Net.Response r = Net.fetch(url, null, null, 10000);
        if (r == null) return o.put("ok", false).put("message", "The TV could not reach that address");
        if (r.code == 404) return o.put("ok", false).put("message", "Not found: for a private calendar, use the secret address, not the public one");
        if (r.code != 200 || r.body == null || !r.body.contains("BEGIN:VCALENDAR")) return o.put("ok", false).put("message", "That address did not return a calendar (HTTP " + r.code + ")");
        long now = System.currentTimeMillis();
        int n = Calendars.parse(r.body, "", now, now + 86_400_000L).size();
        return o.put("ok", true).put("events", n).put("message", "Connected · " + (n == 0 ? "nothing in the next day" : n + (n == 1 ? " event" : " events") + " in the next day"));
    }

    /** One symbol looked up the way the board will: source, name and the current price. */
    private JSONObject market(String symbol) throws Exception {
        JSONObject o = new JSONObject();
        Config.Market m = Config.market(symbol);
        if (m == null) return o.put("ok", false);
        Markets.Lookup l = Markets.lookup(m);
        o.put("ok", l.ok).put("source", m.source.equals("coingecko") ? "CoinGecko" : "Yahoo Finance").put("symbol", m.symbol);
        if (l.ok) o.put("name", l.name).put("price", Markets.price(l.price)).put("change", Markets.change(l.changePct));
        return o;
    }

    private void changed() { ui.post(() -> { Runnable r = onChange; if (r != null) r.run(); }); }

    // ----- helpers -----

    private void sendAsset(OutputStream out, String name, String type) throws Exception {
        try (InputStream in = app.getAssets().open(name); java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            sendBytes(out, 200, type, buf.toByteArray());
        }
    }

    private static void send(OutputStream out, int status, String type, String body) throws Exception { sendBytes(out, status, type, body.getBytes(StandardCharsets.UTF_8)); }

    private static void sendBytes(OutputStream out, int status, String type, byte[] body) throws Exception {
        String head = "HTTP/1.0 " + status + " " + (status == 200 ? "OK" : status == 403 ? "Forbidden" : status == 404 ? "Not Found" : status == 429 ? "Too Many Requests" : "Error") + "\r\n"
                + "Content-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1)); out.write(body); out.flush();
    }

    private static String param(String query, String key) {
        for (String p : query.split("&")) { int k = p.indexOf('='); if (k > 0 && p.substring(0, k).equals(key)) { try { return java.net.URLDecoder.decode(p.substring(k + 1), "UTF-8"); } catch (Exception e) { return null; } } }
        return null;
    }

    private static String hhmm(int m) { return String.format(Locale.US, "%02d:%02d", m / 60, m % 60); }

    /** "192.0.2.10:8787" for the board and the settings row; null when the TV has no local address. */
    static String addressLine() { String ip = localIp(); return ip == null ? null : ip + ":" + PORT; }

    static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            for (NetworkInterface ni : Collections.list(ifs)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    if (a.isSiteLocalAddress()) { if (ni.getName().startsWith("wlan") || ni.getName().startsWith("eth")) return a.getHostAddress(); if (fallback == null) fallback = a.getHostAddress(); }
                }
            }
            return fallback;
        } catch (Exception e) { return null; }
    }

    static String tvName(Context ctx) {
        String n = Settings.Global.getString(ctx.getContentResolver(), "device_name");
        return n == null || n.isEmpty() ? android.os.Build.MODEL : n;
    }
}
