package io.github.bikeusaland.metropacer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bikeusaland.metropacer.ClickSound
import io.github.bikeusaland.metropacer.MetronomeEngine
import io.github.bikeusaland.metropacer.MetronomeEngine.Transport
import io.github.bikeusaland.metropacer.Subdivision
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal val Orange = Color(0xFFFF9500)
private val StopRed = Color(0xFFFF3B30)
private val GradientTop = Color(0.08f, 0.09f, 0.16f)
private val GradientBottom = Color(0.02f, 0.02f, 0.06f)
private val Chip = Color.White.copy(alpha = 0.08f)

/** Common running-cadence targets (steps per minute). */
private val cadenceTargets = listOf(160, 170, 180, 190)

/** "Off" (1) means every beat is identical; 2–4 accents the first beat of each measure. */
private val accentOptions = listOf(1, 2, 3, 4)

/** Time after the last tap before the tap-tempo gesture resets. */
private const val TAP_RESET_MS = 2_000L

@Composable
fun MetroPacerScreen(engine: MetronomeEngine) {
    val transport by engine.transport.collectAsStateWithLifecycle()
    val bpm by engine.bpm.collectAsStateWithLifecycle()
    val flashEnabled by engine.flashEnabled.collectAsStateWithLifecycle()
    val beat by engine.beat.collectAsStateWithLifecycle()
    val isRunning = transport == Transport.RUNNING

    var showOptions by rememberSaveable { mutableStateOf(false) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Orange)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(GradientTop, GradientBottom)))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    // Inset outside the scroll so content never slides under the status bar.
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header()
                BpmDisplay(bpm, isRunning)
                TempoSlider(bpm, engine::setBpm)
                TapTempoButton(engine)
                CadencePresets(bpm, engine::setBpm)
                OptionsPanel(engine, bpm, expanded = showOptions) { showOptions = !showOptions }
                PlayButton(isRunning, engine::toggle)
                Hint(engine, transport)
            }

            // Drawn over everything: a beat the runner can SEE when the click is drowned out.
            if (flashEnabled) BeatFlash(beat, isRunning, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Header() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MetroPacer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Plays on top of your audio", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
    }
}

@Composable
private fun BpmDisplay(bpm: Double, isRunning: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunnerSlot { RunnerView(bpm, isRunning, mirrored = false, phase = 0.0, modifier = it) }
            Text(
                "${bpm.roundToInt()}",
                color = Color.White,
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            RunnerSlot { RunnerView(bpm, isRunning, mirrored = true, phase = 0.5, modifier = it) }
        }
        Text("SPM · BPM", color = Color.White.copy(alpha = 0.5f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Runners share whatever width the number leaves, up to their natural 86dp. */
@Composable
private fun RowScope.RunnerSlot(content: @Composable (Modifier) -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        content(Modifier.widthIn(max = 86.dp).fillMaxWidth())
    }
}

@Composable
private fun TempoSlider(bpm: Double, onChange: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onChange(it.roundToInt().toDouble()) },
            valueRange = MetronomeEngine.MIN_BPM.toFloat()..MetronomeEngine.MAX_BPM.toFloat(),
            colors = sliderColors(),
        )
        Row(Modifier.fillMaxWidth()) {
            Caption("${MetronomeEngine.MIN_BPM.toInt()}")
            Spacer(Modifier.weight(1f))
            Caption("${MetronomeEngine.MAX_BPM.toInt()}")
        }
    }
}

