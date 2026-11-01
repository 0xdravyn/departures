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
- [ ] A release APK attached to a GitHub Release with its SHA-256, so nobody has to install a JDK to try it. (`departures-v0.1.0.apk` built and signed Oct 31; the Release itself is the owner's.)

## 3. Security and privacy
- [x] `tools/check-secrets.sh --all` clean; pre-commit hook documented; CI (`checks.yml`) green with gitleaks over the full history.
- [x] History review: every commit inspected for config, keystores, screenshots that show calendar titles the author does not want public (Oct 31; commits rewritten to the 0xdravyn noreply identity).
- [x] Screenshots in the README taken from the app's demo mode (sample data), on the real TV.
- [ ] SECURITY.md reporting path tested (a private contact or GitHub private vulnerability reporting enabled).
- [x] Repository settings: main ruleset (no deletion, no force push), secret scanning and push protection on.

## 3b. Audit, Oct 31 (security, efficiency, packaging, energy)

Done in one pass over the whole tree; each line says what was checked and what, if anything, changed.
- **Permissions:** `INTERNET`; optional and ADB-granted only: `READ_TV_LISTINGS`, `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`. No network-state, location, storage, boot or wake-lock permission; the app holds the screen on only through the window flag while the board activity is in front.
- **Exported components:** the board (the launcher tile), Settings (the system may open it from the screensaver meta-data; it reads no extras), Import (reads one fixed shell-owned path, no extras), the dream (guarded by `BIND_DREAM_SERVICE`). No receivers, no providers, nothing exports the configuration.
- **Network:** HTTPS only (`Net` refuses anything else; cleartext is off in the manifest); hosts are the configured calendar links, Open-Meteo, Yahoo chart, CoinGecko. Timeouts on every call; bodies capped at 8 MB; a plain `Departures/0.1` user agent; nothing identifying sent. Calendar addresses are never logged (the label is).
- **Storage:** config, caches and preferences in app-private storage only; the pushed copy in `/data/local/tmp` is deleted by the setup step. Uninstall removes everything.
- **Efficiency:** at rest the board redraws once a minute (and on data changes); 60 fps only during a flip wave of about a second, and the wave starts at the first column that changes, so a lone change at the right edge flips at once instead of waiting through the wave. Each character's fit in its tile is measured once per size, not per frame. Measured on the Sony (Google TV 12): about 0 to 6% of one core at rest with the scatter effect on, 3 to 9% with seconds on the clock (about five frames a second), 34 MB proportional set size. The per-bit `RectF` allocation in the draw loop was replaced by one reused rectangle (132 fewer allocations per frame). Data refreshes only while the board is on screen: weather every 30 min, calendars every 15 (conditional requests), Watch Next every 5, quotes every 10 plus one forced refresh per Markets view. The data thread stops when the board leaves the screen. Measured on the Sony: about 24 MB resident while running, nothing when not.
- **Energy:** no background services, no alarms, no wake locks; the screensaver obeys the TV's own sleep timer. True black background (OLED pixels off); quiet hours dim the rest.
- **Packaging:** R8 minify and resource shrinking on, no libraries, 93 KB APK, one bundled OFL font. Release signing from a keystore outside the repo; without it the build signs with the debug key so anyone can build.
- **Repo hygiene:** `tools/check-secrets.sh` as pre-commit and in CI with gitleaks; `.gitignore` covers config, keystores, build output; demo mode for screenshots.
- **Open items:** a second TV for compatibility; a reviewer who has not seen the project to read `SECURITY.md` and `PROMPT.md` cold; the TX calendar's Workspace policy on secret addresses (works today).

## 4. Repository presentation (CleanYourTV style)
- [x] README: banner, tagline, intro, badges, "Start here", "What you get" with the four views, "Two ways to run it", "Setup instructions", "The settings page", "Security", "Restoring your TV", "Contributing", "Support", "Licence".
- [x] "The story": dropped from the README; the owner tells it on X instead.
- [x] `FUNDING.yml` with the Buy Me a Coffee handle (dravyn), support badge and a Support section in the README.
- [x] Licence notes for the bundled font (OFL) kept next to it.

## 5. Launch
- [ ] X post drafted by the author and posted by the author (owner): what it is, one short clip of the flips, the repo link. Nothing is scheduled or posted by tooling. Before the post: the GitHub Release (so the README's download link works) and the social preview image (`docs/social-preview.png`, Settings > General > Social preview) so the link card shows the board.
- [ ] A CleanYourTV `SHOWCASE.md` row, if the maintainers accept screensavers that work with their launcher.
- [ ] Watch the first issues for a week; fix setup problems before features.
