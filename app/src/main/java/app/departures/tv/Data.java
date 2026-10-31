package app.departures.tv;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.Collections;
import java.util.List;

/**
 * Everything the faces need, refreshed on a background thread while the board is on screen and not at all
 * otherwise. Weather and calendars come from their disk caches first, then from the network when stale; the
 * listener is told on the main thread each time something changed.
 */
final class Data {
    interface Listener { void onData(Data d); }

    final Config cfg;
    volatile Faces.Weather weather;
    volatile List<Faces.Event> events = Collections.emptyList();
    volatile Faces.Resume resume;

    private final Context ctx;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler bg;
    private final Runnable refresh = new Runnable() { @Override public void run() { refreshNow(true); if (bg != null) bg.postDelayed(this, 5 * 60_000L); } };

    Data(Context ctx, Config cfg, Listener listener) { this.ctx = ctx.getApplicationContext(); this.cfg = cfg; this.listener = listener; }

    void start() {
        thread = new HandlerThread("departures-data"); thread.start(); bg = new Handler(thread.getLooper());
        bg.post(() -> refreshNow(false));   // caches first: a board within a frame or two
        bg.post(refresh);                   // then the network, and every five minutes after that
    }

    void stop() { if (bg != null) bg.removeCallbacksAndMessages(null); if (thread != null) thread.quitSafely(); thread = null; bg = null; }

    private void refreshNow(boolean network) {
        boolean changed = false;
        Faces.Weather w = Weather.load(ctx, cfg, network);
        if (w != null && (weather == null || !same(w, weather))) { weather = w; changed = true; }
        List<Faces.Event> ev = Calendars.upcoming(ctx, cfg, network);
        if (!ev.isEmpty() || !events.isEmpty()) { events = ev; changed = true; }
        Faces.Resume r = WatchNext.newest(ctx);
        if ((r == null) != (resume == null) || (r != null && !(r.title.equals(resume.title) && r.detail.equals(resume.detail)))) { resume = r; changed = true; }
        if (changed) ui.post(() -> listener.onData(this));
    }

    /** The next event: one that is running now, else the soonest to start. */
    Faces.Event nextEvent(long now) {
        Faces.Event soonest = null;
        for (Faces.Event e : events) {
            if (e.startMs <= now && e.endMs > now) return e;
            if (e.startMs > now && (soonest == null || e.startMs < soonest.startMs)) soonest = e;
        }
        return soonest;
    }

    private static boolean same(Faces.Weather a, Faces.Weather b) { return a.now == b.now && a.high == b.high && a.low == b.low && a.rain == b.rain && a.condition.equals(b.condition); }
}
