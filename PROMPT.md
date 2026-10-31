# The prompt

Paste this into Claude Code, Codex or another coding assistant with shell access, on a computer on the same Wi-Fi as the TV. It walks the assistant through installing Departures on an Android TV or Google TV set and checking the result with screenshots. Nothing here needs root, and every step can be undone (the last section says how).

Before you start, on the TV: Settings → System → About → press OK on "Android TV OS build" (or "Build") 7 times, then back → Developer options → turn on USB debugging. Keep the TV on and awake.

Have ready: your city's latitude and longitude, and the **secret iCal address** of each calendar you want on the board (Google Calendar on the web → Settings → the calendar → Integrate calendar → "Secret address in iCal format"; it contains `/private-`). Paste those into the config file the assistant writes, not into the chat.

---

I want to install Departures, a split-flap departure board screensaver, on my Android TV from https://github.com/0xdravyn/departures. Work on the real device step by step, verify with screenshots pulled from the TV, and ask me before anything that depends on my preferences. Never print, log or repeat my calendar addresses.

Get the code
- Clone the repository if it is not in the working folder: `git clone https://github.com/0xdravyn/departures` and work from it.
- Read README.md and SECURITY.md first. Follow them over anything you assume.

Connect
- Find the TV on my network (mDNS `_androidtvremote2._tcp`, the ARP table, or ask me for the IP) and `adb connect <ip>:5555`. If port 5555 stays closed, the TV may only offer Wireless debugging under Developer options: ask me for the pairing code and port, `adb pair`, `adb connect`, then `adb tcpip 5555`.
- The first connection shows "Allow USB debugging?" on the TV; tell me to tick "Always allow" and accept.

Build or take the APK
- If a release APK is in the repository's Releases page, prefer it; otherwise build with JDK 17 and the Android SDK: `./gradlew assembleRelease` (it signs with the debug key, which is fine for one TV). Tell me what you had to install on this computer.
- `adb install -r app/build/outputs/apk/release/app-release.apk`.

Configure
- Copy `config.example.json` to `config.json` (it is git-ignored). Fill in my coordinates, units, 12h or 24h clock and quiet hours after asking me; leave the calendar lines for me to paste in, then wait until I say the file is saved.
- Check each calendar address only by its shape and by fetching it: report "ok" or the HTTP status, never the address. A `/public/` address returns 404 for a private calendar; the secret one has `/private-` in it.
- Push and import: `adb push config.json /data/local/tmp/departures-config.json`, `adb shell am start -n app.departures.tv/.ImportActivity`, then `adb shell rm /data/local/tmp/departures-config.json`. Confirm the import toast or the "configuration imported" log line.
- If I want the Up next face: `adb shell pm grant app.departures.tv android.permission.READ_TV_LISTINGS`.

Verify
- Open the preview: `adb shell am start -n app.departures.tv/.PreviewActivity`, wait 10 seconds, pull a screenshot (`adb shell screencap -p /data/local/tmp/d.png`, `adb pull`), and show me. Check the weather is for my city and the next event is real. Press Right (`adb shell input keyevent KEYCODE_DPAD_RIGHT`) and screenshot the Up next face.
- If the screen is black, the TV is probably asleep: `adb shell input keyevent KEYCODE_WAKEUP` and try again.
- Read `adb logcat -d -s Departures` and explain any warning to me in plain words.

Make it the screensaver
- `adb shell settings put secure screensaver_components app.departures.tv/.DeparturesDream`, and tell me the TV's idle timer (`adb shell settings get system screen_off_timeout`, in milliseconds); offer to set it to 120000 for two minutes.
- Start it once to prove it: `adb shell am start -n com.android.systemui/.Somnambulator`, wait 10 seconds, screenshot, then `adb shell input keyevent KEYCODE_HOME`.
- Tell me to switch USB debugging back off on the TV when we are done.

Undo, if I ask
- `adb shell settings put secure screensaver_components <the previous value you noted>`, then `adb shell pm uninstall --user 0 app.departures.tv`.
