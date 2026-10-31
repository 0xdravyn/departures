package app.departures.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.Calendar;

/**
 * The board as a foreground app: the launcher tile opens it and it stays up, screen held on, until Back. For a
 * bar, a shop, a studio or anyone who wants the board on all day: a screensaver obeys the TV's sleep timers,
 * an app in front does not. (Some TVs add their own "idle standby" after hours without a remote press; turn
 * that off in the TV's settings for all-day use.) Same faces, rotation and quiet hours as the screensaver.
 *
 * Remote: Left/Right jump between faces and pause the rotation for two minutes, OK redraws, holding OK (or the
 * Menu button) opens Settings, Down/Up preview the quiet-hours dim, Back leaves. Without a configuration it shows sample data, so a fresh install has something to look at.
 */
public final class BoardActivity extends Activity implements Data.Listener {
    private static final long MANUAL_PAUSE_MS = 2 * 60_000L;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final FaceCycle cycle = new FaceCycle();
    private BoardView view;
    private Config cfg;
    private Data data;
    private long manualUntil;

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (System.currentTimeMillis() >= manualUntil) { if (data != null) cycle.next(data, cfg); else cycle.set(nextOn(cycle.face)); show(true); if (cycle.face == FaceCycle.MARKETS && data != null) ui.postDelayed(() -> { if (cycle.face == FaceCycle.MARKETS) data.refreshQuotes(); }, cycle.delayMs(cfg) / 2); }
            ui.postDelayed(this, cycle.delayMs(cfg));
        }
    };
    private final Runnable minute = new Runnable() { @Override public void run() { if (cycle.face != FaceCycle.KEEP_WATCHING) show(true); applyQuiet(); ui.postDelayed(this, DeparturesDream.msToNextMinute()); } };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cfg = Config.load(this);
        view = new BoardView(this);
        view.setFlips(cfg.flips);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(hintView(), new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (!cfg.nowOn) cycle.set(nextOn(FaceCycle.MARKETS));
        // Demo mode (adb shell am start -n app.departures.tv/.BoardActivity --ez demo true): sample data only,
        // for screenshots and documentation that must not show anyone's real calendar.
        boolean demo = getIntent() != null && getIntent().getBooleanExtra("demo", false);
        if (!demo) { data = new Data(this, cfg, this); view.setBoard(Faces.now(System.currentTimeMillis(), cfg.clock24, null, null, cfg.hasLocation(), !cfg.calendars.isEmpty())); data.start(); }
        else view.setBoard(sample());
        applyQuiet();
        ui.postDelayed(rotate, cycle.delayMs(cfg));
        ui.postDelayed(minute, DeparturesDream.msToNextMinute());
    }

    @Override protected void onDestroy() { ui.removeCallbacksAndMessages(null); if (data != null) data.stop(); super.onDestroy(); }

    @Override public void onData(Data d) { show(view.getWidth() > 0); }

    private Board sample() {
        long now = System.currentTimeMillis();
        if (cycle.face == FaceCycle.KEEP_WATCHING) return Faces.keepWatching(Faces.sampleResume());
        if (cycle.face == FaceCycle.MARKETS) return Faces.markets(Faces.sampleQuotes(), now, false);
        return Faces.now(now, false, Faces.sampleWeather(), Faces.sampleEvent(now));
    }

    private void show(boolean animate) {
        Board b = data != null ? cycle.board(data, cfg, System.currentTimeMillis()) : sample();
        if (animate) view.flipTo(b); else view.setBoard(b);
    }

    private void applyQuiet() {
        Calendar c = Calendar.getInstance();
        view.setDim(cfg.quiet(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) ? Math.min(cfg.quietDim, cfg.brightness) : cfg.brightness);
    }

    /** The next view that is switched on, after this one (wrapping); the same one if it is the only one on. */
    private int nextOn(int from) { for (int i = 1; i <= 3; i++) { int f = (from + i) % 3; if (cfg.viewOn(f)) return f; } return from; }
    private int prevOn(int from) { for (int i = 1; i <= 3; i++) { int f = ((from - i) % 3 + 3) % 3; if (cfg.viewOn(f)) return f; } return from; }

    private void manual(int delta) {
        manualUntil = System.currentTimeMillis() + MANUAL_PAUSE_MS;
        cycle.set(delta > 0 ? nextOn(cycle.face) : prevOn(cycle.face));
        show(true);
    }

    /** "Hold OK on your remote for settings", over a dimmed board for three seconds at every launch; any key ends it early. */
    private View hint;
    private View hintView() {
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText("Hold OK on your remote for settings");
        t.setTextColor(0xFFF2EEE6); t.setTextSize(30); t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); t.setGravity(android.view.Gravity.CENTER);
        t.setBackgroundColor(0x99000000);
        hint = t;
        ui.postDelayed(this::hideHint, 3000);
        return t;
    }
    private void hideHint() { if (hint != null && hint.getVisibility() == View.VISIBLE) hint.animate().alpha(0f).setDuration(350).withEndAction(() -> hint.setVisibility(View.GONE)).start(); }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) { openSettings(); return true; }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && !e.isCanceled() && (e.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0) { show(true); return true; }
        return super.onKeyUp(code, e);
    }

    private void openSettings() { startActivity(new android.content.Intent(this, SettingsActivity.class)); }

    /** Back from Settings: re-read the choices and apply them at once. */
    @Override protected void onResume() {
        super.onResume();
        Config fresh = Config.load(this);
        boolean changed = cfg != null && (fresh.nowOn != cfg.nowOn || fresh.keepWatchingOn != cfg.keepWatchingOn || fresh.marketsOn != cfg.marketsOn || fresh.clock24 != cfg.clock24 || fresh.quietEnabled != cfg.quietEnabled || fresh.quietFromMin != cfg.quietFromMin || fresh.quietToMin != cfg.quietToMin || fresh.brightness != cfg.brightness || fresh.flips != cfg.flips || fresh.nowSeconds != cfg.nowSeconds || fresh.marketsSeconds != cfg.marketsSeconds || fresh.keepWatchingSeconds != cfg.keepWatchingSeconds || fresh.imperial != cfg.imperial);
        cfg = fresh;
        if (changed) { view.setFlips(cfg.flips); if (!cfg.viewOn(cycle.face)) cycle.set(nextOn(cycle.face)); manualUntil = 0; applyQuiet(); show(true); }
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        hideHint();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: manual(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: manual(-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: if (e.getRepeatCount() == 0) { e.startTracking(); return true; } return true;
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_SETTINGS: openSettings(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: view.setDim(cfg.quietDim); return true;
            case KeyEvent.KEYCODE_DPAD_UP: view.setDim(1f); return true;
            default: return super.onKeyDown(code, e);
        }
    }
}
