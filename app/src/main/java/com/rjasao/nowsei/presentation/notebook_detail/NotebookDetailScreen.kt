package com.rjasao.nowsei.presentation.notebook_detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.presentation.navigation.Routes
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookDetailScreen(
    navController: NavController,
    viewModel: NotebookDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Pega o estado do diálogo diretamente do ViewModel
    val dialogState = viewModel.dialogState

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is NotebookDetailContract.Effect.NavigateToPages -> {
                    // 1. CORRIGIDO: Usa a função Routes.sections() para construir a rota
                    // de forma segura e consistente, passando os argumentos necessários.
                    val route = Routes.sectionDetail(effect.sectionId, effect.sectionTitle)
                    navController.navigate(route)
                }
            }
        }
    }

    // --- LÓGICA DO DIÁLOGO DE SEÇÃO ---
    if (dialogState != null) {
        var currentTitle by remember { mutableStateOf(dialogState.title) }

        AlertDialog(
            onDismissRequest = { viewModel.onEvent(NotebookDetailContract.Event.OnDismissDialog) },
            title = { Text(text = if (dialogState.section == null) "Nova Seção" else "Editar Seção") },
            text = {
                OutlinedTextField(
                    value = currentTitle,
                    onValueChange = { currentTitle = it },
                    label = { Text("Título da Seção") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onEvent(NotebookDetailContract.Event.OnSaveSection(dialogState.section?.id, currentTitle))
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onEvent(NotebookDetailContract.Event.OnDismissDialog) }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.notebook?.title ?: "Carregando...") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.onEvent(NotebookDetailContract.Event.OnAddSectionClick)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Seção")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (state.sections.isEmpty()) {
                Text(
                    text = "Nenhuma seção encontrada.\nClique no botão '+' para adicionar.",
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.sections, key = { it.id }) { section ->
                        SectionItem(
                            section = section,
                            onClick = { viewModel.onEvent(NotebookDetailContract.Event.OnSectionClick(section)) },
                            onLongClick = { viewModel.onEvent(NotebookDetailContract.Event.OnEditSectionClick(section)) },
                            onDelete = { viewModel.onEvent(NotebookDetailContract.Event.OnDeleteSection(section)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionItem(
    section: Section,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Deletar Seção")
            }
        }
    }
}
