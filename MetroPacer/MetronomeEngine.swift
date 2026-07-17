import Foundation
import AVFoundation
import Combine
import MediaPlayer
import QuartzCore   // CACurrentMediaTime() for tap-tempo timing
#if canImport(UIKit)
import UIKit
#endif
#if canImport(CoreHaptics)
import CoreHaptics
#endif

/// The selectable click timbres. Each is synthesized procedurally (no audio
/// files), so the app stays tiny and every sound shares the same exact timing.
enum ClickSound: String, CaseIterable, Identifiable {
    case woodblock = "Woodblock"
    case rim       = "High Click"
    case beep      = "Beep"
    case tick      = "Tick"

    var id: String { rawValue }

    /// Produces one click sample at frame `frame` (0-based), given the sample
    /// rate. When `accent` is true the click is pitched up and slightly louder,
    /// marking the downbeat of a measure so a runner can hear "1" land.
    func sample(frame: Int, sampleRate: Double, accent: Bool = false) -> Float {
        let t = Double(frame)
        // Accent shifts pitch up a perfect-ish fifth and adds a little gain, which
        // reads as a distinct "high one" without needing a separate timbre.
        let pitch = accent ? 1.5 : 1.0
        let gain = accent ? 1.25 : 1.0
        let value: Double
        switch self {
        case .woodblock:
            // Dry knock: ~800 Hz with a fast decay and a touch of 2nd harmonic.
            let env = exp(-t / (sampleRate * 0.006))
            let f = 2.0 * Double.pi / sampleRate
            let body = sin(f * 800 * pitch * t) + 0.4 * sin(f * 1_600 * pitch * t)
            value = body * env * 0.5
        case .rim:
            // Bright, very short tick around 2 kHz — cuts through speech at speed.
            let env = exp(-t / (sampleRate * 0.0035))
            value = sin(2.0 * Double.pi * 2_000 * pitch * t / sampleRate) * env * 0.7
        case .beep:
            // Clean electronic tone, slightly longer, distinct from voice.
            let env = exp(-t / (sampleRate * 0.020))
            value = sin(2.0 * Double.pi * 1_320 * pitch * t / sampleRate) * env * 0.6
        case .tick:
            // The original 1 kHz sine tick.
            let env = exp(-t / (sampleRate * 0.008))
            value = sin(2.0 * Double.pi * 1_000 * pitch * t / sampleRate) * env * 0.9
        }
        return Float(min(max(value * gain, -1), 1))   // clamp so the louder accent can't clip
    }

    /// How long the click portion lasts, in seconds.
    var durationSeconds: Double {
        switch self {
        case .woodblock: return 0.04
        case .rim:       return 0.025
        case .beep:      return 0.06
        case .tick:      return 0.03
        }
    }
}

/// How many evenly-spaced clicks sound within each beat. The main beat is the
/// first click (and honors the accent rules); the rest are quieter in-between
/// clicks so the player can subdivide without losing the pulse.
enum Subdivision: Int, CaseIterable, Identifiable {
    case none       = 1   // just the beat
    case eighths    = 2   // "1-and"
    case triplets   = 3   // "1-trip-let"
    case sixteenths = 4   // "1-e-and-a"

    var id: Int { rawValue }

    /// Short label for the picker.
    var label: String {
        switch self {
        case .none:       return "None"
        case .eighths:    return "8ths"
        case .triplets:   return "Trip"
        case .sixteenths: return "16ths"
        }
    }
}

/// Sample-accurate metronome built on AVAudioEngine.
///
/// Timing is driven by the audio render clock — clicks are scheduled at exact
/// sample-frame offsets rather than via `Timer`, so there is no cumulative drift.
/// The audio session mixes with other apps (e.g. a podcast playing in Spotify
/// or Apple Podcasts) instead of interrupting them.
final class MetronomeEngine: ObservableObject {

    // MARK: Public, observable state

    @Published private(set) var isRunning = false

    /// Surfaces the last audio failure so the UI isn't silently dead.
    @Published private(set) var lastError: String?

