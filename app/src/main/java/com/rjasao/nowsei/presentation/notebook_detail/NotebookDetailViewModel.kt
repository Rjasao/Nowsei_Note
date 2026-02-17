package com.rjasao.nowsei.presentation.notebook_detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.usecase.DeleteSectionUseCase
import com.rjasao.nowsei.domain.usecase.GetNotebookByIdUseCase
import com.rjasao.nowsei.domain.usecase.GetSectionsUseCase
import com.rjasao.nowsei.domain.usecase.UpsertSectionUseCase
import com.rjasao.nowsei.presentation.notebook_detail.NotebookDetailContract.Effect
import com.rjasao.nowsei.presentation.notebook_detail.NotebookDetailContract.Event
import com.rjasao.nowsei.presentation.notebook_detail.NotebookDetailContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NotebookDetailViewModel @Inject constructor(
    private val getNotebookByIdUseCase: GetNotebookByIdUseCase,
    private val getSectionsUseCase: GetSectionsUseCase,
    private val upsertSectionUseCase: UpsertSectionUseCase,
    private val deleteSectionUseCase: DeleteSectionUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _effect: Channel<Effect> = Channel()
    val effect = _effect.receiveAsFlow()

    // 1. CORRIGIDO: O nome do argumento deve ser "notebookId" para corresponder ao NavGraph.kt
    private val notebookId: String = savedStateHandle.get<String>("notebookId") ?: ""

    var dialogState by mutableStateOf<DialogState?>(null)
        private set

    init {
        loadNotebookAndSections()
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.OnSectionClick -> {
                viewModelScope.launch {
                    _effect.send(Effect.NavigateToPages(event.section.id, event.section.title))
                }
            }
            Event.OnAddSectionClick -> {
                dialogState = DialogState(section = null, title = "")
            }
            is Event.OnEditSectionClick -> {
                dialogState = DialogState(section = event.section, title = event.section.title)
            }
            Event.OnDismissDialog -> {
                dialogState = null
            }
            is Event.OnSaveSection -> {
                saveSection(event.id, event.title)
            }
            is Event.OnDeleteSection -> {
                viewModelScope.launch {
                    deleteSectionUseCase(event.section)
                }
            }
        }
    }

    private fun loadNotebookAndSections() {
        if (notebookId.isBlank()) {
            _state.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            getNotebookByIdUseCase(notebookId)?.let { notebook ->
                _state.update { it.copy(notebook = notebook) }
            }

            getSectionsUseCase(notebookId)
                .onEach { sections ->
                    _state.update { it.copy(sections = sections, isLoading = false) }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun saveSection(id: String?, title: String) {
        if (notebookId.isBlank() || title.isBlank()) {
            dialogState = null
            return
        }
        viewModelScope.launch {
            val sectionToSave = Section(
                id = id ?: UUID.randomUUID().toString(),
                title = title.trim(),
                notebookId = notebookId,
                content = dialogState?.section?.content ?: "",
                lastModifiedAt = Date()
            )
            upsertSectionUseCase(sectionToSave)
            dialogState = null
        }
    }

    data class DialogState(val section: Section?, val title: String)
}
