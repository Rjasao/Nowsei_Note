@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rjasao.nowsei.presentation.section_detail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.presentation.navigation.Routes
import com.rjasao.nowsei.presentation.section_detail.SectionDetailContract.DialogState
import com.rjasao.nowsei.presentation.section_detail.SectionDetailContract.Event
import com.rjasao.nowsei.presentation.section_detail.SectionDetailContract.State
import org.burnoutcrew.reorderable.*

@Composable
fun SectionDetailScreen(
    navController: NavController,
    viewModel: SectionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            if (event is SectionDetailViewModel.UiEvent.ShowSnackbar) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Controle dos dialogs de acao da pagina
    when (dialogState) {

        is DialogState.Delete -> {
            val current = dialogState as DialogState.Delete
            DeleteConfirmationDialog(
                pageTitle = current.page.title,
                onConfirm = { viewModel.onEvent(Event.OnConfirmDialog) },
                onDismiss = { viewModel.onEvent(Event.OnDismissDialog) }
            )
        }

        is DialogState.Rename -> {
            val current = dialogState as DialogState.Rename
            RenamePageDialog(
                currentTitle = current.newTitle,
                onTitleChange = { viewModel.onEvent(Event.OnDialogTitleChanged(it)) },
                onConfirm = { viewModel.onEvent(Event.OnConfirmDialog) },
                onDismiss = { viewModel.onEvent(Event.OnDismissDialog) }
            )
        }

        null -> Unit
    }

    SectionDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateUp = { navController.popBackStack() },
        onNavigateToPage = { pageId ->
            navController.navigate(Routes.pageDetail(pageId))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionDetailContent(
    state: State,
    snackbarHostState: SnackbarHostState,
    onEvent: (Event) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToPage: (String) -> Unit
) {

    val canReorder = !state.isSearchActive && state.searchQuery.isBlank()

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (canReorder) onEvent(Event.OnMovePage(from.index, to.index))
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(state.sectionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(Event.OnToggleSearch) }) {
                        Icon(Icons.Default.Search, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(Event.OnAddPageClick) }
            ) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->

        LazyColumn(
            state = reorderableState.listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .reorderable(reorderableState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(state.pages, key = { _, page -> page.id }) { _, page ->

                ReorderableItem(reorderableState, key = page.id) { isDragging ->

                    val elevation by animateDpAsState(
                        if (isDragging) 12.dp else 1.dp, label = ""
                    )
                    val alpha by animateFloatAsState(
                        if (isDragging) 0.7f else 1f, label = ""
                    )
                    val scale by animateFloatAsState(
                        if (isDragging) 1.02f else 1f, label = ""
                    )

                    PageItem(
                        page = page,
                        elevation = elevation,
                        cardModifier = Modifier
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                            }
                            .detectReorderAfterLongPress(reorderableState),
                        onClick = { onNavigateToPage(page.id) },
                        onRenameClick = { onEvent(Event.OnEditPageClick(page)) },
                        onDeleteClick = { onEvent(Event.OnDeletePageClick(page)) },
                        onDuplicateClick = { onEvent(Event.OnDuplicatePageClick(page)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageItem(
    page: Page,
    cardModifier: Modifier,
    elevation: Dp,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDuplicateClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(elevation),
        modifier = cardModifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        onClick = { showMenu = false; onRenameClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        onClick = { showMenu = false; onDuplicateClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDeleteClick() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    pageTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir Pagina") },
        text = { Text("Deseja excluir \"$pageTitle\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Excluir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun RenamePageDialog(
    currentTitle: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear Pagina") },
        text = {
            OutlinedTextField(
                value = currentTitle,
                onValueChange = onTitleChange,
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
