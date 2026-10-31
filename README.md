<p align="center">
  <img src="docs/banner.png" alt="Departures: a split-flap departure board on a TV, showing the date, time, weather and the next calendar event" width="900">
</p>

<p align="center"><b>A split-flap departure board for your TV.</b><br>
Date, time, the weather, what's next on your calendar, what you were watching, and the markets, flipping in place as they change.</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/licence-MIT-cfffcf?style=flat&labelColor=15181e" alt="MIT licence"></a>
  <a href="https://github.com/0xdravyn/departures/stargazers"><img src="https://img.shields.io/github/stars/0xdravyn/departures?style=flat&color=cfffcf&labelColor=15181e" alt="Stars"></a>
  <a href="https://github.com/0xdravyn/departures/actions"><img src="https://img.shields.io/github/actions/workflow/status/0xdravyn/departures/checks.yml?style=flat&labelColor=15181e" alt="Checks"></a>
  <a href="https://buymeacoffee.com/dravyn"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Support: buy me a coffee"></a>
</p>

Runs on Android TV and Google TV (Android 8.0 and newer): as the **screensaver**, or as an **app** that stays on all day. No account, no server, no analytics. Your calendar links and location never leave the TV.

## Start here

| You want | Go to |
|---|---|
| **To see it** | [What you get](#what-you-get), three screenshots from a real TV |
| **To install it with a coding assistant** | [`PROMPT.md`](PROMPT.md): paste it into Claude Code or Codex and it does the setup with you |
| **To install it by hand** | [Setup](#setup), about ten minutes with ADB |
| **To know what it reads and sends** | [Your data stays yours](#your-data-stays-yours) and [`SECURITY.md`](SECURITY.md) |
| **To change or extend it** | [`DESIGN.md`](DESIGN.md) for the decisions, [`CONTRIBUTING.md`](CONTRIBUTING.md) for the rules |

## What you get

A 22 by 6 board of flaps, full screen on a true black background (on an OLED those pixels are off). Three faces take turns; bits flip only where a character changed, in a wave from the left, and there is no sound.

| Now | Keep watching? | Markets |
|---|---|---|
| ![Now face](docs/face-now.png) | ![Keep watching face](docs/face-keep-watching.png) | ![Markets face](docs/face-markets.png) |
| Date and time (the minutes flip), the weather now with today's high and low, and what's next on your calendars today: the event running now, else the next to start, each giving way to the following one as it ends. Once today is done, tomorrow's first event. | The newest resume entry across your streaming apps (Android TV's Watch Next table), when there is one. | Up to five assets with the day's change, green or red, under a `MARKETS @ time` header. Defaults: S&P 500, NVIDIA, Google, Bitcoin, gold. |

Quiet hours dim the board at night. The screenshots are the app's demo mode, which uses sample data.

## Two ways to run it

- **As the screensaver** (the usual way): after the TV's idle timer, over Home or any app that is not playing. The TV's own sleep timer still applies afterwards, as with any screensaver.
- **As an app, all day:** open Departures from the home screen. It holds the screen on and runs until you press Back, for a bar, a shop, a studio or a hallway. Some TVs add an "idle standby" that switches off after a few hours without a remote press whatever is running (Sony: Settings > System > Power > Idle TV standby); turn it off for all-day use.

## Settings on the TV

Hold OK on the board (or press the remote's Menu button). One row per view: how long it stays before the next, or Off to drop it from the rotation (Markets only for a shop, Now only for a hallway; the last view on cannot be switched off). Also the flip animation, board brightness, 12 or 24-hour clock, Celsius or Fahrenheit, and quiet hours with their start and end. A three-second reminder at every app launch says how to get here. The app and the screensaver share these settings. What needs typing (your coordinates, calendar addresses, the market list) lives in `config.json`.

## Setup

What you need: a computer on the same Wi-Fi as the TV, `adb` (Android platform tools), and USB debugging switched on in the TV's Developer options (Settings > System > About, press OK on the build number seven times, then back one level).

1. **Install the APK.** Take `app-release.apk` from the latest [release](https://github.com/0xdravyn/departures/releases) or build it yourself (`./gradlew assembleRelease`, JDK 17 and the Android SDK), then:
   ```bash
   adb connect <tv-ip>:5555
   adb install -r app-release.apk
   ```
2. **Write your configuration.** Copy [`config.example.json`](config.example.json) to `config.json` (it is git-ignored) and fill in:
   - `location`: your latitude and longitude, for the weather.
   - `calendars`: one entry per calendar, with its **secret iCal address**. In Google Calendar on the web: Settings > the calendar > Integrate calendar > "Secret address in iCal format" (it contains `/private-`; the public address returns 404 for a private calendar). Anyone holding that link can read the calendar, so treat it like a password.
   - `markets`: plain names or tickers, up to five: `["S&P 500", "NVDA", "bitcoin", "gold"]`. Stocks, indices and futures come from Yahoo Finance's public endpoint; crypto from CoinGecko, chosen from the name (`bitcoin`, `ETH`, or any lower-case CoinGecko id). `"yahoo:"` and `"coingecko:"` prefixes force a source; `[]` turns the face off.
   - `clock`, `units`, `quiet_hours`, `calendar.lookahead_days`: optional.
3. **Push it into the app's private storage** (nowhere else on the TV can read it):
   ```bash
   adb push config.json /data/local/tmp/departures-config.json
   adb shell am start -n app.departures.tv/.ImportActivity
   adb shell rm /data/local/tmp/departures-config.json
   ```
4. **Optional, for Keep watching?:** `adb shell pm grant app.departures.tv android.permission.READ_TV_LISTINGS`
5. **Make it the screensaver.** Google TV's own Screen saver page lists only Google's modes, so the switch lives in Departures' settings; grant it the two permissions once:
   ```bash
   adb shell pm grant app.departures.tv android.permission.WRITE_SECURE_SETTINGS
   adb shell appops set app.departures.tv WRITE_SETTINGS allow
   ```
   Then open Departures, hold OK, and set **TV screensaver: Departures** and **Start after the TV idles for** (the same value the TV calls its sleep or screensaver delay). Without the grants, the ADB equivalent is `adb shell settings put secure screensaver_components app.departures.tv/.DeparturesDream` and `adb shell settings put system screen_off_timeout 120000` (two minutes).
6. Open Departures from the home screen once to check the board, then switch USB debugging off again.

**Kiosk use.** For a board that comes back on its own after a power cut, let the home screen start it: the [CleanYourTV](https://github.com/Gathin23/cleanyourtv) launcher can open an app at every power-on (`open_on_start`); Android itself does not let an app start a screen from the background on TVs. Pin a face in Settings, turn quiet hours off if you are open late, and switch off the TV's idle standby.

## Your data stays yours

Departures runs entirely on the TV. It reads your calendars from the secret addresses you give it, fetches weather from [Open-Meteo](https://open-meteo.com/) (no account, no key), quotes from Yahoo Finance's public chart endpoint (unofficial, delayed) and CoinGecko's free price API (no key), and reads the TV's own Watch Next table. There is no server of ours, no account, no analytics and no self-updater. Outside an exchange's session its rows simply stop moving; crypto rows keep moving. The change is against the previous close for stocks, indices and futures, and the last 24 hours for crypto. Quotes refresh every ten minutes in the background and once more half-way through each Markets view, so a moving price flips while you watch; the sources may add their own delay. This is a wall board, not a terminal. Details and the reporting path: [`SECURITY.md`](SECURITY.md).

## Restoring your TV

```bash
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop   # Google TV's own screensaver
adb shell pm uninstall --user 0 app.departures.tv
```

Uninstalling removes the configuration with it. Nothing else on the TV was changed.

## Questions people ask

**Why 22 by 6?** It is the geometry of the full-size split-flap boards people hang on walls, and a real constraint: every row is a 22-character headline. Text that does not fit is shortened, never shrunk.

**Why no sound?** A real board clatters; a TV in a living room should not. There is no sound and no option for it.

**Does it work on my TV?** Any Android TV or Google TV on Android 8.0 or newer should run it; it was built on a Sony BRAVIA (Google TV 12). Samsung Tizen, LG webOS and Fire TV cannot run it. If your TV behaves differently, open an issue with the model.

**What about burn-in?** The background is true black, the flaps are dark grey, and the board changes every minute. Quiet hours dim it further at night.

**Can it show something else?** The faces are small Java classes over a plain grid; see `DESIGN.md` for what is planned (a message face, a week view) and `CONTRIBUTING.md` for how to add one.

## The story

<!-- The maintainer's own words go here: the debloated TV, the Vestaboard that started it, why a screensaver. Keep it short. -->
*To be written.*

## Contributing

Issues and pull requests are welcome; read [`CONTRIBUTING.md`](CONTRIBUTING.md) first. The short version: nothing personal in git, no new permissions without a README line, keep the board honest, test on a real TV.

## Support

If the board earns a place on your wall, [buy me a coffee](https://buymeacoffee.com/dravyn). It keeps the TV on.

## Licence

MIT. The bundled font is Barlow Condensed (SIL Open Font License 1.1); see `app/src/main/assets/fonts/OFL.txt`.
