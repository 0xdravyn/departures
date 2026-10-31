package app.departures.tv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings on the TV, with the remote: Up/Down to a row, OK or Right to step a value forward, Left back, Back to
 * leave. One row per view: how long it stays before the next, or Off to drop it from the rotation (at least one view
 * stays on). Stored in private preferences and read by both the board app and the screensaver, so one change
 * covers both. Reached by holding OK on the board, from the Menu button, or from the TV's Screen saver settings.
 * What needs typing (coordinates, calendar addresses, the market list) stays in config.json.
 */
public final class SettingsActivity extends Activity {
    private static final int[] SECONDS = {15, 30, 60, 120, 300, 600};
    private static final float[] BRIGHTNESS = {1f, 0.8f, 0.6f, 0.4f};

    private SharedPreferences prefs;
    private Config cfg;
    private ListView list;
    private final List<Row> rows = new ArrayList<>();

    private abstract static class Row {
        final String title; Row(String t) { title = t; }
        abstract String value();
        abstract void step(int dir);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE);
        cfg = Config.load(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK); root.setPadding(dp(80), dp(36), dp(80), dp(28));
        TextView head = new TextView(this); head.setText("DEPARTURES  ·  SETTINGS"); head.setTextColor(0xFFE8781E); head.setTextSize(22); head.setTypeface(Typeface.DEFAULT_BOLD); head.setLetterSpacing(0.1f);
        root.addView(head);
        TextView sub = new TextView(this); sub.setTextColor(0xFF888888); sub.setTextSize(15); sub.setPadding(0, dp(6), 0, dp(14));
        sub.setText(status());
        root.addView(sub);
        list = new ListView(this); list.setDividerHeight(dp(1)); list.setSelector(new android.graphics.drawable.ColorDrawable(0x22FFFFFF));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = new TextView(this); hint.setTextColor(0xFF666666); hint.setTextSize(14); hint.setPadding(0, dp(12), 0, 0);
        hint.setText(screensaverHint());
        root.addView(hint);
        setContentView(root);
        buildRows();
        list.setAdapter(new ArrayAdapter<Row>(this, 0, rows) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                Row r = rows.get(pos);
                boolean heading = r instanceof Heading;
                LinearLayout line = new LinearLayout(this.getContext()); line.setOrientation(LinearLayout.HORIZONTAL); line.setPadding(dp(16), heading ? dp(18) : dp(12), dp(16), heading ? dp(4) : dp(12));
                TextView t = new TextView(this.getContext()); t.setText(r.title); t.setTextColor(heading ? 0xFFE8781E : Color.WHITE); t.setTextSize(heading ? 14 : 20); if (heading) { t.setLetterSpacing(0.1f); t.setTypeface(Typeface.DEFAULT_BOLD); }
                TextView v = new TextView(this.getContext()); v.setText(r.value()); v.setTextColor(0xFFF1C426); v.setTextSize(20); v.setGravity(Gravity.END);
                line.addView(t, new LinearLayout.LayoutParams(0, -2, 1f)); line.addView(v, new LinearLayout.LayoutParams(-2, -2));
                return line;
            }
            @Override public boolean isEnabled(int pos) { return !(rows.get(pos) instanceof Heading); }
            @Override public boolean areAllItemsEnabled() { return false; }
        });
        list.setOnItemClickListener((p, v, pos, id) -> { rows.get(pos).step(1); refresh(); });
        list.requestFocus();
        list.setSelectionFromTop(1, dp(56));   // first real row selected, the VIEWS heading still visible above it
    }

    private static final class Heading extends Row { Heading(String t) { super(t); } String value() { return ""; } void step(int d) {} }

    /** The footer: Han's line, plus the two ADB grants when the screensaver rows are locked. */
    private String screensaverHint() {
        String s = "Press Back to save. Changes also apply to the screensaver mode.";
        if (!canSecure() || !canSystem()) s += "  Screensaver rows unlock after, over ADB:  adb shell pm grant app.departures.tv android.permission.WRITE_SECURE_SETTINGS  and  adb shell appops set app.departures.tv WRITE_SETTINGS allow";
        return s;
    }

    private String status() {
        StringBuilder s = new StringBuilder();
        s.append(cfg.present ? "Configuration imported" : "No configuration yet (push config.json over ADB)");
        if (cfg.present) {
            s.append("  ·  ").append(cfg.calendars.size()).append(cfg.calendars.size() == 1 ? " calendar" : " calendars");
            s.append("  ·  ").append(cfg.hasLocation() ? (cfg.locationName.isEmpty() ? "location set" : cfg.locationName) : "no location");
            s.append("  ·  ").append(cfg.markets.isEmpty() ? "markets off in config" : cfg.markets.size() + " markets");
        }
        return s.toString();
    }

    private void buildRows() {
        rows.clear();
        rows.add(new Heading("VIEWS"));
        rows.add(view("Now  (date, time, weather, next event)", "now", 60));
        rows.add(view("Keep watching?  (what you were watching)", "keep_watching", 30));
        rows.add(view("Markets  (prices and the day's change)", "markets", 60));
        rows.add(new Heading("BOARD"));
        rows.add(new Row("Flip animation") {
            String value() { return prefs.getBoolean("flips", true) ? "On" : "Off, changes appear at once"; }
            void step(int d) { prefs.edit().putBoolean("flips", !prefs.getBoolean("flips", true)).apply(); }
        });
        rows.add(new Row("Brightness") {
            String value() { return Math.round(prefs.getFloat("brightness", 1f) * 100) + "%"; }
            void step(int d) { float b = prefs.getFloat("brightness", 1f); int i = 0; for (int k = 0; k < BRIGHTNESS.length; k++) if (Math.abs(BRIGHTNESS[k] - b) < 0.01f) i = k; prefs.edit().putFloat("brightness", BRIGHTNESS[Math.floorMod(i + d, BRIGHTNESS.length)]).apply(); }
        });
        rows.add(new Row("Clock") {
            String value() { return prefs.getBoolean("clock24", cfg.clock24) ? "24-hour" : "12-hour"; }
            void step(int d) { prefs.edit().putBoolean("clock24", !prefs.getBoolean("clock24", cfg.clock24)).apply(); }
        });
        rows.add(new Row("Temperature") {
            String value() { return prefs.getBoolean("imperial", cfg.imperial) ? "Fahrenheit" : "Celsius"; }
            void step(int d) { prefs.edit().putBoolean("imperial", !prefs.getBoolean("imperial", cfg.imperial)).apply(); }
        });
        rows.add(new Heading("QUIET HOURS  (the board dims at night)"));
        rows.add(new Row("Quiet hours") {
            String value() { return prefs.getBoolean("quiet_enabled", true) ? "On" : "Off"; }
            void step(int d) { prefs.edit().putBoolean("quiet_enabled", !prefs.getBoolean("quiet_enabled", true)).apply(); }
        });
        rows.add(time("From", "quiet_from", cfg.quietFromMin));
        rows.add(time("Until", "quiet_to", cfg.quietToMin));
        rows.add(new Heading("SCREENSAVER  (the TV's own page does not list third-party screensavers)"));
        rows.add(new Row("TV screensaver") {
            String value() {
                String cur = android.provider.Settings.Secure.getString(getContentResolver(), "screensaver_components");
                boolean ours = cur != null && cur.contains("app.departures.tv");
                return canSecure() ? (ours ? "Departures" : "TV default") : (ours ? "Departures  (locked, see below)" : "TV default  (locked, see below)");
            }
            void step(int d) {
                if (!canSecure()) { Toast.makeText(SettingsActivity.this, "Grant WRITE_SECURE_SETTINGS over ADB first (see README)", Toast.LENGTH_LONG).show(); return; }
                String cur = android.provider.Settings.Secure.getString(getContentResolver(), "screensaver_components");
                boolean ours = cur != null && cur.contains("app.departures.tv");
                if (ours) { String prev = prefs.getString("previous_screensaver", ""); android.provider.Settings.Secure.putString(getContentResolver(), "screensaver_components", prev.isEmpty() ? "com.google.android.apps.tv.dreamx/.service.Backdrop" : prev); }
                else {
                    if (cur != null && !cur.isEmpty()) prefs.edit().putString("previous_screensaver", cur).apply();
                    android.provider.Settings.Secure.putString(getContentResolver(), "screensaver_components", "app.departures.tv/.DeparturesDream");
                    android.provider.Settings.Secure.putInt(getContentResolver(), "screensaver_enabled", 1);
                    // a sensible start: two minutes idle, unless the TV is already quicker
                    try { if (canSystem() && android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT) > 120000) android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 120000); } catch (Exception ignored) {}
                }
            }
        });
        rows.add(new Row("Start after the TV idles for") {
            int[] mins = {1, 2, 5, 10, 15, 30};
            int current() { try { return (int) Math.round(android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT) / 60000.0); } catch (Exception e) { return -1; } }
            String value() { int m = current(); String s = m < 0 ? "unknown" : m + (m == 1 ? " minute" : " minutes"); return canSystem() ? s : s + "  (locked, see below)"; }
            void step(int d) {
                if (!canSystem()) { Toast.makeText(SettingsActivity.this, "Grant WRITE_SETTINGS over ADB first (see README)", Toast.LENGTH_LONG).show(); return; }
                int m = current(), i = 1; for (int k = 0; k < mins.length; k++) if (mins[k] == m) i = k;
                android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT, mins[Math.floorMod(i + d, mins.length)] * 60000);
            }
        });
        rows.add(new Heading(""));
        rows.add(new Row("Reset these settings") {
            String value() { return ""; }
            void step(int d) { prefs.edit().clear().apply(); cfg = Config.load(SettingsActivity.this); Toast.makeText(SettingsActivity.this, "Settings reset", Toast.LENGTH_SHORT).show(); }
        });
    }

    /** A view's row: Off, or how long it stays. The last view that is on cannot be switched off. */
    private Row view(String title, String key, int dflt) {
        return new Row(title) {
            boolean on() { return prefs.getBoolean(key + "_on", true); }
            int secs() { return prefs.getInt(key + "_seconds", dflt); }
            String value() { if (!on()) return "Off"; int s = secs(); return s >= 60 ? (s / 60) + (s == 60 ? " minute" : " minutes") : s + " seconds"; }
            void step(int d) {
                // the list runs Off, 15 s, 30 s, 1, 2, 5, 10 min, and round again
                int i = on() ? 1 + indexOf(secs()) : 0;
                int n = Math.floorMod(i + d, SECONDS.length + 1);
                if (n == 0) {
                    int othersOn = (prefs.getBoolean("now_on", true) ? 1 : 0) + (prefs.getBoolean("keep_watching_on", true) ? 1 : 0) + (prefs.getBoolean("markets_on", true) ? 1 : 0) - (on() ? 1 : 0);
                    if (othersOn == 0) { Toast.makeText(SettingsActivity.this, "Keep at least one view on", Toast.LENGTH_SHORT).show(); return; }
                    prefs.edit().putBoolean(key + "_on", false).apply();
                } else prefs.edit().putBoolean(key + "_on", true).putInt(key + "_seconds", SECONDS[n - 1]).apply();
            }
            int indexOf(int s) { for (int k = 0; k < SECONDS.length; k++) if (SECONDS[k] == s) return k; return 2; }
        };
    }

    private Row time(String title, String key, int dflt) {
        return new Row(title) {
            String value() { int m = prefs.getInt(key, dflt); return String.format("%02d:%02d", m / 60, m % 60); }
            void step(int d) { int m = prefs.getInt(key, dflt); prefs.edit().putInt(key, Math.floorMod(m + 30 * d, 24 * 60)).apply(); }
        };
    }

    private boolean canSecure() { return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED; }
    private boolean canSystem() { return android.provider.Settings.System.canWrite(this); }

    private void refresh() { ((ArrayAdapter<?>) list.getAdapter()).notifyDataSetChanged(); }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        int pos = list.getSelectedItemPosition();
        if (pos >= 0 && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) { rows.get(pos).step(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); refresh(); return true; }
        return super.onKeyDown(code, e);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
