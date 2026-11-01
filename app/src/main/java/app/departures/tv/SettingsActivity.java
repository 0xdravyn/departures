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
 * What needs typing (your city, calendar addresses, the custom message, the market list) is done on the setup
 * page from a computer; the first row says where it is.
 */
public final class SettingsActivity extends Activity {
    private static final int[] SECONDS = {15, 30, 60, 120, 300, 600};
    private static final float[] BRIGHTNESS = {1f, 0.8f, 0.6f, 0.4f};
    private static final String[] VIEW_KEYS = {"today", "keep_watching", "markets", "message"};

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
        list.setSelectionFromTop(1, dp(56));   // first real row selected, the heading still visible above it
    }

    @Override protected void onStart() { super.onStart(); SetupServer.get(this).retain(); }
    @Override protected void onStop() { SetupServer.get(this).release(); super.onStop(); }

    private static final class Heading extends Row { Heading(String t) { super(t); } String value() { return ""; } void step(int d) {} }

    /** The footer: Han's line, plus the two ADB grants when the screensaver rows are locked. */
    private String screensaverHint() {
        String s = "Press Back to save. Changes also apply to the screensaver mode.";
        if (!Screensaver.canSecure(this) || !Screensaver.canSystem(this)) s += "  Screensaver rows unlock after, over ADB:  " + Screensaver.GRANT_SECURE + "  and  " + Screensaver.GRANT_SYSTEM;
        return s;
    }

    private String status() {
        StringBuilder s = new StringBuilder();
        s.append(cfg.present ? "Set up" : "Nothing set up yet: open the address below on a computer");
        if (cfg.present) {
            s.append("  ·  ").append(cfg.calendars.size()).append(cfg.calendars.size() == 1 ? " calendar" : " calendars");
            s.append("  ·  ").append(cfg.hasLocation() ? (cfg.locationName.isEmpty() ? "location set" : cfg.locationName) : "no location");
            s.append("  ·  ").append(cfg.markets.isEmpty() ? "no markets" : cfg.markets.size() + " markets");
        }
        return s.toString();
    }

    private void buildRows() {
        rows.clear();
        rows.add(new Heading("SET UP  (your city, calendars, custom message, markets)"));
        rows.add(new Row("Open this address on a computer on the same Wi-Fi") {
            String value() { String a = SetupServer.addressLine(); return (a == null ? "TV is not on Wi-Fi" : "http://" + a) + "    code " + Config.pairCode(SettingsActivity.this); }
            void step(int d) { Config.newPairCode(SettingsActivity.this); Toast.makeText(SettingsActivity.this, "New code. Devices that were connected before will ask for it again.", Toast.LENGTH_LONG).show(); }
        });
        rows.add(new Heading("VIEWS"));
        rows.add(view("Today  (date, time, weather, next event or your note)", "today", 60));
        rows.add(view("Keep watching?  (what you were watching)", "keep_watching", 30));
        rows.add(view("Markets  (prices and the day's change)", "markets", 60));
        rows.add(view("Custom message  (your own board)", "message", 30));
        rows.add(new Heading("BOARD"));
        rows.add(new Row("Flip animation") {
            String value() { return prefs.getBoolean("flips", true) ? "On" : "Off, changes appear at once"; }
            void step(int d) { prefs.edit().putBoolean("flips", !prefs.getBoolean("flips", true)).apply(); }
        });
        rows.add(new Row("Brightness") {
            String value() { return Math.round(prefs.getFloat("brightness", 1f) * 100) + "%"; }
            void step(int d) { float b = prefs.getFloat("brightness", 1f); int i = 0; for (int k = 0; k < BRIGHTNESS.length; k++) if (Math.abs(BRIGHTNESS[k] - b) < 0.01f) i = k; prefs.edit().putFloat("brightness", BRIGHTNESS[Math.floorMod(i + d, BRIGHTNESS.length)]).apply(); }
        });
        rows.add(new Row("Random scatter effect  (a few blank bits flip now and then)") {
            String value() { return prefs.getBoolean("scatter", true) ? "On" : "Off"; }
            void step(int d) { prefs.edit().putBoolean("scatter", !prefs.getBoolean("scatter", true)).apply(); }
        });
        rows.add(new Row("Seconds on the clock") {
            String value() { return prefs.getBoolean("clock_seconds", false) ? "On, ticking" : "Off"; }
            void step(int d) { prefs.edit().putBoolean("clock_seconds", !prefs.getBoolean("clock_seconds", false)).apply(); }
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
        rows.add(new Heading("SCREENSAVER MODE  (starts the board by itself when the TV sits idle)"));
        rows.add(new Row("Screensaver mode") {
            String value() { boolean ours = Screensaver.isOurs(SettingsActivity.this); String s = ours ? "Enabled" : "Off, TV screensaver: " + Screensaver.currentName(SettingsActivity.this); return Screensaver.canSecure(SettingsActivity.this) ? s : s + "  (locked, see below)"; }
            void step(int d) { String err = Screensaver.enable(SettingsActivity.this, !Screensaver.isOurs(SettingsActivity.this)); if (err != null) Toast.makeText(SettingsActivity.this, err, Toast.LENGTH_LONG).show(); }
        });
        rows.add(new Row("Start after the TV idles for") {
            final int[] mins = {1, 2, 5, 10, 15, 30};
            String value() { int m = Screensaver.minutes(SettingsActivity.this); String s = m < 0 ? "unknown" : m + (m == 1 ? " minute" : " minutes"); return Screensaver.canSystem(SettingsActivity.this) ? s : s + "  (locked, see below)"; }
            void step(int d) {
                int m = Screensaver.minutes(SettingsActivity.this), i = 1; for (int k = 0; k < mins.length; k++) if (mins[k] == m) i = k;
                String err = Screensaver.setMinutes(SettingsActivity.this, mins[Math.floorMod(i + d, mins.length)]);
                if (err != null) Toast.makeText(SettingsActivity.this, err, Toast.LENGTH_LONG).show();
            }
        });
        rows.add(new Heading(""));
        rows.add(new Row("Reset these settings") {
            String value() { return ""; }
            void step(int d) { Config.resetSettings(SettingsActivity.this); cfg = Config.load(SettingsActivity.this); Toast.makeText(SettingsActivity.this, "Settings reset", Toast.LENGTH_SHORT).show(); }
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
                    int othersOn = 0; for (String k : VIEW_KEYS) if (!k.equals(key) && prefs.getBoolean(k + "_on", true)) othersOn++;
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

    private void refresh() { ((ArrayAdapter<?>) list.getAdapter()).notifyDataSetChanged(); }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        int pos = list.getSelectedItemPosition();
        if (pos >= 0 && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) { rows.get(pos).step(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); refresh(); return true; }
        return super.onKeyDown(code, e);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
