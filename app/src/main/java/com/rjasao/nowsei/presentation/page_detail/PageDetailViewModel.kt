package com.rjasao.nowsei.presentation.page_detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rjasao.nowsei.domain.model.ContentBlock
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.usecase.GetPageByIdUseCase
import com.rjasao.nowsei.domain.usecase.UpsertPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PageDetailViewModel @Inject constructor(
    private val getPageByIdUseCase: GetPageByIdUseCase,
    private val upsertPageUseCase: UpsertPageUseCase,
    private val pageDraftStore: PageDraftStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val TAG = "Nowsei.PageSave"
    }

    private var currentPage: Page? = null
    private var observeJob: Job? = null
    private var autoSaveJob: Job? = null
    private var draftRecovered = false
    private var lastNonEmptyHtml: String = ""
    private var contentVersion: Long = 0L
    private var lastLocalEditAt: Long = 0L

    private val _uiState = MutableStateFlow(PageDetailUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        val pageId: String = savedStateHandle["pageId"] ?: ""
        if (pageId.isNotBlank()) {
            loadPage(pageId)
        } else {
            _uiState.value = PageDetailUiState(isLoading = false)
        }
    }

    fun loadPage(pageId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            getPageByIdUseCase(pageId).collectLatest { page ->
                if (page == null) {
                    Log.w(TAG, "loadPage pageId=$pageId -> null")
                    _uiState.value = PageDetailUiState(isLoading = false)
                    return@collectLatest
                }
                if (lastLocalEditAt > 0L && page.updatedAt < lastLocalEditAt) {
                    Log.d(
                        TAG,
                        "loadPage ignored stale snapshot id=${page.id} pageUpdatedAt=${page.updatedAt} localUpdatedAt=$lastLocalEditAt"
                    )
                    return@collectLatest
                }

                val persistedHtml = page.extractHtmlForEditor()
                val draft = pageDraftStore.load(page.id)
                val shouldRecoverDraft = !draftRecovered &&
                        draft != null &&
                        draft.updatedAt > page.updatedAt &&
                        (draft.html.isNotBlank() || draft.title.isNotBlank())

                val effectiveTitle = if (shouldRecoverDraft) {
                    draft!!.title.ifBlank { page.title }
                } else {
                    page.title
                }
                val effectiveHtml = if (shouldRecoverDraft) {
                    draft!!.html.ifBlank { persistedHtml }
                } else {
                    persistedHtml
                }

                currentPage = page.copy(
                    title = effectiveTitle,
                    updatedAt = maxOf(page.updatedAt, draft?.updatedAt ?: 0L)
                )
                val currentState = _uiState.value
                if (!currentState.isLoading &&
                    currentState.title == effectiveTitle &&
                    currentState.htmlContent == effectiveHtml
                ) {
                    return@collectLatest
                }
                if (effectiveHtml.isNotBlank()) {
                    lastNonEmptyHtml = effectiveHtml
                }
                draftRecovered = true

                Log.d(
                    TAG,
                    "loadPage ok id=${page.id} title='${effectiveTitle}' htmlLen=${effectiveHtml.length} blocks=${page.contentBlocks.size} updatedAt=${page.updatedAt} recovered=$shouldRecoverDraft"
                )

                _uiState.value = PageDetailUiState(
                    title = effectiveTitle,
                    visitDate = formatVisitDate(page.createdAt),
                    visitDateMillis = page.createdAt,
                    htmlContent = effectiveHtml,
                    isLoading = false,
                    lastModified = formatDate(maxOf(page.updatedAt, draft?.updatedAt ?: page.updatedAt))
                )
            }
        }
    }

    fun onVisitDateChange(newDateMillis: Long) {
        val page = currentPage ?: return
        val normalizedDate = normalizeDateOnly(newDateMillis)
        val now = System.currentTimeMillis()
        contentVersion++
        lastLocalEditAt = now

        currentPage = page.copy(
            createdAt = normalizedDate,
            updatedAt = now
        )
        _uiState.value = _uiState.value.copy(
            visitDate = formatVisitDate(normalizedDate),
            visitDateMillis = normalizedDate,
            lastModified = formatDate(now)
        )
        scheduleSave(expectedVersion = contentVersion)
    }

    fun onTitleChange(newTitle: String) {
        val page = currentPage ?: return
        val now = System.currentTimeMillis()
        contentVersion++
        lastLocalEditAt = now
        Log.d(TAG, "onTitleChange id=${page.id} len=${newTitle.length}")

        currentPage = page.copy(
            title = newTitle,
            updatedAt = now
        )

        _uiState.value = _uiState.value.copy(
            title = newTitle,
            lastModified = formatDate(now)
        )
        saveDraftNow(
            title = newTitle,
            html = _uiState.value.htmlContent,
            updatedAt = now
        )

        if (shouldSaveImmediately(page, html = _uiState.value.htmlContent, title = newTitle)) {
            val version = contentVersion
            viewModelScope.launch { saveNow(version) }
        } else {
            scheduleSave(expectedVersion = contentVersion)
        }
    }

    fun onHtmlContentChange(newHtml: String) {
        val page = currentPage ?: return
        val current = _uiState.value

        if (current.htmlContent == newHtml) return
        Log.d(TAG, "onHtmlContentChange id=${page.id} htmlLen=${newHtml.length}")

        val now = System.currentTimeMillis()
        contentVersion++
        lastLocalEditAt = now

        _uiState.value = current.copy(
            htmlContent = newHtml,
            lastModified = formatDate(now)
        )
        if (newHtml.isNotBlank()) {
            lastNonEmptyHtml = newHtml
        }

        currentPage = page.copy(updatedAt = now)
        saveDraftNow(
            title = _uiState.value.title,
            html = newHtml,
            updatedAt = now
        )

        if (shouldSaveImmediately(page, html = newHtml, title = current.title)) {
            val version = contentVersion
            viewModelScope.launch { saveNow(version) }
        } else {
            scheduleSave(expectedVersion = contentVersion)
        }
    }

    fun flushSaveNow(latestHtml: String? = null, onSaved: (() -> Unit)? = null) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            Log.d(
                TAG,
                "flushSaveNow id=${currentPage?.id} latestHtmlLen=${latestHtml?.length ?: -1}"
            )
            if (latestHtml != null) {
                val now = System.currentTimeMillis()
                contentVersion++
                lastLocalEditAt = now
                val htmlToUse = latestHtml.ifBlank {
                    _uiState.value.htmlContent.ifBlank { lastNonEmptyHtml }
                }
                _uiState.value = _uiState.value.copy(
                    htmlContent = htmlToUse,
                    lastModified = formatDate(now)
                )
                if (htmlToUse.isNotBlank()) {
                    lastNonEmptyHtml = htmlToUse
                }
                currentPage = currentPage?.copy(updatedAt = now)
                saveDraftNow(
                    title = _uiState.value.title,
                    html = htmlToUse,
                    updatedAt = now
                )
            }
            saveNow(contentVersion)
            onSaved?.invoke()
        }
    }

    private fun scheduleSave(delayMs: Long = 700L, expectedVersion: Long) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(delayMs)
            saveNow(expectedVersion)
        }
    }

    private suspend fun saveNow(expectedVersion: Long? = null) {
        if (expectedVersion != null && expectedVersion != contentVersion) {
            Log.d(TAG, "saveNow skip stale expectedVersion=$expectedVersion currentVersion=$contentVersion")
            return
        }
        val page = currentPage ?: return
        val normalizedTitle = _uiState.value.title.trim().ifBlank { page.title.trim() }
        if (normalizedTitle.isBlank()) {
            Log.w(TAG, "saveNow skipped blank title id=${page.id}")
            return
        }
        val persistedHtml = page.contentBlocks
            .filterIsInstance<ContentBlock.TextBlock>()
            .sortedBy { it.order }
            .firstOrNull()
            ?.text
            .orEmpty()
        val html = _uiState.value.htmlContent
            .ifBlank { lastNonEmptyHtml }
            .ifBlank { persistedHtml }
        val existingTextId = page.contentBlocks
            .filterIsInstance<ContentBlock.TextBlock>()
            .sortedBy { it.order }
            .firstOrNull()
            ?.id
            ?: UUID.randomUUID().toString()

        // Persistencia robusta: guarda todo o documento (texto + imagens) como HTML unico.
        val newBlocks = listOf(
            ContentBlock.TextBlock(
                id = existingTextId,
                order = 0,
                text = html
            )
        )

        val pageToSave = page.copy(
            title = normalizedTitle,
            contentBlocks = newBlocks,
            updatedAt = System.currentTimeMillis()
        )
        lastLocalEditAt = pageToSave.updatedAt

        currentPage = pageToSave
        Log.d(
            TAG,
            "saveNow upsert id=${pageToSave.id} titleLen=${pageToSave.title.length} htmlLen=${html.length} blocks=${pageToSave.contentBlocks.size}"
        )
        upsertPageUseCase(pageToSave)
        pageDraftStore.clear(pageToSave.id)
        Log.d(TAG, "saveNow success id=${pageToSave.id}")
    }

    private fun saveDraftNow(title: String, html: String, updatedAt: Long) {
        val pageId = currentPage?.id ?: return
        viewModelScope.launch {
            pageDraftStore.save(
                PageDraftSnapshot(
                    pageId = pageId,
                    title = title,
                    html = html,
                    updatedAt = updatedAt
                )
            )
        }
    }

    private fun formatDate(time: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        return sdf.format(Date(time))
    }

    private fun formatVisitDate(time: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return sdf.format(Date(time))
    }

    private fun normalizeDateOnly(time: Long): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = time
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun shouldSaveImmediately(page: Page, html: String, title: String): Boolean {
        val onlyBlankTextBlocks = page.contentBlocks.all { block ->
            when (block) {
                is ContentBlock.TextBlock -> block.text.isBlank()
                is ContentBlock.ImageBlock -> false
            }
        }
        return onlyBlankTextBlocks && (html.isNotBlank() || title.isNotBlank())
    }
}

