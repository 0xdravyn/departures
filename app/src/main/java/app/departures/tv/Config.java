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
    public int nowSeconds = 60, upNextSeconds = 30;
    public boolean present;

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
            if (faces != null) { c.nowSeconds = Math.max(10, faces.optInt("now_seconds", 60)); c.upNextSeconds = Math.max(10, faces.optInt("up_next_seconds", 30)); }
            c.present = true;
        } catch (Exception e) {
            android.util.Log.w("Departures", "config.json unreadable: " + e.getClass().getSimpleName());
        }
        return c;
    }

    public boolean hasLocation() { return !Double.isNaN(latitude) && !Double.isNaN(longitude); }

    /** True inside quiet hours, which may wrap past midnight. */
    public boolean quiet(int minuteOfDay) {
        return quietFromMin <= quietToMin ? (minuteOfDay >= quietFromMin && minuteOfDay < quietToMin)
                                          : (minuteOfDay >= quietFromMin || minuteOfDay < quietToMin);
    }

    private static int minutes(String hhmm, int dflt) {
        try { String[] p = hhmm.trim().split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); } catch (Exception e) { return dflt; }
    }
}
