package app.departures.tv;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Upcoming events from iCal feeds. Each feed is fetched with If-None-Match / If-Modified-Since and cached on disk,
 * so a failed fetch keeps the last good copy. The parser handles what Google Calendar exports: folded lines,
 * DTSTART/DTEND as UTC, TZID or all-day DATE, DURATION, RRULE (DAILY, WEEKLY with BYDAY, MONTHLY, YEARLY, INTERVAL,
 * COUNT, UNTIL), EXDATE, RECURRENCE-ID overrides and STATUS:CANCELLED. Only a window around now is expanded.
 */
final class Calendars {
    private static final long FRESH_MS = 15 * 60_000L;
    private static final long BACK_MS = 6 * 3600_000L, AHEAD_MS = 14 * 86_400_000L;
    private static final int MAX_INSTANCES = 400;

    /** Events across all calendars that end after now, soonest first. */
    static List<Faces.Event> upcoming(Context ctx, Config cfg, boolean allowNetwork) {
        long now = System.currentTimeMillis();
        List<Faces.Event> all = new ArrayList<>();
        int i = 0;
        for (Config.Calendar c : cfg.calendars) {
            String ics = feed(ctx, c, i++, allowNetwork);
            if (ics == null) continue;
            try { all.addAll(parse(ics, c.label, now - BACK_MS, now + AHEAD_MS)); }
            catch (Exception e) { android.util.Log.w("Departures", "calendar " + c.label + ": parse failed: " + e.getClass().getSimpleName()); }
        }
        List<Faces.Event> out = new ArrayList<>();
        for (Faces.Event e : all) if (e.endMs > now) out.add(e);
        Collections.sort(out, (a, b) -> Long.compare(a.startMs, b.startMs));
        return out;
    }