private fun Page.extractHtmlForEditor(): String {
    val sorted = contentBlocks.sortedBy { it.order }
    if (sorted.isEmpty()) return ""

    if (sorted.size == 1 && sorted.first() is ContentBlock.TextBlock) {
        val text = (sorted.first() as ContentBlock.TextBlock).text
        if (looksLikeHtml(text)) return text
        return textToHtml(text)
    }

    val sb = StringBuilder()

    sorted.forEach { block ->
        when (block) {
            is ContentBlock.TextBlock -> {
                if (block.text.isBlank()) {
                    sb.append("<p><br></p>")
                } else if (looksLikeHtml(block.text)) {
                    sb.append(block.text)
                } else {
                    sb.append(textToHtml(block.text))
                }
            }

            is ContentBlock.ImageBlock -> {
                val src = escapeHtmlAttr(block.imageUrl)
                val caption = block.caption // evita smart cast issue

                sb.append("<figure class=\"image-card\"><img src=\"")
                    .append(src)
                    .append("\" /><figcaption class=\"image-caption\" contenteditable=\"true\"><span class=\"image-caption-label\">")
                    .append("img ")
                    .append("%02d".format(block.order + 1))
                    .append("</span><span class=\"image-caption-text\"> - ")
                    .append(
                        escapeHtml(
                            caption?.takeIf { it.isNotBlank() } ?: "toque para descrever"
                        )
                    )
                    .append("</span></figcaption></figure>")
            }
        }
    }

    return sb.toString()
}

