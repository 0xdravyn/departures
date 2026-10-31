# Split-flap screensaver: design brief (draft, Oct 31)

Goal 2 of the TV project: when the TV idles, a Vestaboard-style split-flap board replaces Google's ambient photo, with Google's own overlay language kept on top. the author, Oct 31: "keep the design language as close to Google's current screensaver; it's the background image we're replacing." Reference product: the [Vestaboard Flagship](https://www.vestaboard.com/flagship). Nothing is coded until the open questions below are settled.

## References

**Google ambient mode on this TV** (`reference-google-ambient-2026-10-31.png`, captured over ADB): full-bleed photo; top-right in two lines, TV name with a Cast glyph and the Wi-Fi name with a Wi-Fi glyph; bottom-left the photo credit; bottom-right the weather (temperature and a condition glyph) and, larger, the time in 12-hour form without AM/PM. Google Sans-like type, white, soft drop shadow, no panels. The photo changes every few minutes.

**Vestaboard Flagship** (vestaboard.com, read Oct 31): 6 rows x 22 columns = 132 "bits", 64 flaps per bit (8,448 flaps); 41.2 x 22 in, so the board is about 1.87:1; black or white colourway, framed; characters A to Z, 0 to 9, `! @ # $ & ( ) - + = ; : ' " % , . ? / ♥` plus colour tiles (the classic set: red, orange, yellow, green, blue, violet, white). Flips make an audible clatter on the real board. The app side: messages sent or scheduled, pinned messages, **Quiet Hours**, and (Vestaboard+) **channels** that rotate automated content: calendar, weather, now playing, news, quotes, sports, stocks, countdowns, celebrations; custom recurring content.

## Proposal

**One Android screensaver (Daydream) app**, `ca.han.bravia.flap`, set as the system screensaver (`screensaver_components`), so it starts after the 2-minute idle timer the TV already has, over Home and over any app that is not playing. Modelled on CleanYourTV's screensaver add-ons; same build chain as the Continue tile.

**Picture.** A matte near-black room; the board centred, drawn at the Flagship's 22 x 6 geometry and 1.87:1 ratio at about 70% of the screen width, with a thin dark frame and a faint top light so it reads as an object rather than a texture. Bits are slightly rounded dark tiles with the Vestaboard's split line across the middle; characters in a condensed sans in off-white; colour bits as flat chips. A very slow drift of the whole picture (a few pixels a minute) and the overlay clock moving once a minute keep nothing static.

**Overlay, Google's layout kept:** bottom-right the temperature + condition glyph and the large time; top-right the two small lines; bottom-left where Google puts the credit, the name of the channel currently on the board ("Calendar", "Up next"). Open: whether the top-right lines stay as TV name and Wi-Fi (Google's Cast hints) or become something useful (date, or nothing).

**Flip behaviour.** Content changes flip bit by bit from the top-left in a short wave (about 1.2 s total), each bit cycling a few intermediate glyphs; no flips otherwise. Silent by default; an optional soft flip sound is a toggle, off. Burn-in is not a concern on this LCD, so the cadence is for interest: a channel change every 3 to 5 minutes, and a time-only refresh never (the time lives in the overlay).

