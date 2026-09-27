package com.ghostmode.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.ghostmode.app.R

/**
 * The main switch: a large round button. When the mode is on it glows and emits slow pulses;
 * when off it is a calm neutral disc.
 */
@Composable
fun GhostHero(
    isOn: Boolean,
    isBusy: Boolean,
    enabled: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val glow = scheme.primary
    val transition = rememberInfiniteTransition(label = "hero")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val float by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val activeAmount by animateFloatAsState(if (isOn) 1f else 0f, tween(500), label = "active")
    val discTop by animateColorAsState(if (isOn) scheme.primary else scheme.surfaceContainerHighest, tween(500), label = "discTop")
    val discBottom by animateColorAsState(if (isOn) scheme.primaryContainer else scheme.surfaceContainerHigh, tween(500), label = "discBottom")
    val iconTint by animateColorAsState(if (isOn) scheme.onPrimary else scheme.onSurfaceVariant, tween(500), label = "icon")
    val pressScale by animateFloatAsState(if (isBusy) 0.96f else 1f, label = "busyScale")

    Box(modifier = modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val baseRadius = 84.dp.toPx()
            val maxRadius = size.minDimension / 2f
            // Soft glow behind the disc.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.35f * activeAmount), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )
            // Expanding pulse rings.
            if (activeAmount > 0f) {
                repeat(3) { index ->
                    val phase = (pulse + index / 3f) % 1f
                    val radius = baseRadius + (maxRadius - baseRadius) * phase
                    drawCircle(
                        color = glow.copy(alpha = (1f - phase) * 0.45f * activeAmount),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            // Static ring for the idle state.
            drawCircle(
                color = scheme.outlineVariant.copy(alpha = 1f - activeAmount),
                radius = baseRadius + 14.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(184.dp),
                strokeWidth = 3.dp,
                color = if (isOn) scheme.primary else scheme.secondary,
                trackColor = Color.Transparent
            )
        }

        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(168.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(discTop, discBottom)))
                .border(1.dp, if (isOn) Color.White.copy(alpha = 0.18f) else scheme.outlineVariant, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled && !isBusy,
                    role = Role.Switch,
                    onClick = onClick
                )
                .semantics { this.stateDescription = stateDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ghost),
                contentDescription = null,
                tint = iconTint.copy(alpha = if (enabled) 1f else 0.5f),
                modifier = Modifier
                    .size(76.dp)
                    .offset { IntOffset(0, (float * 4f * activeAmount).dp.roundToPx()) }
            )
        }
    }
}
