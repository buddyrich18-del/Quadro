# Quadro

An Android app for keeping an eye on energy use, temperature and humidity: it reads an Emoncms server and public JSON/CSV links, and shows web pages (for example a FusionSolar link).

It is not an official Emoncms or FusionSolar app.

## What it does

- **Energy:** current power, today's consumption, line chart with 3H/6H/D/W/M, daily kWh bars, kWh/cost switch.
- **Custom chart:** lines (up to 4 series) or bars, grouping by hour or day, average/sum/min/max, adjustable axis. Touch and hold to read the values.
- **Temperature and humidity:** current values with a written trend (rising, steady, falling), the comfort range shown under each value, captions above the charts (unit and green area), notifications when values leave the limits.
- **Sources:** feeds from an Emoncms server (read-only key) or any public link that returns JSON or CSV.
- **Solar / web page:** if the link is FusionSolar, the app reads five values from the page and shows them as tiles with these captions, in order: Real-time power, Yield today, Yield this month, Yield this year, Total yield. For any other link it shows only the web page.
- **One card at a time:** choose the card to view at the top; use ← and → to change their order.
- **Line charts:** bold stroke with sharp corners, a light area under the line and a faint grid. The colour is chosen per series (and for the Energy card).
- **Appearance and language:** in Servers and settings choose a light, dark or automatic background, the text size and the language (Italian, English or as the phone).
- **Screensaver:** after 1, 5, 15 or 30 minutes without touches it shows a black background with the time (moving position every minute) and lowers the brightness to the level you choose (1 to 100%). Alternatively it can cycle through the cards you created at the interval you choose, from 10 seconds to 5 minutes (more than one card is needed), with a fade to black at each change. In full-screen mode (optional) it hides the phone bars, the title and the buttons. A touch closes it. Useful with "Keep screen on".

The APK is signed with the debug key: fine for personal use, not for the Play Store.

## First start

1. **Servers → Add a server.** Address (e.g. `emonpi.local` or `emoncms.org`) and the read-only API key from the Emoncms Account page. The QR button tries to read the code on the Account page; if it does not recognise it, enter the details by hand. **Test the connection** checks right away that it works.
2. **+ Card** and choose the type.

### Public links (JSON or CSV)

In the series editor choose "Public link". For JSON give the path of the list (e.g. `data.points`, empty if the root is already a list) and the time and value keys; for lists of `[time, value]` pairs use positions 0 and 1. For CSV give the name or position of the columns. Accepted times: Unix seconds or milliseconds, ISO-8601, `dd/mm/yyyy hh:mm`.

### FusionSolar

The kiosk link is a web page, not an API. The "Solar / web page" card opens it in a hidden view, reads the visible text with its position and matches each number with a unit (kW, kWh, %, etc.) to the nearest label. It uses no internal Huawei calls and no credentials: it reads what you would see by opening the link.

Limits: it is a heuristic tuned without seeing the real page. Charts drawn by the page cannot be read, only text. If Huawei changes the page, it may need retuning. For charts with history you need a JSON/CSV source (for example the official Huawei API with your plant's credentials). Anyone with the kiosk link can see the plant's data: do not share it.

## Good to know

- **Alerts:** Android does not guarantee the exact time; the check runs about every 15 minutes, and only if the phone has a network connection. On Android 13+ the app asks for notification permission. With very aggressive battery saving they may be delayed.
- **Security:** keys are stored in an encrypted store and travel in the `Authorization` header, not in the address. Plain HTTP is allowed because an emonPi on a local network often has no HTTPS; the editor warns you.
- **Daily bars:** they are computed by summing hourly consumption in the phone's time zone. For time zones with a half-hour offset the bars may be shifted.
- **Web page:** the card runs JavaScript (needed for FusionSolar) and is limited to https addresses. Values are re-read every 5 minutes.

## Structure

- `core/`: tested, pure Kotlin logic (parsers, statistics, reading values from web pages, thresholds with hysteresis, Emoncms URLs, configuration).
- `app/`: Jetpack Compose interface, hand-drawn charts, encrypted key store, alerts with WorkManager.
