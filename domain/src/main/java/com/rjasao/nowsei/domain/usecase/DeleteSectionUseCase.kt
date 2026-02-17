package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.repository.SectionRepository
import javax.inject.Inject

/**
 * Use case para deletar uma seção.
 */
class DeleteSectionUseCase @Inject constructor(
    private val repository: SectionRepository
) {
    /**
     * Deleta a seção fornecida.
     * @param section A seção a ser deletada.
     */
    suspend operator fun invoke(section: Section) {
        repository.deleteSection(section)
    }
}
