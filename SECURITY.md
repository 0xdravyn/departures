# Security

Departures handles one kind of secret: the iCal addresses of your calendars. Anyone holding such an address can read that calendar, so the app treats them accordingly.

- The configuration lives only in the app's private storage on the TV (`/data/data/app.departures.tv/files/config.json`), written by `ImportActivity` from a file you push over ADB and nowhere else. No exported provider, receiver or service reads it. Nothing is logged from it; errors name the calendar's label, never its address.
- Network access is limited to the calendar hosts in your configuration, `api.open-meteo.com` (weather), `query1.finance.yahoo.com` and `api.coingecko.com` (quotes, only for the assets you list), and happens only while the screensaver is on screen. No other endpoint is contacted, and nothing identifying is sent: no account, no device id, a plain `Departures/0.1` user agent.
- The app requests `INTERNET`, and three optional permissions that only ADB can grant: `READ_TV_LISTINGS` (the TV's Watch Next table, for the Keep watching? view), `WRITE_SECURE_SETTINGS` and `WRITE_SETTINGS` (to choose the screensaver and the idle delay from the app's own Settings screen, because Google TV's page does not list third-party screensavers). It uses them for exactly those two settings and nothing else; without the grants those rows are read-only.
- Keep ADB debugging on your TV switched off when you are not using it; the setup needs it only for the install and the configuration push.
- Never commit your `config.json`, keystores or `keystore.properties`; the `.gitignore` covers them. Keep `config.example.json` free of real addresses.

To report a problem, open a GitHub issue without the sensitive details and say you have a security concern; the maintainer will follow up privately.

## The setup page

While Departures is on screen, the TV serves its setup page on port 8787 to the local network only (it binds the TV's own address; nothing is exposed beyond your router). Every API request must carry the four-digit code shown on the TV, checked on the TV for each request; the page only reflects the answer. There is no account and no session beyond that code, which the owner can replace from the TV's settings. Calendar addresses are written to the TV's private storage and are never returned by any endpoint, logged, or echoed in an error. Requests are limited to 64 KB, served over plain HTTP because a local address cannot carry a public certificate, and the page loads no external resources. Anyone on your Wi-Fi who can read the code off the TV screen can change the board; that is the intended trust boundary of a home or shop network.
