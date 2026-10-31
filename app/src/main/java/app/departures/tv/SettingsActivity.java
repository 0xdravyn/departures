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

import java.util.ArrayList;
import java.util.List;

/**
 * Settings on the TV, with the remote: Up/Down to a row, OK (or Left/Right) to step its value, Back to leave.
 * Stored in the app's private preferences, read by both the board app and the screensaver (Config.load), so one
 * change covers both. Reached by holding OK on the board, from the Menu button, or from the TV's own
 * Settings > Screen saver > Departures. What needs typing (coordinates, calendar addresses) stays in config.json.
 */
public final class SettingsActivity extends Activity {
    private static final int[] FACE_PIN = {-1, FaceCycle.NOW, FaceCycle.KEEP_WATCHING, FaceCycle.MARKETS};
    private static final String[] FACE_NAMES = {"Cycle through all faces", "Now only", "Keep watching? only", "Markets only"};
    private static final int[] SECONDS = {15, 30, 60, 120, 300, 600};

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
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK); root.setPadding(dp(80), dp(40), dp(80), dp(32));
        TextView head = new TextView(this); head.setText("DEPARTURES  ·  SETTINGS"); head.setTextColor(0xFFE8781E); head.setTextSize(22); head.setTypeface(Typeface.DEFAULT_BOLD); head.setLetterSpacing(0.1f);
        root.addView(head);
        TextView sub = new TextView(this); sub.setTextColor(0xFF888888); sub.setTextSize(15); sub.setPadding(0, dp(6), 0, dp(18));
        sub.setText(status());
        root.addView(sub);
        list = new ListView(this); list.setDividerHeight(dp(1)); list.setSelector(new android.graphics.drawable.ColorDrawable(0x22FFFFFF));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = new TextView(this); hint.setTextColor(0xFF666666); hint.setTextSize(14); hint.setPadding(0, dp(14), 0, 0);
        hint.setText("Up/Down choose a row  ·  OK or Left/Right change it  ·  Back saves and leaves. Changes also apply to the screensaver.");
        root.addView(hint);
        setContentView(root);
        buildRows();
        list.setAdapter(new ArrayAdapter<Row>(this, 0, rows) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout line = new LinearLayout(this.getContext()); line.setOrientation(LinearLayout.HORIZONTAL); line.setPadding(dp(16), dp(14), dp(16), dp(14));
                TextView t = new TextView(this.getContext()); t.setText(rows.get(pos).title); t.setTextColor(Color.WHITE); t.setTextSize(20);
                TextView v = new TextView(this.getContext()); v.setText(rows.get(pos).value()); v.setTextColor(0xFFF1C426); v.setTextSize(20); v.setGravity(Gravity.END);
                line.addView(t, new LinearLayout.LayoutParams(0, -2, 1f)); line.addView(v, new LinearLayout.LayoutParams(-2, -2));
                return line;
            }
        });
        list.setOnItemClickListener((p, v, pos, id) -> { rows.get(pos).step(1); refresh(); });
        list.requestFocus();
    }

    private String status() {
        StringBuilder s = new StringBuilder();
        s.append(cfg.present ? "Configuration imported" : "No configuration yet (push config.json over ADB)");
        if (cfg.present) {
            s.append("  ·  ").append(cfg.calendars.size()).append(cfg.calendars.size() == 1 ? " calendar" : " calendars");
            s.append("  ·  ").append(cfg.hasLocation() ? (cfg.locationName.isEmpty() ? "location set" : cfg.locationName) : "no location");
            s.append("  ·  ").append(cfg.markets.isEmpty() ? "markets off" : cfg.markets.size() + " markets");
        }
        return s.toString();
    }

    private void buildRows() {
        rows.clear();
        rows.add(new Row("Show") {
            String value() { int i = 0; for (int k = 0; k < FACE_PIN.length; k++) if (FACE_PIN[k] == prefs.getInt("pinned_face", -1)) i = k; return FACE_NAMES[i]; }
            void step(int d) { int i = 0; for (int k = 0; k < FACE_PIN.length; k++) if (FACE_PIN[k] == prefs.getInt("pinned_face", -1)) i = k; prefs.edit().putInt("pinned_face", FACE_PIN[Math.floorMod(i + d, FACE_PIN.length)]).apply(); }
        });
        rows.add(seconds("Now face, seconds", "now_seconds", 60));
        rows.add(seconds("Keep watching? face, seconds", "keep_watching_seconds", 30));
        rows.add(seconds("Markets face, seconds", "markets_seconds", 60));
        rows.add(new Row("Clock") {
            String value() { return prefs.getBoolean("clock24", cfg.clock24) ? "24-hour" : "12-hour"; }
            void step(int d) { prefs.edit().putBoolean("clock24", !prefs.getBoolean("clock24", cfg.clock24)).apply(); }
        });
        rows.add(new Row("Temperature") {
            String value() { return prefs.getBoolean("imperial", cfg.imperial) ? "Fahrenheit" : "Celsius"; }
            void step(int d) { prefs.edit().putBoolean("imperial", !prefs.getBoolean("imperial", cfg.imperial)).apply(); }
        });
        rows.add(new Row("Quiet hours (dim at night)") {
            String value() { return prefs.getBoolean("quiet_enabled", true) ? String.format("On, %02d:%02d to %02d:%02d", cfg.quietFromMin / 60, cfg.quietFromMin % 60, cfg.quietToMin / 60, cfg.quietToMin % 60) : "Off"; }
            void step(int d) { prefs.edit().putBoolean("quiet_enabled", !prefs.getBoolean("quiet_enabled", true)).apply(); }
        });
        rows.add(new Row("Reset these settings") {
            String value() { return ""; }
            void step(int d) { prefs.edit().clear().apply(); }
        });
    }

    private Row seconds(String title, String key, int dflt) {
        return new Row(title) {
            String value() { int s = prefs.getInt(key, dflt); return s >= 60 ? (s / 60) + (s == 60 ? " minute" : " minutes") : s + " seconds"; }
            void step(int d) { int s = prefs.getInt(key, dflt), i = 0; for (int k = 0; k < SECONDS.length; k++) if (SECONDS[k] == s) i = k; prefs.edit().putInt(key, SECONDS[Math.floorMod(i + d, SECONDS.length)]).apply(); }
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
