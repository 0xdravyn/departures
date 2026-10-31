# Security

Departures handles one kind of secret: the iCal addresses of your calendars. Anyone holding such an address can read that calendar, so the app treats them accordingly.

- The configuration lives only in the app's private storage on the TV (`/data/data/app.departures.tv/files/config.json`), written by `ImportActivity` from a file you push over ADB and nowhere else. No exported provider, receiver or service reads it. Nothing is logged from it; errors name the calendar's label, never its address.
- Network access is limited to the calendar hosts in your configuration, `api.open-meteo.com` (weather), `query1.finance.yahoo.com` and `api.coingecko.com` (quotes, only for the assets you list), and happens only while the screensaver is on screen. No other endpoint is contacted, and nothing identifying is sent: no account, no device id, a plain `Departures/0.1` user agent.
- The app requests `INTERNET`, and three optional permissions that only ADB can grant: `READ_TV_LISTINGS` (the TV's Watch Next table, for the Keep watching? view), `WRITE_SECURE_SETTINGS` and `WRITE_SETTINGS` (to choose the screensaver and the idle delay from the app's own Settings screen, because Google TV's page does not list third-party screensavers). It uses them for exactly those two settings and nothing else; without the grants those rows are read-only.
- Keep ADB debugging on your TV switched off when you are not using it; the setup needs it only for the install and the configuration push.
- Never commit your `config.json`, keystores or `keystore.properties`; the `.gitignore` covers them. Keep `config.example.json` free of real addresses.

To report a problem, open a GitHub issue without the sensitive details and say you have a security concern; the maintainer will follow up privately.
