package app.departures.tv;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Builds the boards. Data classes are plain values so the faces can be rendered from samples before any
 * network code exists, and tested without a TV.
 */
public final class Faces {
    /** Simultaneous events take turns this often; the board redraws on the same beat. */
    public static final long ALTERNATE_MS = 30_000L;
    public static final class Weather { public int now, high, low; public String condition = ""; public boolean rain; }
    public static final class Event { public String title = ""; public String label = ""; public long startMs, endMs; public boolean allDay; }
    public static final class Resume { public String title = "", detail = "", app = ""; }

    /** The Now face of the first builds, kept for the demo and tests: date, time, weather and the next event. */
    public static Board now(long nowMs, boolean clock24, Weather w, Event e) {
        Config c = new Config(); c.present = true; c.clock24 = clock24; c.latitude = 0; c.longitude = 0; c.calendars.add(new Config.Calendar("", "https://example.invalid"));
        return today(nowMs, c, w, e == null ? java.util.Collections.emptyList() : List.of(e), null, null);
    }

    /**
     * Today: row 1 the date and time; rows 2 and 3 the weather; rows 5 and 6 the next event with its countdown,
     * or two lines of the owner's own. Events that share the same moment take turns every 30 seconds, with +N at
     * the end of the title row for the ones waiting. A TV with no configuration yet shows where to set it up instead.
     */
    public static Board today(long nowMs, Config cfg, Weather w, List<Event> events, String setupAddress, String pairCode) {
        int n = events == null ? 0 : events.size();
        Event e = n == 0 ? null : events.get((int) ((nowMs / ALTERNATE_MS) % n));
        Board b = new Board();
        Date d = new Date(nowMs);
        String date = new SimpleDateFormat("EEE d MMM", Locale.US).format(d).toUpperCase(Locale.US);
        String time = cfg.clock24 ? new SimpleDateFormat(cfg.clockSeconds ? "HH:mm:ss" : "HH:mm", Locale.US).format(d)
                : new SimpleDateFormat(cfg.clockSeconds ? "h:mm:ss a" : "h:mm a", Locale.US).format(d).replace("AM", " AM").replace("PM", " PM").replaceAll("  +", " ");
        b.text(0, 0, date);
        b.textRight(0, time);
        if (!cfg.present) {
            // Nothing of the owner's on this TV yet: the board itself says where to go.
            b.chip(2, 0, Board.CHIP_BLUE); b.chip(3, 0, Board.CHIP_BLUE);
            b.text(2, 2, "TO SET UP, OPEN");
            b.text(3, 2, setupAddress == null ? "CONNECT THE TV TO WIFI" : setupAddress);
            if (pairCode != null) b.text(5, 2, "CODE  " + pairCode);
            return b;
        }
        if (w != null) {
            String wx = w.now + "° " + w.condition;
            b.text(1, 0, wx);
            b.set(1, Math.min(Board.COLS - 1, wx.length() + 1), Board.weatherGlyph(w.condition, false));   // the glyph after the words: the condition now; RAIN on the next row is today
            String hl = "HIGH " + w.high + "°  LOW " + w.low + "°";
            b.text(2, 0, hl);
            if (w.rain && hl.length() + 1 <= Board.COLS - 4) b.textRight(2, "RAIN");
        } else if (!cfg.hasLocation()) { b.text(1, 0, "WEATHER: SET LOCATION"); b.text(2, 0, "ON THE SETUP PAGE"); }
        b.chip(4, 0, Board.CHIP_BLUE); b.chip(5, 0, Board.CHIP_BLUE);
        if (cfg.todayMessage) {
            writeRich(b, 4, 1, cfg.todayLine1);
            writeRich(b, 5, 1, cfg.todayLine2);
        } else if (e != null) {
            String more = n > 1 ? "+" + (n - 1) : "";
            b.text(4, 1, fit(e.title, Board.COLS - 1 - (more.isEmpty() ? 0 : more.length() + 1)));
            if (!more.isEmpty()) b.textRight(4, more);
            // Row 6: the time and countdown on the left, the calendar label on the right, never overlapping:
            // the label keeps its place and the countdown gives way (minutes first, then the whole countdown).
            String label = e.label == null ? "" : e.label.toUpperCase(Locale.US);
            int room = Board.COLS - 1 - (label.isEmpty() ? 0 : label.length() + 2);
            String w5 = when(nowMs, e, cfg.clock24);
            if (w5.length() > room) w5 = w5.replaceAll(" \\d+M$", "");
            if (w5.length() > room) w5 = w5.replaceAll("  IN .*$", "");
            b.text(5, 1, fit(w5, room));
            if (!label.isEmpty()) b.textRight(5, label);
        } else if (cfg.calendars.isEmpty()) {
            b.text(4, 1, "ADD A CALENDAR");
            b.text(5, 1, "ON THE SETUP PAGE");
        } else {
            b.text(4, 1, "NOTHING SCHEDULED");
            b.text(5, 1, "TODAY :)");
        }
        return b;
    }

