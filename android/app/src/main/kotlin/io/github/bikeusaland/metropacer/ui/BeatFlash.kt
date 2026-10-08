package io.github.bikeusaland.metropacer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.bikeusaland.metropacer.MetronomeEngine.BeatPulse

/**
 * Full-screen edge vignette that snaps bright on each beat and fades out. The downbeat
 * flashes orange and a touch brighter; off-beats flash white. A vignette (not a solid
 * fill) keeps the controls readable while staying visible in peripheral vision.
 */
@Composable
fun BeatFlash(beat: BeatPulse, isRunning: Boolean, modifier: Modifier = Modifier) {
    val level = remember { Animatable(0f) }
    LaunchedEffect(beat.tick) {
        if (beat.tick == 0) return@LaunchedEffect
        level.snapTo(if (beat.accent) 1f else 0.7f)
        level.animateTo(0f, tween(durationMillis = 180, easing = LinearOutSlowInEasing))
    }
    val color = if (beat.accent) Orange else Color.White
    Canvas(modifier.graphicsLayer { alpha = if (isRunning) level.value else 0f }) {
        val outer = 520.dp.toPx()
        val inner = 120.dp.toPx() / outer
        drawRect(
            Brush.radialGradient(
                0f to color.copy(alpha = 0f),
                inner to color.copy(alpha = 0f),
                1f to color.copy(alpha = 0.55f),
                center = center,
                radius = outer,
            )
        )
    }
}
