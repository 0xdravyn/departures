# Departures

A split-flap departure board as your TV's screensaver. Date, time, local weather and your next calendar event, flipping in place as they change; a second face shows what you were watching last. Built for Android TV and Google TV sets that have been cleaned up with a custom launcher, but it runs on any Android TV 8.0 or newer.

Status: working on one Sony BRAVIA (Google TV 12): weather, calendars, Watch Next, flips, quiet hours. Not yet released; see `DESIGN.md` for the plan and what is next.

## What it shows

- **Now**: `SAT 31 OCT  1:48 PM`, the weather now and today's high and low, and the next event from your calendars with a countdown that ticks every minute.
- **Up next**: the newest resume entry across your streaming apps (what Android TV calls Watch Next), when there is one.

Faces alternate; bits flip only when their character changes. No sound. Quiet hours dim the board at night.

## Your data stays yours

Departures runs entirely on the TV. It reads your calendars from the secret iCal addresses you give it, fetches weather from [Open-Meteo](https://open-meteo.com/) (no account, no key), and reads the TV's own Watch Next table. There is no server of ours, no account, no analytics and no self-updater. Read `SECURITY.md` before you set it up.

## Setup (short version)

1. Build the APK (`./gradlew assembleRelease`, JDK 17 and the Android SDK) or take a release.
2. Install over ADB: `adb install -r app/build/outputs/apk/release/app-release.apk`.
3. Copy `config.example.json` to `config.json`, fill in your coordinates and calendar links, and push it into the app's private storage:
   `adb push config.json /data/local/tmp/departures-config.json && adb shell am start -n app.departures.tv/.ImportActivity`
4. Grant the Watch Next permission if you want the Up next face: `adb shell pm grant app.departures.tv android.permission.READ_TV_LISTINGS`.
5. Choose Departures in Settings > System > Screen saver (or `adb shell settings put secure screensaver_components app.departures.tv/.DeparturesDream`).

## Licence

MIT. The bundled font is Barlow Condensed (SIL Open Font License 1.1); see `app/src/main/assets/fonts/OFL.txt`.
