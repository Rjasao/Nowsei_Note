package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case para obter a lista de todos os cadernos.
 *
 * Este use case atua como um intermediário entre a camada de apresentação (ViewModel)
 * e a camada de dados (Repository), expondo a funcionalidade de busca de cadernos
 * de uma forma limpa e testável.
 */
class GetNotebooksUseCase @Inject constructor(
    private val repository: NotebookRepository
) {
    /**
     * Permite que a classe seja chamada como uma função (ex: `getNotebooksUseCase()`).
     * Retorna um Flow que emite a lista de cadernos sempre que houver uma atualização.
     */
    operator fun invoke(): Flow<List<Notebook>> {
        return repository.getAllNotebooks()
    }
}