    /** "TODAY 3PM  IN 1H 12M", "NOW", "TOMORROW 9AM", "MON 9AM". */
    static String when(long nowMs, Event e, boolean clock24) {
        long diff = e.startMs - nowMs;
        if (nowMs >= e.startMs && nowMs < e.endMs) return "NOW";
        String day;
        long dayNow = (nowMs + tz(nowMs)) / 86400000L, dayEv = (e.startMs + tz(e.startMs)) / 86400000L;
        if (dayEv == dayNow) day = "TODAY"; else if (dayEv == dayNow + 1) day = "TOMORROW";
        else day = new SimpleDateFormat("EEE", Locale.US).format(new Date(e.startMs)).toUpperCase(Locale.US);
        String time = e.allDay ? "" : (clock24 ? new SimpleDateFormat("HH:mm", Locale.US) : new SimpleDateFormat("h:mma", Locale.US)).format(new Date(e.startMs)).toUpperCase(Locale.US).replace(":00", "");
        String in;
        if (diff < 0) in = ""; else if (diff < 3600000L) in = "IN " + Math.max(1, diff / 60000) + "M";
        else if (diff < 86400000L) in = "IN " + diff / 3600000 + "H " + (diff % 3600000) / 60000 + "M"; else in = "";
        // Same day with a countdown: the time and "IN 1H 12M" say more than the word TODAY, and together they fit
        // beside a calendar label ("3:46PM  IN 1H 12M" is 17 columns).
        String s = (day.equals("TODAY") && !in.isEmpty() ? time : (day + " " + time)).trim();
        if (!in.isEmpty()) s = s + "  " + in;
        return s;
    }

    /** Keep watching: title, episode line and app, with red chips top-right and bottom-right. */
    public static Board keepWatching(Resume r) {
        Board b = new Board();
        b.text(0, 0, "KEEP WATCHING?");
        b.chip(0, Board.COLS - 1, Board.CHIP_RED);
        b.text(2, 0, fit(r.title, Board.COLS));
        b.text(3, 0, fit(r.detail, Board.COLS));
        b.text(4, 0, fit(r.app, Board.COLS));
        b.chip(5, Board.COLS - 2, Board.CHIP_RED); b.chip(5, Board.COLS - 1, Board.CHIP_RED);
        return b;
    }

    /**
     * Markets: a header with the face's orange chip, then one row per asset: a green or red chip for the day's
     * direction, the label, the price and the change against the previous close (24 h for crypto).
     * Our own layout, not the Vestaboard channel's: chips on the left as on the other faces, percentages not points.
     */
    public static Board markets(List<Markets.Quote> quotes, long nowMs, boolean clock24) { return markets(quotes, nowMs, clock24, false); }

    public static Board markets(List<Markets.Quote> quotes, long nowMs, boolean clock24, boolean seconds) {
        Board b = new Board();
        // Header, centred: MARKETS @ time of the board; orange face chips at both ends. No open/closed word: a list
        // mixes exchange hours and crypto, which never closes. Then up to five rows.
        String time = new SimpleDateFormat(clock24 ? (seconds ? "HH:mm:ss" : "HH:mm") : (seconds ? "h:mm:ssa" : "h:mma"), Locale.US).format(new Date(nowMs)).toUpperCase(Locale.US);
        String head = "MARKETS @ " + time;
        if (head.length() > Board.COLS - 4) head = "MARKETS " + time;   // with seconds in 12-hour time, keep a bit clear of the corner chips
        b.textCenter(0, head);
        b.chip(0, 0, Board.CHIP_ORANGE); b.chip(0, Board.COLS - 1, Board.CHIP_ORANGE);
        int row = 1;
        for (Markets.Quote q : quotes) {
            if (row >= Board.ROWS) break;
            if (!q.ok) { b.text(row, 2, fit(q.label.toUpperCase(Locale.US), 8)); b.textRight(row, "NO DATA"); row++; continue; }
            b.chip(row, 0, q.changePct > 0.05 ? Board.CHIP_GREEN : q.changePct < -0.05 ? Board.CHIP_RED : Board.CHIP_WHITE);
            b.text(row, 2, fit(q.label.toUpperCase(Locale.US), 8));
            String price = Markets.price(q.price), chg = Markets.change(q.changePct);
            b.text(row, Math.max(10, 15 - price.length()), price);
            b.textRight(row, chg);
            row++;
        }
        return b;
    }

