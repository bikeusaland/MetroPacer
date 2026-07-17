import SwiftUI

struct ContentView: View {
    @StateObject private var metronome = MetronomeEngine()

    /// Pending work that clears the tap-tempo gesture after a pause. Held so a
    /// fresh tap can cancel and reschedule it.
    @State private var tapResetWork: DispatchWorkItem?

    /// Whether the secondary "Options" panel (fine-tune, sound, accent, volume,
    /// feedback) is expanded. Collapsed by default to keep the main screen to the
    /// core "set cadence and go" loop.
    @State private var showOptions = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.08, green: 0.09, blue: 0.16),
                         Color(red: 0.02, green: 0.02, blue: 0.06)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 28) {
                    header

                    bpmDisplay

                    tempoSlider

                    tapTempoButton

                    cadencePresets

                    optionsPanel

                    playButton

                    hint
                }
                .padding(.horizontal, 28)
                .padding(.top, 40)
                .padding(.bottom, 24)
            }

            // Screen flash, drawn on top of everything. Pulses on each beat when
            // enabled — a downbeat flashes orange, off-beats white. Gives a runner
            // a beat they can SEE when the click is drowned out.
            if metronome.flashEnabled {
                BeatFlash(beat: metronome.beat, isRunning: metronome.isRunning)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
        }
    }

    // MARK: Subviews

    private var header: some View {
        VStack(spacing: 6) {
            Text("MetroPacer")
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)
            Text("Plays on top of your audio")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.55))
        }
    }

    private var bpmDisplay: some View {
        VStack(spacing: 2) {
            HStack(spacing: 0) {
                RunnerView(bpm: metronome.bpm, isRunning: metronome.isRunning,
                           mirrored: false, phase: 0)
                Text("\(Int(metronome.bpm.rounded()))")
                    .font(.system(size: 84, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .contentTransition(.numericText())
                    .animation(.snappy, value: Int(metronome.bpm.rounded()))
                    .layoutPriority(1)
                RunnerView(bpm: metronome.bpm, isRunning: metronome.isRunning,
                           mirrored: true, phase: 0.5)
            }
            Text("SPM · BPM")
                .font(.headline)
                .foregroundStyle(.white.opacity(0.5))
        }
    }

    private var tempoSlider: some View {
        VStack(spacing: 8) {
            Slider(
                value: $metronome.bpm,
                in: MetronomeEngine.minBPM...MetronomeEngine.maxBPM,
                step: 1
            )
            .tint(.orange)

            HStack {
                Text("\(Int(MetronomeEngine.minBPM))")
                Spacer()
                Text("\(Int(MetronomeEngine.maxBPM))")
            }
            .font(.caption)
            .foregroundStyle(.white.opacity(0.4))
        }
    }

    /// Common running-cadence targets (steps per minute).
    private let cadenceTargets: [Int] = [160, 170, 180, 190]

    private var cadencePresets: some View {
        VStack(spacing: 8) {
            HStack(spacing: 10) {
                ForEach(cadenceTargets, id: \.self) { spm in
                    Button {
                        metronome.bpm = Double(spm)
                    } label: {
                        Text("\(spm)")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(
                                Int(metronome.bpm.rounded()) == spm ? Color.orange : .white.opacity(0.08),
                                in: RoundedRectangle(cornerRadius: 12)
                            )
                            .foregroundStyle(.white)
                    }
                }
            }
            Text("Cadence presets (SPM)")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.4))
        }
    }

    // MARK: Options (collapsible)

    /// A single disclosure that folds away the secondary controls, keeping the
    /// default screen to the core set-cadence-and-go loop. Tapping the header
    /// expands the fine-tune, sound, accent, volume, and feedback controls.
    private var optionsPanel: some View {
        VStack(spacing: 20) {
            Button {
                withAnimation(.snappy) { showOptions.toggle() }
            } label: {
                HStack {
                    Image(systemName: "slider.horizontal.3")
                    Text("Options")
                        .font(.headline.weight(.semibold))
                    Spacer()
                    Image(systemName: "chevron.down")
                        .rotationEffect(.degrees(showOptions ? 180 : 0))
                }
                .frame(height: 44)
                .padding(.horizontal, 16)
                .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(.white.opacity(0.9))
            }

            if showOptions {
                VStack(spacing: 24) {
                    fineAdjustButtons
                    accentPicker
                    subdivisionPicker
                    soundPicker
                    volumeSlider
                    feedbackToggles
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private var soundPicker: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ForEach(ClickSound.allCases) { option in
                    Button {
                        metronome.sound = option
                    } label: {
                        Text(option.rawValue)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .background(
                                metronome.sound == option ? Color.orange : .white.opacity(0.08),
                                in: RoundedRectangle(cornerRadius: 10)
                            )
                            .foregroundStyle(.white)
                    }
                }
            }
            Text("Beat sound")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.4))
        }
    }

    /// Time-signature / accent picker. "Off" (1) means every beat is identical;
    /// 2–8 accents the first beat of each measure so the downbeat is audible.
    private let accentOptions: [Int] = [1, 2, 3, 4]

    private var accentPicker: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ForEach(accentOptions, id: \.self) { count in
                    Button {
                        metronome.beatsPerMeasure = count
                    } label: {
                        Text(count == 1 ? "Off" : "\(count)/4")
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .background(
                                metronome.beatsPerMeasure == count ? Color.orange : .white.opacity(0.08),
                                in: RoundedRectangle(cornerRadius: 10)
                            )
                            .foregroundStyle(.white)
                    }
                }
            }
            Text("Accent (downbeat)")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.4))
        }
    }

    /// Subdivision picker: how many clicks sound per beat (eighths, triplets,
    /// sixteenths). The in-between clicks are quieter than the main beat.
    private var subdivisionPicker: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ForEach(Subdivision.allCases) { option in
                    Button {
                        metronome.subdivision = option
                    } label: {
                        Text(option.label)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .background(
                                metronome.subdivision == option ? Color.orange : .white.opacity(0.08),
                                in: RoundedRectangle(cornerRadius: 10)
                            )
                            .foregroundStyle(.white)
                    }
                }
            }
            Text("Subdivision")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.4))
        }
    }

    /// Toggles for non-audio beat feedback so the metronome stays usable when
    /// the click is inaudible (wind, traffic, earbuds out).
    private var feedbackToggles: some View {
        HStack(spacing: 10) {
            toggleChip(label: "Flash", systemImage: "bolt.fill",
                       isOn: metronome.flashEnabled) {
                metronome.flashEnabled.toggle()
            }
            toggleChip(label: "Vibrate", systemImage: "iphone.radiowaves.left.and.right",
                       isOn: metronome.hapticsEnabled) {
                metronome.hapticsEnabled.toggle()
            }
        }
    }

    private func toggleChip(label: String, systemImage: String,
                            isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: systemImage)
                Text(label)
                    .font(.subheadline.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(
                isOn ? Color.orange : .white.opacity(0.08),
                in: RoundedRectangle(cornerRadius: 12)
            )
            .foregroundStyle(.white)
        }
    }

    private var volumeSlider: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                Image(systemName: "speaker.fill")
                    .foregroundStyle(.white.opacity(0.5))
                Slider(value: $metronome.volume, in: 0...1)
                    .tint(.orange)
                Image(systemName: "speaker.wave.3.fill")
                    .foregroundStyle(.white.opacity(0.5))
            }
            Text("Beat volume  \(Int((metronome.volume * 100).rounded()))%")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.4))
        }
    }

    private var fineAdjustButtons: some View {
        HStack(spacing: 16) {
            adjustButton(label: "-5") { adjust(by: -5) }
            adjustButton(label: "-1") { adjust(by: -1) }
            adjustButton(label: "+1") { adjust(by: 1) }
            adjustButton(label: "+5") { adjust(by: 5) }
        }
    }

    private func adjustButton(label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.headline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(.white)
        }
    }

    /// Tap-tempo: tap along to a cadence and the BPM is set from your taps.
    /// Shows live feedback while a gesture is in progress, then quietly resets.
    private var tapTempoButton: some View {
        Button {
            metronome.tap()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "hand.tap.fill")
                Text(metronome.tapCount >= 2 ? "Tap · \(metronome.tapCount)"
                     : metronome.tapCount == 1 ? "Keep tapping…"
                     : "Tap Tempo")
                    .font(.headline.weight(.semibold))
                    .contentTransition(.numericText())
            }
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(
                metronome.tapCount > 0 ? Color.orange.opacity(0.85) : .white.opacity(0.08),
                in: RoundedRectangle(cornerRadius: 14)
            )
            .foregroundStyle(.white)
        }
        .animation(.snappy, value: metronome.tapCount)
        // Clear the in-progress gesture shortly after the user stops tapping, so
        // the next tap starts fresh and the label returns to "Tap Tempo".
        .onChange(of: metronome.tapCount) { _, count in
            guard count > 0 else { return }
            tapResetWork?.cancel()
            let work = DispatchWorkItem { metronome.resetTap() }
            tapResetWork = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: work)
        }
    }

    private var playButton: some View {
        Button {
            metronome.toggle()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: metronome.isRunning ? "stop.fill" : "play.fill")
                Text(metronome.isRunning ? "Stop" : "Start")
            }
            .font(.title3.weight(.bold))
            .frame(maxWidth: .infinity)
            .frame(height: 64)
            .background(
                metronome.isRunning ? Color.red : Color.orange,
                in: RoundedRectangle(cornerRadius: 18)
            )
            .foregroundStyle(.white)
        }
    }

    private var hint: some View {
        VStack(spacing: 10) {
            if let error = metronome.lastError {
                Text(error)
                    .font(.footnote.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.red.opacity(0.9))
            }
            Text("Open Apple Podcasts or Spotify and press play there first, then start the beat here.")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.45))
        }
        .padding(.bottom, 12)
    }

    // MARK: Actions

    private func adjust(by delta: Double) {
        metronome.bpm = (metronome.bpm + delta)
            .clamped(to: MetronomeEngine.minBPM...MetronomeEngine.maxBPM)
    }
}

/// A full-screen edge-vignette flash that snaps to full brightness on each beat
/// and fades out, driven by the engine's `BeatPulse`. The downbeat flashes
/// orange and a touch brighter; off-beats flash white. A vignette (rather than a
/// solid fill) keeps the controls readable while still being unmissable in
/// peripheral vision.
private struct BeatFlash: View {
    let beat: MetronomeEngine.BeatPulse
    let isRunning: Bool

    @State private var level: Double = 0

    private var color: Color {
        beat.accent ? Color.orange : Color.white
    }

    var body: some View {
        RadialGradient(
            colors: [color.opacity(0), color.opacity(0.55)],
            center: .center,
            startRadius: 120,
            endRadius: 520
        )
        .opacity(isRunning ? level : 0)
        // Snap up instantly on a new beat, then ease the fade out.
        .onChange(of: beat.tick) { _, _ in
            level = beat.accent ? 1.0 : 0.7
            withAnimation(.easeOut(duration: 0.18)) { level = 0 }
        }
    }
}

private extension Double {
    func clamped(to range: ClosedRange<Double>) -> Double {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

#Preview {
    ContentView()
}