private fun looksLikeHtml(text: String): Boolean {
    val t = text.trim()
    return t.contains("<p", ignoreCase = true) ||
            t.contains("<img", ignoreCase = true) ||
            t.contains("<div", ignoreCase = true) ||
            t.contains("</")
}

private fun textToHtml(text: String): String {
    if (text.isBlank()) return ""
    return text
        .split("\n")
        .joinToString(separator = "") { line ->
            if (line.isBlank()) "<p><br></p>" else "<p>${escapeHtml(line)}</p>"
        }
}

private fun escapeHtml(text: String): String {
    return buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}

private fun escapeHtmlAttr(text: String): String = escapeHtml(text)


private fun buildStructuredBlocksFromHtml(
    html: String,
    existingBlocks: List<ContentBlock>
): List<ContentBlock> {
    val normalizedHtml = html.trim()
    val existingTextIds = existingBlocks
        .filterIsInstance<ContentBlock.TextBlock>()
        .sortedBy { it.order }
        .map { it.id }
        .toMutableList()
    val existingImageBlocks = existingBlocks
        .filterIsInstance<ContentBlock.ImageBlock>()
        .sortedBy { it.order }
        .toMutableList()

    if (normalizedHtml.isBlank()) {
        return listOf(
            ContentBlock.TextBlock(
                id = existingTextIds.removeFirstOrNull() ?: UUID.randomUUID().toString(),
                order = 0,
                text = ""
            )
        )
    }

    val imageBlockRegex = pageHtmlImageRegex()

    val blocks = mutableListOf<ContentBlock>()
    var order = 0
    var cursor = 0

    imageBlockRegex.findAll(normalizedHtml).forEach { match ->
        val textSegment = normalizedHtml.substring(cursor, match.range.first)
        if (appendTextBlockIfNeeded(blocks, textSegment, order, existingTextIds)) {
            order++
        }

        parseImageBlock(match.value, order, existingImageBlocks)?.let {
            blocks += it
            order++
        }

        cursor = match.range.last + 1
    }

    val trailingText = normalizedHtml.substring(cursor)
    appendTextBlockIfNeeded(blocks, trailingText, order, existingTextIds)

    if (blocks.isEmpty()) {
        blocks += ContentBlock.TextBlock(
            id = existingTextIds.removeFirstOrNull() ?: UUID.randomUUID().toString(),
            order = 0,
            text = normalizedHtml
        )
    }

    return blocks
}

