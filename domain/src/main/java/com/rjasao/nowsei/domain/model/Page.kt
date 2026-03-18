package com.rjasao.nowsei.domain.model

data class Page(
    val id: String,
    val sectionId: String,
    val title: String,
    /** Conteúdo em blocos (texto, imagem, etc.). */
    val contentBlocks: List<ContentBlock> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Modelo de conteúdo por blocos (estilo OneNote):
 * uma página é uma sequência ordenada de blocos (texto/imagem...).
 */
sealed class ContentBlock {
    abstract val id: String
    /** Ordem visual do bloco na página. */
    abstract val order: Int

    data class TextBlock(
        override val id: String,
        override val order: Int,
        val text: String,
        val fontSize: Float = 16f,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    ) : ContentBlock()

    data class ImageBlock(
        override val id: String,
        override val order: Int,
        /** URI local (content://) ou caminho/URL. */
        val imageUrl: String,
        val thumbnailUrl: String? = null,
        val caption: String? = null,
        val width: Int? = null,
        val height: Int? = null
    ) : ContentBlock()

    // Futuro: InkBlock, ChecklistBlock, TableBlock, etc.
}
