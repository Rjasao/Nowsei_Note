package com.rjasao.nowsei.domain.repository

import com.rjasao.nowsei.domain.model.Page
import kotlinx.coroutines.flow.Flow

/**
 * Interface que define o contrato para o repositório de Páginas.
 */
interface PageRepository {

    /**
     * Busca todas as páginas que pertencem a uma seção específica.
     *
     * @param sectionId O ID da seção.
     * @return Um Flow contendo a lista de Páginas.
     */
    fun getPagesForSection(sectionId: String): Flow<List<Page>>

    /**
     * CORREÇÃO: Adicionada a assinatura da função que estava faltando.
     * Busca uma única página pelo seu ID.
     *
     * @param pageId O ID da página.
     * @return Um Flow contendo a Página ou null se não for encontrada.
     */
    fun getPageById(pageId: String): Flow<Page?>

    /**
     * Insere ou atualiza uma página no repositório.
     *
     * @param page A página a ser salva.
     */
    suspend fun upsertPage(page: Page)

    /**
     * Método que será usado pelo SyncWorker.
     * A implementação deste método pode simplesmente chamar o upsertPage.
     */
    suspend fun savePage(page: Page)

    /**
     * Deleta uma página específica.
     *
     * @param page A página a ser deletada.
     */
    suspend fun deletePage(page: Page)
}
