package com.rjasao.nowsei.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Modelo persistível da caixa de texto no canvas.
 * - seleção é transitória (não salvar em banco/arquivo)
 * - width/height são da caixa, não alteram a fonte
 */
data class CanvasTextBoxModel(
    val id: String,
    val text: String = "",
    val x: Float = 32f,
    val y: Float = 32f,
    val width: Float = 320f,
    val height: Float = 120f
)

private enum class CanvasResizeHandle {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
}

@Composable
fun BoxScope.CanvasTextBox(
    model: CanvasTextBoxModel,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onChange: (CanvasTextBoxModel) -> Unit,
    onTextChange: (String) -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
) {
    val minW = 120f
    val minH = 56f
    val handleSize = 12.dp

    val textValueState = remember(model.id) { mutableStateOf(TextFieldValue(model.text)) }
    if (textValueState.value.text != model.text) {
        textValueState.value = textValueState.value.copy(text = model.text)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(model.x.roundToInt(), model.y.roundToInt()) }
            .size(model.width.dp, model.height.dp)
            .pointerInput(model.id) {
                detectTapGestures(
                    onLongPress = { onSelect() }
                )
            }
            .pointerInput(model.id, isSelected) {
                if (!isSelected) return@pointerInput
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onChange(
                            model.copy(
                                x = model.x + dragAmount.x,
                                y = model.y + dragAmount.y
                            )
                        )
                    }
                )
            }
            .drawBehind {
                if (!isSelected) return@drawBehind
                val strokeWidth = 1.5.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                drawRect(
                    color = Color.Gray,
                    topLeft = Offset.Zero,
                    size = size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        pathEffect = dash
                    )
                )
            }
    ) {
        BasicTextField(
            value = textValueState.value,
            onValueChange = { v ->
                textValueState.value = v
                onTextChange(v.text)
            },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(model.id) {
                    detectTapGestures(onTap = { /* entra em edição sem selecionar */ })
                }
        )

        if (isSelected) {
            val handles = listOf(
                CanvasResizeHandle.TOP_LEFT to Alignment.TopStart,
                CanvasResizeHandle.TOP to Alignment.TopCenter,
                CanvasResizeHandle.TOP_RIGHT to Alignment.TopEnd,
                CanvasResizeHandle.LEFT to Alignment.CenterStart,
                CanvasResizeHandle.RIGHT to Alignment.CenterEnd,
                CanvasResizeHandle.BOTTOM_LEFT to Alignment.BottomStart,
                CanvasResizeHandle.BOTTOM to Alignment.BottomCenter,
                CanvasResizeHandle.BOTTOM_RIGHT to Alignment.BottomEnd
            )

            handles.forEach { (handle, align) ->
                Box(
                    modifier = Modifier
                        .align(align)
                        .offset(
                            x = when (align) {
                                Alignment.TopStart, Alignment.CenterStart, Alignment.BottomStart -> (-6).dp
                                Alignment.TopEnd, Alignment.CenterEnd, Alignment.BottomEnd -> 6.dp
                                else -> 0.dp
                            },
                            y = when (align) {
                                Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd -> (-6).dp
                                Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> 6.dp
                                else -> 0.dp
                            }
                        )
                        .size(handleSize)
                        .background(Color.White)
                        .drawBehind {
                            drawRect(Color.Gray)
                        }
                        .pointerInput(model.id, handle) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val updated = resizeModel(model, handle, dragAmount.x, dragAmount.y, minW, minH)
                                onChange(updated)
                            }
                        }
                )
            }
        }
    }
}

private fun resizeModel(
    m: CanvasTextBoxModel,
    h: CanvasResizeHandle,
    dx: Float,
    dy: Float,
    minW: Float,
    minH: Float
): CanvasTextBoxModel {
    var x = m.x
    var y = m.y
    var w = m.width
    var hgt = m.height

    fun left(delta: Float) {
        val newW = (w - delta).coerceAtLeast(minW)
        val applied = w - newW
        x += applied
        w = newW
    }

    fun right(delta: Float) {
        w = (w + delta).coerceAtLeast(minW)
    }

    fun top(delta: Float) {
        val newH = (hgt - delta).coerceAtLeast(minH)
        val applied = hgt - newH
        y += applied
        hgt = newH
    }

    fun bottom(delta: Float) {
        hgt = (hgt + delta).coerceAtLeast(minH)
    }

    when (h) {
        CanvasResizeHandle.TOP_LEFT -> { left(dx); top(dy) }
        CanvasResizeHandle.TOP -> top(dy)
        CanvasResizeHandle.TOP_RIGHT -> { right(dx); top(dy) }
        CanvasResizeHandle.LEFT -> left(dx)
        CanvasResizeHandle.RIGHT -> right(dx)
        CanvasResizeHandle.BOTTOM_LEFT -> { left(dx); bottom(dy) }
        CanvasResizeHandle.BOTTOM -> bottom(dy)
        CanvasResizeHandle.BOTTOM_RIGHT -> { right(dx); bottom(dy) }
    }

    return m.copy(x = x, y = y, width = w, height = hgt)
}