    /// One pulse per audible click, published on the main thread at the instant
    /// the click sounds. `accent` marks the downbeat. The UI observes this to
    /// flash the screen in lock-step with the audio, so a runner who can't hear
    /// the click (wind, traffic, earbuds out) can still see the beat. `tick`
    /// increments every beat so SwiftUI registers each one even at the same
    /// accent value.
    struct BeatPulse: Equatable {
        var tick: Int = 0
        var accent: Bool = false
    }
    @Published private(set) var beat = BeatPulse()

    /// Number of taps registered in the current tap-tempo gesture. Drives a
    /// little "keep tapping…" affordance in the UI and resets to 0 when the
    /// gesture times out. `0` means no tap gesture is in progress.
    @Published private(set) var tapCount = 0

    /// Whether to flash the screen on each beat. Persisted.
    @Published var flashEnabled: Bool = true {
        didSet { defaults.set(flashEnabled, forKey: Keys.flashEnabled) }
    }

    /// Whether to fire a haptic tap on each beat (iOS only). Persisted.
    @Published var hapticsEnabled: Bool = false {
        didSet {
            defaults.set(hapticsEnabled, forKey: Keys.hapticsEnabled)
            if hapticsEnabled { prepareHaptics() }
        }
    }

    /// Beats per minute. Clamped to [minBPM, maxBPM]. Persisted across launches.
    @Published var bpm: Double = 120 {
        didSet {
            let clamped = min(max(bpm, Self.minBPM), Self.maxBPM)
            if clamped != bpm { bpm = clamped; return }     // re-enters didSet, then exits
            defaults.set(bpm, forKey: Keys.bpm)
            // Tempo changes take effect on the next scheduled beat automatically,
            // because the scheduler reads `bpm` each time it computes the next interval.
            // Keep the lock-screen tempo label in sync while running.
            if isRunning { updateNowPlaying() }
        }
    }

    /// Metronome click volume, 0...1. Applies live to the player node and is
    /// independent of the podcast and the system volume. Persisted across launches.
    @Published var volume: Float = 0.8 {
        didSet {
            let clamped = min(max(volume, 0), 1)
            if clamped != volume { volume = clamped; return }
            player.volume = volume
            defaults.set(volume, forKey: Keys.volume)
        }
    }

    /// The click timbre. Changing it takes effect within a few beats as the
    /// queued buffers drain and new ones are built with the new sound. Persisted.
    @Published var sound: ClickSound = .woodblock {
        didSet { defaults.set(sound.rawValue, forKey: Keys.sound) }
    }

    /// Beats per measure. `1` means every beat is identical (no accent); `2…8`
    /// accents the first beat of each measure so the downbeat is audible. Clamped
    /// to [1, maxBeatsPerMeasure]. Persisted across launches.
    @Published var beatsPerMeasure: Int = 1 {
        didSet {
            let clamped = min(max(beatsPerMeasure, 1), Self.maxBeatsPerMeasure)
            if clamped != beatsPerMeasure { beatsPerMeasure = clamped; return }
            defaults.set(beatsPerMeasure, forKey: Keys.beatsPerMeasure)
            // Realign so the next downbeat starts a fresh measure cleanly.
            schedulerQueue.async { [weak self] in self?.beatIndex = 0 }
        }
    }

    /// How many clicks sound per beat. `.none` is the plain beat; others add
    /// quieter in-between clicks. Takes effect within a beat or two as queued
    /// buffers drain. Persisted across launches.
    @Published var subdivision: Subdivision = .none {
        didSet { defaults.set(subdivision.rawValue, forKey: Keys.subdivision) }
    }

    // MARK: Persistence

    private let defaults = UserDefaults.standard
    private enum Keys {
        static let bpm = "metronome.bpm"
        static let volume = "metronome.volume"
        static let sound = "metronome.sound"
        static let beatsPerMeasure = "metronome.beatsPerMeasure"
        static let flashEnabled = "metronome.flashEnabled"
        static let hapticsEnabled = "metronome.hapticsEnabled"
        static let subdivision = "metronome.subdivision"
    }

