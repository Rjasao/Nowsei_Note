package com.rjasao.nowsei.presentation.page_detail

import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.rjasao.nowsei.presentation.page_detail.pdf.PagePdfExporter
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import androidx.compose.material3.ExperimentalMaterial3Api



/**
 * ✅ Editor estilo OneNote (primeiro passo):
 * - Conteúdo em blocos (texto + imagens) no mesmo fluxo
 * - Imagens “editáveis” (remover, mover pra cima/baixo, tamanho, legenda)
 * - Exportar PDF e compartilhar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDetailScreen(
    navController: NavController? = null,
    viewModel: PageDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val activity = context.findActivity()
    var imageMenuExpanded by remember { mutableStateOf(false) }

    // Picker de imagem
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.addImageFromUri(uri)
    }

    // Scanner (auto + manual crop/perspectiva)
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val imageUris = scanResult?.pages?.mapNotNull { it.imageUri } ?: emptyList()
            if (imageUris.isNotEmpty()) viewModel.addImagesFromUris(imageUris)
        }
    }

    // Garante flush ao sair da tela
    DisposableEffect(Unit) {
        onDispose { viewModel.flushSaveNow() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Página") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.addTextBlockAfterFocus() },
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Novo bloco de texto")
                    }
                    Box {
                        IconButton(
                            onClick = { imageMenuExpanded = true },
                            enabled = !state.isLoading
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = "Inserir")
                        }

                        DropdownMenu(
                            expanded = imageMenuExpanded,
                            onDismissRequest = { imageMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Galeria") },
                                onClick = {
                                    imageMenuExpanded = false
                                    pickImageLauncher.launch("image/*")
                                },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) }
                            )

                            DropdownMenuItem(
                                text = { Text("Scan (auto+manual)") },
                                onClick = {
                                    imageMenuExpanded = false
                                    val act = activity ?: return@DropdownMenuItem

                                    val options = GmsDocumentScannerOptions.Builder()
                                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                                        .setGalleryImportAllowed(true)
                                        .setPageLimit(8)
                                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                                        .build()

                                    val scanner = GmsDocumentScanning.getClient(options)
                                    scanner.getStartScanIntent(act)
                                        .addOnSuccessListener { intentSender ->
                                            scanLauncher.launch(
                                                IntentSenderRequest.Builder(intentSender).build()
                                            )
                                        }
                                },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) }
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val uri = PagePdfExporter.exportToPdf(
                                context = context,
                                pageTitle = state.title,
                                blocks = state.blocks
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Compartilhar PDF"))
                        },
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Exportar PDF")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                Text("Carregando...", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título") },
                singleLine = true
            )

            // Conteúdo em blocos (texto e imagem na mesma sequência)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.blocks, key = { it.id }) { block ->
                    when (block) {
                        is PageBlockUi.TextBlock -> {
                            OutlinedTextField(
                                value = block.value,
                                onValueChange = { v: TextFieldValue -> viewModel.onTextBlockChange(block.id, v) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Texto") },
                                minLines = 3
                            )
                        }

                        is PageBlockUi.ImageBlock -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Imagem no fluxo
                                val imgModifier = when (block.sizeMode) {
                                    ImageSizeMode.FIT_WIDTH -> Modifier.fillMaxWidth()
                                    ImageSizeMode.MEDIUM -> Modifier.size(320.dp)
                                    ImageSizeMode.SMALL -> Modifier.size(220.dp)
                                }

                                AsyncImage(
                                    model = File(block.storedPath),
                                    contentDescription = "Imagem",
                                    modifier = imgModifier
                                )

                                // Ações “editáveis” básicas
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.moveBlock(block.id, up = true) }) {
                                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "Mover para cima")
                                        }
                                        IconButton(onClick = { viewModel.moveBlock(block.id, up = false) }) {
                                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "Mover para baixo")
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.cycleImageSize(block.id) }) {
                                            Icon(Icons.Outlined.Image, contentDescription = "Tamanho")
                                        }
                                        IconButton(onClick = { viewModel.removeBlock(block.id) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Remover")
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = block.caption,
                                    onValueChange = { viewModel.onImageCaptionChange(block.id, it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Legenda (opcional)") },
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(2.dp))
            state.lastModified?.let { last ->
                Text(
                    text = "Salvo em: $last",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
