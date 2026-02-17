package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case para obter uma página específica pelo seu ID.
 * Este caso de uso encapsula a lógica de negócio para buscar uma página do repositório.
 */
class GetPageByIdUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {
    /**
     * Executa o caso de uso.
     * @param pageId O ID da página a ser buscada.
     * @return Um Flow que emite a Page ou null se não for encontrada.
     */
    operator fun invoke(pageId: String): Flow<Page?> {
        return pageRepository.getPageById(pageId)
    }
}
