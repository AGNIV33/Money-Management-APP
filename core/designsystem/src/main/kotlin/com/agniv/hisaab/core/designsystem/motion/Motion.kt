package com.agniv.hisaab.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Durations from DESIGN.md §5. Short on purpose: cheap phones drop frames on long animations. */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val SCREEN = 300
    const val FILL = 700
}

/**
 * True when the user turned animations off ("Remove animations" in Android accessibility settings). Every Kinly
 * animation checks this and jumps straight to its end state.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Progress that fills from 0 the first time it is shown, then follows changes smoothly. */
@Composable
fun animateFill(target: Float): State<Float> {
    val reduced = rememberReducedMotion()
    var started by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { started = true }
    return animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = if (reduced) tween(0) else tween(Motion.FILL, easing = FastOutSlowInEasing),
        label = "fill",
    )
}

/**
 * Shakes sideways each time [trigger] changes to a new non-zero value (wrong OTP / wrong PIN).
 * Uses a layer translation only, so nothing is re-measured.
 */
@Suppress("MagicNumber") // Keyframe times and swing sizes: read together they are the shake curve.
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val reduced = rememberReducedMotion()
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0 || reduced) return@LaunchedEffect
        offset.animateTo(
            targetValue = 0f,
            animationSpec =
                keyframes {
                    durationMillis = SHAKE_MS
                    -SHAKE_PX at 50
                    SHAKE_PX at 100
                    -SHAKE_PX * 0.6f at 170
                    SHAKE_PX * 0.6f at 240
                    -SHAKE_PX * 0.3f at 300
                },
        )
    }
    return graphicsLayer { translationX = offset.value }
}

private const val SHAKE_MS = 360
private const val SHAKE_PX = 18f

/** Pops in with a small overshoot (check marks, "Completed"). */
@Composable
fun Modifier.popIn(): Modifier {
    val reduced = rememberReducedMotion()
    val scale = remember { Animatable(if (reduced) 1f else POP_FROM) }
    LaunchedEffect(Unit) {
        if (!reduced) scale.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS))
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

private const val POP_FROM = 0.4f
private const val POP_DAMPING = 0.45f
private const val POP_STIFFNESS = 400f

/**
 * Soft rings spreading out from the mic while listening. Two rings on one Canvas: no extra layout passes.
 * Draws nothing when animations are off.
 */
@Composable
fun PulseRings(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    if (rememberReducedMotion()) return
    val transition = rememberInfiniteTransition(label = "rings")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    Canvas(modifier.size(size)) {
        val maxR = this.size.minDimension / 2
        // Second ring runs half a cycle behind the first.
        listOf(t, (t + HALF) % 1f).forEach { p ->
            drawCircle(
                color = color.copy(alpha = (1f - p) * RING_ALPHA),
                radius = maxR * (RING_START + (1f - RING_START) * p),
                style = Stroke(width = 6.dp.toPx()),
            )
        }
    }
}

private const val HALF = 0.5f
private const val RING_MS = 1600
private const val RING_ALPHA = 0.5f
private const val RING_START = 0.45f
