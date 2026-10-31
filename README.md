# Departures

A split-flap departure board as your TV's screensaver. Date, time, local weather and your next calendar event, flipping in place as they change; a second face shows what you were watching last. Built for Android TV and Google TV sets that have been cleaned up with a custom launcher, but it runs on any Android TV 8.0 or newer.

Status: working on one Sony BRAVIA (Google TV 12): weather, calendars, Watch Next, flips, quiet hours. Not yet released; see `DESIGN.md` for the plan and what is next.

## What it shows

- **Now**: `SAT 31 OCT  1:48 PM`, the weather now and today's high and low, and what is next on your calendars today: the event running now, else the next one to start, each giving way to the following one as it ends; once today is done, tomorrow's first event (`"calendar": {"lookahead_days": 0}` keeps it to today only).
- **Keep watching?**: the newest resume entry across your streaming apps (what Android TV calls Watch Next), when there is one.
- **Markets**: up to four prices with the day's change, green or red, under a centred header that says OPEN or CLOSED. The defaults are the S&P 500, NVIDIA, Bitcoin and gold; put your own in `config.json` by plain name or ticker (`"markets": ["S&P 500", "NVDA", "bitcoin", "gold"]`), or set `"markets": []` to turn the face off. Everything comes from one source, Yahoo Finance's public chart endpoint, which covers stocks, indices, futures and the major crypto pairs (`BTC-USD`); a token Yahoo does not list can be written as `"coingecko:<id>"`. The header says OPEN or CLOSED for the exchange-traded rows; outside the session the prices and the change are the last session's. The change is against the previous close for stocks, indices and futures, and the last 24 hours for crypto, which never closes. Quotes refresh every 10 minutes while the board is on screen and may themselves be delayed by the source; this is a wall board, not a terminal.

Faces rotate (Now, Keep watching?, Now, Markets); bits flip only when their character changes. No sound. Quiet hours dim the board at night. The background is true black, so on an OLED those pixels are simply off.

## Two ways to run it

- **As the screensaver** (the usual way): after the TV's idle timer, over Home or any app that is not playing. The TV's own sleep timer still applies afterwards, as with any screensaver.
- **As an app, all day:** open Departures from the home screen. It holds the screen on and runs until you press Back, for a bar, a shop, a studio or a hallway. Some TVs add an "idle standby" that switches off after a few hours without a remote press whatever is running (Sony: Settings > System > Power > Idle TV standby); turn it off for all-day use. Left/Right jump between faces, Back leaves.

## Your data stays yours

Departures runs entirely on the TV. It reads your calendars from the secret iCal addresses you give it, fetches weather from [Open-Meteo](https://open-meteo.com/) (no account, no key), quotes from Yahoo Finance's public chart endpoint (unofficial, delayed) and CoinGecko's free price API (no key), and reads the TV's own Watch Next table. There is no server of ours, no account, no analytics and no self-updater. Read `SECURITY.md` before you set it up.

## Setup (short version)

1. Build the APK (`./gradlew assembleRelease`, JDK 17 and the Android SDK) or take a release.
2. Install over ADB: `adb install -r app/build/outputs/apk/release/app-release.apk`.
3. Copy `config.example.json` to `config.json`, fill in your coordinates and calendar links, and push it into the app's private storage:
   `adb push config.json /data/local/tmp/departures-config.json && adb shell am start -n app.departures.tv/.ImportActivity`
4. Grant the Watch Next permission if you want the Keep watching face: `adb shell pm grant app.departures.tv android.permission.READ_TV_LISTINGS`.
5. Choose Departures in Settings > System > Screen saver (or `adb shell settings put secure screensaver_components app.departures.tv/.DeparturesDream`).

## Licence

MIT. The bundled font is Barlow Condensed (SIL Open Font License 1.1); see `app/src/main/assets/fonts/OFL.txt`.