    static let minBPM: Double = 20
    static let maxBPM: Double = 300
    static let maxBeatsPerMeasure = 8

    // MARK: Audio graph

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()

    // MARK: Haptics (iOS)

    #if canImport(CoreHaptics)
    /// Lazily-created haptic engine. Nil if the device has no haptics or
    /// CoreHaptics is unsupported (e.g. simulator, older hardware).
    private var hapticEngine: CHHapticEngine?
    #endif

    /// The audio format the player runs at, locked once the graph is wired.
    private var audioFormat: AVAudioFormat?

    /// Format the player runs at. Captured once at setup.
    private var sampleRate: Double = 44_100

    // MARK: Scheduling

    /// Serial queue that tops up the queued beats.
    private let schedulerQueue = DispatchQueue(label: "metronome.scheduler")

    /// We keep enough one-beat buffers queued to cover a fixed look-ahead
    /// *duration* rather than a fixed beat *count*. The look-ahead exists to
    /// absorb output latency (Bluetooth especially), which is a time, not a beat
    /// count — so at slow tempos a single beat already covers it, while fast
    /// tempos need a couple. Computing depth from the target time caps how much
    /// stale-tempo audio is committed ahead, so a tempo change is heard within
    /// roughly one beat instead of `queueDepth` beats. Buffers are never flushed,
    /// so there's no glitch on change — we just stop adding old-tempo beats.
    private let lookAheadSeconds: TimeInterval = 0.22

    /// Clamp the computed depth so playback is always gapless yet lag stays low.
    ///
    /// The floor MUST be 2: `AVAudioPlayerNode` plays queued buffers gaplessly
    /// only if the NEXT buffer is already scheduled before the current one ends.
    /// With a depth of 1 the queue runs dry between beats — the render thread
    /// under-runs, producing an audible hitch and uneven beat loudness/timing.
    /// Keeping one beat playing plus at least one always queued behind it
    /// guarantees a buffer is always ready. The ceiling caps stale-tempo audio.
    private let minQueueDepth = 2
    private let maxQueueDepth = 4

    /// Beats of look-ahead to keep queued, derived from `lookAheadSeconds` at the
    /// current tempo, floored at 2 for gapless playback. Worst-case tempo-change
    /// lag is `depth` beats (now 2 across 100–200 BPM ⇒ 0.6–1.2 s), still half
    /// the old fixed-4 behavior (up to ~2.4 s) but without any under-run.
    private func currentQueueDepth(bpm: Double) -> Int {
        let secondsPerBeat = 60.0 / max(bpm, 1)
        let needed = Int(ceil(lookAheadSeconds / secondsPerBeat))
        return min(max(needed, minQueueDepth), maxQueueDepth)
    }

    /// Beats currently scheduled but not yet finished playing.
    private var beatsInFlight = 0

    /// The BPM each queued buffer was built at, so a tempo change rebuilds.
    private var queuedBPM: Double = 0

    /// Position within the current measure of the NEXT buffer to be scheduled.
    /// `0` is the accented downbeat. Mutated only on `schedulerQueue`.
    private var beatIndex = 0

    /// Sample-frame (in the player's own timeline) at which the NEXT buffer will
    /// start. We schedule buffers at explicit times rather than chaining `at: nil`
    /// so we can convert each start frame to a host time and fire the screen
    /// flash / haptic at the exact instant the click sounds. Mutated only on
    /// `schedulerQueue`.
    private var nextBeatSampleTime: AVAudioFramePosition = 0

    /// Monotonic beat counter used to drive `BeatPulse.tick`. Mutated only on
    /// `schedulerQueue`.
    private var pulseCounter = 0

    /// How often the scheduler wakes to top up the queue.
    private let schedulerTick: TimeInterval = 0.02

    // MARK: Tap tempo

    /// Timestamps (monotonic, seconds) of recent taps in the current gesture.
    /// Only the intervals between them matter, so we keep a short rolling window.
    private var tapTimes: [TimeInterval] = []

