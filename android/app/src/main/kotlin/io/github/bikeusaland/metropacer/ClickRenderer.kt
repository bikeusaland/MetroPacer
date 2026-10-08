package io.github.bikeusaland.metropacer

import kotlin.math.max
import kotlin.math.min

/** Live metronome settings, read once at the start of every beat. */
interface BeatSettings {
    val bpm: Double
    val sound: ClickSound
    val beatsPerMeasure: Int
    val subdivision: Subdivision
}

/**
 * Renders the click track as a continuous PCM stream, one beat at a time.
 *
 * Timing is counted in output frames, so it is sample-accurate and drift-free: every beat
 * is exactly `60 / bpm * sampleRate` frames long, regardless of how the stream is chunked.
 * Settings are sampled at each beat boundary, so a tempo/sound/subdivision change is heard
 * from the next beat on. Mirrors the iOS one-buffer-per-beat design without allocating per
 * beat: click waveforms are precomputed once per sample rate.
 *
 * Not thread-safe; owned by the audio thread.
 *
 * @param onBeat called from [render] when a beat starts, with the absolute stream frame of
 *   its main click and whether it is the accented downbeat.
 */
class ClickRenderer(
    private val sampleRate: Int,
    private val settings: BeatSettings,
    private val onBeat: (frame: Long, accent: Boolean) -> Unit,
) {
    /** `clicks[sound.ordinal * 2 + (accent ? 1 : 0)]` */
    private val clicks: Array<FloatArray> = Array(ClickSound.entries.size * 2) { i ->
        val sound = ClickSound.entries[i / 2]
        val accent = i % 2 == 1
        FloatArray((sampleRate * sound.durationSeconds).toInt()) { f ->
            sound.sample(f, sampleRate.toDouble(), accent)
        }
    }

    private var framesRendered = 0L

    /** Position within the measure of the NEXT beat; 0 is the accented downbeat. */
    private var beatIndex = 0
    private var lastMeasure = 0

    // Current beat, fixed at its start.
    private var beatFrames = 0
    private var posInBeat = 0
    private var clicksPerBeat = 1
    private var mainClick: FloatArray = clicks[0]
    private var subClick: FloatArray = clicks[0]

    /** Fills `out[0 until count]` with the next [count] frames of the click track. */
    fun render(out: FloatArray, count: Int) {
        out.fill(0f, 0, count)
        var i = 0
        while (i < count) {
            if (posInBeat >= beatFrames) startBeat(framesRendered + i)
            val n = min(count - i, beatFrames - posInBeat)
            mixClicks(out, i, posInBeat, n)
            posInBeat += n
            i += n
        }
        framesRendered += count
    }

    private fun startBeat(frame: Long) {
        val measure = settings.beatsPerMeasure.coerceIn(1, MetronomeEngine.MAX_BEATS_PER_MEASURE)
        // Realign on a time-signature change so the next beat starts a fresh measure.
        if (measure != lastMeasure) {
            beatIndex = 0
            lastMeasure = measure
        }
        // A measure of 1 disables accenting entirely.
        val accent = measure > 1 && beatIndex == 0
        beatIndex = (beatIndex + 1) % measure

        val sound = settings.sound
        beatFrames = max(1, (60.0 / settings.bpm * sampleRate).toInt())
        posInBeat = 0
        clicksPerBeat = settings.subdivision.clicksPerBeat
        mainClick = clicks[sound.ordinal * 2 + if (accent) 1 else 0]
        subClick = clicks[sound.ordinal * 2] // off-beat subdivisions are never accented
        onBeat(frame, accent)
    }

    /** Writes the part of this beat's clicks that falls in beat frames `[from, from + n)`. */
    private fun mixClicks(out: FloatArray, outOffset: Int, from: Int, n: Int) {
        val to = from + n
        for (k in 0 until clicksPerBeat) {
            val start = (k.toDouble() / clicksPerBeat * beatFrames).toInt()
            val isMain = k == 0
            val src = if (isMain) mainClick else subClick
            val gain = if (isMain) 1f else SUBDIVISION_GAIN
            // A click never runs past the end of its beat.
            val end = start + min(src.size, beatFrames - start)
            val a = max(from, start)
            val b = min(to, end)
            // Later clicks overwrite an earlier click's tail, as on iOS.
            for (f in a until b) out[outOffset + f - from] = src[f - start] * gain
        }
    }

    companion object {
        /** Volume of the in-between subdivision clicks relative to the main beat. */
        const val SUBDIVISION_GAIN = 0.5f
    }
}
