package com.rjasao.nowsei.presentation.sync

data class SyncUiState(
    val isSyncing: Boolean = false,
    val step: Int = 0,            // 0 quando não iniciou, 1..3 em execução
    val totalSteps: Int = 3,
    val stepProgress: Int = 0,    // 0..100 da etapa atual
    val message: String? = null
) {
    val topProgress: Float
        get() {
            if (!isSyncing || totalSteps <= 0) return 0f
            // Barra de cima avança "por etapa" (1/3, 2/3, 3/3)
            val doneSteps = (step - 1).coerceIn(0, totalSteps)
            return doneSteps.toFloat() / totalSteps.toFloat()
        }
}
