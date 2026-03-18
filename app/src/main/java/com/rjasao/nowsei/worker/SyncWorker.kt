package com.rjasao.nowsei.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.rjasao.nowsei.domain.usecase.SyncAllDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncAllDataUseCase: SyncAllDataUseCase
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORKER_NAME = "SyncWorker"
        private const val TAG = "SyncWorker"

        // Chaves de progresso (usadas pela SettingsViewModel)
        const val PROGRESS_STEP = "progress_step"
        const val PROGRESS_TOTAL_STEPS = "progress_total_steps"
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_MESSAGE = "progress_message"
        const val PROGRESS_FRACTION = "progress_fraction"

        // Mantemos 3 etapas para compatibilidade com a UI existente
        private const val TOTAL_STEPS = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Etapa 1: iniciar
            setProgressCompat(
                step = 1,
                current = 0,
                total = 1,
                fraction = 0f,
                message = "Iniciando sincronizacao..."
            )

            // Etapa 2: executando (usecase faz o trabalho)
            setProgressCompat(
                step = 2,
                current = 0,
                total = 1,
                fraction = 0.1f,
                message = "Sincronizando com o Google Drive..."
            )

            syncAllDataUseCase()

            // Etapa 3: concluido
            setProgressCompat(
                step = 3,
                current = 1,
                total = 1,
                fraction = 1f,
                message = "Sincronizacao concluida."
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sincronizacao", e)
            setProgressCompat(
                step = 3,
                current = 0,
                total = 1,
                fraction = 0f,
                message = e.message ?: "Falha na sincronizacao."
            )
            Result.failure(
                Data.Builder()
                    .putString(PROGRESS_MESSAGE, e.message ?: "Falha na sincronizacao.")
                    .build()
            )
        }
    }

    private suspend fun setProgressCompat(
        step: Int,
        current: Int,
        total: Int,
        fraction: Float,
        message: String
    ) {
        val safeTotal = max(1, total)
        val safeCurrent = current.coerceIn(0, safeTotal)
        val safeFraction = fraction.coerceIn(0f, 1f)

        val data = Data.Builder()
            .putInt(PROGRESS_STEP, step.coerceIn(1, TOTAL_STEPS))
            .putInt(PROGRESS_TOTAL_STEPS, TOTAL_STEPS)
            .putInt(PROGRESS_CURRENT, safeCurrent)
            .putInt(PROGRESS_TOTAL, safeTotal)
            .putFloat(PROGRESS_FRACTION, safeFraction)
            .putString(PROGRESS_MESSAGE, message)
            .build()

        setProgress(data)
    }
}