    /// Gap after which a tap is treated as the START of a new gesture rather than
    /// a continuation — i.e. the slowest tempo we'll track is 60/maxTapGap BPM.
    /// 2s ⇒ taps slower than 30 BPM reset the gesture, which is well below any
    /// musical/running tempo, so legitimate taps are never dropped.
    private let maxTapGap: TimeInterval = 2.0

    /// How many of the most recent intervals to average. A small window tracks
    /// tempo changes quickly while still smoothing out human jitter.
    private let tapWindow = 5

    private var schedulerTimer: DispatchSourceTimer?

    // MARK: Setup

    init() {
        engine.attach(player)
        observeInterruptions()
        setupRemoteCommands()
        loadSavedSettings()
    }

    /// Restores the last-used BPM, volume, and sound. Assigning the stored
    /// values directly (not through clamping branches) keeps it simple; the
    /// didSet observers persist them right back, which is harmless.
    private func loadSavedSettings() {
        if defaults.object(forKey: Keys.bpm) != nil {
            bpm = defaults.double(forKey: Keys.bpm)
        }
        if defaults.object(forKey: Keys.volume) != nil {
            volume = defaults.float(forKey: Keys.volume)
        }
        if let raw = defaults.string(forKey: Keys.sound),
           let saved = ClickSound(rawValue: raw) {
            sound = saved
        }
        if defaults.object(forKey: Keys.beatsPerMeasure) != nil {
            beatsPerMeasure = defaults.integer(forKey: Keys.beatsPerMeasure)
        }
        if defaults.object(forKey: Keys.flashEnabled) != nil {
            flashEnabled = defaults.bool(forKey: Keys.flashEnabled)
        }
        if defaults.object(forKey: Keys.hapticsEnabled) != nil {
            hapticsEnabled = defaults.bool(forKey: Keys.hapticsEnabled)
        }
        if defaults.object(forKey: Keys.subdivision) != nil,
           let saved = Subdivision(rawValue: defaults.integer(forKey: Keys.subdivision)) {
            subdivision = saved
        }
    }

    /// Whether the graph has been wired against a live, warm output format.
    private var graphReady = false

    /// Activates the session, starts the engine, then connects the player using
    /// the engine's REAL output format and builds a matching click buffer.
    ///
    /// This is done lazily (not in init) because on a cold launch the output
    /// format reads as 0 ch / 0 Hz until the session is active and the engine
    /// has started — connecting against that degenerate format makes the engine
    /// render silence without throwing. That was the "no error, no beat" case.
    private func prepareGraph() throws {
        // 1. Always re-assert the session — a cold launch or another app can
        // leave it inactive, which silently kills output. Activating the session
        // is what makes the output format valid; the engine does NOT need to be
        // running yet to read it.
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try session.setActive(true)

        // 2. Wire the graph ONCE, while the engine is stopped. The player must be
        // connected to the mixer BEFORE the engine starts — starting an engine
        // whose only source node is unconnected gives no signal path, which is
        // the "engine runs, no sound" case. Connect first, start second.
        if !graphReady {
            let outFormat = engine.outputNode.outputFormat(forBus: 0)
            let rate = outFormat.sampleRate > 0 ? outFormat.sampleRate : session.sampleRate
            sampleRate = rate > 0 ? rate : 44_100

            guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 2) else {
                throw NSError(domain: "Metronome", code: 1,
                              userInfo: [NSLocalizedDescriptionKey: "Bad format @\(sampleRate)Hz"])
            }

            engine.connect(player, to: engine.mainMixerNode, format: format)
            player.volume = volume
            audioFormat = format
            graphReady = true
        }

