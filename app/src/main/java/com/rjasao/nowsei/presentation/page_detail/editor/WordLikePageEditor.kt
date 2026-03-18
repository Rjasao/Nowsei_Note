package com.rjasao.nowsei.presentation.page_detail.editor

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private class WordEditorBridge(
    private val onTitleChangedCallback: (String) -> Unit,
    private val onHtmlChangedCallback: (String) -> Unit,
    private val onFormattingChangedCallback: (EditorFormattingState) -> Unit,
    private val onVisitDateLongPressedCallback: () -> Unit
) {
    companion object {
        private const val TAG = "Nowsei.EditorBridge"
    }

    @JavascriptInterface
    fun onTitleChanged(title: String) {
        Log.d(TAG, "onTitleChanged len=${title.length}")
        onTitleChangedCallback(title)
    }

    @JavascriptInterface
    fun onContentChanged(html: String) {
        Log.d(TAG, "onContentChanged htmlLen=${html.length}")
        onHtmlChangedCallback(html)
    }

    @JavascriptInterface
    fun onFormattingChanged(
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        isBulletedList: Boolean,
        isNumberedList: Boolean,
        blockTag: String,
        fontSizeLevel: Int,
        textAlign: String,
        textColor: String
    ) {
        onFormattingChangedCallback(
            EditorFormattingState(
                isBold = isBold,
                isItalic = isItalic,
                isUnderline = isUnderline,
                isBulletedList = isBulletedList,
                isNumberedList = isNumberedList,
                blockTag = blockTag,
                fontSizeLevel = fontSizeLevel,
                textAlign = textAlign,
                textColor = textColor
            )
        )
    }

    @JavascriptInterface
    fun onVisitDateLongPressed() {
        onVisitDateLongPressedCallback()
    }
}

data class EditorFormattingState(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isBulletedList: Boolean = false,
    val isNumberedList: Boolean = false,
    val blockTag: String = "p",
    val fontSizeLevel: Int = 3,
    val textAlign: String = "left",
    val textColor: String = "#1f2937"
)

@Stable
class WordEditorController {
    internal var webView: WebView? = null
    internal var lastKnownHtml: String = ""

    fun focus() {
        webView?.evaluateJavascript("window.NowseiEditor && window.NowseiEditor.focus();", null)
    }

    fun requestHtml() {
        webView?.evaluateJavascript("window.NowseiEditor && window.NowseiEditor.notifyHtml();", null)
    }

    fun setTitle(title: String) {
        val safe = escapeForJsTemplate(title)
        webView?.evaluateJavascript(
            "window.NowseiEditor && window.NowseiEditor.setTitle(`$safe`);",
            null
        )
    }

    fun getHtml(onResult: (String) -> Unit) {
        val wv = webView ?: run {
            onResult(lastKnownHtml)
            return
        }

        wv.evaluateJavascript(
            """
            (function(){
              var fromEditor = '';
              if (window.NowseiEditor && window.NowseiEditor.getHtml) {
                fromEditor = window.NowseiEditor.getHtml() || '';
              }
              if (fromEditor && fromEditor.length > 0) return fromEditor;
              var body = document.querySelector('.page-body');
              return (body && body.innerHTML) ? body.innerHTML : '';
            })();
            """.trimIndent()
        ) { result ->
            val decoded = decodeJsStringResult(result)
            onResult(decoded.ifBlank { lastKnownHtml })
        }
    }

    fun setHtml(html: String) {
        lastKnownHtml = html
        val safe = escapeForJsTemplate(html)
        webView?.evaluateJavascript(
            "window.NowseiEditor && window.NowseiEditor.setHtml(`$safe`);",
            null
        )
    }

    fun setVisitDate(visitDate: String) {
        val safe = escapeForJsTemplate(visitDate)
        webView?.evaluateJavascript(
            "window.NowseiEditor && window.NowseiEditor.setVisitDate(`$safe`);",
            null
        )
    }

    fun applyInitialDocument(title: String, visitDate: String, html: String) {
        lastKnownHtml = html
        val safeTitle = escapeForJsTemplate(title)
        val safeVisitDate = escapeForJsTemplate(visitDate)
        val safeHtml = escapeForJsTemplate(html)
        webView?.evaluateJavascript(
            """
            (function() {
              if (!window.NowseiEditor) return;
              window.NowseiEditor.setTitle(`$safeTitle`);
              window.NowseiEditor.setVisitDate(`$safeVisitDate`);
              window.NowseiEditor.setHtml(`$safeHtml`);
              window.NowseiEditor.markReady();
            })();
            """.trimIndent(),
            null
        )
    }

    fun insertImage(imageUrl: String, onInserted: (() -> Unit)? = null) {
        val safe = imageUrl.replace("\\", "\\\\").replace("'", "\\'")
        webView?.evaluateJavascript(
            "window.NowseiEditor && window.NowseiEditor.insertImage('$safe');",
            if (onInserted == null) null else { _ ->
                requestHtml()
                onInserted()
            }
        )
    }

    fun insertHtml(html: String) {
        val safe = html
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
        webView?.evaluateJavascript(
            "window.NowseiEditor && window.NowseiEditor.insertHtml(`$safe`);",
            null
        )
    }

    fun insertCurrentDateTimePtBr() {
        val now = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR"))
            .format(java.util.Date())
        insertHtml("<p><b>Data/Hora:</b> ${escapeHtmlForHtmlInjection(now)}</p>")
    }

    fun insertSeparator() = insertHtml("<hr>")

    fun insertVistoriaTemplate() {
        insertHtml(
            """
            <h2>Vistoria</h2>
            <p><b>Data:</b> ____/____/______</p>
            <p><b>Unidade:</b> ______________________________</p>
            <p><b>Endereco:</b> ______________________________</p>
            <p><b>Presentes:</b> ______________________________</p>
            <p><b>Objetivo:</b> ______________________________</p>
            <h3>Constatacoes</h3>
            <p></p>
            <h3>Encaminhamentos</h3>
            <p></p>
            """.trimIndent()
        )
    }

    fun insertAssinaturaTemplate() {
        insertHtml(
            """
            <hr>
            <p><b>Responsavel tecnico:</b> ______________________________</p>
            <p><b>Cargo/Funcao:</b> ______________________________</p>
            <p><b>Setor:</b> ______________________________</p>
            """.trimIndent()
        )
    }

    fun bold() = exec("bold")
    fun italic() = exec("italic")
    fun underline() = exec("underline")
    fun unorderedList() = exec("insertUnorderedList")
    fun orderedList() = exec("insertOrderedList")
    fun undo() = exec("undo")
    fun redo() = exec("redo")
    fun increaseFontSize() = webView?.evaluateJavascript(
        "window.NowseiEditor && window.NowseiEditor.adjustFontSize(1);",
        null
    )
    fun decreaseFontSize() = webView?.evaluateJavascript(
        "window.NowseiEditor && window.NowseiEditor.adjustFontSize(-1);",
        null
    )
    fun justifyLeft() = exec("justifyLeft")
    fun justifyCenter() = exec("justifyCenter")
    fun justifyRight() = exec("justifyRight")
    fun justifyFull() = exec("justifyFull")
    fun setTextColor(color: String) {
        val safeValue = color.replace("\\", "\\\\").replace("'", "\\'")
        webView?.evaluateJavascript(
            "window.NowseiEditor && window.NowseiEditor.setTextColor('$safeValue');",
            null
        )
    }

