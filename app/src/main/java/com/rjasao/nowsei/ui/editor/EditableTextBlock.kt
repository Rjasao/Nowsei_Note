package com.rjasao.nowsei.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rjasao.nowsei.domain.model.ContentBlock
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


private enum class ResizeHandle {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
}

/**
 * Bloco de texto "estilo canvas":
 * - Sem contorno quando não selecionado
 * - Seleção por toque longo
 * - Moldura pontilhada + alças (8 handles) quando selecionado
 * - Arraste da caixa (long press + drag) sem alterar fonte/texto
 * - Redimensionamento pelas alças
 *
 * Obs.: estado de posição/tamanho é visual (UI). Persistência pode ser adicionada depois.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditableTextBlock(
    block: ContentBlock.TextBlock,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Digite aqui..."
) {
    var text by remember(block.id, block.text) { mutableStateOf(block.text) }

    var isSelected by rememberSaveable(block.id) { mutableStateOf(false) }
    var offsetX by rememberSaveable(block.id) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(block.id) { mutableFloatStateOf(0f) }
    var boxWidthDp by rememberSaveable(block.id) { mutableFloatStateOf(320f) }
    var boxMinHeightDp by rememberSaveable(block.id) { mutableFloatStateOf(90f) }

    val densityScale = 1f // usaremos dp diretamente com dragAmount px -> convertido abaixo via helper

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        val widthDp = boxWidthDp.coerceIn(180f, 1200f).dp
        val minHeightDp = boxMinHeightDp.coerceIn(64f, 2000f).dp

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(widthDp)
                .heightIn(min = minHeightDp)
                .combinedClickable(
                    onClick = { /* mantém foco/cursor do campo */ },
                    onLongClick = { isSelected = true }
                )
                .pointerInput(block.id, isSelected) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmount ->
                            if (!isSelected) return@detectDragGesturesAfterLongPress
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    )
                }
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onTextChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeightDp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = block.fontSize.sp,
                    fontWeight = if (block.isBold) androidx.compose.ui.text.font.FontWeight.Bold else MaterialTheme.typography.bodyLarge.fontWeight,
                    fontStyle = if (block.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else MaterialTheme.typography.bodyLarge.fontStyle,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions.Default,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (isSelected) {
                DashedSelectionFrame(
                    onHandleDrag = { handle, dragPx ->
                        val dxDp = dragPx.x.toDpLike()
                        val dyDp = dragPx.y.toDpLike()

                        when (handle) {
                            ResizeHandle.RIGHT -> {
                                boxWidthDp = (boxWidthDp + dxDp).coerceAtLeast(180f)
                            }
                            ResizeHandle.LEFT -> {
                                val newW = (boxWidthDp - dxDp).coerceAtLeast(180f)
                                val applied = boxWidthDp - newW
                                boxWidthDp = newW
                                offsetX += (applied * densityScale)
                            }
                            ResizeHandle.BOTTOM -> {
                                boxMinHeightDp = (boxMinHeightDp + dyDp).coerceAtLeast(64f)
                            }
                            ResizeHandle.TOP -> {
                                val newH = (boxMinHeightDp - dyDp).coerceAtLeast(64f)
                                val applied = boxMinHeightDp - newH
                                boxMinHeightDp = newH
                                offsetY += (applied * densityScale)
                            }
                            ResizeHandle.TOP_LEFT -> {
                                val newW = (boxWidthDp - dxDp).coerceAtLeast(180f)
                                val appliedW = boxWidthDp - newW
                                boxWidthDp = newW
                                offsetX += (appliedW * densityScale)

                                val newH = (boxMinHeightDp - dyDp).coerceAtLeast(64f)
                                val appliedH = boxMinHeightDp - newH
                                boxMinHeightDp = newH
                                offsetY += (appliedH * densityScale)
                            }
                            ResizeHandle.TOP_RIGHT -> {
                                boxWidthDp = (boxWidthDp + dxDp).coerceAtLeast(180f)
                                val newH = (boxMinHeightDp - dyDp).coerceAtLeast(64f)
                                val appliedH = boxMinHeightDp - newH
                                boxMinHeightDp = newH
                                offsetY += (appliedH * densityScale)
                            }
                            ResizeHandle.BOTTOM_LEFT -> {
                                val newW = (boxWidthDp - dxDp).coerceAtLeast(180f)
                                val appliedW = boxWidthDp - newW
                                boxWidthDp = newW
                                offsetX += (appliedW * densityScale)
                                boxMinHeightDp = (boxMinHeightDp + dyDp).coerceAtLeast(64f)
                            }
                            ResizeHandle.BOTTOM_RIGHT -> {
                                boxWidthDp = (boxWidthDp + dxDp).coerceAtLeast(180f)
                                boxMinHeightDp = (boxMinHeightDp + dyDp).coerceAtLeast(64f)
                            }
                        }
                    },
                    onTapInsideFrame = { isSelected = true }
                )
            }
        }
    }
}