private fun appendTextBlockIfNeeded(
    blocks: MutableList<ContentBlock>,
    htmlSegment: String,
    order: Int,
    existingTextIds: MutableList<String>
): Boolean {
    val normalizedSegment = normalizeHtmlTextSegment(htmlSegment)
    if (normalizedSegment.isBlank()) return false

    blocks += ContentBlock.TextBlock(
        id = existingTextIds.removeFirstOrNull() ?: UUID.randomUUID().toString(),
        order = order,
        text = normalizedSegment
    )
    return true
}

private fun parseImageBlock(
    htmlSegment: String,
    order: Int,
    existingImageBlocks: MutableList<ContentBlock.ImageBlock>
): ContentBlock.ImageBlock? {
    val src = Regex("""<img\b[^>]*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(htmlSegment)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val captionHtml = Regex(
        """<figcaption\b[^>]*>(.*?)</figcaption>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
        .find(htmlSegment)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: Regex("""<p>(.*?)</p>\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(htmlSegment)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    val captionText = stripImageCaptionPrefix(extractPlainTextFromHtml(captionHtml))
        .ifBlank { "toque para descrever" }

    val existing = existingImageBlocks.removeFirstOrNull()
    return ContentBlock.ImageBlock(
        id = existing?.id ?: UUID.randomUUID().toString(),
        order = order,
        imageUrl = src,
        thumbnailUrl = existing?.thumbnailUrl,
        caption = captionText,
        width = existing?.width,
        height = existing?.height
    )
}

private fun normalizeHtmlTextSegment(segment: String): String {
    return segment
        .trim()
        .replace(Regex("""^(?:<p>\s*(?:<br\s*/?>|&nbsp;|\s)*</p>\s*)+""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""(?:<p>\s*(?:<br\s*/?>|&nbsp;|\s)*</p>\s*)+$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .trim()
}

