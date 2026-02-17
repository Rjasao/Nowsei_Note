package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Section
// CORREÇÃO: Importar o repositório correto
import com.rjasao.nowsei.domain.repository.SectionRepository
import javax.inject.Inject

/**
 * Caso de uso para inserir uma nova seção ou atualizar uma existente.
 */
class UpsertSectionUseCase @Inject constructor(
    // CORREÇÃO: Injetar o repositório correto
    private val sectionRepository: SectionRepository
) {
    /**
     * Executa o caso de uso.
     * @param section A seção a ser salva.
     */
    suspend operator fun invoke(section: Section) {
        // CORREÇÃO: Usa o repositório correto para salvar a seção
        sectionRepository.upsertSection(section)
    }
}
