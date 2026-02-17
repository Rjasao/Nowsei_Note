package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.repository.PageRepository
import javax.inject.Inject

/**
 * Use case para deletar uma página.
 */
class DeletePageUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {
    /**
     * Executa o caso de uso.
     * @param page A página a ser deletada.
     */
    suspend operator fun invoke(page: Page) {
        pageRepository.deletePage(page)
    }
}
