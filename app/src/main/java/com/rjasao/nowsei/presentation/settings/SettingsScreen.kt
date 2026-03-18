@file:Suppress("DEPRECATION")

package com.rjasao.nowsei.presentation.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.rjasao.nowsei.presentation.settings.components.SyncCard

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = hiltViewModel()
    val ui: SettingsUiState by vm.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // ✅ Mostra a mensagem final (sucesso/erro) e só limpa depois
    LaunchedEffect(ui.lastMessage) {
        val msg = ui.lastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg)
        vm.clearMessage()
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            vm.onSignInFailed("Login cancelado.")
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            vm.onSignInSuccess(account)
        } catch (e: Exception) {
            vm.onSignInFailed(e.message ?: "Falha no login.")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                    Text("Conta Google", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val photoUrl = ui.signedInAccount?.photoUrl?.toString()
                        if (!photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Foto do usuário",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.size(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(ui.signedInAccount?.displayName ?: "Não conectado")
                            Text(
                                ui.signedInAccount?.email ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (ui.signedInAccount == null) {
                        OutlinedButton(
                            onClick = { signInLauncher.launch(vm.googleSignInClient.signInIntent) },
                            enabled = !ui.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (ui.isLoading) "Conectando..." else "Conectar") }
                    } else {
                        OutlinedButton(
                            onClick = { vm.signOut() },
                            enabled = !ui.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (ui.isLoading) "Desconectando..." else "Desconectar") }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            SyncCard(
                state = ui,
                onSyncNow = { vm.syncNow() }
            )
        }
    }
}
