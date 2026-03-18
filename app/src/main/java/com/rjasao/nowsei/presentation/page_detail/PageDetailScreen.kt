package com.rjasao.nowsei.presentation.page_detail

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.rjasao.nowsei.core.image.ImageOptimizer
import com.rjasao.nowsei.presentation.page_detail.editor.EditorFormattingState
import com.rjasao.nowsei.presentation.page_detail.editor.WordEditorController
import com.rjasao.nowsei.presentation.page_detail.editor.WordLikePageEditor
import com.rjasao.nowsei.presentation.page_detail.editor.rememberWordEditorController
import com.rjasao.nowsei.presentation.page_detail.pdf.PageHtmlPdfExporter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.UUID

private data class PendingPdfExport(
    val uri: Uri,
    val suggestedName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDetailScreen(
    navController: NavController? = null,
    viewModel: PageDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findActivity()
    val openVisitDatePicker = remember(context, state.visitDateMillis) {
        {
            val base = if (state.visitDateMillis > 0L) state.visitDateMillis else System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply { timeInMillis = base }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val selected = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    viewModel.onVisitDateChange(selected.timeInMillis)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    val editorController = rememberWordEditorController()
    var formattingState by remember { mutableStateOf(EditorFormattingState()) }

    var imageMenuExpanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var pendingCameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPdfExport by remember { mutableStateOf<PendingPdfExport?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }
    val persistLatestEditorHtml = remember(editorController, viewModel, state.htmlContent) {
        { onPersisted: (() -> Unit)? ->
            editorController.getHtml { latestHtml ->
                val htmlToPersist = latestHtml
                    .ifBlank { editorController.lastKnownHtml }
                    .ifBlank { state.htmlContent }
                viewModel.flushSaveNow(htmlToPersist, onPersisted)
            }
        }
    }

    // Modo do PDF na UI (sem API experimental)
    var pdfModeUi by rememberSaveable {
        mutableStateOf(PageHtmlPdfExporter.PdfMode.CONTINUOUS_SINGLE_PAGE)
    }

    // Galeria -> passa pelo otimizador (90KB)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val localFileUri = persistImageFromUriAsFileUri(context, uri, ImageOptimizer.Target.GALLERY_20KB)
        if (localFileUri == null) {
            Toast.makeText(context, "Falha ao importar imagem da galeria.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        editorController.insertImage(localFileUri) {
            persistLatestEditorHtml(null)
        }
    }

    // Câmera alta resolução -> passa pelo otimizador (120KB)
    val cameraFullResLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@rememberLauncherForActivityResult

        val outputUri = pendingCameraOutputUri ?: return@rememberLauncherForActivityResult

        val localFileUri = persistImageFromUriAsFileUri(context, outputUri, ImageOptimizer.Target.CAMERA_30KB)
        if (localFileUri == null) {
            Toast.makeText(context, "Falha ao otimizar imagem da câmera.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        editorController.insertImage(localFileUri) {
            persistLatestEditorHtml(null)
        }
    }

    // Permissão câmera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Permissão da câmera negada.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val outputUri = createTempCameraCaptureUri(context)
        if (outputUri == null) {
            Toast.makeText(context, "Falha ao preparar arquivo da câmera.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        pendingCameraOutputUri = outputUri
        cameraFullResLauncher.launch(outputUri)
    }

    // Scanner ML Kit -> passa pelo otimizador (180KB)
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pages = scanResult?.pages ?: emptyList()

        pages.forEach { page ->
            val localFileUri = persistImageFromUriAsFileUri(context, page.imageUri, ImageOptimizer.Target.SCAN_70KB)
            if (localFileUri != null) {
                editorController.insertImage(localFileUri)
            }
        }
        persistLatestEditorHtml(null)
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        val export = pendingPdfExport
        pendingPdfExport = null

        if (destinationUri == null || export == null) {
            isExportingPdf = false
            if (export != null) {
                Toast.makeText(context, "Salvamento do PDF cancelado.", Toast.LENGTH_SHORT).show()
            }
            return@rememberLauncherForActivityResult
        }

        val saved = copyUriContent(context, export.uri, destinationUri)
        isExportingPdf = false

        Toast.makeText(
            context,
            if (saved) "PDF salvo com sucesso." else "Falha ao salvar o PDF.",
            if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        ).show()
    }

    pendingPdfExport?.let { export ->
        AlertDialog(
            onDismissRequest = {
                pendingPdfExport = null
                isExportingPdf = false
            },
            title = { Text("PDF pronto") },
            text = { Text("Escolha se deseja enviar no WhatsApp ou salvar no celular.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sharePdf(context, export.uri, export.suggestedName, preferWhatsApp = true)
                        pendingPdfExport = null
                        isExportingPdf = false
                    }
                ) {
                    Text("WhatsApp")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        savePdfLauncher.launch(export.suggestedName)
                    }
                ) {
                    Text("Salvar no celular")
                }
            }
        )
    }

    DisposableEffect(lifecycleOwner, editorController, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                persistLatestEditorHtml(null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = !state.isLoading) {
        persistLatestEditorHtml {
            navController?.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Página") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        persistLatestEditorHtml {
                            navController?.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { imageMenuExpanded = true },
                            enabled = !state.isLoading
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = "Inserir imagem")
                        }

                        DropdownMenu(
                            expanded = imageMenuExpanded,
                            onDismissRequest = { imageMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Galeria") },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                onClick = {
                                    imageMenuExpanded = false
                                    pickImageLauncher.launch("image/*")
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Câmera (alta resolução)") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    imageMenuExpanded = false
                                    openCameraWithPermission(
                                        context = context,
                                        onAlreadyGranted = {
                                            val outputUri = createTempCameraCaptureUri(context)
                                            if (outputUri == null) {
                                                Toast.makeText(
                                                    context,
                                                    "Falha ao preparar arquivo da câmera.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@openCameraWithPermission
                                            }
                                            pendingCameraOutputUri = outputUri
                                            cameraFullResLauncher.launch(outputUri)
                                        },
                                        onRequestPermission = {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Scan (auto+manual)") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
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
                                        .addOnFailureListener {
                                            Toast.makeText(context, "Falha ao abrir scanner.", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { exportMenuExpanded = true },
                            enabled = !state.isLoading && !isExportingPdf
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Exportar PDF")
                        }

                        DropdownMenu(
                            expanded = exportMenuExpanded,
                            onDismissRequest = { exportMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Salvar PDF continuo") },
                                onClick = {
                                    exportMenuExpanded = false
                                    pdfModeUi = PageHtmlPdfExporter.PdfMode.CONTINUOUS_SINGLE_PAGE
                                    exportCurrentPageAsPdf(
                                        editorController = editorController,
                                        state = state,
                                        selectedMode = pdfModeUi,
                                        onHtmlCaptured = viewModel::onHtmlContentChange,
                                        context = context,
                                        viewportWidthPx = with(density) {
                                            (context.resources.displayMetrics.widthPixels - 24.dp.roundToPx())
                                                .coerceAtLeast(1)
                                        },
                                        onExportingChange = { isExportingPdf = it },
                                        onPdfReady = { pdfUri, suggestedName ->
                                            pendingPdfExport = PendingPdfExport(
                                                uri = pdfUri,
                                                suggestedName = suggestedName
                                            )
                                            isExportingPdf = false
                                        }
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Salvar PDF A4 paginado") },
                                onClick = {
                                    exportMenuExpanded = false
                                    pdfModeUi = PageHtmlPdfExporter.PdfMode.PAGINATED_A4
                                    exportCurrentPageAsPdf(
                                        editorController = editorController,
                                        state = state,
                                        selectedMode = pdfModeUi,
                                        onHtmlCaptured = viewModel::onHtmlContentChange,
                                        context = context,
                                        viewportWidthPx = with(density) {
                                            (context.resources.displayMetrics.widthPixels - 24.dp.roundToPx())
                                                .coerceAtLeast(1)
                                        },
                                        onExportingChange = { isExportingPdf = it },
                                        onPdfReady = { pdfUri, suggestedName ->
                                            pendingPdfExport = PendingPdfExport(
                                                uri = pdfUri,
                                                suggestedName = suggestedName
                                            )
                                            isExportingPdf = false
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Carregando página...")
                }
                return@Column
            }

            EditorFormatToolbar(
                formattingState = formattingState,
                onBold = { editorController.bold() },
                onItalic = { editorController.italic() },
                onUnderline = { editorController.underline() },
                onBullets = { editorController.unorderedList() },
                onNumbers = { editorController.orderedList() },
                onDecreaseFont = { editorController.decreaseFontSize() },
                onIncreaseFont = { editorController.increaseFontSize() },
                onAlignLeft = { editorController.justifyLeft() },
                onAlignCenter = { editorController.justifyCenter() },
                onAlignRight = { editorController.justifyRight() },
                onAlignJustify = { editorController.justifyFull() },
                onTextColorSelected = { editorController.setTextColor(it) },
                onParagraph = { editorController.formatParagraph() },
                onH2 = { editorController.formatH2() },
                onH3 = { editorController.formatH3() },
                onUndo = { editorController.undo() },
                onRedo = { editorController.redo() },
                onInsertDateTime = {
                    editorController.insertCurrentDateTimePtBr()
                    editorController.requestHtml()
                },
                onInsertDivider = {
                    editorController.insertSeparator()
                    editorController.requestHtml()
                },
                onInsertVistoria = {
                    editorController.insertVistoriaTemplate()
                    editorController.requestHtml()
                },
                onInsertAssinatura = {
                    editorController.insertAssinaturaTemplate()
                    editorController.requestHtml()
                }
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp
            ) {
                WordLikePageEditor(
                    modifier = Modifier.fillMaxSize(),
                    title = state.title,
                    visitDate = state.visitDate,
                    initialHtml = state.htmlContent,
                    controller = editorController,
                    onTitleChanged = viewModel::onTitleChange,
                    onHtmlChanged = viewModel::onHtmlContentChange,
                    onFormattingChanged = { formattingState = it },
                    onVisitDateLongPressed = openVisitDatePicker
                )
            }

            state.lastModified?.let { last ->
                Text(
                    text = if (isExportingPdf) "Exportando PDF..." else "Salvo em: $last",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditorFormatToolbar(
    formattingState: EditorFormattingState,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onBullets: () -> Unit,
    onNumbers: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onAlignLeft: () -> Unit,
    onAlignCenter: () -> Unit,
    onAlignRight: () -> Unit,
    onAlignJustify: () -> Unit,
    onTextColorSelected: (String) -> Unit,
    onParagraph: () -> Unit,
    onH2: () -> Unit,
    onH3: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsertDateTime: () -> Unit,
    onInsertDivider: () -> Unit,
    onInsertVistoria: () -> Unit,
    onInsertAssinatura: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ToolbarIconButton(active = false, onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Desfazer")
                }
                ToolbarIconButton(active = false, onClick = onRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Refazer")
                }
            }

            ToolbarDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ToolbarIconButton(active = formattingState.isBold, onClick = onBold) {
                    Icon(Icons.Outlined.FormatBold, contentDescription = "Negrito")
                }
                ToolbarIconButton(active = formattingState.isItalic, onClick = onItalic) {
                    Icon(Icons.Outlined.FormatItalic, contentDescription = "Italico")
                }
                ToolbarIconButton(active = formattingState.isUnderline, onClick = onUnderline) {
                    Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Sublinhado")
                }
            }

            ToolbarDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ToolbarIconButton(active = formattingState.isBulletedList, onClick = onBullets) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Lista")
                }
                ToolbarIconButton(active = formattingState.isNumberedList, onClick = onNumbers) {
                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Lista numerada")
                }
            }

            ToolbarDivider()

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = false,
                    onClick = onDecreaseFont,
                    label = { Text("A-") }
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(fontSizeLabel(formattingState.fontSizeLevel)) }
                )
                FilterChip(
                    selected = false,
                    onClick = onIncreaseFont,
                    label = { Text("A+") }
                )
            }

            ToolbarDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = formattingState.textAlign == "left",
                    onClick = onAlignLeft,
                    label = { Text("Esq") }
                )
                FilterChip(
                    selected = formattingState.textAlign == "center",
                    onClick = onAlignCenter,
                    label = { Text("Centro") }
                )
                FilterChip(
                    selected = formattingState.textAlign == "right",
                    onClick = onAlignRight,
                    label = { Text("Dir") }
                )
                FilterChip(
                    selected = formattingState.textAlign == "justify",
                    onClick = onAlignJustify,
                    label = { Text("Just") }
                )
            }

            ToolbarDivider()

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.FormatColorText,
                    contentDescription = "Cor do texto",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                editorTextColors().forEach { colorHex ->
                    EditorColorButton(
                        colorHex = colorHex,
                        selected = formattingState.textColor.equals(colorHex, ignoreCase = true),
                        onClick = { onTextColorSelected(colorHex) }
                    )
                }
            }

            ToolbarDivider()

            AssistChip(
                onClick = onParagraph,
                label = { Text("P") },
                leadingIcon = { Icon(Icons.Outlined.Title, contentDescription = null) },
                enabled = formattingState.blockTag != "p"
            )
            AssistChip(onClick = onH2, label = { Text("H2") }, enabled = formattingState.blockTag != "h2")
            AssistChip(onClick = onH3, label = { Text("H3") }, enabled = formattingState.blockTag != "h3")

            ToolbarDivider()

            AssistChip(onClick = onInsertDateTime, label = { Text("Data") })
            AssistChip(onClick = onInsertDivider, label = { Text("Linha") })
            AssistChip(onClick = onInsertVistoria, label = { Text("Vistoria") })
            AssistChip(onClick = onInsertAssinatura, label = { Text("Assinatura") })
        }
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(
        modifier = Modifier.height(28.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun ToolbarIconButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = if (active) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
        content = content
    )
}

@Composable
private fun EditorColorButton(
    colorHex: String,
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp = 30.dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.small
            )
            .clip(MaterialTheme.shapes.small),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = parseComposeColor(colorHex),
            contentColor = Color.Transparent
        )
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
        )
    }
}

private fun editorTextColors(): List<String> = listOf(
    "#1f2937",
    "#0f766e",
    "#1d4ed8",
    "#b45309",
    "#b91c1c"
)

private fun fontSizeLabel(level: Int): String = when (level) {
    1 -> "12"
    2 -> "14"
    3 -> "16"
    4 -> "18"
    5 -> "22"
    6 -> "26"
    7 -> "32"
    else -> "16"
}

private fun parseComposeColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrElse {
        Color(0xFFE5E7EB)
    }
}

private fun exportCurrentPageAsPdf(
    editorController: WordEditorController,
    state: PageDetailUiState,
    selectedMode: PageHtmlPdfExporter.PdfMode,
    onHtmlCaptured: (String) -> Unit,
    context: Context,
    viewportWidthPx: Int,
    onExportingChange: (Boolean) -> Unit,
    onPdfReady: (Uri, String) -> Unit
) {
    onExportingChange(true)

    val exporterMode = when (selectedMode) {
        PageHtmlPdfExporter.PdfMode.CONTINUOUS_SINGLE_PAGE -> PageHtmlPdfExporter.PdfMode.CONTINUOUS_SINGLE_PAGE
        PageHtmlPdfExporter.PdfMode.PAGINATED_A4 -> PageHtmlPdfExporter.PdfMode.PAGINATED_A4
        else -> PageHtmlPdfExporter.PdfMode.AUTO
    }

    editorController.getHtml { latestHtml ->
        val htmlToExport = latestHtml.ifBlank { state.htmlContent }

        if (latestHtml.isNotBlank()) {
            onHtmlCaptured(latestHtml)
        }

        PageHtmlPdfExporter.exportHtmlToPdf(
            context = context,
            pageTitle = state.title,
            visitDate = state.visitDate,
            htmlBodyContent = htmlToExport,
            viewportWidthPx = viewportWidthPx,
            mode = exporterMode,
            onSuccess = { pdfUri ->
                onPdfReady(pdfUri, buildPdfFileName(state.title, exporterMode))
            },
            onError = { err ->
                onExportingChange(false)
                Toast.makeText(
                    context,
                    "Falha ao exportar PDF: ${err.message ?: "erro desconhecido"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

private fun buildPdfFileName(
    title: String,
    mode: PageHtmlPdfExporter.PdfMode
): String {
    val suffix = when (mode) {
        PageHtmlPdfExporter.PdfMode.PAGINATED_A4 -> "_A4"
        else -> "_continuo"
    }
    val safeTitle = title
        .trim()
        .ifBlank { "pagina" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), "_")
    return "$safeTitle$suffix.pdf"
}

private fun copyUriContent(context: Context, source: Uri, target: Uri): Boolean {
    return runCatching {
        context.contentResolver.openInputStream(source).useInputStream { input ->
            context.contentResolver.openOutputStream(target, "w").useOutputStream { output ->
                input.copyTo(output)
            }
        }
        true
    }.getOrDefault(false)
}

private fun sharePdf(
    context: Context,
    pdfUri: Uri,
    suggestedName: String,
    preferWhatsApp: Boolean
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        putExtra(Intent.EXTRA_SUBJECT, suggestedName.removeSuffix(".pdf"))
        putExtra(Intent.EXTRA_TITLE, suggestedName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri(suggestedName, pdfUri)
    }

    val packageManager = context.packageManager
    val targetIntent = if (preferWhatsApp) {
        Intent(shareIntent).apply { `package` = "com.whatsapp" }
            .takeIf { it.resolveActivity(packageManager) != null }
            ?: shareIntent
    } else {
        shareIntent
    }

    val chooser = Intent.createChooser(targetIntent, "Enviar PDF").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "Nenhum app encontrado para enviar o PDF.", Toast.LENGTH_SHORT).show()
    }
}

private inline fun <T> InputStream?.useInputStream(block: (InputStream) -> T): T {
    requireNotNull(this)
    return use(block)
}

private inline fun <T> OutputStream?.useOutputStream(block: (OutputStream) -> T): T {
    requireNotNull(this)
    return use(block)
}

private fun openCameraWithPermission(
    context: Context,
    onAlreadyGranted: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (granted) onAlreadyGranted() else onRequestPermission()
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun createTempCameraCaptureUri(context: Context): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()
}

private fun persistImageFromUriAsFileUri(
    context: Context,
    uri: Uri,
    target: ImageOptimizer.Target
): String? {
    return runCatching {
        val outUri = ImageOptimizer.persistOptimizedJpeg(
            context = context,
            sourceUri = uri,
            target = target
        )
        outUri?.toString()

    }.getOrNull()
}
