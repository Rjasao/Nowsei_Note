@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rjasao.nowsei.presentation.section_detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

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

    when (val currentDialog = dialogState) {
        is DialogState.Delete -> DeleteConfirmationDialog(
            pageTitle = currentDialog.page.title,
            onConfirm = { viewModel.onEvent(Event.OnConfirmDialog) },
            onDismiss = { viewModel.onEvent(Event.OnDismissDialog) }
        )

        is DialogState.Rename -> RenamePageDialog(
            currentTitle = currentDialog.newTitle,
            onTitleChange = { viewModel.onEvent(Event.OnDialogTitleChanged(it)) },
            onConfirm = { viewModel.onEvent(Event.OnConfirmDialog) },
            onDismiss = { viewModel.onEvent(Event.OnDismissDialog) }
        )

        null -> Unit
    }

    SectionDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateUp = { navController.popBackStack() },
        onNavigateToPage = { pageId -> navController.navigate(Routes.pageDetail(pageId)) }
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
    // Reordenação só quando NÃO está buscando
    val canReorder = !state.isSearchActive && state.searchQuery.isBlank()

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (canReorder) onEvent(Event.OnMovePage(from.index, to.index))
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (state.isSearchActive) {
                SearchAppBar(
                    query = state.searchQuery,
                    onQueryChange = { onEvent(Event.OnSearchQueryChanged(it)) },
                    onClose = { onEvent(Event.OnToggleSearch) }
                )
            } else {
                DefaultAppBar(
                    title = state.sectionTitle,
                    onNavigateUp = onNavigateUp,
                    onSearchClick = { onEvent(Event.OnToggleSearch) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(Event.OnAddPageClick) }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Página")
            }
        }
    ) { padding ->
        if (canReorder) {
            // ✅ Reorder ligado: gesto aplicado NO CARD INTEIRO
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
                        val elevation = animateDpAsState(
                            if (isDragging) 12.dp else 1.dp,
                            label = "elevation"
                        )
                        val alpha = animateFloatAsState(
                            if (isDragging) 0.75f else 1f,
                            label = "alpha"
                        )
                        val scale = animateFloatAsState(
                            if (isDragging) 1.02f else 1f,
                            label = "scale"
                        )

                        PageItem(
                            page = page,
                            elevation = elevation.value,
                            // ✅ translucidez + “suspenso”
                            cardModifier = Modifier
                                .graphicsLayer {
                                    this.alpha = alpha.value
                                    this.scaleX = scale.value
                                    this.scaleY = scale.value
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
        } else {
            // Busca ativa: sem reorder (índices mudam)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.filteredPages, key = { _, page -> page.id }) { _, page ->
                    PageItem(
                        page = page,
                        elevation = 1.dp,
                        cardModifier = Modifier, // sem drag
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultAppBar(
    title: String,
    onNavigateUp: () -> Unit,
    onSearchClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Buscar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    "Localizar em páginas...",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            },
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.onPrimary,
                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboardController?.hide() })
        )
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
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        // ✅ aplica drag aqui e mantém click separado (sem longclick “roubando”)
        modifier = cardModifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Arrastar",
                modifier = Modifier.padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = page.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Mais")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
private fun RenamePageDialog(
    currentTitle: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear Página") },
        text = {
            OutlinedTextField(
                value = currentTitle,
                onValueChange = onTitleChange,
                label = { Text("Título da página") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = currentTitle.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    pageTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir Página") },
        text = { Text("Tem certeza que deseja excluir \"$pageTitle\"? Esta ação não pode ser desfeita.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Excluir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