    fun formatParagraph() = exec("formatBlock", "p")
    fun formatH2() = exec("formatBlock", "h2")
    fun formatH3() = exec("formatBlock", "h3")

    private fun exec(command: String, value: String? = null) {
        val js = if (value == null) {
            "window.NowseiEditor && window.NowseiEditor.exec('$command');"
        } else {
            val safeValue = value.replace("\\", "\\\\").replace("'", "\\'")
            "window.NowseiEditor && window.NowseiEditor.exec('$command', '$safeValue');"
        }
        webView?.evaluateJavascript(js, null)
    }
}

@Composable
fun rememberWordEditorController(): WordEditorController = remember { WordEditorController() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WordLikePageEditor(
    modifier: Modifier = Modifier,
    title: String,
    visitDate: String,
    initialHtml: String,
    controller: WordEditorController,
    onTitleChanged: (String) -> Unit,
    onHtmlChanged: (String) -> Unit,
    onFormattingChanged: (EditorFormattingState) -> Unit = {},
    onVisitDateLongPressed: () -> Unit = {}
) {
    var pageLoaded by remember { mutableStateOf(false) }
    var initialApplied by remember { mutableStateOf(false) }
    var lastEditorTitle by remember { mutableStateOf(title) }
    var lastEditorHtml by remember { mutableStateOf(initialHtml) }
    var lastAppliedHtml by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                controller.webView = this

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                isFocusable = true
                isFocusableInTouchMode = true

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded = true
                        if (!initialApplied) {
                            controller.applyInitialDocument(title, visitDate, initialHtml)
                            controller.lastKnownHtml = initialHtml
                            lastEditorTitle = title
                            lastAppliedHtml = initialHtml
                            lastEditorHtml = initialHtml
                            initialApplied = true
                        }
                    }
                }

                addJavascriptInterface(
                    WordEditorBridge(
                        onTitleChangedCallback = { newTitle ->
                            lastEditorTitle = newTitle
                            onTitleChanged(newTitle)
                        },
                        onHtmlChangedCallback = { html ->
                            controller.lastKnownHtml = html
                            lastEditorHtml = html
                            onHtmlChanged(html)
                        },
                        onFormattingChangedCallback = onFormattingChanged,
                        onVisitDateLongPressedCallback = onVisitDateLongPressed
                    ),
                    "AndroidBridge"
                )

                loadDataWithBaseURL(
                    "https://nowsei.local/",
                    editorHtmlTemplate(),
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        update = { web ->
            controller.webView = web
            if (pageLoaded && !initialApplied) {
                controller.applyInitialDocument(title, visitDate, initialHtml)
                controller.lastKnownHtml = initialHtml
                lastEditorTitle = title
                lastAppliedHtml = initialHtml
                lastEditorHtml = initialHtml
                initialApplied = true
            } else if (pageLoaded) {
                if (title != lastEditorTitle) {
                    controller.setTitle(title)
                    lastEditorTitle = title
                }
                controller.setVisitDate(visitDate)
            }
        }
    )
}

private fun escapeForJsTemplate(text: String): String {
    return text
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")
}

private fun decodeJsStringResult(result: String?): String {
    if (result == null || result == "null") return ""
    if (result.length >= 2 && result.first() == '"' && result.last() == '"') {
        val body = result.substring(1, result.length - 1)
        return body
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u0026", "&")
    }
    return result
}

