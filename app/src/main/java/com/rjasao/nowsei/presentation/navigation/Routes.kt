package com.rjasao.nowsei.presentation.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    // Rota estática para a lista de cadernos.
    const val NOTEBOOKS = "notebooks"

    // Rota estática para a tela de configurações.
    const val SETTINGS = "settings" // CORRIGIDO: Declaração correta da constante.

    // Padrão de rota para detalhes do caderno, usado no NavGraph.
    const val NOTEBOOK_DETAIL = "notebooks/{notebookId}"

    // Padrão de rota para detalhes da seção, usado no NavGraph.
    const val SECTION_DETAIL = "sections/{sectionId}?title={sectionTitle}"

    // Padrão de rota para detalhes da página, usado no NavGraph.
    const val PAGE_DETAIL = "pages/{pageId}"

    /**
     * Constrói a rota real a ser navegada para detalhes do caderno.
     */
    fun notebookDetail(notebookId: String): String = "notebooks/$notebookId"

    /**
     * Constrói a rota real para os detalhes da seção.
     */
    fun sectionDetail(sectionId: String, sectionTitle: String): String {
        val encodedTitle = URLEncoder.encode(sectionTitle, StandardCharsets.UTF_8.toString())
        return "sections/$sectionId?title=$encodedTitle"
    }

    /**
     * Constrói a rota real para os detalhes da página.
     */
    fun pageDetail(pageId: String): String = "pages/$pageId"
}
