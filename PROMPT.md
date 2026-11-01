# The prompt

Paste this into Claude Code, Codex or another coding assistant with shell access, on a computer on the same Wi-Fi as the TV. It walks the assistant through installing Departures on an Android TV or Google TV set and checking the result with screenshots. Nothing here needs root, and every step can be undone (the last section says how).

Before you start, on the TV: Settings → System → About → press OK on "Android TV OS build" (or "Build") 7 times, then back → Developer options → turn on USB debugging. Keep the TV on and awake.

You do not need to give the assistant anything personal. Your city, calendars and markets go in afterwards on a web page the TV serves on your Wi-Fi; the assistant never sees a calendar address.

---

I want to install Departures, a split-flap departure board screensaver, on my Android TV from https://github.com/0xdravyn/departures. Work on the real device step by step, verify with screenshots pulled from the TV, and ask me before anything that depends on my preferences. The configuration is mine to do on the TV's settings page afterwards; do not ask me for calendar addresses or paste any into the chat.

Get the code
- Clone the repository if it is not in the working folder: `git clone https://github.com/0xdravyn/departures` and work from it.
- Read README.md and SECURITY.md first. Follow them over anything you assume.

Connect
- Find the TV on my network (mDNS `_androidtvremote2._tcp`, the ARP table, or ask me for the IP) and `adb connect <ip>:5555`. If port 5555 stays closed, the TV may only offer Wireless debugging under Developer options: ask me for the pairing code and port, `adb pair`, `adb connect`, then `adb tcpip 5555`.
- The first connection shows "Allow USB debugging?" on the TV; tell me to tick "Always allow" and accept.

Build or take the APK
- If a release APK is in the repository's Releases page, prefer it; otherwise build with JDK 17 and the Android SDK: `./gradlew assembleRelease` (it signs with the debug key, which is fine for one TV). Tell me what you had to install on this computer.
- `adb install -r app/build/outputs/apk/release/departures-v0.1.0.apk`, then confirm `adb shell dumpsys package app.departures.tv` shows `User 0: ... installed=true`; if not, `adb shell pm install-existing --user 0 app.departures.tv` (a TV with a second profile keeps per-user install state, and the app, its screensaver and its import stay invisible otherwise).

Grant the three permissions it cannot ask for itself
- `adb shell pm grant app.departures.tv android.permission.READ_TV_LISTINGS` (the Keep watching? view).
- `adb shell pm grant app.departures.tv android.permission.WRITE_SECURE_SETTINGS` and `adb shell appops set app.departures.tv WRITE_SETTINGS allow` (screensaver mode; Google TV's own Screen saver page does not list third-party screensavers).

Verify and hand over
- Open the board: `adb shell am start -n app.departures.tv/.BoardActivity`, wait 10 seconds, pull a screenshot (`adb shell screencap -p /data/local/tmp/d.png`, `adb pull`), and show me. A TV with nothing set up shows "TO SET UP, OPEN", an address and a four-digit code on the board.
- If the screen is black, the TV is probably asleep: `adb shell input keyevent KEYCODE_WAKEUP` and try again.
- Read `adb logcat -d -s Departures` and explain any warning to me in plain words.
- Tell me the address and code from the screenshot and open the address in my browser. From here I fill in the Content tab (city, calendars, message, markets), hit Apply, and on the Settings tab enable screensaver mode if I want it. The page also works later from any computer on the Wi-Fi while Departures is on the TV screen.
- Tell me to switch USB debugging back off on the TV when we are done.

Undo, if I ask
- `adb shell settings put secure screensaver_components <the previous value you noted>`, then `adb uninstall app.departures.tv`.
