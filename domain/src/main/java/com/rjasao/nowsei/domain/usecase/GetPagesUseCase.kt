package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Page
// CORREÇÃO: Importar o repositório correto
import com.rjasao.nowsei.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Caso de uso para obter a lista de páginas de uma seção específica.
 */
class GetPagesUseCase @Inject constructor(
    // CORREÇÃO: Injetar o repositório correto
    private val pageRepository: PageRepository
) {

    /**
     * Executa o caso de uso.
     * @param sectionId O ID da seção da qual queremos obter as páginas.
     * @return Um Flow que emite a lista de páginas para a seção fornecida.
     */
    operator fun invoke(sectionId: String): Flow<List<Page>> {
        // CORREÇÃO: Usa o repositório correto para buscar as páginas
        return pageRepository.getPagesForSection(sectionId)
    }
}
