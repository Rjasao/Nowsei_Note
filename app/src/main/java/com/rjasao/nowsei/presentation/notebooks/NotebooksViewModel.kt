package com.rjasao.nowsei.presentation.notebooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.usecase.DeleteNotebookUseCase
import com.rjasao.nowsei.domain.usecase.GetNotebooksUseCase
import com.rjasao.nowsei.domain.usecase.SaveNotebookUseCase
import com.rjasao.nowsei.presentation.notebooks.NotebooksContract.Effect
import com.rjasao.nowsei.presentation.notebooks.NotebooksContract.Event
import com.rjasao.nowsei.presentation.notebooks.NotebooksContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NotebooksViewModel @Inject constructor(
    private val getNotebooksUseCase: GetNotebooksUseCase,
    private val saveNotebookUseCase: SaveNotebookUseCase,
    private val deleteNotebookUseCase: DeleteNotebookUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _effect: Channel<Effect> = Channel()
    val effect = _effect.receiveAsFlow()

    init {
        observeNotebooks()
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.OnAddNotebookClicked -> {
                _state.update { it.copy(isAddNotebookDialogOpen = true) }
            }

            Event.OnDismissNotebookDialog -> {
                _state.update { it.copy(isAddNotebookDialogOpen = false) }
            }

            is Event.OnConfirmNotebookCreation -> {
                _state.update { it.copy(isAddNotebookDialogOpen = false) }
                saveNewNotebookAndNavigate(event.name)
            }

            is Event.OnDeleteNotebookClick -> {
                viewModelScope.launch {
                    deleteNotebookUseCase(event.notebook)
                }
            }

            is Event.OnNotebookClick -> {
                viewModelScope.launch {
                    _effect.send(Effect.NavigateToNotebookDetail(event.notebook.id))
                }
            }

            // 1. NOVO: Adicionar o case para o evento de clique nas configurações.
            Event.OnSettingsClicked -> {
                viewModelScope.launch {
                    _effect.send(Effect.NavigateToSettings)
                }
            }
        }
    }

    /**
     * Salva o caderno e, APÓS o salvamento, emite o efeito de navegação.
     */
    private fun saveNewNotebookAndNavigate(name: String) {
        if (name.isBlank()) return

        viewModelScope.launch {
            val newNotebook = Notebook(
                id = UUID.randomUUID().toString(),
                title = name.trim(),
                createdAt = Date(),
                lastModifiedAt = Date(),
                colorHex = "#FAFAFA"
            )

            saveNotebookUseCase(newNotebook)

            _effect.send(Effect.NavigateToNotebookDetail(newNotebook.id))
        }
    }

    private fun observeNotebooks() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            getNotebooksUseCase().collect { notebooks ->
                _state.update { currentState ->
                    currentState.copy(
                        notebooks = notebooks,
                        isLoading = false
                    )
                }
            }
        }
    }
}