    /** Message: six lines of the owner's own, as typed on the setup page, chips and all. */
    public static Board message(List<String> lines) {
        Board b = new Board();
        for (int r = 0; r < Board.ROWS && r < lines.size(); r++) writeRich(b, r, 0, lines.get(r));
        return b;
    }

    /**
     * Writes a line with its chips: {r} {o} {y} {g} {b} {v} {w} are colour chips, a colour emoji becomes the
     * nearest chip, and anything the board has no flap for is left out rather than shown as a blank.
     */
    static void writeRich(Board b, int row, int col, String s) {
        if (s == null) return;
        String up = s.toUpperCase(Locale.US);
        int c = col;
        for (int i = 0; i < up.length() && c < Board.COLS; ) {
            int cp = up.codePointAt(i);
            int len = Character.charCount(cp);
            if (cp == '{' && i + 2 < up.length() && up.charAt(i + 2) == '}') {
                int k = "ROYGBVW".indexOf(up.charAt(i + 1));
                if (k >= 0) { b.chip(row, c++, Board.CHIPS[k]); i += 3; continue; }
            }
            char chip = emojiChip(cp);
            if (chip != 0) { b.chip(row, c++, chip); i += len; continue; }
            if (cp > 0x24F) { i += len; continue; }   // emoji, variation selectors and the like: no flap, no cell
            char ch = (char) cp;
            if (Board.FLAPS.indexOf(ch) >= 0) b.set(row, c++, ch);
            else if (ch == ' ' || Character.isWhitespace(ch)) b.set(row, c++, ' ');
            i += len;   // other characters are dropped, not blanked
        }
    }

    /** Hearts, squares and circles by colour, plus stars and the sun as yellow. 0 when the code point is not one. */
    static char emojiChip(int cp) {
        switch (cp) {
            case 0x2764: case 0x1F7E5: case 0x1F534: return Board.CHIP_RED;
            case 0x1F9E1: case 0x1F7E7: case 0x1F7E0: return Board.CHIP_ORANGE;
            case 0x1F49B: case 0x1F7E8: case 0x1F7E1: case 0x2B50: case 0x1F31F: case 0x2600: return Board.CHIP_YELLOW;
            case 0x1F49A: case 0x1F7E9: case 0x1F7E2: return Board.CHIP_GREEN;
            case 0x1F499: case 0x1F7E6: case 0x1F535: return Board.CHIP_BLUE;
            case 0x1F49C: case 0x1F7EA: case 0x1F7E3: return Board.CHIP_VIOLET;
            case 0x1F90D: case 0x2B1C: case 0x26AA: return Board.CHIP_WHITE;
            default: return 0;
        }
    }

    /** Clips to the width on a word boundary where it can, else hard. */
    static String fit(String s, int width) {
        if (s == null) return "";
        if (s.length() <= width) return s;
        int cut = s.lastIndexOf(' ', width);
        return cut >= width / 2 ? s.substring(0, cut) : s.substring(0, width);
    }

    private static long tz(long ms) { return java.util.TimeZone.getDefault().getOffset(ms); }

    // ----- samples, for the demo (--ez demo true) and documentation -----

    public static Weather sampleWeather() { Weather w = new Weather(); w.now = 12; w.high = 14; w.low = 8; w.condition = "PARTLY CLOUDY"; w.rain = true; return w; }
    public static Event sampleEvent(long nowMs) { Event e = new Event(); e.title = "Dinner with Sam"; e.label = "HOME"; e.startMs = nowMs + 72 * 60000L; e.endMs = e.startMs + 3600000L; return e; }
    public static List<Markets.Quote> sampleQuotes() {
        List<Markets.Quote> l = new java.util.ArrayList<>();
        Object[][] s = {{"S&P 500", 7580.07, -0.57}, {"NVIDIA", 204.64, -3.0}, {"GOOGLE", 279.84, 1.2}, {"BITCOIN", 77499.0, 1.08}, {"GOLD", 3898.9, -0.4}};
        for (Object[] r : s) { Markets.Quote q = new Markets.Quote(); q.label = (String) r[0]; q.price = (Double) r[1]; q.changePct = (Double) r[2]; q.ok = true; q.state = "BITCOIN".equals(r[0]) ? null : "OPEN"; l.add(q); }
        return l;
    }
    public static Resume sampleResume() { Resume r = new Resume(); r.title = "Kim's Convenience"; r.detail = "S3E6   11 mins left"; r.app = "Netflix"; return r; }
    public static List<String> sampleMessage() { return List.of("{g} BACK AT 6", "", "DINNER IS IN THE OVEN", "{o} 180 FOR 40 MIN", "", "{r}{r}{r} LOVE YOU {r}{r}{r}"); }
}
