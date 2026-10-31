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

## 4. Repository presentation (CleanYourTV style)
- [x] README in CleanYourTV shape: banner, pitch, badges, "Start here", "What you get" with screenshots, setup, kiosk notes, "Restoring", "Questions people ask", "The story" (slot), "Contributing", "Licence". Support badge waits for the handle.
- [ ] "The story" (owner): short and plain, in the maintainer's own voice: the debloated TV, the Vestaboard idea, why a screensaver.
- [x] `FUNDING.yml` with the Buy Me a Coffee handle (dravyn), support badge and a Support section in the README.
- [ ] Licence notes for the bundled font (OFL) kept next to it.

## 5. Launch
- [ ] X post drafted by the author and posted by the author (owner): what it is, one short clip of the flips, the repo link. Nothing is scheduled or posted by tooling.
- [ ] A CleanYourTV `SHOWCASE.md` row, if the maintainers accept screensavers that work with their launcher.
- [ ] Watch the first issues for a week; fix setup problems before features.
