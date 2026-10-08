package io.github.bikeusaland.metropacer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.bikeusaland.metropacer.R
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private val runnerFrames = intArrayOf(
    R.drawable.runner_00, R.drawable.runner_01, R.drawable.runner_02, R.drawable.runner_03,
    R.drawable.runner_04, R.drawable.runner_05, R.drawable.runner_06, R.drawable.runner_07,
    R.drawable.runner_08, R.drawable.runner_09, R.drawable.runner_10, R.drawable.runner_11,
)

/** Resting frame when stopped: neutral mid-stride. */
private const val REST_FRAME = 3

/**
 * A silhouette runner flipping through 12 sprite frames paced to the metronome (one full
 * stride per beat), with glowing motion streaks sweeping behind. [phase] (0..1) offsets
 * the stride so two runners land on opposite feet. Width:height is 86:64.
 */
@Composable
fun RunnerView(
    bpm: Double,
    isRunning: Boolean,
    mirrored: Boolean,
    phase: Double,
    modifier: Modifier = Modifier,
) {
    // Frame clock shared by every runner (System.nanoTime base), ticking only while running.
    val nanos by produceState(0L, isRunning) {
        if (isRunning) while (true) withFrameNanos { value = it }
    }
    val seconds = nanos / 1e9
    val beatSeconds = 60.0 / max(bpm, 1.0)
    val stride = if (isRunning) fraction(seconds / beatSeconds + phase) else 0.0
    val frame = if (isRunning) (stride * runnerFrames.size).toInt() % runnerFrames.size else REST_FRAME
    // Dip at each of the two foot-strikes.
    val bob = if (isRunning) (-sin(2 * stride * 2 * PI) * 4 - 1).toFloat() else 2f
    // Streaks sweep faster than one beat so they read as motion.
    val flow = if (isRunning) fraction(seconds / (beatSeconds * 0.5)) else 0.0
    val alpha by animateFloatAsState(if (isRunning) 1f else 0.4f, tween(300), label = "runnerAlpha")

    Box(
        modifier
            .aspectRatio(86f / 64f)
            .graphicsLayer {
                scaleX = if (mirrored) -1f else 1f // face the number
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center,
    ) {
        MotionStreaks(
            flow = flow,
            intensity = if (isRunning) 1f else 0f,
            modifier = Modifier.fillMaxWidth().aspectRatio(86f / 60f),
        )
        Image(
            painter = painterResource(runnerFrames[frame]),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(54f / 86f)
                .aspectRatio(54f / 64f)
                .offset(y = bob.dp),
        )
    }
}

private fun fraction(x: Double) = x - floor(x)

private class Lane(val y: Float, val length: Float, val width: Float)

private val lanes = listOf(
    Lane(0.30f, 0.85f, 3.0f),
    Lane(0.45f, 1.00f, 4.0f),
    Lane(0.58f, 0.70f, 2.5f),
    Lane(0.70f, 0.92f, 3.5f),
    Lane(0.84f, 0.60f, 2.0f),
)

// Bright at the front, fading toward the back (drawn right to left).
private val streakColors = listOf(
    Color(0.55f, 0.85f, 1.0f, 0.0f),
    Color(0.40f, 0.78f, 1.0f, 0.9f),
    Color(0.20f, 0.60f, 1.0f, 0.0f),
)

/** Glowing speed lines sweeping backward (-x) behind the runner. */
@Composable
private fun MotionStreaks(flow: Double, intensity: Float, modifier: Modifier) {
    Canvas(modifier.blur(2.2.dp, BlurredEdgeTreatment.Unbounded)) {
        if (intensity <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        lanes.forEachIndexed { i, lane ->
            // Stagger each lane's phase so they don't pulse in unison.
            val p = fraction(flow + i * 0.18).toFloat()
            val length = w * 0.55f * lane.length
            val headX = w * 0.62f - p * w * 0.85f
            val thickness = lane.width.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(streakColors, startX = headX, endX = headX - length),
                topLeft = Offset(headX - length, h * lane.y - thickness / 2),
                size = Size(length, thickness),
                cornerRadius = CornerRadius(thickness / 2),
                alpha = (1 - p) * intensity,
            )
        }
    }
}
