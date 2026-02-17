package com.rjasao.nowsei.presentation.notebook_detail

import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Section

class NotebookDetailContract {

    data class State(
        val notebook: Notebook? = null,
        val sections: List<Section> = emptyList(),
        val isLoading: Boolean = false
       )

    sealed interface Event {
        data class OnSectionClick(val section: Section) : Event

        // --- NOVOS EVENTOS PARA O DIÁLOGO DE SEÇÕES ---

        // Evento para quando o usuário clica no botão "+" para CRIAR uma nova seção
        object OnAddSectionClick : Event

        // Evento para quando o usuário quer EDITAR uma seção existente
        data class OnEditSectionClick(val section: Section) : Event

        // Evento para quando o usuário fecha o diálogo sem salvar
        object OnDismissDialog : Event

        // Evento para salvar (criar ou atualizar) a seção com o novo nome
        // 1. CORRIGIDO: O tipo do ID foi alterado de Long? para String?
        data class OnSaveSection(val id: String?, val title: String) : Event

        // Evento para deletar uma seção
        data class OnDeleteSection(val section: Section) : Event
    }

    sealed interface Effect {
        // 2. CORRIGIDO: O tipo do ID foi alterado de Long para String.
        // 3. ADICIONADO: O título da seção foi incluído para a tela de destino.
        data class NavigateToPages(val sectionId: String, val sectionTitle: String) : Effect
    }
}
