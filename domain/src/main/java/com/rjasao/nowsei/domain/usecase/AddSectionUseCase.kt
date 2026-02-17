package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.repository.SectionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Caso de uso para adicionar uma nova seção a um caderno.
 */
class AddSectionUseCase @Inject constructor(
    private val sectionRepository: SectionRepository
) {
    /**
     * Executa o caso de uso.
     * @param notebookId O ID do caderno ao qual a nova seção pertencerá.
     */
    // 1. CORRIGIDO: O tipo do parâmetro foi alterado de Long para String.
    suspend operator fun invoke(notebookId: String) {
        // Pega a data e hora atual como um objeto Date. [1, 2, 5]
        val now = Date()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(now)

        val newSection = Section(
            // O erro de tipo aqui agora está resolvido.
            notebookId = notebookId,
            title = "Seção criada às $formattedDate",
            content = "",
            lastModifiedAt = now
        )
        sectionRepository.upsertSection(newSection)
    }
}
