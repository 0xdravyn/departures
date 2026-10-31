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
    public static final class Weather { public int now, high, low; public String condition = ""; public boolean rain; }
    public static final class Event { public String title = ""; public String label = ""; public long startMs, endMs; public boolean allDay; }
    public static final class Resume { public String title = "", detail = "", app = ""; }

    /** Row 1 date and time; rows 2 and 3 weather; rows 5 and 6 the next event with its countdown. */
    public static Board now(long nowMs, boolean clock24, Weather w, Event e) {
        Board b = new Board();
        Date d = new Date(nowMs);
        String date = new SimpleDateFormat("EEE d MMM", Locale.US).format(d).toUpperCase(Locale.US);
        String time = clock24 ? new SimpleDateFormat("HH:mm", Locale.US).format(d)
                : new SimpleDateFormat("h:mm a", Locale.US).format(d).replace("AM", " AM").replace("PM", " PM").replaceAll("  +", " ");
        b.text(0, 0, date);
        b.textRight(0, time);
        if (w != null) {
            b.set(1, 0, Board.weatherGlyph(w.condition, w.rain));
            b.text(1, 2, w.now + "° " + w.condition);
            String hl = "HIGH " + w.high + "  LOW " + w.low;
            b.text(2, 0, hl);
            if (w.rain) b.textRight(2, "RAIN");
        }
        if (e != null) {
            b.chip(4, 0, Board.CHIP_BLUE); b.chip(5, 0, Board.CHIP_BLUE);
            b.text(4, 1, fit(e.title, Board.COLS - 1));
            // Row 6: the time and countdown on the left, the calendar label on the right, never overlapping:
            // the label keeps its place and the countdown gives way (minutes first, then the whole countdown).
            String label = e.label == null ? "" : e.label.toUpperCase(Locale.US);
            int room = Board.COLS - 1 - (label.isEmpty() ? 0 : label.length() + 2);
            String w5 = when(nowMs, e, clock24);
            if (w5.length() > room) w5 = w5.replaceAll(" \\d+M$", "");
            if (w5.length() > room) w5 = w5.replaceAll("  IN .*$", "");
            b.text(5, 1, fit(w5, room));
            if (!label.isEmpty()) b.textRight(5, label);
        } else {
            b.chip(4, 0, Board.CHIP_BLUE); b.chip(5, 0, Board.CHIP_BLUE);
            b.text(4, 1, "NOTHING ELSE TODAY");
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
    public static Board markets(List<Markets.Quote> quotes, long nowMs, boolean clock24) {
        Board b = new Board();
        // Header, centred: MARKETS @ time of the board; orange face chips at both ends. No open/closed word: a list
        // mixes exchange hours and crypto, which never closes. Then up to five rows.
        String time = (clock24 ? new SimpleDateFormat("HH:mm", Locale.US) : new SimpleDateFormat("h:mma", Locale.US)).format(new Date(nowMs)).toUpperCase(Locale.US);
        b.textCenter(0, "MARKETS @ " + time);
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

    /** Clips to the width on a word boundary where it can, else hard. */
    static String fit(String s, int width) {
        if (s == null) return "";
        if (s.length() <= width) return s;
        int cut = s.lastIndexOf(' ', width);
        return cut >= width / 2 ? s.substring(0, cut) : s.substring(0, width);
    }

    private static long tz(long ms) { return java.util.TimeZone.getDefault().getOffset(ms); }

    // ----- samples, for the preview before data exists -----

    public static Weather sampleWeather() { Weather w = new Weather(); w.now = 12; w.high = 14; w.low = 8; w.condition = "PARTLY CLOUDY"; w.rain = true; return w; }
    public static Event sampleEvent(long nowMs) { Event e = new Event(); e.title = "Dinner with Sam"; e.label = "HOME"; e.startMs = nowMs + 72 * 60000L; e.endMs = e.startMs + 3600000L; return e; }
    public static List<Markets.Quote> sampleQuotes() {
        List<Markets.Quote> l = new java.util.ArrayList<>();
        Object[][] s = {{"S&P 500", 7580.07, -0.57}, {"NVIDIA", 204.64, -3.0}, {"GOOGLE", 279.84, 1.2}, {"BITCOIN", 77499.0, 1.08}, {"GOLD", 3898.9, -0.4}};
        for (Object[] r : s) { Markets.Quote q = new Markets.Quote(); q.label = (String) r[0]; q.price = (Double) r[1]; q.changePct = (Double) r[2]; q.ok = true; q.state = "BITCOIN".equals(r[0]) ? null : "OPEN"; l.add(q); }
        return l;
    }
    public static Resume sampleResume() { Resume r = new Resume(); r.title = "Kim's Convenience"; r.detail = "S3E6   11 min left"; r.app = "Netflix"; return r; }
}
