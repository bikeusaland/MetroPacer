# App Store Connect — App Privacy Questionnaire Answers

Exact selections for the **App Privacy** section in App Store Connect
(My Apps → MetroPacer → App Privacy → **Get Started** / **Edit**).

Verified against the source: MetroPacer has no networking, no analytics/tracking
SDKs, no accounts, and no access to location, contacts, or health data. It stores
only metronome settings (cadence, sound, volume, accent, subdivision, flash/vibrate)
locally in `UserDefaults` on the device.

---

## 1. Data Collection — the only question that matters here

**"Do you or your third-party partners collect data from this app?"**

➡️ Select: **No, we do not collect data from this app.**

### Why "No" is correct
Apple defines "collect" as transmitting data **off the device**. Data that stays on
the device and is not sent anywhere does **not** count as collected. MetroPacer's
settings never leave the device (no server, no SDK, no network calls), so this is a
genuine "No" — not a loophole.

Selecting "No" ends the questionnaire: there are **no data types** to configure and
**no linked/tracking** follow-ups.

### Confirmation
Apple shows a reminder that your answers must be accurate and kept up to date, and
that you're responsible for third-party SDK behavior. MetroPacer bundles no
third-party SDKs, so this holds. Check the box and **Publish**.

> ⚠️ If you later add anything that sends data off device — analytics, crash
> reporting, ads, cloud sync, an account system, or any SDK that phones home — you
> must return here and update these answers before that version ships.

---

## 2. Related fields (not part of the data questionnaire, but nearby)

- **Privacy Policy URL** (App Information → General):
  `https://bikeusaland.github.io/MetroPacer/privacy.html`
- **App Tracking Transparency (ATT):** Not applicable — the app does no tracking,
  so no `NSUserTrackingUsageDescription` and no ATT prompt are required.

---

## Resulting "Privacy Nutrition Label"

With "No" selected, your App Store product page will display:

> **Data Not Collected** — The developer does not collect any data from this app.
