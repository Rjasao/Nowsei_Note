package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Page
// CORREÇÃO: Importar o repositório correto
import com.rjasao.nowsei.domain.repository.PageRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Caso de uso para adicionar uma nova página a uma seção.
 */
class AddPageUseCase @Inject constructor(
    // CORREÇÃO: Injetar o repositório correto
    private val pageRepository: PageRepository
) {
    /**
     * Executa o caso de uso.
     * @param sectionId O ID da seção à qual a nova página pertencerá.
     */
    suspend operator fun invoke(sectionId: String) {
        // Pega a data e hora atual como um objeto Date
        val now = Date()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(now)

        // Cria a nova instância da página com valores padrão
        val newPage = Page(
            sectionId = sectionId,
            title = "Página criada às $formattedDate",
            content = "", // Conteúdo inicial vazio
            lastModifiedAt = now // Define a data de modificação
        )

        // CORREÇÃO: Usa o repositório correto para salvar a nova página
        pageRepository.upsertPage(newPage)
    }
}
