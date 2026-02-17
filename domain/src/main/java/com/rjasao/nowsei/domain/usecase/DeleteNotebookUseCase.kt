package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.repository.NotebookRepository
import javax.inject.Inject

/**
 * Use case para deletar um caderno.
 */
class DeleteNotebookUseCase @Inject constructor(
    private val repository: NotebookRepository
) {
    /**
     * Deleta o caderno fornecido.
     * @param notebook O caderno a ser deletado.
     */
    suspend operator fun invoke(notebook: Notebook) {
        repository.deleteNotebook(notebook)
    }
}
