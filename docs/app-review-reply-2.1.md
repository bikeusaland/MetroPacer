# Reply to App Review — Guideline 2.1 "Information Needed" (2026-09-28)

Apple sent a standard information request for developer accounts with limited
review history. It is **not a rejection**. Two things are required:

1. Reply in App Store Connect (Resolution Center on the v1.0 page) with the text
   below and the screen recording attached.
2. Paste the same text into **App Review Information → Notes** so future
   submissions carry it.

---

## Item 1 — Screen recording (you must capture this)

Apple requires a recording **on a physical iPhone running the latest iOS**, starting
from app launch. Simulator recordings are not accepted.

**Setup**
- iPhone on the current iOS release, Do Not Disturb on, volume up.
- Install the submitted build via TestFlight (build 1.0 (1) is already processed),
  or run from Xcode on the device.
- Open Spotify, Apple Music, or Podcasts and start something recognizable.
- Swipe down to Control Center and confirm audio is playing, then quit to Home.

**Record** (Control Center → Screen Record, with microphone ON so the beat is audible)
1. Tap the MetroPacer icon on the Home screen — the recording must begin here.
2. Main screen appears. Tap the **180** preset.
3. Tap **Start**. Let it run ~5 s so the click is heard over the music and the
   runner animation is visible.
4. Tap **+5**, then **−1**, to show fine adjustment.
5. Tap **Tap Tempo** four or five times in rhythm; show the cadence update.
6. Expand **Options**. Switch the sound (e.g. Woodblock → Beep), change accent to
   3/4, enable a subdivision, adjust Beat Volume.
7. Turn on **Flash** and **Vibrate**; let the screen pulse for a few beats.
8. Press the side button to lock the phone for ~5 s, then unlock — the beat kept
   playing (this demonstrates background audio).
9. Tap **Stop**. End the recording.

Target length: 60–90 seconds. Trim the start if needed but keep the app launch.
Upload the .mov/.mp4 as an attachment in the Resolution Center reply.

---

## Items 2–6 — Paste-ready reply

```
Thank you for reviewing MetroPacer. Responses to each item are below, and a
screen recording captured on a physical iPhone is attached.

1. SCREEN RECORDING
Attached. It begins at app launch and shows the typical flow: choosing a cadence
preset, starting the beat over background music, fine-tuning the tempo, using
Tap Tempo, changing sound/accent/subdivision options, enabling Flash and
Vibrate, and locking the phone while the beat continues. The app has no account
registration or login, no user-generated content, and no paid content or
in-app purchases, so none of those flows exist to demonstrate.

2. PURPOSE AND TARGET AUDIENCE
MetroPacer is a metronome for runners. Runners improve efficiency and reduce
injury risk by holding a consistent step cadence (typically 160–190 steps per
minute), but it is hard to maintain without an external cue. MetroPacer plays a
steady click at the chosen cadence, and it mixes with whatever the user is
already listening to (music or podcasts) rather than replacing it. The target
audience is recreational and competitive runners of any age who want to train
or hold a target cadence. The app is free with no ads or purchases.

3. SETUP AND ACCESS INSTRUCTIONS
No login, account, or sample files are required. To test:
  a. Optionally open Music, Spotify, or Podcasts and press play.
  b. Open MetroPacer. Tap a cadence preset (160/170/180/190) or use the slider,
     +/- buttons, or Tap Tempo.
  c. Tap Start. The click plays at the chosen cadence, layered over any
     background audio without stopping it.
  d. Tap Options to change the click sound, accent pattern, subdivision, and
     beat volume, and to enable Flash (screen pulses on each beat) and Vibrate
     (haptic tap on each beat).
  e. Lock the phone or switch apps; the beat continues (UIBackgroundModes:
     audio).
All settings persist locally between launches.

4. EXTERNAL SERVICES, TOOLS, AND PLATFORMS
None. MetroPacer uses only Apple system frameworks (SwiftUI, AVFoundation for
audio playback with the .mixWithOthers option, CoreHaptics for vibration,
MediaPlayer for lock-screen Now Playing info and play/pause controls) and bundles no third-party SDKs or
packages. It performs no network requests, has no backend, and uses no
analytics, advertising, authentication, payment, or AI services. Settings are
stored in UserDefaults on the device only.

5. REGIONAL DIFFERENCES
None. The app functions identically in all regions. There is no region-specific
content, pricing, or feature gating.

6. REGULATED INDUSTRY / PROTECTED MATERIAL
Not applicable. MetroPacer is a general fitness utility, not a medical device,
and makes no health claims requiring authorization. All sounds, artwork, and
code are original and owned by the developer. The app does not include or
redistribute any third-party protected material; it only plays alongside the
user's own audio apps, which it does not access or control.
```

---

## After replying
- Status returns to *In Review* once Apple picks up the reply; typical turnaround
  is 1–3 days.
- Copy the block above into **App Review Information → Notes** (replaces the
  shorter note from `app-review-notes.md`) so future versions are not asked again.
