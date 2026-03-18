package com.rjasao.nowsei.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rjasao.nowsei.presentation.settings.SettingsUiState
import com.rjasao.nowsei.presentation.sync.SyncUiState
import com.rjasao.nowsei.presentation.sync.components.TwoStageSyncBars
import kotlin.math.roundToInt

@Composable
fun SyncCard(
    state: SettingsUiState,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapped = SyncUiState(
        isSyncing = state.isSyncing,
        step = state.syncStep,
        totalSteps = state.syncTotalSteps,
        stepProgress = (state.syncFraction * 100f).roundToInt(),
        message = state.syncMessage ?: state.lastMessage
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Sincronização", style = MaterialTheme.typography.titleMedium)

            Text(
                text = when {
                    state.isSyncing -> "Em andamento..."
                    !state.lastMessage.isNullOrBlank() -> state.lastMessage.orEmpty()
                    else -> "Pronto."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TwoStageSyncBars(state = mapped, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onSyncNow,
                enabled = !state.isSyncing && state.signedInAccount != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        state.signedInAccount == null -> "Conecte ao Google"
                        state.isSyncing -> "Sincronizando..."
                        else -> "Sincronizar agora"
                    }
                )
            }
        }
    }
}
