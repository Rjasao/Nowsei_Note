package com.rjasao.nowsei.presentation.page_detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.usecase.GetPageByIdUseCase
import com.rjasao.nowsei.domain.usecase.UpsertPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class PageDetailUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val pageId: String? = null,
    val lastModified: Date? = null,
    val blocks: List<PageBlockUi> = emptyList(),
    val focusedBlockId: String? = null
)

/** Estrutura persistida em Page.content (JSON). */
private data class PageDocDto(
    val v: Int = 1,
    val blocks: List<PageBlockDto> = emptyList()
)

private data class PageBlockDto(
    val id: String,
    val type: String, // "text" | "image"
    val text: String? = null,
    val path: String? = null,
    val caption: String? = null,
    val size: String? = null
)

@HiltViewModel
class PageDetailViewModel @Inject constructor(
    private val getPageByIdUseCase: GetPageByIdUseCase,
    private val upsertPageUseCase: UpsertPageUseCase,
    @ApplicationContext private val appContext: Context,
    private val gson: Gson,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PageDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        val pageId: String? = savedStateHandle["pageId"]

        if (!pageId.isNullOrBlank()) {
            _uiState.update { it.copy(pageId = pageId) }
            loadPageDetails(pageId)
        } else {
            _uiState.update {
                it.copy(isLoading = false, title = "Página não encontrada", blocks = listOf(newEmptyTextBlock()))
            }
        }
    }

    private fun loadPageDetails(pageId: String) {
        _uiState.update { it.copy(isLoading = true) }

        getPageByIdUseCase(pageId)
            .onEach { page ->
                if (page == null) {
                    _uiState.update {
                        it.copy(isLoading = false, title = "Página não encontrada", blocks = listOf(newEmptyTextBlock()))
                    }
                    return@onEach
                }

                val blocks = decodeBlocks(page.content)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = page.title,
                        pageId = page.id,
                        lastModified = page.lastModifiedAt,
                        blocks = blocks,
                        focusedBlockId = blocks.firstOrNull()?.id
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
        scheduleAutosave()
    }

    fun onTextBlockChange(blockId: String, value: TextFieldValue) {
        _uiState.update { st ->
            st.copy(
                blocks = st.blocks.map {
                    if (it is PageBlockUi.TextBlock && it.id == blockId) it.copy(value = value) else it
                },
                focusedBlockId = blockId
            )
        }
        scheduleAutosave()
    }

    fun addTextBlockAfterFocus() {
        _uiState.update { st ->
            val insertAfterId = st.focusedBlockId ?: st.blocks.lastOrNull()?.id
            val newBlock = newEmptyTextBlock()
            val newList = insertAfter(st.blocks, insertAfterId, newBlock)
            st.copy(blocks = newList, focusedBlockId = newBlock.id)
        }
        scheduleAutosave()
    }

    fun addImageFromUri(uri: Uri) {
        addImagesFromUris(listOf(uri))
    }

    /**
     * ✅ Inserção em lote (Scanner pode retornar várias páginas).
     * Regra: inserir **logo abaixo do bloco atual** (focus) e mover o foco a cada inserção.
     */
    fun addImagesFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val storedPath = copyUriToInternalStorageAsPng(uri) ?: continue

                _uiState.update { st ->
                    val insertAfterId = st.focusedBlockId ?: st.blocks.lastOrNull()?.id
                    val newBlock = PageBlockUi.ImageBlock(
                        id = UUID.randomUUID().toString(),
                        storedPath = storedPath,
                        caption = "",
                        sizeMode = ImageSizeMode.FIT_WIDTH
                    )
                    st.copy(
                        blocks = insertAfter(st.blocks, insertAfterId, newBlock),
                        focusedBlockId = newBlock.id
                    )
                }
            }
            if (uris.isNotEmpty()) scheduleAutosave()
        }
    }

    fun onImageCaptionChange(blockId: String, caption: String) {
        _uiState.update { st ->
            st.copy(
                blocks = st.blocks.map {
                    if (it is PageBlockUi.ImageBlock && it.id == blockId) it.copy(caption = caption) else it
                },
                focusedBlockId = blockId
            )
        }
        scheduleAutosave()
    }

    fun cycleImageSize(blockId: String) {
        _uiState.update { st ->
            st.copy(
                blocks = st.blocks.map {
                    if (it is PageBlockUi.ImageBlock && it.id == blockId) {
                        val next = when (it.sizeMode) {
                            ImageSizeMode.FIT_WIDTH -> ImageSizeMode.MEDIUM
                            ImageSizeMode.MEDIUM -> ImageSizeMode.SMALL
                            ImageSizeMode.SMALL -> ImageSizeMode.FIT_WIDTH
                        }
                        it.copy(sizeMode = next)
                    } else it
                },
                focusedBlockId = blockId
            )
        }
        scheduleAutosave()
    }

    fun moveBlock(blockId: String, up: Boolean) {
        _uiState.update { st ->
            val idx = st.blocks.indexOfFirst { it.id == blockId }
            if (idx == -1) return@update st

            val newIdx = if (up) idx - 1 else idx + 1
            if (newIdx !in st.blocks.indices) return@update st

            val list = st.blocks.toMutableList()
            val item = list.removeAt(idx)
            list.add(newIdx, item)
            st.copy(blocks = list, focusedBlockId = blockId)
        }
        scheduleAutosave()
    }

    fun removeBlock(blockId: String) {
        _uiState.update { st ->
            val list = st.blocks.toMutableList()
            val idx = list.indexOfFirst { it.id == blockId }
            if (idx == -1) return@update st

            val removed = list.removeAt(idx)
            if (removed is PageBlockUi.ImageBlock) {
                runCatching { File(removed.storedPath).delete() }
            }

            val safeList = if (list.isEmpty()) listOf(newEmptyTextBlock()) else list
            st.copy(blocks = safeList, focusedBlockId = safeList.getOrNull(idx)?.id ?: safeList.last().id)
        }
        scheduleAutosave()
    }

    /** Auto-save tipo OneNote (debounce). */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(650)
            savePageInternal(updateUiTimestamp = true)
        }
    }

    /** Flush ao sair da tela. */
    fun flushSaveNow() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch {
            savePageInternal(updateUiTimestamp = true)
        }
    }

    fun savePage() {
        flushSaveNow()
    }

    private suspend fun savePageInternal(updateUiTimestamp: Boolean) {
        val st = _uiState.value
        val pageId = st.pageId ?: return

        val original = getPageByIdUseCase(pageId).firstOrNull() ?: return
        val now = Date()

        val updated: Page = original.copy(
            title = st.title,
            content = encodeBlocks(st.blocks),
            lastModifiedAt = now
        )

        upsertPageUseCase(updated)
        if (updateUiTimestamp) _uiState.update { it.copy(lastModified = now) }
    }

    // -------------------------------
    // Persistência (JSON) em Page.content
    // -------------------------------

    private fun decodeBlocks(content: String): List<PageBlockUi> {
        // Compat: se for texto puro (versão antiga), vira 1 bloco de texto
        if (content.isBlank()) return listOf(newEmptyTextBlock())

        val dto = try {
            gson.fromJson(content, PageDocDto::class.java)
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }

        if (dto == null || dto.blocks.isEmpty()) {
            return listOf(PageBlockUi.TextBlock(id = UUID.randomUUID().toString(), value = TextFieldValue(content)))
        }

        val out = mutableListOf<PageBlockUi>()
        for (b in dto.blocks) {
            when (b.type) {
                "text" -> {
                    out += PageBlockUi.TextBlock(
                        id = b.id,
                        value = TextFieldValue(b.text.orEmpty())
                    )
                }

                "image" -> {
                    val path = b.path ?: continue
                    val sizeMode = when (b.size) {
                        "SMALL" -> ImageSizeMode.SMALL
                        "MEDIUM" -> ImageSizeMode.MEDIUM
                        else -> ImageSizeMode.FIT_WIDTH
                    }
                    out += PageBlockUi.ImageBlock(
                        id = b.id,
                        storedPath = path,
                        caption = b.caption.orEmpty(),
                        sizeMode = sizeMode
                    )
                }
            }
        }

        return if (out.isEmpty()) listOf(newEmptyTextBlock()) else out
    }

    private fun encodeBlocks(blocks: List<PageBlockUi>): String {
        val dtos = blocks.map { block ->
            when (block) {
                is PageBlockUi.TextBlock -> PageBlockDto(
                    id = block.id,
                    type = "text",
                    text = block.value.text
                )

                is PageBlockUi.ImageBlock -> PageBlockDto(
                    id = block.id,
                    type = "image",
                    path = block.storedPath,
                    caption = block.caption,
                    size = block.sizeMode.name
                )
            }
        }
        return gson.toJson(PageDocDto(v = 1, blocks = dtos))
    }

    // -------------------------------
    // Files
    // -------------------------------

    private fun copyUriToInternalStorageAsPng(uri: Uri): String? {
        return try {
            val resolver = appContext.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

            val dir = File(appContext.filesDir, "page_images").apply { mkdirs() }
            val outFile = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.png")

            // 1) Tenta decodificar e salvar como PNG (requisito do app)
            val bmp = decodeBitmapSafely(bytes, maxSidePx = 2200)
            if (bmp != null) {
                outFile.outputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
            } else {
                // fallback: salva bruto (pode ser JPEG). Evita perder a imagem.
                outFile.outputStream().use { out -> out.write(bytes) }
            }

            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBitmapSafely(bytes: ByteArray, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        var inSampleSize = 1
        while (maxOf(w, h) / inSampleSize > maxSidePx) inSampleSize *= 2

        val opts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    // -------------------------------
    // Helpers
    // -------------------------------

    private fun newEmptyTextBlock(): PageBlockUi.TextBlock =
        PageBlockUi.TextBlock(id = UUID.randomUUID().toString(), value = TextFieldValue(""))

    private fun <T : PageBlockUi> insertAfter(list: List<PageBlockUi>, afterId: String?, newItem: T): List<PageBlockUi> {
        if (list.isEmpty() || afterId == null) return list + newItem
        val idx = list.indexOfFirst { it.id == afterId }
        if (idx == -1) return list + newItem
        val out = list.toMutableList()
        out.add(idx + 1, newItem)
        return out
    }
}
