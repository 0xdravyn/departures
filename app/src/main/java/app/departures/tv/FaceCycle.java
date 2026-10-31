package app.departures.tv;

/**
 * Which face is up and for how long: Now, Keep watching?, Now, Markets, round and round, skipping faces that
 * have nothing to show. Shared by the screensaver and the standalone board so they behave identically.
 */
final class FaceCycle {
    static final int NOW = 0, KEEP_WATCHING = 1, MARKETS = 2;
    private static final int[] CYCLE = {NOW, KEEP_WATCHING, NOW, MARKETS};

    int face = NOW;
    private int step;

    /** Advances to the next view that is switched on (settings) and has something to show. */
    int next(Data data, Config cfg) {
        for (int i = 0; i < CYCLE.length; i++) {
            step = (step + 1) % CYCLE.length;
            int f = CYCLE[step];
            if (!cfg.viewOn(f)) continue;
            if (f == NOW || (f == KEEP_WATCHING && data.resume != null) || (f == MARKETS && hasQuotes(data))) { face = f; return face; }
        }
        // nothing else qualifies: Now if it is on, else whichever view is on (its face copes with missing data)
        face = cfg.nowOn ? NOW : cfg.marketsOn ? MARKETS : cfg.keepWatchingOn ? KEEP_WATCHING : NOW; return face;
    }

    /** Jumps to a face by hand (the remote); the cycle continues from there. */
    void set(int f) { face = f; for (int i = 0; i < CYCLE.length; i++) if (CYCLE[i] == f) { step = i; break; } }

    long delayMs(Config cfg) { return (face == KEEP_WATCHING ? cfg.keepWatchingSeconds : face == MARKETS ? cfg.marketsSeconds : cfg.nowSeconds) * 1000L; }

    static boolean hasQuotes(Data data) { for (Markets.Quote q : data.quotes) if (q.ok) return true; return false; }

    /** The board for the current face from what Data has; a face whose data vanished falls back to Now. */
    Board board(Data data, Config cfg, long now) {
        if (face == KEEP_WATCHING && data.resume != null) return Faces.keepWatching(data.resume);
        if (face == MARKETS && (hasQuotes(data) || !cfg.nowOn)) return Faces.markets(data.quotes, now, cfg.clock24);
        return Faces.now(now, cfg.clock24, data.weather, data.nextEvent(now));
    }
}