@Composable
private fun TapTempoButton(engine: MetronomeEngine) {
    val tapCount by engine.tapCount.collectAsStateWithLifecycle()
    // Bumped on every tap so the reset timer restarts even when the count is capped.
    var tapSerial by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapSerial) {
        if (tapSerial == 0) return@LaunchedEffect
        delay(TAP_RESET_MS)
        engine.resetTap()
    }
    val label = when {
        tapCount >= 2 -> "Tap · $tapCount"
        tapCount == 1 -> "Keep tapping…"
        else -> "Tap Tempo"
    }
    PillButton(
        selected = tapCount > 0,
        selectedColor = Orange.copy(alpha = 0.85f),
        height = 50.dp,
        corner = 14.dp,
        onClick = {
            engine.tap()
            tapSerial++
        },
    ) {
        Icon(Icons.Filled.TouchApp, contentDescription = null)
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun CadencePresets(bpm: Double, onSelect: (Double) -> Unit) {
    LabeledRow("Cadence presets (SPM)", spacing = 10.dp) {
        cadenceTargets.forEach { spm ->
            PillButton(
                selected = bpm.roundToInt() == spm,
                height = 44.dp,
                corner = 12.dp,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(spm.toDouble()) },
            ) {
                Text("$spm", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// region Options (collapsible)

@Composable
private fun OptionsPanel(engine: MetronomeEngine, bpm: Double, expanded: Boolean, onToggle: () -> Unit) {
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = Color.White.copy(alpha = 0.9f)
            Icon(Icons.Filled.Tune, contentDescription = null, tint = tint)
            Text(
                "Options",
                color = tint,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = tint, modifier = Modifier.rotate(chevron))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                FineAdjustButtons(bpm, engine::setBpm)
                AccentPicker(engine)
                SubdivisionPicker(engine)
                SoundPicker(engine)
                VolumeSlider(engine)
                FeedbackToggles(engine)
            }
        }
    }
}

@Composable
private fun FineAdjustButtons(bpm: Double, onChange: (Double) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(-5, -1, 1, 5).forEach { delta ->
            PillButton(
                selected = false,
                height = 50.dp,
                corner = 14.dp,
                modifier = Modifier.weight(1f),
                onClick = { onChange(bpm + delta) },
            ) {
                Text(if (delta > 0) "+$delta" else "$delta", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AccentPicker(engine: MetronomeEngine) {
    val current by engine.beatsPerMeasure.collectAsStateWithLifecycle()
    LabeledRow("Accent (downbeat)") {
        accentOptions.forEach { count ->
            SmallChip(if (count == 1) "Off" else "$count/4", current == count) { engine.setBeatsPerMeasure(count) }
        }
    }
}

@Composable
private fun SubdivisionPicker(engine: MetronomeEngine) {
    val current by engine.subdivision.collectAsStateWithLifecycle()
    LabeledRow("Subdivision") {
        Subdivision.entries.forEach { option ->
            SmallChip(option.label, current == option) { engine.setSubdivision(option) }
        }
    }
}

@Composable
private fun SoundPicker(engine: MetronomeEngine) {
    val current by engine.sound.collectAsStateWithLifecycle()
    LabeledRow("Beat sound") {
        ClickSound.entries.forEach { option ->
            SmallChip(option.label, current == option) { engine.setSound(option) }
        }
    }
}

@Composable
private fun VolumeSlider(engine: MetronomeEngine) {
    val volume by engine.volume.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tint = Color.White.copy(alpha = 0.5f)
            Icon(Icons.AutoMirrored.Filled.VolumeMute, contentDescription = null, tint = tint)
            Slider(
                value = volume,
                onValueChange = engine::setVolume,
                colors = sliderColors(),
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = tint)
        }
        Caption("Beat volume  ${(volume * 100).roundToInt()}%")
    }
}

/** Non-audio beat feedback so the metronome stays usable when the click is inaudible. */
@Composable
private fun FeedbackToggles(engine: MetronomeEngine) {
    val flash by engine.flashEnabled.collectAsStateWithLifecycle()
    val haptics by engine.hapticsEnabled.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleChip("Flash", Icons.Filled.Bolt, flash) { engine.setFlashEnabled(!flash) }
        ToggleChip("Vibrate", Icons.Filled.Vibration, haptics) { engine.setHapticsEnabled(!haptics) }
    }
}

@Composable
private fun RowScope.ToggleChip(label: String, icon: ImageVector, isOn: Boolean, onClick: () -> Unit) {
    PillButton(selected = isOn, height = 44.dp, corner = 12.dp, modifier = Modifier.weight(1f), onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

// endregion

@Composable
private fun PlayButton(isRunning: Boolean, onClick: () -> Unit) {
    PillButton(
        selected = true,
        selectedColor = if (isRunning) StopRed else Orange,
        height = 64.dp,
        corner = 18.dp,
        onClick = onClick,
    ) {
        Icon(if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
        Text(
            if (isRunning) "Stop" else "Start",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun Hint(engine: MetronomeEngine, transport: Transport) {
    val error by engine.lastError.collectAsStateWithLifecycle()
    Column(
        Modifier.padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        error?.let {
            Text(it, color = StopRed.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
        if (transport == Transport.INTERRUPTED) {
            Text("Paused for a call — the beat resumes when it ends.", color = Orange, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Text(
            "Open Spotify, YouTube Music, or your podcast app and press play there first, then start the beat here.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// region Building blocks

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Orange,
    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
)

@Composable
private fun Caption(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
}

/** A row of equal-width controls with a caption underneath. */
@Composable
private fun LabeledRow(caption: String, spacing: Dp = 8.dp, content: @Composable RowScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
        Caption(caption)
    }
}

@Composable
private fun RowScope.SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    PillButton(selected = selected, height = 40.dp, corner = 10.dp, modifier = Modifier.weight(1f), onClick = onClick) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PillButton(
    selected: Boolean,
    height: Dp,
    corner: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Orange,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(if (selected) selectedColor else Chip)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

// endregion
