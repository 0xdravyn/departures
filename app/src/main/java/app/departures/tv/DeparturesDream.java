package app.departures.tv;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

import java.util.Calendar;

/**
 * The screensaver: a Board that rotates Now and Up next on the configured cadence, refreshes the clock on the
 * minute, and dims during quiet hours. Data lives in Data and is only fetched while this is on screen.
 */
public final class DeparturesDream extends DreamService implements Data.Listener {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BoardView view;
    private Config cfg;
    private Data data;
    private boolean upNext;
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            upNext = !upNext && data.resume != null;
            show(true);
            ui.postDelayed(this, (upNext ? cfg.upNextSeconds : cfg.nowSeconds) * 1000L);
        }
    };
    private final Runnable minute = new Runnable() {
        @Override public void run() {
            if (!upNext) show(true);
            applyQuiet();
            ui.postDelayed(this, msToNextMinute());
        }
    };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); setFullscreen(true); setScreenBright(true);
        cfg = Config.load(this);
        view = new BoardView(this);
        setContentView(view);
        view.setBoard(Faces.now(System.currentTimeMillis(), cfg.clock24, null, null));
        applyQuiet();
        data = new Data(this, cfg, this);
        data.start();
        ui.postDelayed(rotate, cfg.nowSeconds * 1000L);
        ui.postDelayed(minute, msToNextMinute());
    }

    @Override public void onDetachedFromWindow() { ui.removeCallbacksAndMessages(null); if (data != null) data.stop(); super.onDetachedFromWindow(); }

    @Override public void onData(Data d) { if (view != null) show(view.getWidth() > 0); }

    private void show(boolean animate) {
        long now = System.currentTimeMillis();
        Board b = upNext && data.resume != null ? Faces.upNext(data.resume) : Faces.now(now, cfg.clock24, data.weather, data.nextEvent(now));
        if (animate) view.flipTo(b); else view.setBoard(b);
    }

    private void applyQuiet() {
        Calendar c = Calendar.getInstance();
        view.setDim(cfg.quiet(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) ? cfg.quietDim : 1f);
    }

    private static long msToNextMinute() { long now = System.currentTimeMillis(); return 60_000L - (now % 60_000L) + 50; }
}
