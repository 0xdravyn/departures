package app.departures.tv;

/**
 * Which face is up and for how long: Today, Keep watching?, Today, Markets, Today, Message, round and round,
 * skipping faces that are switched off or have nothing to show. Shared by the screensaver and the standalone
 * board so they behave identically.
 */
final class FaceCycle {
    static final int TODAY = 0, KEEP_WATCHING = 1, MARKETS = 2, MESSAGE = 3, FACES = 4;
    private static final int[] CYCLE = {TODAY, KEEP_WATCHING, TODAY, MARKETS, TODAY, MESSAGE};

    int face = TODAY;
    private int step;

    /** Advances to the next view that is switched on (settings) and has something to show. */
    int next(Data data, Config cfg) {
        for (int i = 0; i < CYCLE.length; i++) {
            step = (step + 1) % CYCLE.length;
            int f = CYCLE[step];
            if (!cfg.viewOn(f)) continue;
            if (f == TODAY || (f == KEEP_WATCHING && data.resume != null) || (f == MARKETS && hasQuotes(data)) || (f == MESSAGE && cfg.hasMessage())) { face = f; return face; }
        }
        // nothing else qualifies: Today if it is on, else whichever view is on (its face copes with missing data)
        face = cfg.todayOn ? TODAY : cfg.marketsOn ? MARKETS : cfg.messageOn ? MESSAGE : cfg.keepWatchingOn ? KEEP_WATCHING : TODAY; return face;
    }

    /** Jumps to a face by hand (the remote); the cycle continues from there. */
    void set(int f) { face = f; for (int i = 0; i < CYCLE.length; i++) if (CYCLE[i] == f) { step = i; break; } }

    long delayMs(Config cfg) { return (face == KEEP_WATCHING ? cfg.keepWatchingSeconds : face == MARKETS ? cfg.marketsSeconds : face == MESSAGE ? cfg.messageSeconds : cfg.todaySeconds) * 1000L; }

    static boolean hasQuotes(Data data) { for (Markets.Quote q : data.quotes) if (q.ok) return true; return false; }

    /** The board for the current face from what Data has; a face whose data vanished falls back to Today. */
    Board board(Data data, Config cfg, long now) {
        if (face == KEEP_WATCHING && data.resume != null) return Faces.keepWatching(data.resume);
        if (face == MARKETS && (hasQuotes(data) || !cfg.todayOn)) return Faces.markets(data.quotes, now, cfg.clock24);
        if (face == MESSAGE && (cfg.hasMessage() || !cfg.todayOn)) return Faces.message(cfg.message);
        return Faces.today(now, cfg, data.weather, data.nextEvent(now), data.setupAddress, data.pairCode);
    }
}
