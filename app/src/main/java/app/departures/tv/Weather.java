package app.departures.tv;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Today's weather from Open-Meteo (no key, no account): the temperature now, the condition, today's high and low
 * and whether rain is likely. Cached on disk so the board shows the last reading while a fetch is pending or failing.
 */
final class Weather {
    private static final long FRESH_MS = 30 * 60_000L;

    static Faces.Weather load(Context ctx, Config cfg, boolean allowNetwork) {
        File cache = new File(ctx.getFilesDir(), "weather.json");
        Faces.Weather cached = read(cache);
        if (cached != null && System.currentTimeMillis() - cache.lastModified() < FRESH_MS) return cached;
        if (!allowNetwork || !cfg.hasLocation()) return cached;
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + cfg.latitude + "&longitude=" + cfg.longitude
                    + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                    + "&timezone=auto&forecast_days=1" + (cfg.imperial ? "&temperature_unit=fahrenheit" : "");
            String body = Net.get(url, null, 6000);
            if (body == null) return cached;
            JSONObject o = new JSONObject(body);
            JSONObject cur = o.getJSONObject("current"), daily = o.getJSONObject("daily");
            Faces.Weather w = new Faces.Weather();
            w.now = (int) Math.round(cur.getDouble("temperature_2m"));
            w.condition = condition(cur.getInt("weather_code"));
            w.high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
            w.low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
            w.rain = daily.optJSONArray("precipitation_probability_max") != null && daily.getJSONArray("precipitation_probability_max").optInt(0, 0) >= 50;
            JSONObject keep = new JSONObject().put("now", w.now).put("condition", w.condition).put("high", w.high).put("low", w.low).put("rain", w.rain);
            Files.write(cache.toPath(), keep.toString().getBytes(StandardCharsets.UTF_8));
            return w;
        } catch (Exception e) {
            android.util.Log.w("Departures", "weather fetch failed: " + e.getClass().getSimpleName());
            return cached;
        }
    }

    private static Faces.Weather read(File cache) {
        if (!cache.exists()) return null;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8));
            Faces.Weather w = new Faces.Weather();
            w.now = o.getInt("now"); w.condition = o.getString("condition"); w.high = o.getInt("high"); w.low = o.getInt("low"); w.rain = o.optBoolean("rain");
            return w;
        } catch (Exception e) { return null; }
    }

    /** WMO weather codes to short board words (22 columns, less the temperature). */
    static String condition(int code) {
        switch (code) {
            case 0: return "CLEAR";
            case 1: return "MAINLY CLEAR";
            case 2: return "PARTLY CLOUDY";
            case 3: return "OVERCAST";
            case 45: case 48: return "FOG";
            case 51: case 53: case 55: return "DRIZZLE";
            case 56: case 57: return "FREEZING DRIZZLE";
            case 61: case 80: return "LIGHT RAIN";
            case 63: case 81: return "RAIN";
            case 65: case 82: return "HEAVY RAIN";
            case 66: case 67: return "FREEZING RAIN";
            case 71: case 85: return "LIGHT SNOW";
            case 73: return "SNOW";
            case 75: case 86: return "HEAVY SNOW";
            case 77: return "SNOW GRAINS";
            case 95: return "THUNDERSTORM";
            case 96: case 99: return "HAIL STORM";
            default: return "";
        }
    }
}
