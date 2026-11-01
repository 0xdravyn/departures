package app.departures.tv;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

import java.util.Calendar;

/**
 * The screensaver. Rotates the faces (FaceCycle), refreshes the clock on the minute and dims during quiet hours;
 * data is only fetched while this is on screen. For a board that must stay on regardless of the TV's sleep
 * timers, open the app from the home screen instead: BoardActivity is the same thing in the foreground.
 */
public final class DeparturesDream extends DreamService implements Data.Listener {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final FaceCycle cycle = new FaceCycle();
    private final Runnable reloader = this::reload;
    private BoardView view;
    private Config cfg;
    private Data data;

    private final Runnable quotesMidView = () -> { if (cycle.face == FaceCycle.MARKETS && data != null) data.refreshQuotes(); };
    private final Runnable rotate = new Runnable() { @Override public void run() { cycle.next(data, cfg); show(true); if (cycle.face == FaceCycle.MARKETS) ui.postDelayed(quotesMidView, cycle.delayMs(cfg) / 2); ui.postDelayed(this, cycle.delayMs(cfg)); } };
    private final Runnable minute = new Runnable() { @Override public void run() { if (cycle.face != FaceCycle.KEEP_WATCHING) show(true); applyQuiet(); ui.postDelayed(this, msToNextMinute()); } };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); setFullscreen(true); setScreenBright(true);
        cfg = Config.load(this);
        view = new BoardView(this);
        view.setFlips(cfg.flips);
        setContentView(view);
        data = new Data(this, cfg, this);
        view.setBoard(Faces.today(System.currentTimeMillis(), cfg, null, null, SetupServer.addressLine(), data.pairCode));
        applyQuiet();
        if (!cfg.todayOn) cycle.next(data, cfg);   // Today switched off in settings: start on a view that is on
        data.start();
        SetupServer.get(this).retain(); SetupServer.onChange = reloader;
        ui.postDelayed(rotate, cycle.delayMs(cfg));
        ui.postDelayed(minute, msToNextMinute());
    }

    @Override public void onDetachedFromWindow() { if (SetupServer.onChange == reloader) SetupServer.onChange = null; SetupServer.get(this).release(); ui.removeCallbacksAndMessages(null); if (data != null) data.stop(); super.onDetachedFromWindow(); }

    /** The setup page applied something: re-read everything and show it at once. */
    private void reload() {
        if (view == null) return;
        cfg = Config.load(this); view.setFlips(cfg.flips);
        if (data != null) data.stop(); data = new Data(this, cfg, this); data.start();
        if (!cfg.viewOn(cycle.face)) cycle.next(data, cfg);
        applyQuiet(); show(true);
    }

    @Override public void onData(Data d) { if (view != null) show(view.getWidth() > 0); }

    private void show(boolean animate) { Board b = cycle.board(data, cfg, System.currentTimeMillis()); if (animate) view.flipTo(b); else view.setBoard(b); }

    private void applyQuiet() {
        Calendar c = Calendar.getInstance();
        view.setDim(cfg.quiet(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) ? Math.min(cfg.quietDim, cfg.brightness) : cfg.brightness);
    }

    static long msToNextMinute() { long now = System.currentTimeMillis(); return 60_000L - (now % 60_000L) + 50; }
}
