package io.github.bikeusaland.metropacer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Sample-accurate metronome on a streaming [AudioTrack].
 *
 * The click track is rendered frame-by-frame by [ClickRenderer], so tempo never drifts.
 * The engine deliberately does NOT request audio focus: Spotify, YouTube Music, podcast
 * apps etc. keep playing and the click mixes on top of them (the Android equivalent of
 * iOS `.mixWithOthers`). Phone calls pause the beat and it resumes when the call ends.
 *
 * Public API is main-thread only. Owned by [MetroPacerApp] so the UI and
 * [MetronomeService] share one instance.
 */
class MetronomeEngine(context: Context) {

    enum class Transport {
        STOPPED,
        RUNNING,

        /** Paused by a phone call; resumes automatically when it ends. */
        INTERRUPTED,
    }

    /**
     * One pulse per beat, published when the click is heard. `tick` increments every beat
     * so observers see each one even at the same accent value.
     */
    data class BeatPulse(val tick: Int = 0, val accent: Boolean = false)

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("metronome", Context.MODE_PRIVATE)
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    // region Observable state

    private val _transport = MutableStateFlow(Transport.STOPPED)
    val transport: StateFlow<Transport> = _transport.asStateFlow()

    /** Surfaces the last audio failure so the UI isn't silently dead. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _beat = MutableStateFlow(BeatPulse())
    val beat: StateFlow<BeatPulse> = _beat.asStateFlow()

    /** Taps in the current tap-tempo gesture; 0 when no gesture is in progress. */
    private val _tapCount = MutableStateFlow(0)
    val tapCount: StateFlow<Int> = _tapCount.asStateFlow()

    private val _bpm = MutableStateFlow(
        prefs.getFloat(KEY_BPM, 120f).toDouble().coerceIn(MIN_BPM, MAX_BPM)
    )
    val bpm: StateFlow<Double> = _bpm.asStateFlow()

    private val _volume = MutableStateFlow(prefs.getFloat(KEY_VOLUME, 0.8f).coerceIn(0f, 1f))
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _sound = MutableStateFlow(
        ClickSound.entries.firstOrNull { it.name == prefs.getString(KEY_SOUND, null) }
            ?: ClickSound.WOODBLOCK
    )
    val sound: StateFlow<ClickSound> = _sound.asStateFlow()

    private val _beatsPerMeasure = MutableStateFlow(
        prefs.getInt(KEY_BEATS_PER_MEASURE, 1).coerceIn(1, MAX_BEATS_PER_MEASURE)
    )
    val beatsPerMeasure: StateFlow<Int> = _beatsPerMeasure.asStateFlow()

    private val _subdivision = MutableStateFlow(
        Subdivision.entries.firstOrNull { it.clicksPerBeat == prefs.getInt(KEY_SUBDIVISION, 1) }
            ?: Subdivision.NONE
    )
    val subdivision: StateFlow<Subdivision> = _subdivision.asStateFlow()

