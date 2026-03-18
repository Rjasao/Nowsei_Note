@file:Suppress("DEPRECATION")

package com.rjasao.nowsei.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.rjasao.nowsei.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.Observer

data class SettingsUiState(
    val signedInAccount: GoogleSignInAccount? = null,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val lastMessage: String? = null,

    // Progresso da sincronização (2 barras)
    val syncStep: Int = 1,          // 1..3
    val syncTotalSteps: Int = 3,
    val syncCurrent: Int = 0,
    val syncTotal: Int = 1,
    val syncMessage: String? = null,
    val syncFraction: Float = 0f
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val googleSignInClient: GoogleSignInClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val workManager = WorkManager.getInstance(appContext)

    private val syncObserver = Observer<List<WorkInfo>> { infos ->
        // ✅ Pega o job "ativo" se existir; senão pega o mais recente
        val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()

        if (info == null) {
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    syncStep = 1,
                    syncTotalSteps = 3,
                    syncCurrent = 0,
                    syncTotal = 1,
                    syncMessage = null,
                    syncFraction = 0f
                )
            }
            return@Observer
        }

        val running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
        val progress = info.progress
        val prev = _uiState.value

        val step = progress.getInt(SyncWorker.PROGRESS_STEP, prev.syncStep).coerceIn(1, 3)
        val totalSteps = progress.getInt(SyncWorker.PROGRESS_TOTAL_STEPS, prev.syncTotalSteps).coerceAtLeast(1)
        val current = progress.getInt(SyncWorker.PROGRESS_CURRENT, prev.syncCurrent).coerceAtLeast(0)
        val total = progress.getInt(SyncWorker.PROGRESS_TOTAL, prev.syncTotal).coerceAtLeast(1)
        val message = progress.getString(SyncWorker.PROGRESS_MESSAGE) ?: prev.syncMessage
        val fraction = progress.getFloat(SyncWorker.PROGRESS_FRACTION, prev.syncFraction).coerceIn(0f, 1f)

        _uiState.update {
            it.copy(
                isSyncing = running,
                syncStep = step,
                syncTotalSteps = totalSteps,
                syncCurrent = current,
                syncTotal = total,
                syncMessage = message,
                syncFraction = fraction
            )
        }

        if (info.state.isFinished) {
            val msg = when (info.state) {
                WorkInfo.State.SUCCEEDED -> "Sincronizado com sucesso."
                WorkInfo.State.FAILED -> "Falha na sincronização."
                WorkInfo.State.CANCELLED -> "Sincronização cancelada."
                else -> null
            }

            _uiState.update {
                it.copy(
                    lastMessage = msg,
                    isSyncing = false,
                    syncMessage = null,
                    syncFraction = 0f,
                    syncCurrent = 0,
                    syncTotal = 1,
                    syncStep = 1,
                    syncTotalSteps = totalSteps
                )
            }
        }
    }

    init {
        checkCurrentUser()
        observeSyncWorker()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val account = GoogleSignIn.getLastSignedInAccount(appContext)
            _uiState.update { it.copy(signedInAccount = account, isLoading = false) }
        }
    }

    private fun observeSyncWorker() {
        workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME)
            .observeForever(syncObserver)
    }

    override fun onCleared() {
        // ✅ evita observers duplicados / vazamento
        workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORKER_NAME)
            .removeObserver(syncObserver)
        super.onCleared()
    }

    fun syncNow() {
        _uiState.update {
            it.copy(
                lastMessage = "Sincronização iniciada...",
                isSyncing = true,
                syncStep = 1,
                syncTotalSteps = 3,
                syncCurrent = 0,
                syncTotal = 1,
                syncMessage = "Preparando...",
                syncFraction = 0f
            )
        }

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(SyncWorker.WORKER_NAME)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.WORKER_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun signOut() {
        _uiState.update { it.copy(isLoading = true) }
        googleSignInClient.signOut().addOnCompleteListener {
            _uiState.update { st -> st.copy(signedInAccount = null, isLoading = false) }
        }
    }

    fun onSignInSuccess(account: GoogleSignInAccount) {
        _uiState.update { it.copy(signedInAccount = account, isLoading = false, lastMessage = "Conectado.") }
    }

    fun onSignInFailed(message: String = "Falha no login.") {
        _uiState.update { it.copy(isLoading = false, lastMessage = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(lastMessage = null) }
    }
}
