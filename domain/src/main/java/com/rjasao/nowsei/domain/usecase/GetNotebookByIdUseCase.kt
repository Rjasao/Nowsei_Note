package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.repository.NotebookRepository
import javax.inject.Inject

/**
 * Use case para obter um caderno específico pelo seu ID.
 */
class GetNotebookByIdUseCase @Inject constructor(
    private val repository: NotebookRepository
) {
    /**
     * Busca um caderno pelo seu ID.
     * @param id O ID do caderno a ser buscado.
     * @return O [Notebook] encontrado ou `null` se não existir nenhum com o ID fornecido.
     */
    // 1. CORRIGIDO: O parâmetro 'id' agora é do tipo String para alinhar com o repositório.
    suspend operator fun invoke(id: String): Notebook? {
        return repository.getNotebookById(id)
    }
}