    private val _flashEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLASH, true))
    val flashEnabled: StateFlow<Boolean> = _flashEnabled.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, false))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    // endregion

    // region Settings

    fun setBpm(value: Double) {
        val clamped = value.coerceIn(MIN_BPM, MAX_BPM)
        _bpm.value = clamped
        prefs.edit { putFloat(KEY_BPM, clamped.toFloat()) }
    }

    /** Click volume, independent of the music and the system media volume. */
    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _volume.value = clamped
        playback?.setVolume(clamped)
        prefs.edit { putFloat(KEY_VOLUME, clamped) }
    }

    fun setSound(value: ClickSound) {
        _sound.value = value
        prefs.edit { putString(KEY_SOUND, value.name) }
    }

    /** `1` = no accent; `2…8` accents the first beat of each measure. */
    fun setBeatsPerMeasure(value: Int) {
        val clamped = value.coerceIn(1, MAX_BEATS_PER_MEASURE)
        _beatsPerMeasure.value = clamped
        prefs.edit { putInt(KEY_BEATS_PER_MEASURE, clamped) }
    }

    fun setSubdivision(value: Subdivision) {
        _subdivision.value = value
        prefs.edit { putInt(KEY_SUBDIVISION, value.clicksPerBeat) }
    }

    fun setFlashEnabled(value: Boolean) {
        _flashEnabled.value = value
        prefs.edit { putBoolean(KEY_FLASH, value) }
    }

    fun setHapticsEnabled(value: Boolean) {
        _hapticsEnabled.value = value
        prefs.edit { putBoolean(KEY_HAPTICS, value) }
    }

    /** Snapshot the audio thread reads at each beat boundary. */
    private val beatSettings = object : BeatSettings {
        override val bpm get() = _bpm.value
        override val sound get() = _sound.value
        override val beatsPerMeasure get() = _beatsPerMeasure.value
        override val subdivision get() = _subdivision.value
    }

    // endregion

    // region Transport

    private var playback: Playback? = null

    fun start() {
        if (_transport.value == Transport.RUNNING) return
        val started = try {
            Playback(createTrack()).also { it.start() }
        } catch (e: RuntimeException) {
            _lastError.value = "Start failed: ${e.message}"
            _transport.value = Transport.STOPPED
            mainHandler.removeCallbacks(callWatch)
            return
        }
        _lastError.value = null
        playback = started
        _transport.value = Transport.RUNNING
        // Check immediately so starting during a call pauses right away.
        mainHandler.removeCallbacks(callWatch)
        mainHandler.post(callWatch)
    }

    fun stop() {
        mainHandler.removeCallbacks(callWatch)
        releasePlayback()
        _transport.value = Transport.STOPPED
    }

    fun toggle() {
        if (_transport.value == Transport.RUNNING) stop() else start()
    }

    private fun releasePlayback() {
        playback?.stop()
        playback = null
    }

    private fun createTrack(): AudioTrack {
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 48_000
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        check(minBuffer > 0) { "audio output unavailable ($minBuffer)" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("audio track failed to initialize")
        }
        return track
    }

    /** Called on the main thread when the audio thread's write fails. */
    private fun onPlaybackFailed(failed: Playback, code: Int) {
        if (playback !== failed) return
        stop()
        _lastError.value = "Audio output failed ($code)"
    }

    // endregion

    // region Phone-call interruptions

    /**
     * Polls the audio mode while active. Polling (rather than a mode listener, API 31+) keeps
     * one code path for every supported Android version and needs no phone permission.
     */
    private val callWatch: Runnable = object : Runnable {
        override fun run() {
            val mode = audioManager.mode
            val inCall = mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
            when (_transport.value) {
                Transport.STOPPED -> return
                Transport.RUNNING -> if (inCall) {
                    releasePlayback()
                    _transport.value = Transport.INTERRUPTED
                }
                Transport.INTERRUPTED -> if (!inCall) {
                    start()
                    return // start() reschedules the watch (or stopped on failure)
                }
            }
            mainHandler.postDelayed(this, CALL_POLL_MS)
        }
    }

    // endregion

    // region Tap tempo

    /** Monotonic timestamps (seconds) of the current gesture's recent taps. */
    private val tapTimes = DoubleArray(TAP_WINDOW + 1)
    private var tapTimesCount = 0

    /**
     * Registers one tap. Two or more taps within [TAP_MAX_GAP] of each other set the tempo
     * from the average tapped interval; a longer pause starts a fresh measurement.
     */
    fun tap(nowSeconds: Double = SystemClock.elapsedRealtimeNanos() / 1e9) {
        if (tapTimesCount > 0 && nowSeconds - tapTimes[tapTimesCount - 1] > TAP_MAX_GAP) {
            tapTimesCount = 0
        }
        if (tapTimesCount == tapTimes.size) {
            // Keep only enough taps to cover the averaging window.
            System.arraycopy(tapTimes, 1, tapTimes, 0, tapTimes.size - 1)
            tapTimesCount--
        }
        tapTimes[tapTimesCount++] = nowSeconds
        _tapCount.value = tapTimesCount

        if (tapTimesCount < 2) return
        // Mean of consecutive intervals = total span / interval count.
        val averageInterval = (tapTimes[tapTimesCount - 1] - tapTimes[0]) / (tapTimesCount - 1)
        if (averageInterval > 0) setBpm(60.0 / averageInterval)
    }

    /** Forgets the in-progress tap gesture. Does not change the tempo. */
    fun resetTap() {
        tapTimesCount = 0
        _tapCount.value = 0
    }

    // endregion

    // region Haptics

    private val vibrator: Vibrator? = run {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Vibrator::class.java)
        }
        v?.takeIf { it.hasVibrator() }
    }

    /** The accented downbeat hits harder and longer. */
    private val accentVibration = VibrationEffect.createOneShot(40, 255)
    private val beatVibration = VibrationEffect.createOneShot(25, 150)

    // endregion

    /**
     * One run of the beat: a streaming [AudioTrack], the thread that renders into it, and
     * the thread that fires flash/haptic pulses when each click is actually heard.
     */
    private inner class Playback(private val track: AudioTrack) {
        private val sampleRate = track.sampleRate
        @Volatile private var active = true

        private val renderer = ClickRenderer(sampleRate, beatSettings, ::onBeatRendered)
        private val chunk = FloatArray(maxOf(64, sampleRate / 100)) // 10 ms

        // Beats rendered but not yet heard: a fixed ring, so no per-beat allocation.
        private val lock = ReentrantLock()
        private val pulseReady = lock.newCondition()
        private val pendingFrames = LongArray(PULSE_CAPACITY)
        private val pendingTicks = IntArray(PULSE_CAPACITY)
        private val pendingAccents = BooleanArray(PULSE_CAPACITY)
        private var pendingHead = 0
        private var pendingCount = 0
        private var tick = 0

        private val timestamp = AudioTimestamp()

        private val renderThread = thread(start = false, name = "MetroPacer-audio") { render() }
        private val pulseThread = thread(start = false, name = "MetroPacer-pulse") { dispatchPulses() }

        fun start() {
            track.setVolume(_volume.value)
            track.play()
            renderThread.start()
            pulseThread.start()
        }

        fun setVolume(volume: Float) {
            track.setVolume(volume)
        }

        fun stop() {
            active = false
            lock.withLock { pulseReady.signalAll() }
            // pause + flush silences immediately and unblocks a pending write.
            track.pause()
            track.flush()
            renderThread.join(JOIN_TIMEOUT_MS)
            pulseThread.join(JOIN_TIMEOUT_MS)
            track.release()
        }

        private fun render() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (active) {
                renderer.render(chunk, chunk.size)
                var offset = 0
                while (active && offset < chunk.size) {
                    val written = track.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        mainHandler.post { onPlaybackFailed(this, written) }
                        return
                    }
                    offset += written
                }
            }
        }

        /** Audio thread: queue a pulse for the beat starting at [frame]. */
        private fun onBeatRendered(frame: Long, accent: Boolean) {
            tick++
            if (!_flashEnabled.value && !_hapticsEnabled.value) return
            lock.withLock {
                if (pendingCount == PULSE_CAPACITY) return // pulse thread stalled; drop
                val i = (pendingHead + pendingCount) % PULSE_CAPACITY
                pendingFrames[i] = frame
                pendingTicks[i] = tick
                pendingAccents[i] = accent
                pendingCount++
                pulseReady.signalAll()
            }
        }

        private fun dispatchPulses() {
            while (true) {
                var frame: Long
                var pulseTick: Int
                var accent: Boolean
                lock.withLock {
                    while (active && pendingCount == 0) pulseReady.await()
                    if (!active) return
                    frame = pendingFrames[pendingHead]
                    pulseTick = pendingTicks[pendingHead]
                    accent = pendingAccents[pendingHead]
                    pendingHead = (pendingHead + 1) % PULSE_CAPACITY
                    pendingCount--
                }
                // Lead slightly for screen/actuator latency so the pulse lands with the click.
                val deadline = presentationNanos(frame) - PULSE_LEAD_NANOS
                lock.withLock {
                    while (active) {
                        val wait = deadline - System.nanoTime()
                        if (wait <= 0) break
                        pulseReady.awaitNanos(wait)
                    }
                    if (!active) return
                }
                if (_flashEnabled.value) _beat.value = BeatPulse(pulseTick, accent)
                if (_hapticsEnabled.value) {
                    vibrator?.vibrate(if (accent) accentVibration else beatVibration)
                }
            }
        }

        /**
         * When stream frame [frame] reaches the speaker, on the [System.nanoTime] clock.
         * Anchored to the audio clock via [AudioTrack.getTimestamp] (which includes output
         * latency, Bluetooth too), so pulses track the audio without drifting.
         */
        private fun presentationNanos(frame: Long): Long {
            if (track.getTimestamp(timestamp)) {
                return timestamp.nanoTime +
                    (frame - timestamp.framePosition) * 1_000_000_000L / sampleRate
            }
            // No timestamp yet (first moments of playback): use the playback head.
            val head = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
            return System.nanoTime() + (frame - head) * 1_000_000_000L / sampleRate
        }
    }

    companion object {
        const val MIN_BPM = 20.0
        const val MAX_BPM = 300.0
        const val MAX_BEATS_PER_MEASURE = 8

        /** Taps further apart than this start a new gesture (i.e. below 30 BPM). */
        private const val TAP_MAX_GAP = 2.0
        /** Intervals averaged for tap tempo. */
        private const val TAP_WINDOW = 5

        private const val CALL_POLL_MS = 500L
        private const val PULSE_LEAD_NANOS = 12_000_000L
        private const val PULSE_CAPACITY = 32
        private const val JOIN_TIMEOUT_MS = 500L

        private const val KEY_BPM = "metronome.bpm"
        private const val KEY_VOLUME = "metronome.volume"
        private const val KEY_SOUND = "metronome.sound"
        private const val KEY_BEATS_PER_MEASURE = "metronome.beatsPerMeasure"
        private const val KEY_FLASH = "metronome.flashEnabled"
        private const val KEY_HAPTICS = "metronome.hapticsEnabled"
        private const val KEY_SUBDIVISION = "metronome.subdivision"
    }
}
