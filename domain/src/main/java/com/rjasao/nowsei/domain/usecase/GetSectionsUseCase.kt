package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Section
// CORREÇÃO: Importar o repositório correto
import com.rjasao.nowsei.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Caso de uso para obter a lista de seções de um caderno específico.
 */
class GetSectionsUseCase @Inject constructor(
    // CORREÇÃO: Injetar o repositório correto
    private val sectionRepository: SectionRepository
) {
    /**
     * Executa o caso de uso.
     * @param notebookId O ID do caderno cujas seções devem ser buscadas.
     * @return Um Flow que emite a lista de seções.
     */
    operator fun invoke(notebookId: String): Flow<List<Section>> {
        // CORREÇÃO: Usa o repositório correto para buscar as seções
        return sectionRepository.getSectionsForNotebook(notebookId)
    }
}