@Composable
private fun BoxScope.DashedSelectionFrame(
    onHandleDrag: (ResizeHandle, Offset) -> Unit,
    @Suppress("UNUSED_PARAMETER") onTapInsideFrame: () -> Unit,
    handleSize: Dp = 10.dp
) {
    val dashColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .matchParentSize()
            .drawDashedFrame(dashColor)
    )

    // 8 alças
    ResizeHandleBox(Modifier.align(Alignment.TopStart), handleSize) { onHandleDrag(ResizeHandle.TOP_LEFT, it) }
    ResizeHandleBox(Modifier.align(Alignment.TopCenter), handleSize) { onHandleDrag(ResizeHandle.TOP, it) }
    ResizeHandleBox(Modifier.align(Alignment.TopEnd), handleSize) { onHandleDrag(ResizeHandle.TOP_RIGHT, it) }

    ResizeHandleBox(Modifier.align(Alignment.CenterStart), handleSize) { onHandleDrag(ResizeHandle.LEFT, it) }
    ResizeHandleBox(Modifier.align(Alignment.CenterEnd), handleSize) { onHandleDrag(ResizeHandle.RIGHT, it) }

    ResizeHandleBox(Modifier.align(Alignment.BottomStart), handleSize) { onHandleDrag(ResizeHandle.BOTTOM_LEFT, it) }
    ResizeHandleBox(Modifier.align(Alignment.BottomCenter), handleSize) { onHandleDrag(ResizeHandle.BOTTOM, it) }
    ResizeHandleBox(Modifier.align(Alignment.BottomEnd), handleSize) { onHandleDrag(ResizeHandle.BOTTOM_RIGHT, it) }
}

@Composable
private fun ResizeHandleBox(
    modifier: Modifier,
    size: Dp,
    onDrag: (Offset) -> Unit
) {
    Box(
        modifier = modifier
            .offset { IntOffset((-size / 2).roundToPx(), (-size / 2).roundToPx()) }
            .size(size)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(Offset(dragAmount.x, dragAmount.y))
                }
            }
    )
}

private fun Modifier.drawDashedFrame(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(
        Modifier
            .background(androidx.compose.ui.graphics.Color.Transparent)
            .then(
                Modifier
                    .border(0.dp, color)
                    .drawWithContentCompat(color)
            )
    )

private fun Modifier.drawWithContentCompat(color: Color): Modifier =
    this.drawBehind {
        drawRect(
            color = color,
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(10f, 8f),
                    0f
                )
            )
        )
    }
private fun Float.toDpLike(): Float {
    // Conversão simples para sensação visual estável em Compose (sem acesso ao Density aqui).
    // Em telas xhdpi/xxhdpi fica boa; se quiser precisão absoluta, movemos para with(LocalDensity).
    return this / 3f
}

