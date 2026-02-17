package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.repository.PageRepository
import javax.inject.Inject

/**
 * Use case para criar ou atualizar (upsert) uma página.
 * Ele valida os dados da página antes de solicitar a inserção ao repositório.
 */
class UpsertPageUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {
    /**
     * Executa o caso de uso.
     * @param page A página a ser salva.
     * @throws IllegalArgumentException se o título da página estiver em branco.
     */
    suspend operator fun invoke(page: Page) {
        if (page.title.isBlank()) {
            // No futuro, podemos trocar isso por um resultado de erro mais sofisticado.
            throw IllegalArgumentException("O título da página não pode estar em branco.")
        }
        pageRepository.upsertPage(page)
    }
}