**Channels (the board's content), in rotation, each a 6 x 22 layout:**
1. **Today**: date, weather now and the high/low, sunrise or sunset next, one line of the day's first calendar item.
2. **Calendar**: the next three to four events across chosen calendars, with day and time; a colour chip per calendar.
3. **Up next**: what the Continue tile would open (title, episode, app, minutes left), read from the same resolver.
4. **Message**: a pinned text the author sets from the Mac or the TV (a countdown, a reminder, a quote); shown only when set.
Candidates, not in v1: news headlines, now playing (Spotify), stocks or crypto, sports.

**Data.** Weather: Open-Meteo (free, no key), Vancouver by coordinates, refreshed every 30 minutes while the screensaver runs, cached otherwise. Calendar: Google Calendar's secret iCal address per calendar, read-only by design; the address is a secret and lives only in the app's private storage on the TV, pushed over ADB once. Up next: the Continue tile's resolver (same code, same permissions). Nothing runs while the screensaver is not on screen; no background services, no accounts.

**Quiet hours.** After a set hour (say 23:00) the board dims to about 30% and stops flipping; the overlay clock stays. Matches the Vestaboard's Quiet Hours.

**Performance.** Canvas drawing on a SurfaceView; one frame per minute at rest, 60 fps only during the 1.2 s flip wave; no blur, no video, no GPU effects. Target under 40 MB resident while running, nothing when not.

**Not doing.** No Vestaboard account or API (the point is a free, local look-alike), no sound by default, no third-party trackers, no replacement of the launcher's own idle clock (it switches off once this exists so the two don't compete at the 2-minute mark).

## Decisions (the author, Oct 31, afternoon)

- **Overlay is not one-to-one with Google.** Small status items only, and only if they are real: the Wi-Fi name and glyph tied to actual connectivity, the "Living room TV" cast glyph shown only while the Cast receiver is enabled and online; if either cannot be made real, it goes. Time, date and weather move **onto the board, large**, so the overlay does not repeat them.
- **No Message channel in v1** (nice later). The **next calendar event is the main item**: it occupies the lower half of the main face and flips in real time: the countdown ticks by the minute, and when the event ends the following one flips in.
- **Calendars:** three, all read-only: the author, a second personal calendar (spelling to confirm) and a work calendar. A colour chip per calendar .
- **Space:** fill the 22 x 6 grid; date, time and weather big enough to read from the sofa.

**Main face ("Now"), 22 columns x 6 rows, proposed:**
```
SAT 31 OCT   1:48 PM
12° PARTLY CLOUDY  14/8

▮NEXT  TODAY 3:00 PM
▮USTX PRODUCT REVIEW
▮IN 1H 12M        TX
```
Row 1 date and time (the minute digits flip each minute: a small live animation for free). Row 2 weather now, high/low. Rows 4 to 6 the next event with its calendar chip; "IN 1H 12M" becomes "NOW" during the event and the row flips to the next one when it ends. With no event in the next 48 h: the next two days' summary, or the Up next face takes the lower half.

**Secondary faces**, 45 s each every 5 minutes, otherwise the main face: **Week** (the next four events, one per row, chip + day + time + title) and **Up next** (continue-watching item). Mock frames: `mock-now.png`, `mock-up-next.png` (drawn on the Mac with PIL from fixed data, not the app).

## Decisions, second round (the author, Oct 31, later)

- **Layout approved.** "14/8" was today's high and low with the 8 cut off: the weather now takes rows 2 and 3 ("12° PARTLY CLOUDY" / "HIGH 14  LOW 8  RAIN"), the next event rows 5 and 6.
- **Full screen, no overlays.** The Google-style status lines go; the board fills the screen (2.5% side margin, bits sized to the height). No channel label in the corner: the face says what it is.
- **Colour = face.** One accent per face, as chips: blue for Now, red for Up next, yellow for Message. Calendars are told apart by a short label in text, so colour stays unambiguous.
- **Rotation:** Now 60 s, Up next 30 s, Now 60 s, Message 30 s, round and round; a face with nothing to show (no message set, nothing to continue) is skipped. The minute digits and the countdown flip in place on Now.
- **Message channel in v1.** Source: a dedicated Google calendar named "TV Board" shared with the screensaver like the others: an all-day event's title is the message (up to 4 lines of 22, split on words or on " / "), and timed events can carry a message for a window. That gives phone entry, scheduling, repeats and expiry with no new input screen or server; a text file pushed over ADB remains as a fallback. Mock: `mock-message.png`.

## Message face, automated (the author, Oct 31, evening)

