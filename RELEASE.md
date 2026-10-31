# Before this goes public

The checklist for the first public release. Items marked (owner) need the maintainer's own words or accounts.

## 1. Product
- [ ] Features the maintainer wants in v1 before release (list to come).
- [ ] Two weeks of daily use on the author's TV: flips at the minute, calendar changes appearing within 15 minutes, weather refresh, quiet hours at night, behaviour after a TV restart and after Wi-Fi drops.
- [ ] Edge cases on the board: no calendars configured, no events in 14 days, an all-day event, an event running now, titles longer than 21 characters, imperial units, 24-hour clock, a TV with no Watch Next permission.
- [ ] A second TV if one can be borrowed (different brand or Android version).

## 2. Setup experience
- [ ] `PROMPT.md` tried from scratch on a clean machine by someone who has not seen the project.
- [ ] README setup section matches what the prompt does, step for step.
- [ ] A release APK attached to a GitHub Release with its SHA-256, so nobody has to install a JDK to try it.

## 3. Security and privacy
- [ ] `tools/check-secrets.sh --all` clean; pre-commit hook documented; CI (`checks.yml`) green with gitleaks over the full history.
- [ ] History review: every commit inspected for config, keystores, screenshots that show calendar titles the author does not want public.
- [x] Screenshots in the README taken from the app's demo mode (sample data), on the real TV.
- [ ] SECURITY.md reporting path tested (a private contact or GitHub private vulnerability reporting enabled).
- [ ] Repository settings: default branch protected, secret scanning and push protection on, Discussions on if wanted.

## 3b. Audit, Oct 31 (security, efficiency, packaging, energy)

Done in one pass over the whole tree; each line says what was checked and what, if anything, changed.
- **Permissions:** `INTERNET`; optional and ADB-granted only: `READ_TV_LISTINGS`, `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`. No network-state, location, storage, boot or wake-lock permission; the app holds the screen on only through the window flag while the board activity is in front.
- **Exported components:** the board (the launcher tile), Settings (the system may open it from the screensaver meta-data; it reads no extras), Import (reads one fixed shell-owned path, no extras), the dream (guarded by `BIND_DREAM_SERVICE`). No receivers, no providers, nothing exports the configuration.
- **Network:** HTTPS only (`Net` refuses anything else; cleartext is off in the manifest); hosts are the configured calendar links, Open-Meteo, Yahoo chart, CoinGecko. Timeouts on every call; bodies capped at 8 MB; a plain `Departures/0.1` user agent; nothing identifying sent. Calendar addresses are never logged (the label is).
- **Storage:** config, caches and preferences in app-private storage only; the pushed copy in `/data/local/tmp` is deleted by the setup step. Uninstall removes everything.
- **Efficiency:** at rest the board redraws once a minute (and on data changes); 60 fps only during a flip wave of about a second. The per-bit `RectF` allocation in the draw loop was replaced by one reused rectangle (132 fewer allocations per frame). Data refreshes only while the board is on screen: weather every 30 min, calendars every 15 (conditional requests), Watch Next every 5, quotes every 10 plus one forced refresh per Markets view. The data thread stops when the board leaves the screen. Measured on the Sony: about 24 MB resident while running, nothing when not.
- **Energy:** no background services, no alarms, no wake locks; the screensaver obeys the TV's own sleep timer. True black background (OLED pixels off); quiet hours dim the rest.
- **Packaging:** R8 minify and resource shrinking on, no libraries, 93 KB APK, one bundled OFL font. Release signing from a keystore outside the repo; without it the build signs with the debug key so anyone can build.
- **Repo hygiene:** `tools/check-secrets.sh` as pre-commit and in CI with gitleaks; `.gitignore` covers config, keystores, build output; demo mode for screenshots.
- **Open items:** a second TV for compatibility; a reviewer who has not seen the project to read `SECURITY.md` and `PROMPT.md` cold; the TX calendar's Workspace policy on secret addresses (works today).

## 4. Repository presentation (CleanYourTV style)
- [x] README in CleanYourTV shape: banner, pitch, badges, "Start here", "What you get" with screenshots, setup, kiosk notes, "Restoring", "Questions people ask", "The story" (slot), "Contributing", "Licence". Support badge waits for the handle.
- [ ] "The story" (owner): short and plain, in the maintainer's own voice: the debloated TV, the Vestaboard idea, why a screensaver.
- [x] `FUNDING.yml` with the Buy Me a Coffee handle (dravyn), support badge and a Support section in the README.
- [ ] Licence notes for the bundled font (OFL) kept next to it.

## 5. Launch
- [ ] X post drafted by the author and posted by the author (owner): what it is, one short clip of the flips, the repo link. Nothing is scheduled or posted by tooling.
- [ ] A CleanYourTV `SHOWCASE.md` row, if the maintainers accept screensavers that work with their launcher.
- [ ] Watch the first issues for a week; fix setup problems before features.
