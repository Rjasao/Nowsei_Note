package com.rjasao.nowsei.domain.repository

import com.rjasao.nowsei.domain.model.Section
import kotlinx.coroutines.flow.Flow

/**
 * Interface que define o contrato para o repositório de Seções.
 * A camada de domínio depende desta abstração, não da implementação concreta.
 */
interface SectionRepository {

    /**
     * Busca todas as seções que pertencem a um caderno específico.
     *
     * @param notebookId O ID do caderno.
     * @return Um Flow contendo a lista de Seções (modelo de domínio).
     */
    fun getSectionsForNotebook(notebookId: String): Flow<List<Section>>

    /**
     * Insere ou atualiza uma seção no repositório.
     *
     * @param section A seção a ser salva.
     */
    suspend fun upsertSection(section: Section)

    /**
     * CORREÇÃO: Adicionando o método que o SyncWorker espera encontrar.
     * A implementação deste método pode simplesmente chamar o upsertSection.
     */
    suspend fun saveSection(section: Section)

    /**
     * Deleta uma seção específica.
     *
     * @param section A seção a ser deletada.
     */
    suspend fun deleteSection(section: Section)
}
