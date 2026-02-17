package com.rjasao.nowsei.presentation.sync.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.rjasao.nowsei.presentation.sync.SyncUiState

@Composable
fun TwoStageSyncBars(
    state: SyncUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Barra superior: avança 1/3 por etapa (1,2,3)
        LinearProgressIndicator(
            progress = { state.topProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
        )

        // Barra inferior: shimmer fluido enquanto sincroniza
        if (state.isSyncing) {
            ShimmerBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )

            val stepSafe = state.step.coerceAtLeast(1)
            val pct = state.stepProgress.coerceIn(0, 100)

            Text(
                text = state.message ?: "Etapa $stepSafe/${state.totalSteps} • $pct%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun ShimmerBar(
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val t by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerT"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val base = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(trackColor)
    ) {
        // largura “virtual” pra varrer a barra inteira
        val w = 800f
        val startX = t * w - w
        val endX = startX + w

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(startX, 0f),
                        end = Offset(endX, 0f)
                    )
                )
        )
    }
}
