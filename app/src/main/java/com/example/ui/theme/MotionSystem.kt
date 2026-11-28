package com.example.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Calm animation tokens & system reduce-motion integration.
 *
 * - Screen transitions: 200–300 ms
 * - Press feedback: 100–150 ms
 * - List item fade/slide-in: plays once (never loops)
 * - Respects system "remove animations" / reduce motion (returns 0 ms when enabled)
 */
@Immutable
data class MotionConfig(
    val reduceMotion: Boolean = false
) {
    fun screenTransitionDurationMs(): Int = if (reduceMotion) 0 else SCREEN_TRANSITION_MS
    fun pressFeedbackDurationMs(): Int = if (reduceMotion) 0 else PRESS_FEEDBACK_MS
    fun listItemEnterDurationMs(): Int = if (reduceMotion) 0 else LIST_ITEM_ENTER_MS
    fun durationOrZero(normalMs: Int): Int = if (reduceMotion) 0 else normalMs.coerceAtLeast(0)
    val shouldAnimateShimmer: Boolean get() = !reduceMotion

    companion object {
        const val SCREEN_TRANSITION_MS = 240
        const val PRESS_FEEDBACK_MS = 120
        const val LIST_ITEM_ENTER_MS = 220
        const val SHIMMER_CYCLE_MS = 1100
    }
}

val LocalMotionConfig = staticCompositionLocalOf { MotionConfig() }

object MotionPreferences {
    /**
     * Pure evaluator so unit tests can verify reduce-motion logic deterministically.
     */
    fun isReduceMotionActive(
        animatorDurationScale: Float,
        animatorsEnabled: Boolean = true
    ): Boolean = !animatorsEnabled || animatorDurationScale <= 0f

    /**
     * Single helper reading the Android system "Remove animations" / animator scale setting.
     */
    fun isReduceMotionEnabled(context: Context): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)

        val animatorsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        } else {
            true
        }

        return isReduceMotionActive(
            animatorDurationScale = scale,
            animatorsEnabled = animatorsEnabled
        )
    }

    fun resolveMotionConfig(context: Context): MotionConfig =
        MotionConfig(reduceMotion = isReduceMotionEnabled(context))
}

@Composable
fun rememberMotionConfig(overrideReduceMotion: Boolean? = null): MotionConfig {
    val context = LocalContext.current
    val systemReduced = remember(context) { MotionPreferences.isReduceMotionEnabled(context) }
    val reduce = overrideReduceMotion ?: systemReduced
    return remember(reduce) { MotionConfig(reduceMotion = reduce) }
}

/**
 * Calm press scale feedback (100–150 ms) that settles immediately when reduce-motion is on.
 */
fun Modifier.calmPressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.98f
): Modifier = composed {
    val motion = LocalMotionConfig.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (enabled && isPressed && !motion.reduceMotion) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = motion.pressFeedbackDurationMs(),
            easing = FastOutSlowInEasing
        ),
        label = "calm_press_scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Plays a gentle fade + subtle vertical slide once when an item first enters composition.
 * When reduce-motion is enabled, renders immediately without motion or delay.
 */
fun Modifier.calmEnterOnce(
    index: Int = 0
): Modifier = composed {
    val motion = LocalMotionConfig.current
    if (motion.reduceMotion) {
        return@composed this
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val delayMs = (index.coerceIn(0, 6) * 24)
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.listItemEnterDurationMs(),
            delayMillis = delayMs,
            easing = FastOutSlowInEasing
        ),
        label = "calm_enter_alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 12f,
        animationSpec = tween(
            durationMillis = motion.listItemEnterDurationMs(),
            delayMillis = delayMs,
            easing = FastOutSlowInEasing
        ),
        label = "calm_enter_offset"
    )
    graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}
