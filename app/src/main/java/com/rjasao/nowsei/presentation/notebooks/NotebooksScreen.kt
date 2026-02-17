package com.rjasao.nowsei.presentation.notebooks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.presentation.navigation.Routes
import com.rjasao.nowsei.presentation.notebooks.NotebooksContract.Effect
import com.rjasao.nowsei.presentation.notebooks.NotebooksContract.Event
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebooksScreen(
    navController: NavController,
    viewModel: NotebooksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is Effect.NavigateToNotebookDetail -> {
                    val route = Routes.notebookDetail(effect.notebookId)
                    navController.navigate(route)
                }
                is Effect.NavigateToSettings -> {
                    // 1. ATIVADO: A navegação agora está funcional.
                    navController.navigate(Routes.SETTINGS)
                }
            }
        }
    }

    if (state.isAddNotebookDialogOpen) {
        AddNotebookDialog(
            onDismissRequest = {
                viewModel.onEvent(Event.OnDismissNotebookDialog)
            },
            onConfirmation = { notebookName ->
                viewModel.onEvent(Event.OnConfirmNotebookCreation(notebookName))
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus Cadernos") },
                actions = {
                    IconButton(onClick = {
                        viewModel.onEvent(Event.OnSettingsClicked)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.onEvent(Event.OnAddNotebookClicked)
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar Caderno")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else if (state.notebooks.isEmpty()) {
                Text(
                    text = "Nenhum caderno encontrado.\nClique no botão '+' para adicionar.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.notebooks) { notebook ->
                        NotebookItem(
                            notebook = notebook,
                            onNotebookClick = { clickedNotebook ->
                                viewModel.onEvent(Event.OnNotebookClick(clickedNotebook))
                            },
                            onDeleteClick = { notebookToDelete ->
                                viewModel.onEvent(Event.OnDeleteNotebookClick(notebookToDelete))
                            }
                        )
                    }
                }
            }
        }
    }
}

// O resto do arquivo não foi alterado.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookItem(
    notebook: Notebook,
    onNotebookClick: (Notebook) -> Unit,
    onDeleteClick: (Notebook) -> Unit
) {
    ListItem(
        headlineContent = { Text(notebook.title) },
        modifier = Modifier
            .clickable { onNotebookClick(notebook) }
            .padding(horizontal = 8.dp),
        trailingContent = {
            IconButton(onClick = { onDeleteClick(notebook) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Deletar Caderno",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
fun AddNotebookDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit
) {
    var notebookName by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Criar um novo bloco de anotações",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = notebookName,
                    onValueChange = { notebookName = it },
                    label = { Text("Nome do bloco de anotações") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("CANCELAR")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirmation(notebookName) },
                        enabled = notebookName.isNotBlank()
                    ) {
                        Text("CRIAR")
                    }
                }
            }
        }
    }
}

