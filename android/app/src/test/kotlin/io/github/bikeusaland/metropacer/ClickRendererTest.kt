package io.github.bikeusaland.metropacer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ClickRendererTest {

    private class Settings(
        override var bpm: Double = 180.0,
        override var sound: ClickSound = ClickSound.WOODBLOCK,
        override var beatsPerMeasure: Int = 1,
        override var subdivision: Subdivision = Subdivision.NONE,
    ) : BeatSettings

    private class Beat(val frame: Long, val accent: Boolean)

    private fun renderInChunks(
        settings: Settings,
        totalFrames: Int,
        chunk: Int,
        sampleRate: Int = SAMPLE_RATE,
        onBeat: (Int) -> Unit = {},
    ): Pair<FloatArray, List<Beat>> {
        val beats = mutableListOf<Beat>()
        val renderer = ClickRenderer(sampleRate, settings) { frame, accent ->
            beats += Beat(frame, accent)
            onBeat(beats.size)
        }
        val out = FloatArray(totalFrames)
        val buf = FloatArray(chunk)
        var done = 0
        while (done < totalFrames) {
            val n = minOf(chunk, totalFrames - done)
            renderer.render(buf, n)
            buf.copyInto(out, done, 0, n)
            done += n
        }
        return out to beats
    }

    @Test
    fun outputAndBeatFramesAreIndependentOfChunkSize() {
        val settings = Settings(bpm = 173.0, beatsPerMeasure = 3, subdivision = Subdivision.TRIPLETS)
        val (reference, referenceBeats) = renderInChunks(settings, 5 * SAMPLE_RATE, chunk = 480)
        for (chunk in listOf(1, 37, 1024, 7919)) {
            val (out, beats) = renderInChunks(settings, 5 * SAMPLE_RATE, chunk)
            assertArrayEquals("chunk=$chunk", reference, out, 0f)
            assertEquals(referenceBeats.map { it.frame }, beats.map { it.frame })
        }
    }

    @Test
    fun beatsLandOnExactFrameMultiplesWithoutDrift() {
        // 180 BPM at 48 kHz = exactly 16 000 frames per beat; 10 minutes of beats.
        val (_, beats) = renderInChunks(Settings(bpm = 180.0), 10 * 60 * SAMPLE_RATE, chunk = 480)
        assertEquals(1800, beats.size)
        beats.forEachIndexed { i, beat -> assertEquals(i * 16_000L, beat.frame) }
    }

    @Test
    fun accentsFirstBeatOfEachMeasureOnlyWhenMeasureAboveOne() {
        val (_, fourFour) = renderInChunks(Settings(beatsPerMeasure = 4), 3 * SAMPLE_RATE, chunk = 480)
        assertEquals(
            List(fourFour.size) { it % 4 == 0 },
            fourFour.map { it.accent },
        )
        val (_, off) = renderInChunks(Settings(beatsPerMeasure = 1), 3 * SAMPLE_RATE, chunk = 480)
        assertEquals(List(off.size) { false }, off.map { it.accent })
    }

    @Test
    fun timeSignatureChangeRestartsMeasureOnNextBeat() {
        val settings = Settings(beatsPerMeasure = 4)
        // After the 2nd beat starts, switch to 3/4: the 3rd beat must be a fresh downbeat.
        val (_, beats) = renderInChunks(settings, 3 * SAMPLE_RATE, chunk = 480) { n ->
            if (n == 2) settings.beatsPerMeasure = 3
        }
        assertEquals(
            listOf(true, false, true, false, false, true),
            beats.take(6).map { it.accent },
        )
    }

    @Test
    fun tempoChangeAppliesFromNextBeatWithoutCuttingCurrentOne() {
        val settings = Settings(bpm = 120.0) // 24 000 frames
        val (_, beats) = renderInChunks(settings, 2 * SAMPLE_RATE, chunk = 480) { n ->
            if (n == 1) settings.bpm = 240.0 // 12 000 frames from beat 2 on
        }
        assertEquals(listOf(0L, 24_000L, 36_000L, 48_000L), beats.take(4).map { it.frame })
    }

    @Test
    fun subdivisionClicksAreEvenlySpacedAndQuieter() {
        val settings = Settings(bpm = 120.0, sound = ClickSound.TICK, subdivision = Subdivision.SIXTEENTHS)
        val (out, _) = renderInChunks(settings, 24_000, chunk = 480)
        // 4 clicks per 24 000-frame beat start at 0, 6000, 12000, 18000.
        val main = ClickSound.TICK.sample(5, SAMPLE_RATE.toDouble(), accent = false)
        assertEquals(main, out[5], 0f)
        for (start in listOf(6_000, 12_000, 18_000)) {
            assertEquals(main * ClickRenderer.SUBDIVISION_GAIN, out[start + 5], 0f)
        }
        assertEquals(0f, out[3_000], 0f) // silence between clicks
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