private fun escapeHtmlForHtmlInjection(text: String): String {
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

private fun editorHtmlTemplate(): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
<style>
  html, body {
    margin: 0;
    padding: 0;
    background: #0b0f17;
    color: #111;
    font-family: sans-serif;
  }

  .workspace {
    min-height: 100vh;
    padding: 14px 10px 18px;
    box-sizing: border-box;
  }

  .page-shell {
    width: min(100%, 820px);
    margin: 0 auto;
    background: linear-gradient(180deg, #fffefb 0%, #fffaf0 100%);
    border: 1px solid #e6dcc8;
    border-radius: 10px;
    box-shadow:
      0 2px 8px rgba(0,0,0,0.10),
      0 18px 40px rgba(0,0,0,0.08);
    overflow: hidden;
  }

  body.pending-load .page-shell {
    opacity: 0;
  }

  .page-header {
    height: 12px;
    background: linear-gradient(90deg, #ece3d3, #faf3e8);
    border-bottom: 1px solid #eadfca;
  }

  .page {
    min-height: 78vh;
    padding: 0;
    box-sizing: border-box;
    background: #fffdf8;
  }

  .page-title {
    padding: 26px 22px 14px 22px;
    border-bottom: 1px solid #efe4d2;
  }

  .page-title-input {
    display: block;
    width: 100%;
    min-height: 40px;
    border: none;
    outline: none;
    resize: none;
    overflow: hidden;
    background: transparent;
    font: 600 30px/1.2 Georgia, serif;
    letter-spacing: -0.02em;
    color: #1f2937;
    padding: 0;
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .page-title-input::placeholder {
    color: #a8a29e;
  }

  .page-visit-date {
    margin-top: 8px;
    color: #7c6f62;
    font: 600 13px/1.3 Georgia, serif;
    user-select: none;
    -webkit-user-select: none;
  }

  .page-body {
    min-height: calc(78vh - 86px);
    padding: 18px 22px 28px 22px;
    box-sizing: border-box;
    outline: none;
    line-height: 1.5;
    font-size: 16px;
    color: #1f2937;
    background: #fffdf8;
    white-space: normal;
    word-break: break-word;
  }

  .page-body p { margin: 0 0 10px 0; }
  .page-body h2 { margin: 14px 0 8px; font-size: 1.25em; line-height: 1.3; color: #0f172a; }
  .page-body h3 { margin: 12px 0 8px; font-size: 1.10em; line-height: 1.3; color: #0f172a; }
  .page-body ul, .page-body ol { margin: 0 0 10px 22px; padding: 0; }
  .page-body li { margin: 0 0 4px 0; }
  .page-body font[size="1"] { font-size: 12px; }
  .page-body font[size="2"] { font-size: 14px; }
  .page-body font[size="3"] { font-size: 16px; }
  .page-body font[size="4"] { font-size: 18px; }
  .page-body font[size="5"] { font-size: 22px; }
  .page-body font[size="6"] { font-size: 26px; }
  .page-body font[size="7"] { font-size: 32px; }

  .image-card {
    position: relative;
    margin: 0;
    padding: 10px;
    background: #fffaf2;
    border: 1px solid #eadfca;
    border-radius: 16px;
    box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  }

  .image-card.is-dragging {
    border-color: #caa57d;
    box-shadow:
      0 18px 40px rgba(123, 79, 35, 0.24),
      0 6px 12px rgba(15, 23, 42, 0.10);
    opacity: 0.72;
    z-index: 8;
    pointer-events: none;
  }

  .image-card.is-floating {
    position: fixed;
    margin: 0;
    transform: none;
  }

  .image-card-more {
    position: absolute;
    top: 18px;
    right: 18px;
    width: 32px;
    height: 32px;
    border: 1px solid rgba(214, 201, 183, 0.95);
    border-radius: 999px;
    background: rgba(255, 253, 248, 0.96);
    color: #5b4636;
    display: flex;
    align-items: center;
    justify-content: center;
    font: 700 16px/1 sans-serif;
    backdrop-filter: blur(6px);
    z-index: 5;
  }

  .image-card-more::before {
    content: '...';
    letter-spacing: 0.08em;
    transform: translateY(-1px);
  }

  .image-card-drag {
    position: absolute;
    top: 18px;
    left: 18px;
    width: 32px;
    height: 32px;
    border: 1px solid rgba(214, 201, 183, 0.95);
    border-radius: 999px;
    background: rgba(255, 253, 248, 0.96);
    color: #5b4636;
    display: flex;
    align-items: center;
    justify-content: center;
    font: 700 12px/1 sans-serif;
    backdrop-filter: blur(6px);
    z-index: 5;
  }

  .image-card-drag::before {
    content: '::';
    letter-spacing: 0.08em;
  }

  .image-context-menu {
    position: absolute;
    top: 56px;
    right: 18px;
    display: none;
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
    min-width: 112px;
    z-index: 6;
  }

  .image-card.show-menu .image-context-menu {
    display: flex;
  }

  .image-drop-indicator {
    display: none;
    height: 0;
    margin: 10px 0;
    border-top: 2px dashed #caa57d;
    opacity: 0;
    transition: opacity 0.12s ease-out;
  }

  .image-drop-indicator.visible {
    display: block;
    opacity: 1;
  }

  .image-card-action {
    border: 1px solid rgba(214, 201, 183, 0.95);
    background: rgba(255, 253, 248, 0.96);
    color: #5b4636;
    border-radius: 999px;
    padding: 5px 10px;
    min-height: 34px;
    padding: 7px 10px;
    font: 600 12px/1.2 sans-serif;
    backdrop-filter: blur(6px);
  }

  .image-card-action.delete {
    color: #9f2d2d;
    border-color: #e7b6b6;
    background: #fff6f6;
  }

  .image-card img {
    display: block;
    max-width: 100%;
    height: auto;
    margin: 0 auto;
    border-radius: 10px;
    background: #ffffff;
    box-sizing: border-box;
    transform-origin: center center;
    transition: transform 0.12s ease-out;
    touch-action: manipulation;
  }

  .image-caption {
    display: block;
    width: 100%;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid #efe4d2;
    font: 500 14px/1.4 Georgia, serif;
    color: #5b4636;
    outline: none;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .image-caption-label {
    font-weight: 700;
    text-transform: lowercase;
    letter-spacing: 0.04em;
    color: #7c5b43;
    user-select: none;
  }

  .image-caption-text {
    display: inline;
    outline: none;
  }

  .page-body blockquote {
    margin: 0 0 10px 0;
    padding: 8px 12px;
    border-left: 4px solid #cbd5e1;
    background: #f8fafc;
    color: #334155;
  }

  .page-body hr {
    border: none;
    border-top: 1px solid #e5e7eb;
    margin: 12px 0;
  }

  .page-body:empty:before {
    content: attr(data-placeholder);
    color: #94a3b8;
  }

  * { -webkit-tap-highlight-color: rgba(0,0,0,0); }
</style>
</head>
<body class="pending-load">
  <div class="workspace">
    <div class="page-shell">
      <div class="page-header"></div>
      <div class="page">
        <div class="page-title">
          <textarea
            id="title"
            class="page-title-input"
            rows="1"
            placeholder="Titulo"
            aria-label="Titulo da pagina"></textarea>
          <div
            id="visitDate"
            class="page-visit-date"
            aria-label="Data da vistoria"
            title="Pressione e segure para editar a data da vistoria"></div>
        </div>
        <div id="editor"
             class="page-body"
             contenteditable="true"
             spellcheck="true"
             data-placeholder="Digite seu relatorio aqui..."></div>
      </div>
    </div>
  </div>

<script>
(function () {
  const editor = document.getElementById('editor');
  const titleInput = document.getElementById('title');
  const visitDateLabel = document.getElementById('visitDate');
  const FONT_SIZE_LEVELS = {
    1: 12,
    2: 14,
    3: 16,
    4: 18,
    5: 22,
    6: 26,
    7: 32
  };
  let visitDatePressTimer = null;

  function autoResizeTitle() {
    titleInput.style.height = 'auto';
    titleInput.style.height = Math.max(titleInput.scrollHeight, 40) + 'px';
  }

  function notifyTitleChange() {
    autoResizeTitle();
    const normalizedTitle = String(titleInput.value || '')
      .replace(/\u00A0/g, ' ')
      .replace(/\u200B/g, '');
    if (titleInput.value !== normalizedTitle) {
      const selectionStart = typeof titleInput.selectionStart === 'number' ? titleInput.selectionStart : normalizedTitle.length;
      const selectionEnd = typeof titleInput.selectionEnd === 'number' ? titleInput.selectionEnd : normalizedTitle.length;
      titleInput.value = normalizedTitle;
      titleInput.setSelectionRange(selectionStart, selectionEnd);
    }
    if (window.AndroidBridge && window.AndroidBridge.onTitleChanged) {
      window.AndroidBridge.onTitleChanged(normalizedTitle);
    }
  }

  function clearVisitDatePressTimer() {
    if (visitDatePressTimer) {
      clearTimeout(visitDatePressTimer);
      visitDatePressTimer = null;
    }
  }

  function normalizeEditor() {
    if (editor.innerHTML === "<br>" || editor.innerHTML === "&nbsp;") {
      editor.innerHTML = "";
    }
    normalizeLegacyImages();
    ensureTextSlotsAroundImages();
    syncImageChrome();
    renumberImages();
  }

  function markReady() {
    document.body.classList.remove('pending-load');
  }

  function buildImageMenu() {
    return '' +
      '<button type="button" class="image-card-drag" aria-label="Arrastar imagem" data-image-action="drag-handle" contenteditable="false"></button>' +
      '<button type="button" class="image-card-more" aria-label="Mais acoes da imagem" data-image-action="menu" contenteditable="false"></button>' +
      '<div class="image-context-menu" contenteditable="false">' +
      '<button type="button" class="image-card-action" aria-label="Copiar imagem" title="Copiar imagem" data-image-action="copy">copiar</button>' +
      '<button type="button" class="image-card-action" aria-label="Recortar imagem" title="Recortar imagem" data-image-action="cut">recortar</button>' +
      '<button type="button" class="image-card-action delete" aria-label="Remover imagem" title="Remover imagem" data-image-action="remove">excluir</button>' +
      '</div>';
  }

  let activeImageFigure = null;
  let draggingFigure = null;
  let dragMoved = false;
  let longPressTimer = null;
  let pinchState = null;
  let dragActivatedByLongPress = false;
  let autoScrollTimer = null;
  let autoScrollDelta = 0;
  let dragOffset = { x: 0, y: 0 };
  let dragLastPoint = null;
  let dropIndicator = null;
  let dragPlaceholder = null;
  let currentDropRange = null;
  let dragDropIsValid = false;
  let dragOriginParent = null;
  let dragOriginNextSibling = null;

  function ensureDropIndicator() {
    if (dropIndicator && dropIndicator.isConnected) return dropIndicator;
    dropIndicator = document.createElement('div');
    dropIndicator.className = 'image-drop-indicator';
    editor.appendChild(dropIndicator);
    return dropIndicator;
  }

  function syncImageChrome() {
    const figures = editor.querySelectorAll('.image-card');
    figures.forEach((figure) => {
      let menu = figure.querySelector('.image-context-menu');
      let moreButton = figure.querySelector('.image-card-more');
      let dragHandle = figure.querySelector('.image-card-drag');
      if (!menu || !moreButton || !dragHandle) {
        figure.querySelectorAll('.image-context-menu, .image-card-more, .image-card-drag').forEach(node => node.remove());
        figure.insertAdjacentHTML('afterbegin', buildImageMenu());
        menu = figure.querySelector('.image-context-menu');
        moreButton = figure.querySelector('.image-card-more');
        dragHandle = figure.querySelector('.image-card-drag');
      }
      if (menu) {
        menu.setAttribute('contenteditable', 'false');
      }
      if (moreButton) {
        moreButton.setAttribute('contenteditable', 'false');
      }
      if (dragHandle) {
        dragHandle.setAttribute('contenteditable', 'false');
      }

      const img = figure.querySelector('img');
      if (img) {
        img.contentEditable = 'false';
      }
    });
  }

  function buildImageCaption(index, captionText) {
    const safeText = captionText || "toque para descrever";
    return '<span class="image-caption-label" contenteditable="false">img ' + String(index).padStart(2, '0') + '</span>' +
      '<span class="image-caption-text" contenteditable="true"> - ' + safeText + '</span>';
  }

  function renumberImages() {
    const figures = editor.querySelectorAll('.image-card');
    figures.forEach((figure, index) => {
      let caption = figure.querySelector('.image-caption');
      if (!caption) {
        caption = document.createElement('figcaption');
        caption.className = 'image-caption';
        figure.appendChild(caption);
      }

      const currentTextNode = caption.querySelector('.image-caption-text');
      const captionText = currentTextNode
        ? currentTextNode.textContent.replace(/^\s*-\s*/, '').trim()
        : caption.textContent.replace(/^img\s+\d+\s*-\s*/i, '').trim()

      const currentLabel = caption.querySelector('.image-caption-label');
      const expectedLabel = 'img ' + String(index + 1).padStart(2, '0');
      if (!currentLabel || currentLabel.textContent !== expectedLabel) {
        caption.innerHTML = buildImageCaption(index + 1, captionText);
      } else if (!currentTextNode) {
        caption.innerHTML = buildImageCaption(index + 1, captionText);
      }
    });
  }

  function normalizeLegacyImages() {
    const legacyImages = Array.from(editor.querySelectorAll('img'))
      .filter(img => !img.closest('.image-card'));

    legacyImages.forEach((img, index) => {
      const wrapper = document.createElement('figure');
      wrapper.className = 'image-card';

      const movedImg = img.cloneNode(true);
      movedImg.contentEditable = 'false';
      wrapper.appendChild(movedImg);

      const caption = document.createElement('figcaption');
      caption.className = 'image-caption';
      wrapper.appendChild(caption);

      const parent = img.parentNode;
      if (!parent) return;

      const paragraphCaption = parent.nextElementSibling;
      let captionText = '';
      if (
        paragraphCaption &&
        paragraphCaption.tagName === 'P' &&
        !paragraphCaption.querySelector('img') &&
        !paragraphCaption.querySelector('.image-card')
      ) {
        captionText = (paragraphCaption.textContent || '').trim();
      }

      caption.innerHTML = buildImageCaption(index + 1, captionText);

      parent.replaceWith(wrapper);
      if (
        paragraphCaption &&
        paragraphCaption.tagName === 'P' &&
        !paragraphCaption.querySelector('img') &&
        !paragraphCaption.querySelector('.image-card')
      ) {
        paragraphCaption.remove();
      }
    });
  }

  function isEmptyParagraph(node) {
    return !!node &&
      node.tagName === 'P' &&
      !node.querySelector('img') &&
      !node.querySelector('.image-card') &&
      ((node.textContent || '').trim() === '');
  }

  function markTextSlot(paragraph) {
    return paragraph;
  }

  function createEmptyParagraph() {
    const paragraph = document.createElement('p');
    paragraph.innerHTML = '<br>';
    return markTextSlot(paragraph);
  }

  function escapeHtmlAttribute(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  function getLastMeaningfulEditorChild() {
    let node = editor.lastElementChild;
    while (node && node.classList && node.classList.contains('page-end-spacer')) {
      node = node.previousElementSibling;
    }
    return node;
  }

  function ensureEditorTrailingParagraph() {
    const last = getLastMeaningfulEditorChild();
    if (!last) {
      const paragraph = document.createElement('p');
      paragraph.innerHTML = '<br>';
      editor.appendChild(paragraph);
      return markTextSlot(paragraph);
    }

    if (last.classList && last.classList.contains('image-card')) {
      return ensureParagraphAfter(last);
    }

    if (last.tagName !== 'P') {
      const paragraph = document.createElement('p');
      paragraph.innerHTML = '<br>';
      editor.appendChild(paragraph);
      return markTextSlot(paragraph);
    }

    return markTextSlot(last);
  }

  function ensureTextSlotsAroundImages() {
    const figures = Array.from(editor.querySelectorAll('.image-card'));
    if (figures.length === 0) {
      ensureEditorTrailingParagraph();
      ensurePageEndSpacer();
      return;
    }

    figures.forEach((figure, index) => {
      const nextFigure = figures[index + 1];
      if (!nextFigure) return;

      let node = figure.nextElementSibling;
      let keptParagraph = null;
      while (node && node !== nextFigure) {
        const nextNode = node.nextElementSibling;
        if (isEmptyParagraph(node) && !keptParagraph) {
          keptParagraph = node;
        } else if (isEmptyParagraph(node)) {
          node.remove();
        }
        node = nextNode;
      }

      if (!keptParagraph) {
        editor.insertBefore(createEmptyParagraph(), nextFigure);
      }
    });

    ensureEditorTrailingParagraph();
    ensurePageEndSpacer();
  }

  function ensurePageEndSpacer() {
    let spacer = editor.querySelector('.page-end-spacer');
    if (!spacer) {
      spacer = document.createElement('div');
      spacer.className = 'page-end-spacer';
      spacer.style.minHeight = '24px';
      editor.appendChild(spacer);
    } else if (spacer !== editor.lastElementChild) {
      editor.appendChild(spacer);
    }
  }

  function notifyFormattingChange() {
    if (!window.AndroidBridge || !window.AndroidBridge.onFormattingChanged) return;

    let blockTag = document.queryCommandValue('formatBlock') || 'p';
    blockTag = String(blockTag).replace(/[<>]/g, '').toLowerCase();
    if (!blockTag) blockTag = 'p';

    const selectionNode = getSelectionAnchorElement();
    const computedStyle = selectionNode ? window.getComputedStyle(selectionNode) : null;
    const color = normalizeCssColor(
      document.queryCommandValue('foreColor') ||
      (computedStyle ? computedStyle.color : '') ||
      '#1f2937'
    );
    const fontSizeLevel = inferFontSizeLevel(
      document.queryCommandValue('fontSize'),
      computedStyle ? computedStyle.fontSize : ''
    );
    const textAlign = inferTextAlign(selectionNode);

    window.AndroidBridge.onFormattingChanged(
      document.queryCommandState('bold'),
      document.queryCommandState('italic'),
      document.queryCommandState('underline'),
      document.queryCommandState('insertUnorderedList'),
      document.queryCommandState('insertOrderedList'),
      blockTag,
      fontSizeLevel,
      textAlign,
      color
    );
  }

  function clampFontSizeLevel(level) {
    return Math.min(7, Math.max(1, level || 3));
  }

  function inferFontSizeLevel(queryValue, computedFontSize) {
    const parsedQuery = parseInt(queryValue, 10);
    if (!isNaN(parsedQuery)) {
      return clampFontSizeLevel(parsedQuery);
    }

    const px = parseFloat(computedFontSize || '');
    if (isNaN(px)) return 3;

    let bestLevel = 3;
    let bestDiff = Number.MAX_VALUE;
    Object.keys(FONT_SIZE_LEVELS).forEach((key) => {
      const level = parseInt(key, 10);
      const diff = Math.abs(FONT_SIZE_LEVELS[level] - px);
      if (diff < bestDiff) {
        bestLevel = level;
        bestDiff = diff;
      }
    });
    return bestLevel;
  }

  function getSelectionAnchorElement() {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return editor;
    let node = selection.anchorNode || selection.getRangeAt(0).startContainer;
    if (!node) return editor;
    if (node.nodeType === Node.TEXT_NODE) {
      node = node.parentElement;
    }
    return node && node.nodeType === Node.ELEMENT_NODE ? node : editor;
  }

  function getBlockElement(node) {
    let current = node;
    while (current && current !== editor) {
      if (current.parentNode === editor) return current;
      current = current.parentElement;
    }
    return editor;
  }

  function inferTextAlign(node) {
    const block = getBlockElement(node || editor);
    const align = block ? window.getComputedStyle(block).textAlign : 'left';
    if (align === 'center' || align === 'right' || align === 'justify') {
      return align;
    }
    return 'left';
  }

  function normalizeCssColor(rawColor) {
    const value = String(rawColor || '').trim();
    if (!value) return '#1f2937';
    if (value[0] === '#') {
      return value.length === 4
        ? '#' + value[1] + value[1] + value[2] + value[2] + value[3] + value[3]
        : value.toLowerCase();
    }

    const rgbMatch = value.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
    if (!rgbMatch) return '#1f2937';

    const toHex = (component) => {
      const hex = parseInt(component, 10).toString(16);
      return hex.length === 1 ? '0' + hex : hex;
    };
    return '#' + toHex(rgbMatch[1]) + toHex(rgbMatch[2]) + toHex(rgbMatch[3]);
  }

  function notifyChange() {
    normalizeEditor();
    if (window.AndroidBridge && window.AndroidBridge.onContentChanged) {
      window.AndroidBridge.onContentChanged(getSanitizedHtml());
    }
    notifyFormattingChange();
  }

  function getSanitizedHtml() {
    const clone = editor.cloneNode(true);
    clone.querySelectorAll('.image-context-menu, .image-card-more, .image-card-drag').forEach(node => node.remove());
    clone.querySelectorAll('.image-caption-text').forEach(node => {
      node.removeAttribute('contenteditable');
    });
    clone.querySelectorAll('.image-card').forEach(node => {
      node.classList.remove('show-menu');
      node.classList.remove('is-dragging');
    });
    clone.querySelectorAll('img').forEach(node => {
      node.style.transform = '';
      node.removeAttribute('data-zoom-scale');
    });
    return clone.innerHTML || '';
  }

  function scrollFocusedElementIntoView(target) {
    if (!target || !target.scrollIntoView) return;
    setTimeout(function() {
      target.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
    }, 120);
  }

  function hideImageMenu() {
    if (activeImageFigure) {
      activeImageFigure.classList.remove('show-menu');
      activeImageFigure = null;
    }
  }

  function showImageMenu(figure) {
    if (!figure) return;
    if (activeImageFigure && activeImageFigure !== figure) {
      activeImageFigure.classList.remove('show-menu');
    }
    activeImageFigure = figure;
    figure.classList.add('show-menu');
    scrollFocusedElementIntoView(figure);
  }

  function duplicateImageFigure(figure) {
    const clone = figure.cloneNode(true);
    figure.insertAdjacentElement('afterend', clone);
    const spacer = document.createElement('p');
    spacer.innerHTML = '<br>';
    clone.insertAdjacentElement('afterend', spacer);
  }

  function beginDragFigure(figure, clientX, clientY) {
    draggingFigure = figure;
    dragMoved = false;
    dragActivatedByLongPress = true;
    dragOriginParent = figure.parentNode;
    dragOriginNextSibling = figure.nextSibling;
    const rect = figure.getBoundingClientRect();
    dragOffset = {
      x: clientX - rect.left,
      y: clientY - rect.top
    };
    dragLastPoint = { x: clientX, y: clientY };
    dragPlaceholder = document.createElement('div');
    dragPlaceholder.className = 'image-drop-indicator visible';
    dragPlaceholder.style.height = rect.height + 'px';
    dragPlaceholder.style.borderTop = '2px dashed #caa57d';
    dragPlaceholder.style.borderBottom = '2px dashed #caa57d';
    figure.parentNode.insertBefore(dragPlaceholder, figure.nextSibling);
    figure.classList.add('is-dragging');
    figure.classList.add('is-floating');
    figure.style.width = rect.width + 'px';
    figure.style.left = rect.left + 'px';
    figure.style.top = rect.top + 'px';
    currentDropRange = null;
    dragDropIsValid = false;
    hideImageMenu();
    ensureDropIndicator().classList.remove('visible');
  }

  function restoreDraggedFigureToOrigin() {
    if (!draggingFigure || !dragOriginParent) return;
    if (dragOriginNextSibling && dragOriginNextSibling.parentNode === dragOriginParent) {
      dragOriginParent.insertBefore(draggingFigure, dragOriginNextSibling);
    } else {
      dragOriginParent.appendChild(draggingFigure);
    }
  }

  function isDraggedFigureSafelyPlaced() {
    return !!draggingFigure &&
      draggingFigure.isConnected &&
      draggingFigure.parentNode &&
      draggingFigure.closest &&
      draggingFigure.closest('#editor') === editor &&
      draggingFigure.classList &&
      draggingFigure.classList.contains('image-card') &&
      !!draggingFigure.querySelector('img');
  }

  function isPointInsideEditorSheet(clientX, clientY) {
    const shell = document.querySelector('.page-shell');
    if (!shell) return false;
    const rect = shell.getBoundingClientRect();
    return clientX >= rect.left &&
      clientX <= rect.right &&
      clientY >= rect.top &&
      clientY <= rect.bottom;
  }

  function getEditorSheetRect() {
    const shell = document.querySelector('.page-shell');
    return shell ? shell.getBoundingClientRect() : null;
  }

  function clampDragPointToEditorSheet(clientX, clientY) {
    const shellRect = getEditorSheetRect();
    if (!shellRect || !draggingFigure) {
      return { x: clientX, y: clientY };
    }
    const figureRect = draggingFigure.getBoundingClientRect();
    const minX = shellRect.left + dragOffset.x;
    const maxX = shellRect.right - Math.max(figureRect.width - dragOffset.x, 0);
    const minY = shellRect.top + dragOffset.y;
    const maxY = shellRect.bottom - Math.max(figureRect.height - dragOffset.y, 0);
    return {
      x: Math.min(Math.max(clientX, minX), Math.max(minX, maxX)),
      y: Math.min(Math.max(clientY, minY), Math.max(minY, maxY))
    };
  }

  function endDragFigure(clientX, clientY) {
    if (!draggingFigure) return;
    const didMove = dragMoved;
    let committed = false;
    const finalPoint = (typeof clientX === 'number' && typeof clientY === 'number')
      ? { x: clientX, y: clientY }
      : dragLastPoint;
    if (finalPoint && !isPointInsideEditorSheet(finalPoint.x, finalPoint.y)) {
      dragDropIsValid = false;
      currentDropRange = null;
    }
    draggingFigure.classList.remove('is-dragging');
    draggingFigure.classList.remove('is-floating');
    draggingFigure.style.transform = '';
    draggingFigure.style.width = '';
    draggingFigure.style.left = '';
    draggingFigure.style.top = '';
    try {
      if (dragDropIsValid && dragPlaceholder && dragPlaceholder.parentNode) {
        dragPlaceholder.parentNode.insertBefore(draggingFigure, dragPlaceholder);
        committed = true;
      }
    } catch (error) {
      committed = false;
    }
    if (committed && !isDraggedFigureSafelyPlaced()) {
      committed = false;
    }
    if (!committed) {
      restoreDraggedFigureToOrigin();
    }
    if (dragPlaceholder && dragPlaceholder.parentNode) {
      dragPlaceholder.remove();
    }
    dragPlaceholder = null;
    currentDropRange = null;
    dragDropIsValid = false;
    dragLastPoint = null;
    dragOriginParent = null;
    dragOriginNextSibling = null;
    draggingFigure = null;
    dragMoved = false;
    dragActivatedByLongPress = false;
    stopAutoScroll();
    if (dropIndicator) {
      dropIndicator.classList.remove('visible');
    }
    if (didMove) {
      notifyChange();
    }
  }

  function reorderDraggedFigure(clientX, clientY) {
    if (!draggingFigure) return;
    const constrainedPoint = clampDragPointToEditorSheet(clientX, clientY);
    clientX = constrainedPoint.x;
    clientY = constrainedPoint.y;
    dragLastPoint = { x: clientX, y: clientY };
    draggingFigure.style.left = (clientX - dragOffset.x) + 'px';
    draggingFigure.style.top = (clientY - dragOffset.y) + 'px';

    const target = document.elementFromPoint(clientX, clientY);
    const indicator = ensureDropIndicator();
    currentDropRange = null;
    dragDropIsValid = false;
    const validBlockTags = ['P', 'H2', 'H3', 'UL', 'OL', 'BLOCKQUOTE', 'HR', 'FIGURE'];
    let targetBlock = null;

    if (target) {
      if (target.closest) {
        targetBlock = target.closest('.image-card');
      }
      if (!targetBlock) {
        let node = target.nodeType === Node.ELEMENT_NODE ? target : target.parentElement;
        while (node && node !== editor) {
          if (node.parentNode === editor && validBlockTags.includes(node.tagName)) {
            targetBlock = node;
            break;
          }
          node = node.parentElement;
        }
      }
    }

    if (!targetBlock) {
      const candidateBlocks = Array.from(editor.children).filter((node) =>
        node !== draggingFigure &&
        node !== dragPlaceholder &&
        node !== indicator &&
        !(node.classList && node.classList.contains('page-end-spacer'))
      );
      targetBlock = candidateBlocks.find((node) => {
        const rect = node.getBoundingClientRect();
        return clientY < (rect.top + rect.height / 2);
      }) || null;
    }

    if (!targetBlock || targetBlock === draggingFigure || !targetBlock.parentNode) {
      indicator.classList.add('visible');
      const endSpacer = editor.querySelector('.page-end-spacer');
      if (endSpacer && endSpacer.parentNode === editor) {
        editor.insertBefore(indicator, endSpacer);
      } else {
        editor.appendChild(indicator);
      }
      if (dragPlaceholder && dragPlaceholder !== indicator) {
        if (endSpacer && endSpacer.parentNode === editor) {
          editor.insertBefore(dragPlaceholder, endSpacer);
        } else {
          editor.appendChild(dragPlaceholder);
        }
      }
      dragDropIsValid = true;
      dragMoved = true;
      maybeAutoScroll(clientY);
      return;
    }

    const rect = targetBlock.getBoundingClientRect();
    const shouldInsertBefore = clientY < rect.top + (rect.height / 2);
    const parent = targetBlock.parentNode;
    indicator.classList.add('visible');
    if (shouldInsertBefore) {
      parent.insertBefore(indicator, targetBlock);
      if (dragPlaceholder && dragPlaceholder !== indicator) {
        parent.insertBefore(dragPlaceholder, targetBlock);
      }
    } else {
      parent.insertBefore(indicator, targetBlock.nextSibling);
      if (dragPlaceholder && dragPlaceholder !== indicator) {
        parent.insertBefore(dragPlaceholder, targetBlock.nextSibling);
      }
    }
    dragDropIsValid = true;
    dragMoved = true;
    maybeAutoScroll(clientY);
  }

  function clearLongPressTimer() {
    if (longPressTimer) {
      clearTimeout(longPressTimer);
      longPressTimer = null;
    }
  }

  function placeSelectionAtRange(range) {
    const sel = window.getSelection();
    if (!sel) return;
    sel.removeAllRanges();
    sel.addRange(range);
  }

  function placeCaretInsideParagraph(paragraph) {
    if (!paragraph) return null;
    editor.focus();
    const range = document.createRange();
    range.selectNodeContents(paragraph);
    range.collapse(false);
    placeSelectionAtRange(range);
    return range;
  }

  function placeCaretNearPageEnd() {
    const paragraph = ensureEditorTrailingParagraph();
    return placeCaretInsideParagraph(paragraph);
  }

  function getDirectEditorChildFromNode(node) {
    let current = node && node.nodeType === Node.ELEMENT_NODE ? node : node && node.parentElement;
    while (current && current.parentNode !== editor) {
      current = current.parentElement;
    }
    return current && current.parentNode === editor ? current : null;
  }

  function getDropRangeFromPoint(clientX, clientY) {
    let range = null;
    if (document.caretPositionFromPoint) {
      const pos = document.caretPositionFromPoint(clientX, clientY);
      if (pos) {
        range = document.createRange();
        range.setStart(pos.offsetNode, pos.offset);
        range.collapse(true);
      }
    } else if (document.caretRangeFromPoint) {
      range = document.caretRangeFromPoint(clientX, clientY);
    }

    if (!range) return null;

    let node = range.startContainer.nodeType === Node.ELEMENT_NODE
      ? range.startContainer
      : range.startContainer.parentElement;

    while (node && node !== editor) {
      if (node.classList && (
        node.classList.contains('image-card') ||
        node.classList.contains('image-caption') ||
        node.classList.contains('image-context-menu') ||
        node.classList.contains('image-card-more') ||
        node.classList.contains('image-card-drag')
      )) {
        return null;
      }
      node = node.parentElement;
    }

    return node === editor ? range : null;
  }

  function stopAutoScroll() {
    if (autoScrollTimer) {
      clearInterval(autoScrollTimer);
      autoScrollTimer = null;
    }
    autoScrollDelta = 0;
  }

  function maybeAutoScroll(clientY) {
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
    const edgeThreshold = 72;
    let delta = 0;

    if (clientY < edgeThreshold) {
      const ratio = (edgeThreshold - clientY) / edgeThreshold;
      delta = -(3 + (ratio * 7));
    } else if (clientY > viewportHeight - edgeThreshold) {
      const ratio = (clientY - (viewportHeight - edgeThreshold)) / edgeThreshold;
      delta = 3 + (ratio * 7);
    }

    if (delta === 0) {
      stopAutoScroll();
      return;
    }

    autoScrollDelta = delta;
    if (autoScrollTimer) return;
    autoScrollTimer = setInterval(function() {
      if (!draggingFigure || autoScrollDelta === 0) {
        stopAutoScroll();
        return;
      }
      window.scrollBy(0, autoScrollDelta);
      if (draggingFigure) {
        const currentTop = parseFloat(draggingFigure.style.top || '0');
        draggingFigure.style.top = (currentTop + autoScrollDelta) + 'px';
      }
    }, 20);
  }

  function touchDistance(touchA, touchB) {
    const dx = touchA.clientX - touchB.clientX;
    const dy = touchA.clientY - touchB.clientY;
    return Math.sqrt((dx * dx) + (dy * dy));
  }

  function applyImageZoom(img, scale) {
    if (!img) return;
    const bounded = Math.min(Math.max(scale, 1), 3);
    img.style.transform = 'scale(' + bounded + ')';
    img.setAttribute('data-zoom-scale', String(bounded));
  }

  function resetImageZoom(img) {
    if (!img) return;
    img.style.transform = '';
    img.removeAttribute('data-zoom-scale');
  }

  function handleImageTouchStart(event) {
    const img = event.target.closest('img');
    if (!img) return;
    const figure = img.closest('.image-card');
    if (!figure) return;

    if (event.touches.length === 2) {
      clearLongPressTimer();
      pinchState = {
        img: img,
        startDistance: touchDistance(event.touches[0], event.touches[1]),
        startScale: Number(img.getAttribute('data-zoom-scale') || '1')
      };
      return;
    }

    clearLongPressTimer();
  }

  function handleImageTouchMove(event) {
    const img = event.target.closest && event.target.closest('img');

    if (pinchState && img && event.touches.length === 2 && pinchState.img === img) {
      const distance = touchDistance(event.touches[0], event.touches[1]);
      const scale = pinchState.startScale * (distance / pinchState.startDistance);
      applyImageZoom(img, scale);
      event.preventDefault();
      return;
    }

    if (draggingFigure && event.touches.length === 1) {
      reorderDraggedFigure(event.touches[0].clientX, event.touches[0].clientY);
      event.preventDefault();
      return;
    }

    clearLongPressTimer();
  }

  function handleImageTouchEnd(event) {
    clearLongPressTimer();
    if (pinchState && (!event.touches || event.touches.length < 2)) {
      pinchState = null;
    }
    if (draggingFigure && (!event.touches || event.touches.length === 0)) {
      const changedTouch = event.changedTouches && event.changedTouches.length > 0
        ? event.changedTouches[0]
        : null;
      endDragFigure(
        changedTouch ? changedTouch.clientX : undefined,
        changedTouch ? changedTouch.clientY : undefined
      );
    } else if (!event.touches || event.touches.length === 0) {
      stopAutoScroll();
    }
  }

  titleInput.addEventListener('input', notifyTitleChange);
  autoResizeTitle();
  if (visitDateLabel) {
    visitDateLabel.addEventListener('contextmenu', function(event) {
      event.preventDefault();
      if (window.AndroidBridge && window.AndroidBridge.onVisitDateLongPressed) {
        window.AndroidBridge.onVisitDateLongPressed();
      }
    });
    visitDateLabel.addEventListener('mousedown', function() {
      clearVisitDatePressTimer();
      visitDatePressTimer = setTimeout(function() {
        if (window.AndroidBridge && window.AndroidBridge.onVisitDateLongPressed) {
          window.AndroidBridge.onVisitDateLongPressed();
        }
      }, 450);
    });
    visitDateLabel.addEventListener('mouseup', clearVisitDatePressTimer);
    visitDateLabel.addEventListener('mouseleave', clearVisitDatePressTimer);
    visitDateLabel.addEventListener('touchstart', function(event) {
      clearVisitDatePressTimer();
      visitDatePressTimer = setTimeout(function() {
        if (window.AndroidBridge && window.AndroidBridge.onVisitDateLongPressed) {
          window.AndroidBridge.onVisitDateLongPressed();
        }
      }, 450);
      event.preventDefault();
    }, { passive: false });
    visitDateLabel.addEventListener('touchend', clearVisitDatePressTimer, { passive: true });
    visitDateLabel.addEventListener('touchcancel', clearVisitDatePressTimer, { passive: true });
  }

  editor.addEventListener('input', notifyChange);
  editor.addEventListener('keyup', notifyChange);
  editor.addEventListener('mouseup', notifyChange);
  editor.addEventListener('paste', function() { setTimeout(notifyChange, 0); });
  editor.addEventListener('focusin', function(event) {
    scrollFocusedElementIntoView(event.target);
  });
  titleInput.addEventListener('focus', function() {
    scrollFocusedElementIntoView(titleInput);
  });
  editor.addEventListener('touchstart', handleImageTouchStart, { passive: false });
  editor.addEventListener('touchmove', handleImageTouchMove, { passive: false });
  editor.addEventListener('touchend', handleImageTouchEnd, { passive: false });
  editor.addEventListener('touchcancel', handleImageTouchEnd, { passive: false });
  editor.addEventListener('click', function(event) {
    const actionButton = event.target.closest('[data-image-action]');
    if (!actionButton) {
      const onImageCard = event.target.closest('.image-card');
      if (!onImageCard) {
        hideImageMenu();
        if (event.target === editor) {
          placeCaretNearPageEnd();
        }
        if (event.target.closest && event.target.closest('.page-end-spacer')) {
          placeCaretNearPageEnd();
        }
      }
      return;
    }

    const figure = actionButton.closest('.image-card');
    if (!figure) return;

    event.preventDefault();
    event.stopPropagation();

    const action = actionButton.getAttribute('data-image-action');
    if (action === 'drag-handle') {
      return;
    }
    if (action === 'menu') {
      if (figure.classList.contains('show-menu')) {
        hideImageMenu();
      } else {
        showImageMenu(figure);
      }
      return;
    }

    if (action === 'copy') {
      duplicateImageFigure(figure);
      notifyChange();
      return;
    }

    if (action === 'cut') {
      const next = figure.nextElementSibling;
      figure.remove();
      if (next && next.tagName === 'P' && (next.textContent || '').trim() === '') {
        next.remove();
      }
      notifyChange();
      return;
    }

    if (action === 'remove') {
      const next = figure.nextElementSibling;
      figure.remove();
      if (next && next.tagName === 'P' && (next.textContent || '').trim() === '') {
        next.remove();
      }
      notifyChange();
      return;
    }

  });
  editor.addEventListener('touchstart', function(event) {
    const dragHandle = event.target.closest('[data-image-action="drag-handle"]');
    if (!dragHandle || event.touches.length !== 1 || draggingFigure) return;
    const figure = dragHandle.closest('.image-card');
    if (!figure) return;
    beginDragFigure(figure, event.touches[0].clientX, event.touches[0].clientY);
    event.preventDefault();
  }, { passive: false });
  editor.addEventListener('touchend', function(event) {
    if (draggingFigure) return;
    if (event.target === editor || (event.target.closest && event.target.closest('.page-end-spacer'))) {
      placeCaretNearPageEnd();
    }
  });
  document.addEventListener('selectionchange', function() {
    if (document.activeElement === editor || editor.contains(document.activeElement)) {
      notifyFormattingChange();
    }
  });

  function ensureSelectionInsideEditor() {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return null;

    const range = sel.getRangeAt(0);
    let node = range.commonAncestorContainer;
    while (node) {
      if (node === editor) return range;
      node = node.parentNode;
    }
    return null;
  }

  function placeCaretAtEnd() {
    editor.focus();
    const lastEditable = ensureEditorTrailingParagraph();
    const range = document.createRange();
    range.selectNodeContents(lastEditable || editor);
    range.collapse(false);
    placeSelectionAtRange(range);
    return range;
  }

  function exec(command, value) {
    editor.focus();
    if (!ensureSelectionInsideEditor()) {
      placeCaretAtEnd();
    }
    document.execCommand(command, false, value || null);
    notifyChange();
  }

  window.NowseiEditor = {
    setTitle: function(title) {
      if (document.activeElement === titleInput) return;
      const normalizedTitle = String(title || '')
        .replace(/\u00A0/g, ' ')
        .replace(/\u200B/g, '');
      if (titleInput.value !== normalizedTitle) {
        titleInput.value = normalizedTitle;
      }
      autoResizeTitle();
    },

    setVisitDate: function(visitDate) {
      if (visitDateLabel) {
        visitDateLabel.textContent = visitDate || '';
      }
    },

    setHtml: function(html) {
      editor.innerHTML = html || "";
      normalizeEditor();
    },

    markReady: function() {
      markReady();
    },

    getHtml: function() {
      normalizeEditor();
      return getSanitizedHtml();
    },

    notifyHtml: function() {
      notifyChange();
    },

    focus: function() {
      editor.focus();
    },

    exec: function(command, value) {
      exec(command, value);
    },

    adjustFontSize: function(delta) {
      editor.focus();
      if (!ensureSelectionInsideEditor()) {
        placeCaretAtEnd();
      }
      const currentLevel = inferFontSizeLevel(
        document.queryCommandValue('fontSize'),
        window.getComputedStyle(getSelectionAnchorElement()).fontSize
      );
      document.execCommand('styleWithCSS', false, false);
      document.execCommand('fontSize', false, String(clampFontSizeLevel(currentLevel + delta)));
      notifyChange();
    },

    setTextColor: function(color) {
      editor.focus();
      if (!ensureSelectionInsideEditor()) {
        placeCaretAtEnd();
      }
      document.execCommand('styleWithCSS', false, true);
      document.execCommand('foreColor', false, color || '#1f2937');
      notifyChange();
    },

    insertHtml: function(html) {
      editor.focus();

      let range = ensureSelectionInsideEditor();
      if (!range) range = placeCaretAtEnd();

      range.collapse(false);

      const temp = document.createElement('div');
      temp.innerHTML = html || "";

      const frag = document.createDocumentFragment();
      let lastNode = null;

      while (temp.firstChild) {
        lastNode = frag.appendChild(temp.firstChild);
      }

      range.insertNode(frag);

      if (lastNode) {
        const sel = window.getSelection();
        const newRange = document.createRange();
        newRange.setStartAfter(lastNode);
        newRange.collapse(true);
        sel.removeAllRanges();
        sel.addRange(newRange);
      }

      notifyChange();
    },

    insertImage: function(src) {
      editor.focus();

      let range = ensureSelectionInsideEditor();
      if (!range) range = placeCaretAtEnd();
      range.collapse(false);

      const anchorNode = range.startContainer.nodeType === Node.TEXT_NODE
        ? range.startContainer.parentElement
        : range.startContainer;
      const currentBlock = getBlockElement(anchorNode);
      const insertId = 'img-' + Date.now() + '-' + Math.random().toString(16).slice(2);
      const caretId = 'caret-' + Date.now() + '-' + Math.random().toString(16).slice(2);
      const figureHtml = '' +
        '<figure class="image-card" data-insert-id="' + insertId + '">' +
        buildImageMenu() +
        '<img src="' + escapeHtmlAttribute(src) + '" contenteditable="false">' +
        '<figcaption class="image-caption">' + buildImageCaption(editor.querySelectorAll('.image-card').length + 1, '') + '</figcaption>' +
        '</figure>';
      const spacerHtml = '<p data-caret-id="' + caretId + '"><br></p>';

      if (isEmptyParagraph(currentBlock)) {
        currentBlock.insertAdjacentHTML('beforebegin', figureHtml + spacerHtml);
        currentBlock.remove();
      } else {
        document.execCommand('insertHTML', false, figureHtml + spacerHtml);
      }

      syncImageChrome();
      renumberImages();

      const figure = editor.querySelector('[data-insert-id="' + insertId + '"]');
      if (figure) {
        figure.removeAttribute('data-insert-id');
      }
      const spacer = editor.querySelector('[data-caret-id="' + caretId + '"]') || ensureEditorTrailingParagraph();
      if (spacer) {
        spacer.removeAttribute('data-caret-id');
      }

      const sel = window.getSelection();
      const newRange = document.createRange();
      newRange.selectNodeContents(spacer || editor);
      newRange.collapse(true);
      sel.removeAllRanges();
      sel.addRange(newRange);

      notifyChange();
    }
  };
})();
</script>
</body>
</html>
""".trimIndent()
