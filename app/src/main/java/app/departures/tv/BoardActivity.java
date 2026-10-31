package app.departures.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;

import java.util.Calendar;

/**
 * The board as a foreground app: the launcher tile opens it and it stays up, screen held on, until Back. For a
 * bar, a shop, a studio or anyone who wants the board on all day: a screensaver obeys the TV's sleep timers,
 * an app in front does not. (Some TVs add their own "idle standby" after hours without a remote press; turn
 * that off in the TV's settings for all-day use.) Same faces, rotation and quiet hours as the screensaver.
 *
 * Remote: Left/Right jump between faces and pause the rotation for two minutes, OK redraws, Down/Up preview the
 * quiet-hours dim, Back leaves. Without a configuration it shows sample data, so a fresh install has something to look at.
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
            if (System.currentTimeMillis() >= manualUntil) { if (data != null) cycle.next(data); else cycle.set((cycle.face + 1) % 3); show(true); if (cycle.face == FaceCycle.MARKETS && data != null) ui.postDelayed(() -> { if (cycle.face == FaceCycle.MARKETS) data.refreshQuotes(); }, cycle.delayMs(cfg) / 2); }
            ui.postDelayed(this, cycle.delayMs(cfg));
        }
    };
    private final Runnable minute = new Runnable() { @Override public void run() { if (cycle.face != FaceCycle.KEEP_WATCHING) show(true); applyQuiet(); ui.postDelayed(this, DeparturesDream.msToNextMinute()); } };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new BoardView(this);
        setContentView(view);
        cfg = Config.load(this);
        if (cfg.present) { data = new Data(this, cfg, this); view.setBoard(Faces.now(System.currentTimeMillis(), cfg.clock24, null, null)); data.start(); }
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
        view.setDim(cfg.quiet(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) ? cfg.quietDim : 1f);
    }

    private void manual(int delta) {
        manualUntil = System.currentTimeMillis() + MANUAL_PAUSE_MS;
        cycle.set(((cycle.face + delta) % 3 + 3) % 3);
        show(true);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: manual(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: manual(-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: show(true); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: view.setDim(cfg.quietDim); return true;
            case KeyEvent.KEYCODE_DPAD_UP: view.setDim(1f); return true;
            default: return super.onKeyDown(code, e);
        }
    }
}
