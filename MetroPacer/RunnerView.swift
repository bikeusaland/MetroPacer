import SwiftUI

/// A filled-silhouette runner that flips through 12 pre-rendered sprite frames
/// (Runner00…Runner11 in the asset catalog) paced to the metronome — one full
/// stride per beat — with glowing blue motion-streak trails sweeping behind,
/// echoing the speed arcs in the app icon. The frames are smooth blue
/// silhouettes with genuinely alternating legs and arms.
struct RunnerView: View {
    let bpm: Double
    let isRunning: Bool
    var mirrored: Bool = false
    /// Phase offset 0...1 so the two sides stride on opposite feet.
    var phase: Double = 0

    private let frameCount = 12

    private var beatSeconds: Double { 60.0 / max(bpm, 1) }

    var body: some View {
        TimelineView(.animation(paused: !isRunning)) { timeline in
            ZStack {
                // Streaks sit BEHIND the figure and trail backward (-x). Both the
                // streaks and the sprite live in the same mirrored container, so
                // the right-hand runner's trail flips correctly with it.
                MotionStreaks(flow: flow(at: timeline.date),
                              intensity: isRunning ? 1 : 0)
                    .frame(width: 86, height: 60)

                Image(frameName(at: timeline.date))
                    .resizable()
                    .scaledToFit()
                    .frame(width: 54, height: 64)
                    .offset(y: bob(at: timeline.date))
            }
            .scaleEffect(x: mirrored ? -1 : 1, y: 1)   // face the number
            .opacity(isRunning ? 1 : 0.4)
            .animation(.easeOut(duration: 0.3), value: isRunning)
        }
    }

    /// Picks the sprite frame for the current stride phase. When stopped, rests
    /// on a neutral mid-stride frame.
    private func frameName(at date: Date) -> String {
        let idx = isRunning ? Int(stridePhase(at: date) * Double(frameCount)) % frameCount : 3
        return String(format: "Runner%02d", idx)
    }

    private func stridePhase(at date: Date) -> Double {
        guard isRunning else { return 0 }
        let raw = date.timeIntervalSinceReferenceDate / beatSeconds + phase
        return raw - floor(raw)
    }

    private func bob(at date: Date) -> CGFloat {
        guard isRunning else { return 2 }
        let p = stridePhase(at: date)
        return -sin(2 * p * 2 * .pi) * 4 - 1   // dip at each of two foot-strikes
    }

    /// Continuous 0..<1 flow phase for the streaks. Sweeps faster than one beat
    /// so the lines read as motion, and scales with cadence.
    private func flow(at date: Date) -> Double {
        guard isRunning else { return 0 }
        let raw = date.timeIntervalSinceReferenceDate / (beatSeconds * 0.5)
        return raw - floor(raw)
    }
}

/// Glowing speed lines that sweep backward (-x) behind the runner. Each streak
/// is a soft tapered capsule; they cycle position with `flow` and fade at the
/// trailing edge, giving a sense of speed. `intensity` (0…1) gates visibility.
private struct MotionStreaks: View {
    var flow: Double          // 0..<1, advances the sweep
    var intensity: Double     // 0 = hidden, 1 = full

    // Per-streak vertical position (fraction of height) and length factor.
    private let lanes: [(y: CGFloat, len: CGFloat, w: CGFloat)] = [
        (0.30, 0.85, 3.0),
        (0.45, 1.00, 4.0),
        (0.58, 0.70, 2.5),
        (0.70, 0.92, 3.5),
        (0.84, 0.60, 2.0),
    ]

    private let glow = LinearGradient(
        colors: [Color(red: 0.55, green: 0.85, blue: 1.0).opacity(0.0),
                 Color(red: 0.40, green: 0.78, blue: 1.0).opacity(0.9),
                 Color(red: 0.20, green: 0.60, blue: 1.0).opacity(0.0)],
        startPoint: .trailing, endPoint: .leading   // bright at front, fades back
    )

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack {
                ForEach(lanes.indices, id: \.self) { i in
                    let lane = lanes[i]
                    // Stagger each lane's phase so they don't pulse in unison.
                    let p = (flow + Double(i) * 0.18).truncatingRemainder(dividingBy: 1)
                    let streakLen = w * 0.55 * lane.len
                    // Sweep from just behind the figure (right) out to the left,
                    // fading as it travels.
                    let headX = w * 0.62 - CGFloat(p) * w * 0.85
                    let travel = CGFloat(p)                 // 0 (fresh) → 1 (gone)
                    Capsule()
                        .fill(glow)
                        .frame(width: streakLen, height: lane.w)
                        .position(x: headX - streakLen / 2, y: h * lane.y)
                        .opacity((1 - travel) * intensity)
                }
            }
            .frame(width: w, height: h)
            .blur(radius: 2.2)                              // soft glow
            .drawingGroup()                                 // composite the glow
        }
    }
}
