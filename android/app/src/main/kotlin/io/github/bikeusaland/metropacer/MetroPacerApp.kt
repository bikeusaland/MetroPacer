package io.github.bikeusaland.metropacer

import android.app.Application
import io.github.bikeusaland.metropacer.MetronomeEngine.Transport
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MetroPacerApp : Application() {

    /** Process-wide engine shared by the UI and [MetronomeService]. */
    lateinit var engine: MetronomeEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = MetronomeEngine(this)
        // Start the foreground service whenever the beat becomes active. That only happens
        // from a user action in the foreground (the service keeps running through call
        // interruptions), which satisfies Android 12+ background-start limits.
        MainScope().launch {
            engine.transport
                .map { it != Transport.STOPPED }
                .distinctUntilChanged()
                .collect { active -> if (active) MetronomeService.start(this@MetroPacerApp) }
        }
    }
}