        // 3. Now start the engine — the signal path exists.
        if !engine.isRunning {
            engine.prepare()
            try engine.start()
        }
    }

    /// Volume of the in-between subdivision clicks relative to the main beat.
    private static let subdivisionGain: Float = 0.5

    /// Builds ONE buffer that is exactly one beat long. The main beat click lands
    /// at the start (honoring `accent`); `subdivision` adds quieter, evenly-spaced
    /// clicks within the beat (e.g. the "and" of eighths). Playing these buffers
    /// back-to-back gives perfect tempo with no render-clock arithmetic — each
    /// buffer's length IS the beat, and the subdivision clicks ride inside it.
    private func makeBeatBuffer(bpm: Double, sound: ClickSound, accent: Bool,
                                subdivision: Subdivision) -> AVAudioPCMBuffer? {
        guard let format = audioFormat else { return nil }

        let secondsPerBeat = 60.0 / bpm
        let beatFrames = AVAudioFrameCount(secondsPerBeat * sampleRate)
        let clickFrames = Int(min(Double(beatFrames), sampleRate * sound.durationSeconds))

        guard beatFrames > 0,
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: beatFrames) else {
            return nil
        }
        buffer.frameLength = beatFrames

        let n = subdivision.rawValue
        for channel in 0..<Int(format.channelCount) {
            guard let data = buffer.floatChannelData?[channel] else { continue }
            // Buffers start zeroed, so we only need to write the clicks. Place one
            // click at each of the `n` evenly-spaced subdivisions of the beat.
            for i in 0..<n {
                // Subdivision i begins at this fraction of the beat. i == 0 is the
                // main beat; later ones are the quieter in-between clicks.
                let startFrame = Int((Double(i) / Double(n)) * Double(beatFrames))
                // Don't let a click run past the end of the beat buffer.
                let maxLen = Int(beatFrames) - startFrame
                let thisClickFrames = min(clickFrames, maxLen)
                guard thisClickFrames > 0 else { continue }

                let isMain = (i == 0)
                let gain: Float = isMain ? 1.0 : Self.subdivisionGain
                for f in 0..<thisClickFrames {
                    // Off-beat subdivision clicks are never accented.
                    data[startFrame + f] = sound.sample(frame: f, sampleRate: sampleRate,
                                                        accent: isMain && accent) * gain
                }
            }
        }
        return buffer
    }

    // MARK: Transport

    func start() {
        guard !isRunning else { return }

        do {
            // Activate session + engine, then wire the graph against the live
            // output format. Safe to call every start; it no-ops the engine if
            // already running but always re-asserts the session.
            try prepareGraph()
            lastError = nil
        } catch {
            lastError = "Start failed: \(error.localizedDescription)"
            print("Engine start error: \(error)")
            return
        }

        guard audioFormat != nil else {
            lastError = "Audio format not ready"
            return
        }

        player.play()
        // All queue mutation happens on schedulerQueue to avoid races between
        // the priming call and the repeating timer.
        schedulerQueue.async { [weak self] in
            self?.beatsInFlight = 0
            self?.beatIndex = 0           // start every run on the downbeat
            self?.pulseCounter = 0
            // The player's sample timeline resets to 0 each time it's played
            // after a stop, so schedule the first beat a hair into the future to
            // stay safely ahead of the render head.
            self?.nextBeatSampleTime = AVAudioFramePosition((self?.sampleRate ?? 44_100) * 0.05)
            self?.topUpQueue()
        }
        startScheduler()
        isRunning = true
        updateNowPlaying()   // populate lock screen / Control Center
    }

    func stop() {
        guard isRunning else { return }
        schedulerTimer?.cancel()
        schedulerTimer = nil
        // stop() flushes any queued beats so a restart begins cleanly on the beat.
        player.stop()
        beatsInFlight = 0
        isRunning = false
        clearNowPlaying()
    }

    func toggle() {
        isRunning ? stop() : start()
    }

    // MARK: Tap tempo

    /// Registers one tap. Two or more taps within `maxTapGap` of each other set
    /// `bpm` from the average tapped interval (clamped to the valid range), so a
    /// runner can tap along to their natural cadence instead of guessing on the
    /// slider. A long pause starts a fresh measurement.
    ///
    /// Pure timing logic kept in the engine (next to the other BPM logic) so it's
    /// unit-testable without any UI.
    func tap(at now: TimeInterval = CACurrentMediaTime()) {
        // If it's been too long since the last tap, this begins a new gesture.
        if let last = tapTimes.last, now - last > maxTapGap {
            tapTimes.removeAll()
        }

        tapTimes.append(now)
        // Keep only enough taps to cover the averaging window (window intervals
        // need window+1 timestamps).
        if tapTimes.count > tapWindow + 1 {
            tapTimes.removeFirst(tapTimes.count - (tapWindow + 1))
        }
        tapCount = tapTimes.count

        // Need at least two taps to have an interval.
        guard tapTimes.count >= 2 else { return }

        let intervals = zip(tapTimes.dropFirst(), tapTimes).map { $0 - $1 }
        let averageInterval = intervals.reduce(0, +) / Double(intervals.count)
        guard averageInterval > 0 else { return }

        // bpm's didSet clamps to [minBPM, maxBPM] and persists.
        bpm = 60.0 / averageInterval
    }

    /// Forgets the in-progress tap gesture (e.g. after the affordance times out
    /// in the UI). Does not change `bpm`.
    func resetTap() {
        tapTimes.removeAll()
        tapCount = 0
    }

    // MARK: Scheduler

    private func startScheduler() {
        let timer = DispatchSource.makeTimerSource(queue: schedulerQueue)
        timer.schedule(deadline: .now(), repeating: schedulerTick)
        timer.setEventHandler { [weak self] in
            self?.topUpQueue()
        }
        schedulerTimer = timer
        timer.resume()
    }

    /// Keeps `queueDepth` one-beat buffers queued on the player at all times.
    /// Each buffer is exactly one beat long, so back-to-back playback yields
    /// exact tempo with zero render-clock math.
    private func topUpQueue() {
        // If tempo changed, the in-flight buffers are the old length; we just
        // let them drain and build new ones at the new BPM from here on.
        let targetBPM = bpm
        let targetSound = sound
        let measure = beatsPerMeasure
        let targetSubdivision = subdivision
        let depth = currentQueueDepth(bpm: targetBPM)
        while beatsInFlight < depth {
            // Beat 0 of each measure is the accented downbeat. With measure == 1
            // (beatIndex always 0 % 1 == 0) we must NOT accent every beat, so a
            // measure of 1 disables accenting entirely.
            let accent = measure > 1 && beatIndex == 0
            guard let beat = makeBeatBuffer(bpm: targetBPM, sound: targetSound, accent: accent,
                                            subdivision: targetSubdivision) else { break }
            queuedBPM = targetBPM
            beatIndex = (beatIndex + 1) % max(measure, 1)
            beatsInFlight += 1

            // Schedule at an EXPLICIT sample time so we know precisely when this
            // click sounds and can fire the visual/haptic pulse in lock-step.
            // The frames advance by exactly the buffer length, so back-to-back
            // playback is still gapless and drift-free.
            let startTime = AVAudioTime(sampleTime: nextBeatSampleTime, atRate: sampleRate)
            nextBeatSampleTime += AVAudioFramePosition(beat.frameLength)
            pulseCounter += 1
            let tick = pulseCounter

            schedulePulse(forSampleTime: startTime, tick: tick, accent: accent)

            player.scheduleBuffer(beat, at: startTime, options: []) { [weak self] in
                self?.schedulerQueue.async {
                    self?.beatsInFlight -= 1
                }
            }
        }
    }

    /// Converts a beat's player-relative sample time into a wall-clock host time
    /// and fires the screen flash + haptic at that instant, so the visual/haptic
    /// pulse lands with the audible click rather than drifting on a separate
    /// wall clock. Falls back to firing immediately if the render clock isn't
    /// available yet (first beats of a cold start).
    private func schedulePulse(forSampleTime startTime: AVAudioTime, tick: Int, accent: Bool) {
        guard flashEnabled || hapticsEnabled else { return }

        // Map the player's sample timeline to the output's host timeline.
        let fire: () -> Void = { [weak self] in
            guard let self else { return }
            if self.flashEnabled {
                DispatchQueue.main.async { self.beat = BeatPulse(tick: tick, accent: accent) }
            }
            if self.hapticsEnabled { self.playHaptic(accent: accent) }
        }

        if let nodeTime = player.lastRenderTime,
           nodeTime.isHostTimeValid,
           let playerTime = player.playerTime(forNodeTime: nodeTime),
           playerTime.isSampleTimeValid {
            // Anchor the pulse to the AUDIO clock, not `.now()`. `nodeTime` gives
            // both the sample position AND the mach host time of that same render
            // instant, so we can compute the absolute host time of THIS beat and
            // schedule against it. Using `.now() + relativeDelay` instead let each
            // beat drift, because the dispatch clock and the audio render clock
            // are sampled at different moments and `asyncAfter` isn't drift-
            // corrected — small per-beat errors accumulated into audible lag.
            let framesAhead = startTime.sampleTime - playerTime.sampleTime
            let secondsAhead = Double(framesAhead) / sampleRate
            // Subtract a small constant for screen/haptic actuation latency so the
            // pulse coincides with the click rather than trailing it.
            let lead = secondsAhead - 0.012

            // The beat's absolute host time = the render instant's host time plus
            // the frames-ahead converted to host nanoseconds. Schedule against
            // that absolute deadline on the same mach clock DispatchTime uses, so
            // there's no per-beat drift from cross-clock sampling.
            let leadNanos = lead * 1_000_000_000
            let deadline = Self.dispatchTime(forHostTime: nodeTime.hostTime, offsetNanos: leadNanos)
            schedulerQueue.asyncAfter(deadline: deadline, execute: fire)
        } else {
            fire()
        }
    }

    /// mach timebase (host ticks ↔ nanoseconds). Constant for the process, so
    /// computed once.
    private static let timebase: mach_timebase_info_data_t = {
        var info = mach_timebase_info_data_t()
        mach_timebase_info(&info)
        return info
    }()

    /// Builds an absolute `DispatchTime` for a given mach host time plus an
    /// offset in nanoseconds. `AVAudioTime.hostTime` and `DispatchTime` share the
    /// mach uptime clock, so this deadline tracks the audio render clock exactly.
    private static func dispatchTime(forHostTime hostTime: UInt64, offsetNanos: Double) -> DispatchTime {
        // Convert the absolute host time to nanoseconds, then apply the offset.
        let hostNanos = Double(hostTime) * Double(timebase.numer) / Double(timebase.denom)
        let targetNanos = hostNanos + offsetNanos
        if targetNanos <= 0 { return .now() }
        return DispatchTime(uptimeNanoseconds: UInt64(targetNanos))
    }

    // MARK: Now Playing / Remote Controls

    /// Wires the lock screen, Control Center, AirPods, and Apple Watch transport
    /// buttons to the metronome. Done once in init. Without this the system shows
    /// no controls for our audio when the screen is locked, so a runner would
    /// have to unlock and reopen the app just to stop the beat.
    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak self] _ in
            guard let self, !self.isRunning else { return .commandFailed }
            DispatchQueue.main.async { self.start() }
            return .success
        }

        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak self] _ in
            guard let self, self.isRunning else { return .commandFailed }
            DispatchQueue.main.async { self.stop() }
            return .success
        }

        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            DispatchQueue.main.async { self.toggle() }
            return .success
        }

        // We have no timeline to scrub or tracks to skip — disable the rest so
        // the system doesn't offer controls that would do nothing.
        center.stopCommand.isEnabled = true
        center.stopCommand.addTarget { [weak self] _ in
            guard let self, self.isRunning else { return .commandFailed }
            DispatchQueue.main.async { self.stop() }
            return .success
        }
        center.nextTrackCommand.isEnabled = false
        center.previousTrackCommand.isEnabled = false
        center.changePlaybackPositionCommand.isEnabled = false
    }

    /// Publishes the current tempo to the lock screen / Control Center as the
    /// "now playing" item, so a locked phone shows "Metronome — N SPM" with a
    /// working play/pause button.
    private func updateNowPlaying() {
        let info: [String: Any] = [
            MPMediaItemPropertyTitle: "Metronome",
            MPMediaItemPropertyArtist: "\(Int(bpm.rounded())) SPM",
            // No real timeline — report a steady "live" rate so the control shows
            // the pause (rather than play) state while running.
            MPNowPlayingInfoPropertyPlaybackRate: isRunning ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyIsLiveStream: true,
        ]
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    /// Removes our now-playing item when the beat stops so the lock screen
    /// doesn't keep showing a paused metronome (the user's podcast, if any,
    /// remains its own separate now-playing source).
    private func clearNowPlaying() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    // MARK: Haptics

    /// Spins up the haptic engine on first enable. Safe to call repeatedly.
    private func prepareHaptics() {
        #if canImport(CoreHaptics)
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        guard hapticEngine == nil else { return }
        do {
            let engine = try CHHapticEngine()
            // If the system stops the engine (e.g. app backgrounded), restart it
            // so the next beat still taps.
            engine.stoppedHandler = { _ in }
            engine.resetHandler = { [weak self] in try? self?.hapticEngine?.start() }
            try engine.start()
            hapticEngine = engine
        } catch {
            // Haptics are a non-critical enhancement; failing is silent.
            hapticEngine = nil
        }
        #endif
    }

    /// Fires one transient tap. The accented downbeat hits harder/sharper.
    private func playHaptic(accent: Bool) {
        #if canImport(CoreHaptics)
        guard let hapticEngine else { return }
        let intensity = CHHapticEventParameter(parameterID: .hapticIntensity,
                                               value: accent ? 1.0 : 0.6)
        let sharpness = CHHapticEventParameter(parameterID: .hapticSharpness,
                                               value: accent ? 0.9 : 0.5)
        let event = CHHapticEvent(eventType: .hapticTransient,
                                  parameters: [intensity, sharpness],
                                  relativeTime: 0)
        do {
            let pattern = try CHHapticPattern(events: [event], parameters: [])
            let player = try hapticEngine.makePlayer(with: pattern)
            try player.start(atTime: CHHapticTimeImmediate)
        } catch {
            // Ignore — a dropped haptic isn't worth surfacing.
        }
        #endif
    }

    // MARK: Interruptions

    /// Whether the beat was running when an interruption began, so we can resume
    /// it automatically when the interruption ends. `stop()` clears `isRunning`,
    /// so we can't rely on that flag inside the `.ended` handler — we track it
    /// separately here.
    private var wasRunningBeforeInterruption = false

    private func observeInterruptions() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: nil
        )
    }

    @objc private func handleInterruption(_ note: Notification) {
        guard
            let info = note.userInfo,
            let raw = info[AVAudioSessionInterruptionTypeKey] as? UInt,
            let type = AVAudioSession.InterruptionType(rawValue: raw)
        else { return }

        switch type {
        case .began:
            // System paused us (phone call, Siri, an alarm, etc.). Remember we
            // were running so we can pick the cadence back up, then reflect the
            // pause in state.
            wasRunningBeforeInterruption = isRunning
            if isRunning { stop() }

        case .ended:
            // Only resume if (a) we were running before, and (b) the system says
            // it's appropriate to resume. iOS sets `.shouldResume` for transient
            // interruptions (a call that ended) but withholds it when another app
            // took over playback for good — honoring it avoids fighting the user.
            // For a running app, auto-resuming a brief interruption is the right
            // default: a Siri ding shouldn't silently end the runner's cadence.
            defer { wasRunningBeforeInterruption = false }
            guard wasRunningBeforeInterruption else { return }

            let shouldResume: Bool
            if let optionsRaw = info[AVAudioSessionInterruptionOptionKey] as? UInt {
                shouldResume = AVAudioSession.InterruptionOptions(rawValue: optionsRaw)
                    .contains(.shouldResume)
            } else {
                shouldResume = false
            }
            if shouldResume {
                // Hop to the main actor; start() touches @Published state and the
                // audio session, and the notification can arrive off-main.
                DispatchQueue.main.async { [weak self] in self?.start() }
            }

        @unknown default:
            break
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}
