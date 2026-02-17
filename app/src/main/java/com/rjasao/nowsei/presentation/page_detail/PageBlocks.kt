package com.rjasao.nowsei.presentation.page_detail

import androidx.compose.ui.text.input.TextFieldValue

/** UI blocks para edição na Página (texto + imagem no mesmo fluxo). */
sealed interface PageBlockUi {
    val id: String

    data class TextBlock(
        override val id: String,
        val value: TextFieldValue
    ) : PageBlockUi

    data class ImageBlock(
        override val id: String,
        val storedPath: String,
        val caption: String,
        val sizeMode: ImageSizeMode
    ) : PageBlockUi
}

enum class ImageSizeMode {
    FIT_WIDTH,
    MEDIUM,
    SMALL
}
