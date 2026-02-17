package com.rjasao.nowsei.presentation.notebooks

import com.rjasao.nowsei.domain.model.Notebook

/**
 * Define o "contrato" entre a UI (Screen) e a lógica (ViewModel) para a feature de Notebooks.
 * Isso segue o padrão UDF (Unidirectional Data Flow).
 */
class NotebooksContract {

    /**
     * Representa o estado da UI da tela de cadernos.
     * É uma classe imutável. A UI simplesmente observa as mudanças neste estado.
     *
     * @param notebooks A lista de cadernos a ser exibida.
     * @param isLoading Verdadeiro se os dados estiverem sendo carregados.
     * @param isAddNotebookDialogOpen Verdadeiro se o diálogo para adicionar um novo caderno deve ser exibido.
     */
    data class State(
        val notebooks: List<Notebook> = emptyList(),
        val isLoading: Boolean = false,
        val isAddNotebookDialogOpen: Boolean = false
    )

    /**
     * Representa os eventos/ações que o usuário pode disparar a partir da UI.
     * O ViewModel irá processar esses eventos para atualizar o State.
     */
    sealed interface Event {
        object OnAddNotebookClicked : Event
        object OnDismissNotebookDialog : Event
        data class OnConfirmNotebookCreation(val name: String) : Event
        data class OnDeleteNotebookClick(val notebook: Notebook) : Event
        data class OnNotebookClick(val notebook: Notebook) : Event
        // 1. NOVO: Evento disparado ao clicar no ícone de configurações.
        object OnSettingsClicked : Event
    }

    /**
     * Representa os efeitos colaterais (side effects) que o ViewModel pode enviar para a UI.
     * São eventos de consumo único, como navegação ou exibição de um Snackbar.
     */
    sealed interface Effect {
        // Efeito para navegar para a tela de detalhes de um caderno.
        data class NavigateToNotebookDetail(val notebookId: String) : Effect
        // 2. NOVO: Efeito para navegar para a tela de configurações.
        object NavigateToSettings : Effect
    }
}
