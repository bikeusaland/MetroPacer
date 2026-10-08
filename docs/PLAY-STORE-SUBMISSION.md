# MetroPacer — Google Play Submission Guide

End-to-end steps to get the Android build of MetroPacer onto Google Play. The Android app
lives in [`android/`](../android/) (Kotlin + Jetpack Compose); paste-ready store copy is in
[`PlayStore-listing.md`](../PlayStore-listing.md).

## Key facts
| | |
|---|---|
| Package name (permanent) | `io.github.bikeusaland.metropacer` |
| minSdk / targetSdk / compileSdk | 26 (Android 8.0) / 36 (Android 16) / 37 |
| versionName / versionCode | `1.0` / `1` — in `android/app/build.gradle.kts` |
| Support / website | https://bikeusaland.github.io/MetroPacer/support.html |
| Privacy Policy URL | https://bikeusaland.github.io/MetroPacer/privacy.html |
| Data collection | None → Data safety: "No data collected / No data shared" |
| Permissions | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `VIBRATE` (all install-time; no runtime prompts) |

> Play requires new apps and updates to target **API 36 or higher** (since 31 Aug 2026).
> Expect that floor to rise every August — check
> [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878).

---

## 0. Prerequisites
- [ ] **Google Play developer account** — https://play.google.com/console/signup, one-time US$25,
      identity verification (takes a few days).
- [ ] **Paid app?** The iOS app is $0.99. Charging on Play needs a **payments profile**
      (Play Console → Setup → Payments profile). Price can't be changed from Free to paid later,
      so decide before publishing.
- [ ] Build toolchain: JDK 17 and the Android SDK (Android Studio installs both; or
      `brew install openjdk@17 && brew install --cask android-commandlinetools`).
      Set `sdk.dir=...` in `android/local.properties` (git-ignored) if not using Android Studio.

