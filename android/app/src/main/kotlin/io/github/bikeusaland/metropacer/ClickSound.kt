package io.github.bikeusaland.metropacer

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The selectable click timbres. Each is synthesized procedurally (no audio files), so
 * the app stays tiny and every sound shares the same exact timing. Same formulas as
 * the iOS app's `ClickSound`.
 */
enum class ClickSound(val label: String, val durationSeconds: Double) {
    WOODBLOCK("Woodblock", 0.04),
    RIM("High Click", 0.025),
    BEEP("Beep", 0.06),
    TICK("Tick", 0.03);

    /**
     * One click sample at [frame] (0-based). When [accent] is true the click is pitched
     * up and slightly louder, marking the downbeat so a runner can hear "1" land.
     */
    fun sample(frame: Int, sampleRate: Double, accent: Boolean): Float {
        val t = frame.toDouble()
        val pitch = if (accent) 1.5 else 1.0
        val gain = if (accent) 1.25 else 1.0
        val value = when (this) {
            WOODBLOCK -> {
                // Dry knock: ~800 Hz with a fast decay and a touch of 2nd harmonic.
                val env = exp(-t / (sampleRate * 0.006))
                val f = 2.0 * PI / sampleRate
                (sin(f * 800 * pitch * t) + 0.4 * sin(f * 1_600 * pitch * t)) * env * 0.5
            }
            // Bright, very short tick around 2 kHz — cuts through speech at speed.
            RIM -> sin(2.0 * PI * 2_000 * pitch * t / sampleRate) * exp(-t / (sampleRate * 0.0035)) * 0.7
            // Clean electronic tone, slightly longer, distinct from voice.
            BEEP -> sin(2.0 * PI * 1_320 * pitch * t / sampleRate) * exp(-t / (sampleRate * 0.020)) * 0.6
            // The original 1 kHz sine tick.
            TICK -> sin(2.0 * PI * 1_000 * pitch * t / sampleRate) * exp(-t / (sampleRate * 0.008)) * 0.9
        }
        // Clamp so the louder accent can't clip.
        return (value * gain).coerceIn(-1.0, 1.0).toFloat()
    }
}

/**
 * How many evenly spaced clicks sound within each beat. The first is the main beat
 * (and honors the accent); the rest are quieter in-between clicks.
 */
enum class Subdivision(val clicksPerBeat: Int, val label: String) {
    NONE(1, "None"),
    EIGHTHS(2, "8ths"),
    TRIPLETS(3, "Trip"),
    SIXTEENTHS(4, "16ths"),
}
