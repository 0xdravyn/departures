package app.departures.tv;

import android.content.Context;
import android.provider.Settings;

/**
 * Screensaver mode: Departures as the TV's screensaver, and the idle delay before it starts. Google TV's own
 * Screen saver page does not list third-party screensavers, so this writes the two system settings itself,
 * which needs WRITE_SECURE_SETTINGS and WRITE_SETTINGS granted once over ADB (README, Setup step 5). Used by the
 * settings screen on the TV and by the settings page, so both behave the same.
 */
final class Screensaver {
    static final String OURS = "app.departures.tv/.DeparturesDream";
    static final String GOOGLE = "com.google.android.apps.tv.dreamx/.service.Backdrop";
    static final String GRANT_SECURE = "adb shell pm grant app.departures.tv android.permission.WRITE_SECURE_SETTINGS";
    static final String GRANT_SYSTEM = "adb shell appops set app.departures.tv WRITE_SETTINGS allow";

    static boolean canSecure(Context ctx) { return ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED; }
    static boolean canSystem(Context ctx) { return Settings.System.canWrite(ctx); }

    static boolean isOurs(Context ctx) { String cur = Settings.Secure.getString(ctx.getContentResolver(), "screensaver_components"); return cur != null && cur.contains("app.departures.tv"); }

    /** The TV's current screensaver by a short name, for the page and the settings row. */
    static String currentName(Context ctx) {
        String cur = Settings.Secure.getString(ctx.getContentResolver(), "screensaver_components");
        if (cur == null || cur.isEmpty()) return "TV default";
        if (cur.contains("app.departures.tv")) return "Departures";
        if (cur.contains("dreamx")) return "Google's Ambient";
        return "TV default";
    }

    /** Idle minutes before the screensaver starts, or -1 when unreadable. */
    static int minutes(Context ctx) { try { return (int) Math.round(Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT) / 60000.0); } catch (Exception e) { return -1; } }

    /** Switches Departures on as the screensaver (remembering what was there) or back to the previous one. Returns null, or why not. */
    static String enable(Context ctx, boolean on) {
        if (!canSecure(ctx)) return "Needs one line over ADB first: " + GRANT_SECURE;
        android.content.SharedPreferences p = ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE);
        String cur = Settings.Secure.getString(ctx.getContentResolver(), "screensaver_components");
        if (on) {
            if (cur != null && !cur.isEmpty() && !cur.contains("app.departures.tv")) p.edit().putString("previous_screensaver", cur).apply();
            Settings.Secure.putString(ctx.getContentResolver(), "screensaver_components", OURS);
            Settings.Secure.putInt(ctx.getContentResolver(), "screensaver_enabled", 1);
            // a sensible start: two minutes idle, unless the TV is already quicker
            if (canSystem(ctx)) { int m = minutes(ctx); if (m < 0 || m > 2) setMinutes(ctx, 2); }
        } else {
            String prev = p.getString("previous_screensaver", "");
            Settings.Secure.putString(ctx.getContentResolver(), "screensaver_components", prev.isEmpty() ? GOOGLE : prev);
        }
        return null;
    }

    static String setMinutes(Context ctx, int minutes) {
        if (!canSystem(ctx)) return "Needs one line over ADB first: " + GRANT_SYSTEM;
        int m = Math.max(1, Math.min(120, minutes));
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, m * 60000);
        return null;
    }
}
