package com.rjasao.nowsei.domain

import kotlinx.coroutines.flow.Flow

/**
 * Define o contrato para as operações de dados relacionadas às anotações.
 * A implementação real estará na camada de :data.
 */
interface NoteRepository {

    /**
     * Retorna um fluxo com todas as anotações, ordenadas por data.
     * Usamos Flow para que a UI possa observar as mudanças em tempo real.
     */
    fun getAllNotes(): Flow<List<Note>>

    /**
     * Busca uma única anotação pelo seu ID.
     * @param id O ID da anotação a ser buscada.
     */
    suspend fun getNoteById(id: Int): Note?

    /**
     * Insere uma nova anotação ou atualiza uma existente.
     * @param note A anotação a ser inserida ou atualizada.
     */
    suspend fun insertNote(note: Note)

    /**
     * Deleta uma anotação.
     * @param note A anotação a ser deletada.
     */
    suspend fun deleteNote(note: Note)
}
