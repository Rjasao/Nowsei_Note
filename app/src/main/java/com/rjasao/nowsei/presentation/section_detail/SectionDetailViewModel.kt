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
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SectionDetailViewModel @Inject constructor(
    private val getPagesUseCase: GetPagesUseCase,
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

    // ✅ Para remover observer no onCleared (evita leak)
    private var syncObserver: Observer<List<WorkInfo>>? = null

    init {
        val sectionId: String = savedStateHandle["sectionId"] ?: ""
        val sectionTitle: String = savedStateHandle["sectionTitle"] ?: "Seção"

        _state.update { it.copy(sectionId = sectionId, sectionTitle = sectionTitle) }

        if (sectionId.isNotBlank()) loadPages(sectionId)

        observeSyncWorker()
    }

    override fun onCleared() {
        super.onCleared()
        syncObserver?.let {
            workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME).removeObserver(it)
        }
        syncObserver = null
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
        _state.update { it.copy(isLoading = true) }

        loadPagesJob = getPagesUseCase(sectionId)
            .onEach { pages ->
                _state.update { current ->
                    current.copy(
                        pages = pages,
                        isLoading = false
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun toggleSearch() {
        _state.update { current ->
            val newActive = !current.isSearchActive
            current.copy(
                isSearchActive = newActive,
                searchQuery = if (!newActive) "" else current.searchQuery
            )
        }
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
            null -> {}
        }
        _dialogState.value = null
    }

    private fun addPage() {
        viewModelScope.launch {
            val sectionId = state.value.sectionId
            if (sectionId.isBlank()) {
                _uiEventChannel.send(UiEvent.ShowSnackbar("Seção inválida."))
                return@launch
            }

            val nextPosition = (state.value.pages.maxOfOrNull { it.position } ?: -1) + 1

            val newPage = Page(
                id = UUID.randomUUID().toString(),
                sectionId = sectionId,
                title = "Nova Página",
                content = "",
                lastModifiedAt = Date(),
                position = nextPosition
            )

            upsertPageUseCase(newPage)
        }
    }

    private fun renamePage(page: Page, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isBlank()) {
                _uiEventChannel.send(UiEvent.ShowSnackbar("O título não pode ficar em branco."))
                return@launch
            }
            upsertPageUseCase(
                page.copy(
                    title = newTitle,
                    lastModifiedAt = Date()
                )
            )
        }
    }

    private fun deletePage(page: Page) {
        viewModelScope.launch {
            deletePageUseCase(page)
        }
    }

    private fun duplicatePage(page: Page) {
        viewModelScope.launch {
            val nextPosition = (state.value.pages.maxOfOrNull { it.position } ?: -1) + 1

            val duplicated = page.copy(
                id = UUID.randomUUID().toString(),
                title = "${page.title} (cópia)",
                lastModifiedAt = Date(),
                position = nextPosition
            )

            upsertPageUseCase(duplicated)
        }
    }

    private fun onMovePage(from: Int, to: Int) {
        if (state.value.searchQuery.isNotBlank() || state.value.isSearchActive) return

        val current = state.value.pages.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        if (from == to) return

        val moved = current.removeAt(from)
        current.add(to, moved)

        val reordered = current.mapIndexed { index, page ->
            page.copy(position = index, lastModifiedAt = Date())
        }

        _state.update { it.copy(pages = reordered) }

        viewModelScope.launch {
            reordered.forEach { upsertPageUseCase(it) }
        }
    }

    private fun observeSyncWorker() {
        // ✅ compatível: LiveData
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME)

        val observer = Observer<List<WorkInfo>> { infos ->
            val info = infos.firstOrNull() ?: run {
                _state.update { it.copy(isSyncing = false) }
                return@Observer
            }

            val running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
            _state.update { it.copy(isSyncing = running) }

            if (info.state.isFinished) {
                viewModelScope.launch {
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> _uiEventChannel.send(UiEvent.ShowSnackbar("Sincronização concluída."))
                        WorkInfo.State.FAILED -> _uiEventChannel.send(UiEvent.ShowSnackbar("Falha na sincronização."))
                        WorkInfo.State.CANCELLED -> _uiEventChannel.send(UiEvent.ShowSnackbar("Sincronização cancelada."))
                        else -> Unit
                    }
                }
            }
        }

        syncObserver = observer
        liveData.observeForever(observer)
    }

    private fun triggerSync() {
        viewModelScope.launch {
            if (state.value.isSyncing) {
                _uiEventChannel.send(UiEvent.ShowSnackbar("Sincronização já em andamento."))
                return@launch
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                SyncWorker.WORKER_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