> **New personal accounts:** before you can publish to production you must run a
> **closed test with at least 12 testers opted in for 14 days in a row**, then apply for
> production access. Start step 6 as early as possible — it's the long pole.
> ([details](https://support.google.com/googleplay/android-developer/answer/14151465))

---

## 1. Create the upload key (once — back it up!)
Play App Signing holds the real app-signing key; you sign uploads with an **upload key**.
```sh
keytool -genkeypair -v -keystore ~/metropacer-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```
Create `android/keystore.properties` (git-ignored):
```properties
storeFile=/Users/<you>/metropacer-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```
Store the `.jks` and passwords in a password manager. A lost upload key can be reset via
Play support, but it's slow.

---

## 2. Build the release bundle
```sh
cd android
./gradlew testDebugUnitTest bundleRelease
```
Output: `android/app/build/outputs/bundle/release/app-release.aab`, signed with the upload key
when `keystore.properties` exists (unsigned otherwise — Play rejects unsigned bundles).

Bump `versionCode` (and `versionName` as appropriate) in `android/app/build.gradle.kts`
before every upload — Play rejects duplicate version codes.

> Pre-flight on a real phone: start Spotify / YouTube Music, then Start in MetroPacer.
> Confirm the click layers over the music, keeps going with the screen off, the
> notification's **Stop** works, and an incoming call pauses the beat and it resumes after.

---

## 3. Create the app  (Play Console → Home → Create app)
- App name: `MetroPacer: Run Cadence`
- Default language: English (United States)
- App or game: **App** · Free or paid: per step 0
- Accept the declarations → **Create app**

---

## 4. App content  (Policy and programs → App content)
- [ ] **Privacy policy**: https://bikeusaland.github.io/MetroPacer/privacy.html
- [ ] **Ads**: No, my app does not contain ads
- [ ] **App access**: All functionality is available without special access
- [ ] **Content rating**: IARC questionnaire, category *All other app types*, answer **No**
      to everything → Everyone / PEGI 3
- [ ] **Target audience**: 13+ age groups (13–15, 16–17, 18+). Selecting under-13 groups
      opts the app into the Families policy for no benefit.
- [ ] **Data safety**: "Does your app collect or share any of the required user data types?"
      → **No**. Settings are stored only on the device (same answers as
      [`app-privacy-questionnaire.md`](app-privacy-questionnaire.md)).
- [ ] **Health apps**: required for all apps. MetroPacer has no health data or
      sensors; pick the option that matches a fitness pacing tool (e.g. *Activity and fitness*)
      or "no health features" per the form's current wording.
- [ ] **Foreground service permissions** → **Media playback**:
  - *Description:* "MetroPacer plays a metronome click that the user starts and stops. The
    `mediaPlayback` foreground service keeps that click playing while the screen is off or
    another app (e.g. a music player) is in front, which is the app's core function during a
    run. The ongoing media notification shows the tempo and a Stop button."
  - *Impact if deferred/interrupted:* "The beat would stop mid-run; the user loses their
    cadence cue."
  - *Video:* an unlisted YouTube link showing: start music in another app → Start in
    MetroPacer → lock the phone (click continues) → Stop from the notification.
- [ ] Government apps / Financial features / News: No / None / No

---

## 5. Store listing  (Grow users → Store presence → Main store listing)
Source: [`PlayStore-listing.md`](../PlayStore-listing.md)
- [ ] App name, short description, full description
- [ ] App icon: `android/store/icon-512.png`
- [ ] Feature graphic: `android/store/feature-graphic-1024x500.png`
- [ ] Phone screenshots (2–8): `android/store/screenshots/`
- [ ] Category **Health & Fitness**, contact email `metropacer@gmail.com`, website

---

## 6. Closed test  (Test and release → Testing → Closed testing)
1. Create a track (e.g. "Alpha"), add testers by email list or Google Group (≥12 people
   who will stay opted in for 14 days).
2. **Create release** → upload `app-release.aab` → accept **Play App Signing** →
   release notes from `PlayStore-listing.md` → **Review release → Start rollout**.
3. Share the opt-in link; testers install from Play.
4. After 14 days with ≥12 opted-in testers: **Dashboard → Apply for production access**
   (answer the questionnaire about the test).

> Internal testing (up to 100 testers, available within minutes, no review) is handy for
> your own devices but does **not** count toward the 12-tester requirement.

---

## 7. Production
- [ ] **Production → Create release** → promote the tested build (or upload a new `versionCode`)
- [ ] Countries/regions: all (or choose)
- [ ] **Send for review** → typically hours to a few days for a new app
- [ ] Once live: add a "Get it on Google Play" badge to `docs/index.html` (it currently says
      "For iPhone") — use Google's official badge artwork and link
      `https://play.google.com/store/apps/details?id=io.github.bikeusaland.metropacer`.

---

## Repo asset map
| File | Used in |
|------|---------|
| `android/` | Steps 1–2 (source, Gradle build) |
| `PlayStore-listing.md` | Steps 3, 5, 6 (name, descriptions, release notes) |
| `android/store/icon-512.png`, `feature-graphic-1024x500.png` | Step 5 |
| `android/store/screenshots/` | Step 5 |
| `docs/privacy.html`, `docs/support.html` | Steps 4, 5 (hosted URLs) |
| `docs/app-privacy-questionnaire.md` | Step 4 (Data safety answers mirror it) |

## How the Android port maps to iOS
| iOS | Android |
|-----|---------|
| `AVAudioPlayerNode` one-buffer-per-beat scheduling | `ClickRenderer` → streaming `AudioTrack` (frame-counted, drift-free) |
| `.playback` + `.mixWithOthers` | No audio-focus request, so other apps keep playing |
| `UIBackgroundModes: audio` | `mediaPlayback` foreground service (`MetronomeService`) |
| `MPRemoteCommandCenter` / Now Playing | `MediaSessionCompat` + media notification |
| `AVAudioSession` interruptions | Audio-mode watch: calls pause the beat, it resumes after |
| Flash/haptic on render-clock host time | Pulses scheduled from `AudioTrack.getTimestamp` |
| `CHHapticEngine` | `Vibrator` one-shot effects |
| `UserDefaults` | `SharedPreferences` (same keys) |
