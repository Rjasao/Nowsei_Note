package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.repository.NotebookRepository
import javax.inject.Inject

/**
 * Use case para salvar (criar ou atualizar) um caderno.
 */
class SaveNotebookUseCase @Inject constructor(
    private val repository: NotebookRepository
) {
    /**
     * Valida e então instrui o repositório a salvar o caderno.
     */
    @Throws(InvalidNotebookException::class)
    suspend operator fun invoke(notebook: Notebook) {
        if (notebook.title.isBlank()) {
            throw InvalidNotebookException("O título do caderno não pode ser vazio.")
        }
        // A única responsabilidade do UseCase é chamar o repositório.
        // Ele não sabe se o salvamento é local, remoto ou ambos.
        repository.upsertNotebook(notebook)
    }
}

/**
 * Exceção customizada para ser lançada quando um caderno é considerado inválido.
 */
class InvalidNotebookException(message: String) : Exception(message)
