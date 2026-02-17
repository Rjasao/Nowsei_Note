package com.rjasao.nowsei.domain

/**
 * Representa uma única anotação no aplicativo "Nowsei".
 * Esta é a nossa entidade principal na camada de domínio.
 *
 * @param id O identificador único da anotação.
 * @param title O título da anotação.
 * @param content O conteúdo da anotação.
 * @param timestamp A data e hora em que a anotação foi criada ou modificada.
 */
data class Note(
    val id: Int,
    val title: String,
    val content: String,
    val timestamp: Long
)
