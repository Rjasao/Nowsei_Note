package com.rjasao.nowsei.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme

/**
 * Canvas da página (base para editor estilo OneNote).
 * Etapa 7: adiciona callback de toque no fundo para deseleção.
 */
@Composable
fun PageEditorCanvas(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") selectedId: String?,
    gesturesEnabled: Boolean = true,
    onBackgroundTap: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(2.dp)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (gesturesEnabled && onBackgroundTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { onBackgroundTap() })
                    }
                } else Modifier
            )
    ) {
        content()
    }
}
