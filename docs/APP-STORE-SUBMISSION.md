# MetroPacer — App Store Submission Guide

End-to-end steps to get MetroPacer onto the App Store. Repo-side prep is complete;
what remains is the Apple account / GUI work below. Paste-ready content lives in the
files referenced at each step.

## Key facts
| | |
|---|---|
| Bundle ID | `io.github.bikeusaland.metropacer` |
| Apple Team | `D5CC9YCM6F` |
| Platform | iOS 18+, iPhone only |
| Support URL | https://bikeusaland.github.io/MetroPacer/support.html |
| Privacy Policy URL | https://bikeusaland.github.io/MetroPacer/privacy.html |
| Data collection | None → "Data Not Collected" |

---

## 0. Prerequisites
- [ ] **Apple Developer Program** membership active on team `D5CC9YCM6F` ($99/yr) — https://developer.apple.com/account
- [ ] **Released (non-beta) Xcode** installed. ⚠️ The Xcode 27 **beta** cannot submit
      to the App Store — Apple rejects beta-built uploads. Use the released Xcode for
      the final archive.
- [ ] Signed in to https://appstoreconnect.apple.com with that team.

---

## 1. Register the Bundle ID  (Developer portal)
Certificates, Identifiers & Profiles → **Identifiers** → **+** → **App IDs** → **App**.
- Description: `MetroPacer`
- Bundle ID: **Explicit** → `io.github.bikeusaland.metropacer`
- Capabilities: leave all unchecked (background audio is an Info.plist key, not a capability)
- Continue → Register.

> Optional — Xcode auto-registers this when you archive with the team selected, so
> you can skip this and let Xcode do it.

---

## 2. Create the App Record  (App Store Connect)
My Apps → **+** → **New App**.
- Platforms: **iOS**
- Name: `MetroPacer: Run Cadence` (must be unique; fall back to `MetroPacer`)
- Primary Language: English (U.S.)
- Bundle ID: select `io.github.bikeusaland.metropacer`
- SKU: `metropacer-001` (private, any unique string)
- User Access: Full Access → **Create**

---

## 3. App Information  (left sidebar)
Source: `AppStore-listing.md`
- [ ] Subtitle: `Running cadence metronome`
- [ ] Category: Primary **Health & Fitness**; Secondary **Music** (optional)
- [ ] **Privacy Policy URL**: https://bikeusaland.github.io/MetroPacer/privacy.html
- [ ] Content Rights: confirm you hold the rights
- [ ] Age Rating: fill questionnaire → results in **4+**

---

## 4. Pricing and Availability
- [ ] Price: **Free** (or set a tier)
- [ ] Availability: all territories (or choose)

---

## 5. App Privacy
Source: `app-privacy-questionnaire.md`
- [ ] "Do you collect data from this app?" → **No, we do not collect data**
- [ ] Confirm → **Publish**  → label shows **Data Not Collected**

---

## 6. Prepare the Version Page (e.g. "1.0")
Source: `AppStore-listing.md`, `app-review-notes.md`, `screenshots/`
- [ ] **Screenshots** → 6.9" iPhone set from `screenshots/captioned/`
      (upload order: 01 → 04 → 02 → 03)
- [ ] **Promotional Text**
- [ ] **Description**
- [ ] **Keywords**
- [ ] **Support URL**: https://bikeusaland.github.io/MetroPacer/support.html
- [ ] **What's New** (v1.0 notes)
- [ ] **App Review Information**: contact name/phone/email (metropacer@gmail.com);
      no demo account needed; paste the reviewer note from `app-review-notes.md`
- [ ] **Build**: appears here after step 7 (leave for now)

---

## 7. Archive & Upload the Build  (Xcode — released version)
1. Open `MetroPacer.xcodeproj`.
2. Target → **Signing & Capabilities**: team `D5CC9YCM6F`, Automatic signing.
3. Toolbar device selector → **Any iOS Device (arm64)**.
4. **Product → Archive**.
5. In the Organizer: **Distribute App → App Store Connect → Upload**.
6. Wait for processing (10–30 min). Then on the version page, **+ Build** and pick it.

> Pre-flight: run on a real device with Spotify/Podcasts playing and confirm the
> metronome layers over the audio — that's the core listing claim and reviewers test it.

---

## 8. Submit for Review
- [ ] Confirm export-compliance (no non-exempt encryption → typically **No**)
- [ ] **Add for Review** → **Submit**
- [ ] Status → *Waiting for Review* → *In Review* → *Ready for Sale*

---

## Repo asset map
| File | Used in |
|------|---------|
| `AppStore-listing.md` | Steps 2, 3, 6 (name, subtitle, copy, keywords, category) |
| `app-privacy-questionnaire.md` | Step 5 |
| `app-review-notes.md` | Step 6 (App Review Information) |
| `screenshot-checklist.md` | Step 6 (how the shots were made) |
| `screenshots/` (+ `captioned/`) | Step 6 (upload these) |
| `support.html` / `privacy.html` | Steps 3, 6 (hosted URLs) |
