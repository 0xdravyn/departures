package app.departures.tv;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One small HTTPS GET with timeouts. Returns null on any failure; never logs the URL (calendar addresses are secrets). */
final class Net {
    static final class Response { String body; String etag; String lastModified; int code; }

    static String get(String url, String ifNoneMatch, int timeoutMs) {
        Response r = fetch(url, ifNoneMatch, null, timeoutMs);
        return r == null || r.code != 200 ? null : r.body;
    }

    static Response fetch(String url, String ifNoneMatch, String ifModifiedSince, int timeoutMs) {
        if (!url.startsWith("https://")) return null;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(timeoutMs); con.setReadTimeout(timeoutMs);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "Departures/0.1 (Android TV screensaver)");
            if (ifNoneMatch != null) con.setRequestProperty("If-None-Match", ifNoneMatch);
            if (ifModifiedSince != null) con.setRequestProperty("If-Modified-Since", ifModifiedSince);
            Response r = new Response();
            r.code = con.getResponseCode();
            r.etag = con.getHeaderField("ETag");
            r.lastModified = con.getHeaderField("Last-Modified");
            if (r.code == 200) {
                try (InputStream in = con.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[16384]; int n; long total = 0;
                    while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; if (total > 8_000_000L) break; }
                    r.body = out.toString(StandardCharsets.UTF_8.name());
                }
            }
            return r;
        } catch (Exception e) {
            return null;
        } finally { if (con != null) con.disconnect(); }
    }
}
