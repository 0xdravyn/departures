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
    volatile List<Markets.Quote> quotes = Collections.emptyList();
    /** Where the settings page is ("192.0.2.10:8787"), for the board of a TV with nothing configured yet; null off Wi-Fi. */
    volatile String setupAddress;
    final String pairCode;

    private final Context ctx;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler bg;
    private final Runnable refresh = new Runnable() { @Override public void run() { refreshNow(true); if (bg != null) bg.postDelayed(this, 5 * 60_000L); } };

    Data(Context ctx, Config cfg, Listener listener) { this.ctx = ctx.getApplicationContext(); this.cfg = cfg; this.listener = listener; this.pairCode = Config.pairCode(this.ctx); }

    void start() {
        thread = new HandlerThread("departures-data"); thread.start(); bg = new Handler(thread.getLooper());
        bg.post(() -> refreshNow(false));   // caches first: a board within a frame or two
        bg.post(refresh);                   // then the network, and every five minutes after that
    }

    /** Fresh quotes now, cache or not: the Markets face asks for this part-way through its minute. */
    void refreshQuotes() {
        if (bg == null) return;
        bg.post(() -> {
            List<Markets.Quote> qs = Markets.load(ctx, cfg, true, true);
            if (!qs.isEmpty()) { quotes = qs; ui.post(() -> listener.onData(this)); }
        });
    }

    void stop() { if (bg != null) bg.removeCallbacksAndMessages(null); if (thread != null) thread.quitSafely(); thread = null; bg = null; }

    private void refreshNow(boolean network) {
        boolean changed = false;
        String addr = SetupServer.addressLine();
        if (addr == null ? setupAddress != null : !addr.equals(setupAddress)) { setupAddress = addr; changed = true; }
        Faces.Weather w = Weather.load(ctx, cfg, network);
        if (w != null && (weather == null || !same(w, weather))) { weather = w; changed = true; }
        List<Faces.Event> ev = Calendars.upcoming(ctx, cfg, network);
        if (!ev.isEmpty() || !events.isEmpty()) { events = ev; changed = true; }
        Faces.Resume r = WatchNext.newest(ctx);
        if ((r == null) != (resume == null) || (r != null && !(r.title.equals(resume.title) && r.detail.equals(resume.detail)))) { resume = r; changed = true; }
        List<Markets.Quote> qs = Markets.load(ctx, cfg, network);
        if (!qs.isEmpty() || !quotes.isEmpty()) { quotes = qs; changed = true; }
        if (changed) ui.post(() -> listener.onData(this));
    }

    /**
     * The event the board shows: one running now, else the next to start today; once today is done, the first
     * event within cfg.lookaheadDays (tomorrow by default), so the evening board points at the morning. Null
     * when nothing qualifies, and the face says so.
     */
    Faces.Event nextEvent(long now) { List<Faces.Event> l = nextEvents(now); return l.isEmpty() ? null : l.get(0); }

    /**
     * The event the board shows and everything scheduled at the same moment: all events running now that started
     * together, else all that start at the same next time. The face alternates between them every 30 seconds and
     * marks the others with +N, so a glance never hides a clash.
     */
    List<Faces.Event> nextEvents(long now) {
        Faces.Event first = pick(now);
        List<Faces.Event> out = new java.util.ArrayList<>();
        if (first == null) return out;
        for (Faces.Event e : events) if (e.startMs == first.startMs && e.endMs > now && e.allDay == first.allDay) out.add(e);
        if (out.isEmpty()) out.add(first);
        return out;
    }

    private Faces.Event pick(long now) {
        java.util.Calendar c = java.util.Calendar.getInstance(); c.setTimeInMillis(now);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
        c.add(java.util.Calendar.DAY_OF_MONTH, 1 + cfg.lookaheadDays);
        long horizon = c.getTimeInMillis();
        Faces.Event soonest = null;
        for (Faces.Event e : events) {
            if (e.startMs <= now && e.endMs > now) return e;
            if (e.startMs > now && e.startMs < horizon && (soonest == null || e.startMs < soonest.startMs)) soonest = e;
        }
        return soonest;
    }

    private static boolean same(Faces.Weather a, Faces.Weather b) { return a.now == b.now && a.high == b.high && a.low == b.low && a.rain == b.rain && a.condition.equals(b.condition); }
}
