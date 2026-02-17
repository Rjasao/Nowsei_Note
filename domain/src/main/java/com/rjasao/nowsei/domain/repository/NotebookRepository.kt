package com.rjasao.nowsei.domain.repository

import com.rjasao.nowsei.domain.model.Notebook
import kotlinx.coroutines.flow.Flow

interface NotebookRepository {

    fun getAllNotebooks(): Flow<List<Notebook>>

    suspend fun getNotebookById(id: String): Notebook?

    // Método que já existe, pode ser usado internamente pela implementação.
    suspend fun upsertNotebook(notebook: Notebook): Boolean

    suspend fun deleteNotebook(notebook: Notebook)

    // CORREÇÃO: Adicionando o método que o SyncWorker espera encontrar.
    // A implementação deste método pode simplesmente chamar o upsertNotebook.
    suspend fun saveNotebook(notebook: Notebook)

    suspend fun getNotebookIdForSection(sectionId: String): String?
}
