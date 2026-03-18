package com.rjasao.nowsei.presentation.section_detail

import android.app.Application
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.usecase.AddPageUseCase
import com.rjasao.nowsei.domain.usecase.DeletePageUseCase
import com.rjasao.nowsei.domain.usecase.GetPagesUseCase
import com.rjasao.nowsei.domain.usecase.UpsertPageUseCase
import com.rjasao.nowsei.presentation.section_detail.SectionDetailContract.DialogState
import com.rjasao.nowsei.presentation.section_detail.SectionDetailContract.Event
import com.rjasao.nowsei.presentation.section_detail.SectionDetailContract.State
import com.rjasao.nowsei.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SectionDetailViewModel @Inject constructor(
    private val getPagesUseCase: GetPagesUseCase,
    private val addPageUseCase: AddPageUseCase,
    private val upsertPageUseCase: UpsertPageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _dialogState = MutableStateFlow<DialogState?>(null)
    val dialogState = _dialogState.asStateFlow()

    private val _uiEventChannel = Channel<UiEvent>()
    val uiEvent = _uiEventChannel.receiveAsFlow()

    sealed interface UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent
    }

    private val workManager = WorkManager.getInstance(application)
    private var loadPagesJob: Job? = null
    private var syncObserver: Observer<List<WorkInfo>>? = null

    init {
        val sectionId: String = savedStateHandle["sectionId"] ?: ""
        val sectionTitle: String = savedStateHandle["sectionTitle"] ?: "Secao"

        _state.update { it.copy(sectionId = sectionId, sectionTitle = sectionTitle) }

        if (sectionId.isNotBlank()) {
            loadPages(sectionId)
        }
        observeSyncWorker()
    }

    override fun onCleared() {
        super.onCleared()
        syncObserver?.let {
            workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME)
                .removeObserver(it)
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.OnAddPageClick -> addPage()
            is Event.OnDuplicatePageClick -> duplicatePage(event.page)
            is Event.OnDeletePageClick -> _dialogState.value = DialogState.Delete(event.page)
            is Event.OnEditPageClick -> _dialogState.value = DialogState.Rename(event.page, event.page.title)
            Event.OnConfirmDialog -> handleConfirmDialog()
            Event.OnDismissDialog -> _dialogState.value = null
            is Event.OnDialogTitleChanged -> handleDialogTitleChange(event.newTitle)
            Event.OnToggleSearch -> toggleSearch()
            is Event.OnSearchQueryChanged -> onSearchQueryChanged(event.query)
            Event.OnSyncClick -> triggerSync()
            is Event.OnMovePage -> onMovePage(event.from, event.to)
        }
    }

    private fun loadPages(sectionId: String) {
        loadPagesJob?.cancel()

        loadPagesJob = getPagesUseCase(sectionId)
            .onEach { pages ->
                _state.update {
                    it.copy(
                        pages = pages,
                        isLoading = false
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun onMovePage(from: Int, to: Int) {
        if (state.value.searchQuery.isNotBlank() || state.value.isSearchActive) return

        val currentPages = state.value.pages.toMutableList()
        if (from !in currentPages.indices || to !in currentPages.indices) return
        if (from == to) return

        val moved = currentPages.removeAt(from)
        currentPages.add(to, moved)

        _state.update { it.copy(pages = currentPages) }

        viewModelScope.launch {
            currentPages.forEach {
                upsertPageUseCase(it)
            }
        }
    }

    private fun addPage() {
        viewModelScope.launch {
            val sectionId = state.value.sectionId
            if (sectionId.isBlank()) {
                _uiEventChannel.send(UiEvent.ShowSnackbar("Secao invalida."))
                return@launch
            }

            runCatching {
                addPageUseCase(
                    sectionId = sectionId,
                    title = "Nova Pagina"
                )
            }.onFailure {
                _uiEventChannel.send(UiEvent.ShowSnackbar("Nao foi possivel criar a pagina."))
            }
        }
    }

    private fun duplicatePage(page: Page) {
        viewModelScope.launch {
            val duplicated = page.copy(
                id = UUID.randomUUID().toString(),
                title = "${page.title} (copia)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            upsertPageUseCase(duplicated)
        }
    }

    private fun renamePage(page: Page, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isBlank()) {
                _uiEventChannel.send(UiEvent.ShowSnackbar("O titulo nao pode ficar em branco."))
                return@launch
            }

            upsertPageUseCase(
                page.copy(
                    title = newTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun deletePage(page: Page) {
        viewModelScope.launch {
            deletePageUseCase(page)
        }
    }

    private fun toggleSearch() {
        _state.update { it.copy(isSearchActive = !it.isSearchActive) }
    }

    private fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    private fun handleDialogTitleChange(newTitle: String) {
        val current = _dialogState.value
        if (current is DialogState.Rename) {
            _dialogState.value = current.copy(newTitle = newTitle)
        }
    }

    private fun handleConfirmDialog() {
        when (val current = _dialogState.value) {
            is DialogState.Delete -> deletePage(current.page)
            is DialogState.Rename -> renamePage(current.page, current.newTitle)
            null -> Unit
        }
        _dialogState.value = null
    }

    private fun observeSyncWorker() {
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME)

        val observer = Observer<List<WorkInfo>> { infos ->
            val info = infos.firstOrNull() ?: return@Observer
            val running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
            _state.update { it.copy(isSyncing = running) }
        }

        syncObserver = observer
        liveData.observeForever(observer)
    }

    private fun triggerSync() {
        viewModelScope.launch {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniqueWork(
                SyncWorker.WORKER_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