    private static String feed(Context ctx, Config.Calendar c, int index, boolean allowNetwork) {
        File cache = new File(ctx.getFilesDir(), "cal-" + index + ".ics"), meta = new File(ctx.getFilesDir(), "cal-" + index + ".meta");
        String cached = null;
        try { if (cache.exists()) cached = new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8); } catch (Exception ignored) {}
        if (cached != null && System.currentTimeMillis() - cache.lastModified() < FRESH_MS) return cached;
        if (!allowNetwork) return cached;
        String etag = null, lastMod = null;
        try { if (meta.exists()) { String[] m = new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8).split("\n", 2); etag = m[0].isEmpty() ? null : m[0]; lastMod = m.length > 1 && !m[1].isEmpty() ? m[1] : null; } } catch (Exception ignored) {}
        Net.Response r = Net.fetch(c.url, cached != null ? etag : null, cached != null ? lastMod : null, 10000);
        try {
            if (r != null && r.code == 304 && cached != null) { cache.setLastModified(System.currentTimeMillis()); return cached; }
            if (r != null && r.code == 200 && r.body != null && r.body.contains("BEGIN:VCALENDAR")) {
                Files.write(cache.toPath(), r.body.getBytes(StandardCharsets.UTF_8));
                Files.write(meta.toPath(), ((r.etag == null ? "" : r.etag) + "\n" + (r.lastModified == null ? "" : r.lastModified)).getBytes(StandardCharsets.UTF_8));
                return r.body;
            }
            android.util.Log.w("Departures", "calendar " + c.label + ": fetch " + (r == null ? "failed" : "http " + r.code) + (cached != null ? ", using cache" : ""));
        } catch (Exception e) { android.util.Log.w("Departures", "calendar " + c.label + ": cache write failed"); }
        return cached;
    }

    // ---------- parsing ----------

    private static final class VEvent {
        String uid, summary, status, rrule, recurrenceId; long start, end; boolean allDay; TimeZone tz;
        final List<String> exdates = new ArrayList<>();
    }

    static List<Faces.Event> parse(String ics, String label, long windowStart, long windowEnd) {
        List<String> lines = unfold(ics);
        List<VEvent> events = new ArrayList<>();
        Map<String, Set<String>> overridden = new HashMap<>();   // uid -> recurrence ids replaced by their own VEVENT
        VEvent cur = null;
        for (String line : lines) {
            if (line.equals("BEGIN:VEVENT")) { cur = new VEvent(); continue; }
            if (line.equals("END:VEVENT")) { if (cur != null && cur.start > 0) { events.add(cur); if (cur.recurrenceId != null) overridden.computeIfAbsent(cur.uid, k -> new HashSet<>()).add(cur.recurrenceId); } cur = null; continue; }
            if (cur == null) continue;
            int colon = line.indexOf(':'); if (colon < 0) continue;
            String head = line.substring(0, colon), value = line.substring(colon + 1);
            String name = head.contains(";") ? head.substring(0, head.indexOf(';')) : head;
            switch (name) {
                case "UID": cur.uid = value; break;
                case "SUMMARY": cur.summary = unescape(value); break;
                case "STATUS": cur.status = value; break;
                case "RRULE": cur.rrule = value; break;
                case "RECURRENCE-ID": cur.recurrenceId = value; break;
                case "EXDATE": for (String v : value.split(",")) cur.exdates.add(v); break;
                case "DTSTART": { long[] t = time(head, value); cur.start = t[0]; cur.allDay = t[1] == 1; cur.tz = tzOf(head, cur.allDay); break; }
                case "DTEND": cur.end = time(head, value)[0]; break;
                case "DURATION": if (cur.end == 0) cur.end = cur.start + duration(value); break;
                default: break;
            }
        }
        List<Faces.Event> out = new ArrayList<>();
        for (VEvent v : events) {
            if ("CANCELLED".equals(v.status)) continue;
            if (v.end == 0) v.end = v.start + (v.allDay ? 86_400_000L : 0);
            long len = Math.max(0, v.end - v.start);
            if (v.rrule == null) {
                if (v.end > windowStart && v.start < windowEnd) out.add(event(v, v.start, v.end, label));
                continue;
            }
            Set<String> skip = overridden.getOrDefault(v.uid, Collections.emptySet());
            for (long s : expand(v, windowStart, windowEnd)) {
                if (isExcluded(v, s, skip)) continue;
                out.add(event(v, s, s + len, label));
            }
        }
        return out;
    }

    private static Faces.Event event(VEvent v, long s, long e, String label) {
        Faces.Event ev = new Faces.Event(); ev.title = v.summary == null ? "(untitled)" : v.summary; ev.label = label; ev.startMs = s; ev.endMs = e; ev.allDay = v.allDay; return ev;
    }

    /** Occurrences of a recurring event inside the window (a bounded walk, never the whole series). */
    private static List<Long> expand(VEvent v, long windowStart, long windowEnd) {
        Map<String, String> rule = new HashMap<>();
        for (String part : v.rrule.split(";")) { int eq = part.indexOf('='); if (eq > 0) rule.put(part.substring(0, eq).toUpperCase(Locale.US), part.substring(eq + 1).toUpperCase(Locale.US)); }
        String freq = rule.getOrDefault("FREQ", "");
        int interval = Math.max(1, parseInt(rule.get("INTERVAL"), 1));
        int count = parseInt(rule.get("COUNT"), Integer.MAX_VALUE);
        long until = rule.containsKey("UNTIL") ? time("UNTIL", rule.get("UNTIL"))[0] : Long.MAX_VALUE;
        Set<Integer> byDay = new HashSet<>();
        if (rule.containsKey("BYDAY")) for (String d : rule.get("BYDAY").split(",")) { int wd = weekday(d.replaceAll("^[-+]?\\d+", "")); if (wd > 0) byDay.add(wd); }
        List<Long> out = new ArrayList<>();
        Calendar c = Calendar.getInstance(v.tz == null ? TimeZone.getDefault() : v.tz);
        c.setTimeInMillis(v.start);
        int produced = 0, guard = 0;
        if ("WEEKLY".equals(freq) && !byDay.isEmpty()) {
            // walk week by week from the week of DTSTART, emitting the chosen weekdays
            Calendar wk = (Calendar) c.clone();
            wk.set(Calendar.DAY_OF_WEEK, wk.getFirstDayOfWeek());
            if (wk.getTimeInMillis() > v.start) wk.add(Calendar.DAY_OF_MONTH, -7);
            while (produced < count && guard++ < 2000) {
                for (int d = 0; d < 7; d++) {
                    Calendar day = (Calendar) wk.clone(); day.add(Calendar.DAY_OF_MONTH, d);
                    long t = day.getTimeInMillis();
                    if (t < v.start || !byDay.contains(day.get(Calendar.DAY_OF_WEEK))) continue;
                    if (t > until) return out;
                    if (produced++ >= count) return out;
                    if (t >= windowEnd) return out;
                    if (t + Math.max(0, v.end - v.start) > windowStart) out.add(t);
                    if (out.size() >= MAX_INSTANCES) return out;
                }
                wk.add(Calendar.DAY_OF_MONTH, 7 * interval);
            }
            return out;
        }
        int field; switch (freq) { case "DAILY": field = Calendar.DAY_OF_MONTH; break; case "WEEKLY": field = Calendar.WEEK_OF_YEAR; break; case "MONTHLY": field = Calendar.MONTH; break; case "YEARLY": field = Calendar.YEAR; break; default: return out; }
        while (produced < count && guard++ < 5000) {
            long t = c.getTimeInMillis();
            if (t > until || t >= windowEnd) break;
            if (t + Math.max(0, v.end - v.start) > windowStart) out.add(t);
            produced++;
            if (out.size() >= MAX_INSTANCES) break;
            c.add(field, interval);
        }
        return out;
    }

    private static boolean isExcluded(VEvent v, long start, Set<String> overridden) {
        for (String ex : v.exdates) if (Math.abs(time("EXDATE", ex)[0] - start) < 60_000L) return true;
        for (String rid : overridden) if (Math.abs(time("RECURRENCE-ID", rid)[0] - start) < 60_000L) return true;
        return false;
    }

    /** {millis, allDay 0/1}. Handles 20261031T150000Z, 20261031T150000 with TZID=..., and 20261031 (DATE). */
    static long[] time(String head, String value) {
        try {
            value = value.trim();
            TimeZone tz = tzOf(head, value.length() == 8);
            if (value.length() == 8) {
                Calendar c = Calendar.getInstance(TimeZone.getDefault());
                c.clear(); c.set(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(4, 6)) - 1, Integer.parseInt(value.substring(6, 8)));
                return new long[]{c.getTimeInMillis(), 1};
            }
            boolean utc = value.endsWith("Z");
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);
            f.setTimeZone(utc ? TimeZone.getTimeZone("UTC") : tz);
            return new long[]{f.parse(utc ? value.substring(0, value.length() - 1) : value).getTime(), 0};
        } catch (Exception e) { return new long[]{0, 0}; }
    }

    private static TimeZone tzOf(String head, boolean allDay) {
        int i = head.indexOf("TZID=");
        if (i < 0) return TimeZone.getDefault();
        String id = head.substring(i + 5); int semi = id.indexOf(';'); if (semi > 0) id = id.substring(0, semi);
        TimeZone tz = TimeZone.getTimeZone(id);
        return tz;
    }

    private static long duration(String d) {
        long ms = 0; java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([WDHMS])").matcher(d);
        while (m.find()) { long n = Long.parseLong(m.group(1)); switch (m.group(2)) { case "W": ms += n * 7 * 86_400_000L; break; case "D": ms += n * 86_400_000L; break; case "H": ms += n * 3_600_000L; break; case "M": ms += n * 60_000L; break; default: ms += n * 1000L; } }
        return ms;
    }

    private static int weekday(String d) { switch (d) { case "SU": return Calendar.SUNDAY; case "MO": return Calendar.MONDAY; case "TU": return Calendar.TUESDAY; case "WE": return Calendar.WEDNESDAY; case "TH": return Calendar.THURSDAY; case "FR": return Calendar.FRIDAY; case "SA": return Calendar.SATURDAY; default: return 0; } }
    private static int parseInt(String s, int d) { try { return s == null ? d : Integer.parseInt(s); } catch (Exception e) { return d; } }
    private static String unescape(String s) { return s.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\\\", "\\"); }

    private static List<String> unfold(String ics) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = null;
        for (String raw : ics.split("\r?\n")) {
            if (!raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t') && cur != null) { cur.append(raw, 1, raw.length()); continue; }
            if (cur != null) out.add(cur.toString());
            cur = new StringBuilder(raw);
        }
        if (cur != null) out.add(cur.toString());
        return out;
    }
}
