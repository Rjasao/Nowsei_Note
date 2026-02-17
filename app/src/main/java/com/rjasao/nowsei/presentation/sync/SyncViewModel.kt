package com.rjasao.nowsei.presentation.sync

import android.content.Context
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rjasao.nowsei.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.roundToInt
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext appContext: Context
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(appContext)

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val observer = Observer<List<WorkInfo>> { infos ->
        val info = infos.firstOrNull()

        if (info == null) {
            _uiState.update { it.copy(isSyncing = false, step = 0, stepProgress = 0, message = null) }
            return@Observer
        }

        val running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED

        // ✅ padrão do seu projeto: PROGRESS_*
        val progress = info.progress
        val step = progress.getInt(SyncWorker.PROGRESS_STEP, 0)
        val totalSteps = progress.getInt(SyncWorker.PROGRESS_TOTAL_STEPS, 3)
        val fraction = progress.getFloat(SyncWorker.PROGRESS_FRACTION, 0f).coerceIn(0f, 1f)
        val message = progress.getString(SyncWorker.PROGRESS_MESSAGE)

        _uiState.update {
            it.copy(
                isSyncing = running,
                step = step,
                totalSteps = totalSteps,
                stepProgress = (fraction * 100f).roundToInt(),
                message = message
            )
        }

        if (info.state.isFinished) {
            val finalMsg = when (info.state) {
                WorkInfo.State.SUCCEEDED -> "Sincronizado com sucesso."
                WorkInfo.State.FAILED -> "Falha na sincronização."
                WorkInfo.State.CANCELLED -> "Sincronização cancelada."
                else -> null
            }
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    step = 0,
                    stepProgress = 0,
                    message = finalMsg ?: it.message
                )
            }
        }
    }

    init {
        workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME)
            .observeForever(observer)
    }

    fun startSync() {
        val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(SyncWorker.WORKER_NAME)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.WORKER_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun onCleared() {
        workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME)
            .removeObserver(observer)
        super.onCleared()
    }
}