Precedence: a TV Board calendar message if one is current; otherwise the **quote of the day**. Quote sources, in order of reliability:
1. **Curated local list** (v1): a `quotes.json` on the TV with short, attributed quotes grouped by author (Rick Rubin first; others the author names). One quote per day per author channel, chosen deterministically from the date so it is stable all day and never repeats until the list is exhausted; the face shows the quote across up to four rows and the author on the last row with the yellow chip. Fits the 22 x 6 grid only if the quote is 80 characters or less, so the list is curated to that. Works offline; a 100-quote list lasts about three months before repeating.
2. **Refill from the Mac** (v1.1): Alice extends the list in review with the author (short quotes, attributed, no bulk copying of a book) and pushes the file over ADB; or, once a TV Board calendar exists and is shared with the work account, writes a week of all-day events so the TV needs nothing pushed.
3. **Internet quote APIs**: not used. The free ones (ZenQuotes, Quotable) are generic and unreliable and cannot filter to one person; scraping Wikiquote from the TV is brittle. Not worth a network dependency for this.

Decided: Rick Rubin only for now (the author, Oct 31). Open: whether a date line ("31 OCT") stays on the quote face.

## Scope and publishing (the author, Oct 31, evening)

- **v1 faces: Now and Up next only.** Message and quotes are deferred (the design above stays for later). Week face optional, after the two work.
- **Open source from the start.** the author wants to publish the screensaver for other debloated-TV owners (calendar, local time and weather on a split-flap board). So the code lives in its own repository, not in the vault, and is written as a product from day one: MIT licence, README, a sample config with placeholders, no personal data anywhere in git.
- **Security rules for the public repo:** calendar links, coordinates, Wi-Fi names, IPs and device names never in the repo (private config pushed over ADB into app-private storage; the repo ships `config.example.json`); nothing exported that could leak the config (no exported provider or receiver that reads it); network only to the configured calendar hosts and Open-Meteo; no analytics, no accounts, no self-updater; calendar links never written to logs; a short SECURITY.md stating the model and how to report issues; `.gitignore` covers build output, `local.properties`, keystores and any `private/` folder. the author's own keystore and config stay in a private file outside the repository.
- **Up next must be self-contained** for a public app: read Watch Next in the screensaver itself (`READ_TV_LISTINGS`), no dependency on the Continue tile or the launcher patches, both of which stay private to the author's setup.
- Defaults assumed until the author says otherwise: 12-hour clock (matches the TV), quiet hours from 23:00, both configurable.

## Calendar feeds verified (Oct 31, 14:25)

Three secret iCal addresses in a private file outside the repository (never in the vault or the repo), all answering 200 with the right calendar names: three calendars of a few hundred events each. Implementation notes from the sizes: parse incrementally and keep only events in a 14-day window; expand `RRULE` recurrences inside that window (the music calendar's upcoming items are recurring, so a DTSTART-only scan shows none); respect `VALARM`-free all-day events as date-only; re-fetch every 15 minutes while the screensaver runs with `If-Modified-Since`, cache the last good copy so a network blip never blanks the board.

## Open questions for the author (remaining)

1. Message source: the "TV Board" calendar route, or a text file / phone web page instead?
2. Calendar access: the secret iCal address of each calendar (Google Calendar settings > the calendar > "Integrate calendar" > "Secret address in iCal format"), kept in the private config only. A Workspace admin may disable secret addresses; the fallback is sharing that calendar to a personal account.
3. Quiet hours start time, and 12- or 24-hour clock (mock assumes 12 h).
4. Board colourway: black assumed.

## Build plan once settled

1. Static render first: one frame of each channel from fixed sample data, screenshotted on the TV and reviewed with the author, before any animation or data work.
2. Flip animation and the slow drift.
3. Data: weather, then calendar, then Up next; each one checked on the TV.
4. Install as the system screensaver, switch the launcher's idle clock off, live with it.
