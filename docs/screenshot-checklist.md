# MetroPacer — App Store Screenshot Checklist

App Store Connect requires screenshots per device size. Requirements change over
time — always confirm against the current spec in App Store Connect when you upload.
Values below reflect Apple's spec as of 2025.

---

## ⚠️ First decide: iPhone-only or iPhone + iPad?

The project currently targets **iPhone + iPad** (`TARGETED_DEVICE_FAMILY = "1,2"`).

- **If you keep iPad support:** you MUST provide iPad screenshots too, and the app
  will be reviewed on an iPad. MetroPacer's UI is portrait, iPhone-first — check it
  looks acceptable on iPad before committing to this.
- **Recommended for v1 — iPhone only:** set `TARGETED_DEVICE_FAMILY = "1"` in the
  project (both Debug and Release). Then you only need iPhone screenshots and skip
  iPad review risk. (Ask and I can make this change.)

---

## Required sizes

Up to 10 screenshots per size; **at least 1 required** for each required size.
Portrait, PNG or JPEG, no transparency, no rounded corners/alpha.

### iPhone (required)
| Size class | Pixels (portrait) | Reference device |
|------------|-------------------|------------------|
| **6.9" (required)** | **1290 × 2796** (or 1320 × 2868) | iPhone 16 Pro Max / 15 Pro Max |
| 6.5" (optional fallback) | 1242 × 2688 or 1284 × 2778 | iPhone 11 Pro Max / XS Max |

> Apple auto-scales the 6.9" set down to smaller iPhones, so a single 6.9" set is
> enough for all iPhones. Provide 6.9" at minimum.

### iPad (only if you keep iPad support)
| Size class | Pixels (portrait) | Reference device |
|------------|-------------------|------------------|
| **13" (required for iPad)** | **2064 × 2752** (or 2048 × 2732) | iPad Pro 13" / 12.9" |

---

## Shot list (aim for 3–5, ordered by impact)

The first 1–2 show in search results — make them count. Add a short caption banner
above each screenshot (device-frame + caption tools listed below).

1. **Hero — the run screen.** Big cadence number with the runner animation on both
   sides, Start button visible.
   Caption: *"Lock into your cadence."*

2. **Cadence presets.** Show a preset (e.g. 180) selected/highlighted.
   Caption: *"One tap to 180 SPM."*

3. **Plays over your music.** The main screen with the "Plays on top of your audio"
   subtitle visible; consider a caption that sells the differentiator.
   Caption: *"Your music keeps playing."*

4. **Options expanded.** Scroll so sounds, accent, and subdivision pickers show.
   Caption: *"Four sounds, accents, subdivisions."*

5. **Feel & see the beat.** Capture a beat flash (or show Flash/Vibrate toggles on).
   Caption: *"See it and feel it — even in the wind."*

---

## How to capture

1. Run the app in the **iOS Simulator** on the exact reference device
   (e.g. "iPhone 16 Pro Max" for 6.9"). Simulator screenshots are allowed and give
   exact pixel sizes automatically.
2. Set up the screen (pick cadence, start the beat, expand options, etc.).
3. Capture: **File → Save Screen** in Simulator, or ⌘S. Output matches the required
   pixel dimensions for that device.
   - CLI alternative: `xcrun simctl io booted screenshot shot.png`
4. Clean status bar (optional but polished): `xcrun simctl status_bar booted override
   --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3`
5. Add caption banners / device frames if desired.

### Timing shots that depend on animation
The runner animation and beat flash are motion — to catch a good frame, press Start,
then capture repeatedly, or record video (`xcrun simctl io booted recordVideo out.mov`)
and pull a frame.

---

## Optional but recommended

- **App Preview video** (15–30s, per size, up to 3). Screen-record a short run:
  set cadence → Start → beat flash → options. Big conversion lift, not required.
- Keep captions consistent in font/color across all shots so the set reads as a system.

---

## Pre-upload checklist
- [ ] Decided iPhone-only vs iPhone+iPad (and updated `TARGETED_DEVICE_FAMILY` if needed)
- [ ] 6.9" iPhone set captured (3–5 shots)
- [ ] iPad 13" set captured (only if iPad supported)
- [ ] All portrait, correct pixels, no alpha/rounded corners
- [ ] First screenshot is the strongest (shows in search)
- [ ] Captions consistent and legible
