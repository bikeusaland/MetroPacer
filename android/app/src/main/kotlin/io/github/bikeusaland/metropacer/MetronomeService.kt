package io.github.bikeusaland.metropacer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.bikeusaland.metropacer.MetronomeEngine.Transport
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.media.app.NotificationCompat as MediaNotificationCompat

/**
 * Foreground `mediaPlayback` service that keeps the beat alive with the screen off or the
 * app in the background, and publishes a media session so the lock screen, notification
 * shade and headset buttons can stop/start it (the Android counterpart of the iOS
 * Now Playing / remote-command wiring).
 *
 * Runs exactly while the engine is not [Transport.STOPPED]; [MetroPacerApp] starts it and
 * it stops itself.
 */
class MetronomeService : Service() {

    private lateinit var engine: MetronomeEngine
    private lateinit var session: MediaSessionCompat
    private val scope = MainScope()
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        engine = (application as MetroPacerApp).engine
        ensureChannel()
        session = MediaSessionCompat(this, "MetroPacer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = engine.start()
                override fun onPause() = engine.stop()
                override fun onStop() = engine.stop()
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }

        val spm = engine.bpm.map { it.roundToInt() }.distinctUntilChanged()
        scope.launch {
            combine(engine.transport, spm, ::Pair).collectLatest { (transport, bpm) ->
                if (transport == Transport.STOPPED) {
                    // Fails (and keeps us alive) if a newer start is already pending.
                    stopSelfResult(lastStartId)
                    return@collectLatest
                }
                updateSession(transport, bpm)
                // Notification updates are rate-limited by the system; while the slider is
                // being dragged only the settled value is posted.
                delay(NOTIFICATION_DEBOUNCE_MS)
                promote(transport, bpm)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Every startForegroundService() must be answered with startForeground().
        promote(engine.transport.value, engine.bpm.value.roundToInt())
        if (intent?.action == ACTION_STOP) engine.stop()
        // Stopped before this start was delivered: the collector won't emit again.
        if (engine.transport.value == Transport.STOPPED) stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSession(transport: Transport, bpm: Int) {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Metronome")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "$bpm SPM")
                .build()
        )
        val running = transport == Transport.RUNNING
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    if (running) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    if (running) 1f else 0f,
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
                )
                .build()
        )
    }

    /** Posts (or refreshes) the ongoing notification and holds foreground status. */
    private fun promote(transport: Transport, bpm: Int) {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_metronome)
            .setContentTitle("Metronome")
            .setContentText(
                if (transport == Transport.INTERRUPTED) "Paused for call — resumes after" else "$bpm SPM"
            )
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Metronome playback", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_DEBOUNCE_MS = 250L
        private const val ACTION_STOP = "io.github.bikeusaland.metropacer.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java))
        }
    }
}
