# Contributing

Thanks for looking. Departures is small on purpose: plain Java, no libraries, one board, a few faces. Good contributions keep it that way.

**Before you start:** open an issue for anything bigger than a fix, so nobody builds the same thing twice. Faces, data sources and TV-specific quirks are all welcome; see `DESIGN.md` for what is planned and what was decided against.

**Setup:** JDK 17 and the Android SDK (platform 34). `./gradlew assembleRelease` builds a debug-signed APK; `adb install -r` it, then follow the README's setup. Enable the pre-commit hook once per clone so you cannot commit a calendar address by accident:

```bash
git config core.hooksPath .githooks
```

**Rules of the house**
- Nothing personal in git: no calendar addresses, coordinates, Wi-Fi names, device names or IPs, in code, docs, screenshots or issues. `tools/check-secrets.sh --all` must pass; CI runs it and gitleaks.
- No new permissions without a README line explaining them. No background services, no analytics, no self-updater, no network beyond the configured calendar hosts and Open-Meteo.
- Keep the board honest: 22 columns, 6 rows, the flap set in `Board.java`. If text does not fit, shorten the words, do not shrink the type.
- Test on a real TV and say which one in the pull request.
- Match the code style around you: small files, comments that say why, no frameworks.

**Licence:** MIT. By contributing you agree your changes are MIT too.

## Adding a view

Views are the contribution this project most wants. One view is one small static method in `Faces.java` that fills a `Board` (22 by 6 cells: letters, digits, punctuation, the seven colour chips and the four weather glyphs), plus:

1. a constant and a slot in the rotation in `FaceCycle.java`, with the rule for when the view has something to show;
2. a row in `SettingsActivity.java` (on or off, seconds) and the matching entry in the settings page's Views section (`assets/settings.html`) and `SetupServer.state()`;
3. any new data in `Data.java`, fetched on the background thread, cached on disk, and never logged if it is personal;
4. a README line and, if it needs a permission or a network source, a `SECURITY.md` line.

Keep to the grid: text that does not fit is shortened, never shrunk, and nothing moves except flaps.
