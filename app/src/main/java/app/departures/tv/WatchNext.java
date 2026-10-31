package app.departures.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

/**
 * The newest resume entry in Android TV's Watch Next table (what the home screen's "Continue watching" row shows),
 * for the Up next face. Needs READ_TV_LISTINGS, granted over ADB; without it the face is simply skipped.
 */
final class WatchNext {
    private static final long WINDOW_MS = 30L * 24 * 3600 * 1000;

    static Faces.Resume newest(Context ctx) {
        Faces.Resume best = null; long bestWhen = 0;
        PackageManager pm = ctx.getPackageManager();
        try (Cursor c = ctx.getContentResolver().query(Uri.parse("content://android.media.tv/watch_next_program"), null, null, null, null)) {
            long now = System.currentTimeMillis();
            while (c != null && c.moveToNext()) {
                int type = i(c, "watch_next_type", 0);
                if (type > 1) continue;
                long when = l(c, "last_engagement_time_utc_millis");
                if (when <= 0 || now - when > WINDOW_MS || when <= bestWhen) continue;
                long pos = l(c, "last_playback_position_millis"), dur = l(c, "duration_millis");
                if (type == 0 && dur > 0 && pos >= dur * 0.95) continue;
                String pkg = s(c, "package_name");
                Faces.Resume r = new Faces.Resume();
                r.title = nz(s(c, "title"), "Untitled");
                String se = se(c);
                String left = (dur > 0 && pos > 0) ? ((dur - pos) / 60000) + " min left" : (type == 1 ? "next episode" : "");
                r.detail = ((se == null ? "" : se) + (left.isEmpty() ? "" : "   " + left)).trim();
                r.app = label(pm, pkg);
                best = r; bestWhen = when;
            }
        } catch (SecurityException e) {
            android.util.Log.i("Departures", "Watch Next not readable (READ_TV_LISTINGS not granted); Up next face off");
        } catch (Exception e) {
            android.util.Log.w("Departures", "Watch Next query failed: " + e.getClass().getSimpleName());
        }
        return best;
    }

    private static String s(Cursor c, String col) { int k = c.getColumnIndex(col); return k < 0 || c.isNull(k) ? null : c.getString(k); }
    private static long l(Cursor c, String col) { int k = c.getColumnIndex(col); return k < 0 || c.isNull(k) ? 0 : c.getLong(k); }
    private static int i(Cursor c, String col, int d) { int k = c.getColumnIndex(col); return k < 0 || c.isNull(k) ? d : c.getInt(k); }
    private static String nz(String v, String d) { return v == null || v.isEmpty() ? d : v; }
    private static String se(Cursor c) { String s = s(c, "season_display_number"), e = s(c, "episode_display_number"); if (e == null) return null; return (s != null ? "S" + s : "") + "E" + e; }
    private static String label(PackageManager pm, String pkg) { if (pkg == null) return ""; try { return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (Exception e) { return pkg; } }
}
