package com.rjasao.nowsei.presentation.page_detail.pdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.rjasao.nowsei.presentation.page_detail.PageHtmlTextKind
import com.rjasao.nowsei.presentation.page_detail.PageHtmlToken
import com.rjasao.nowsei.presentation.page_detail.parsePageHtmlTokens
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object PageHtmlPdfExporter {

    private const val TAG = "NowseiPdf"
    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_A4_PT = 842
    private const val PAGE_MARGIN_PT = 24
    private const val CONTENT_WIDTH_PT = PAGE_WIDTH_PT - (PAGE_MARGIN_PT * 2) - 44
    private const val TEXT_GAP_PT = 10
    private const val IMAGE_GAP_PT = 16
    private const val DEFAULT_EDITOR_WIDTH_PX = 820
    private const val MIN_EDITOR_CONTENT_WIDTH_PX = 240

    enum class PdfMode {
        CONTINUOUS_SINGLE_PAGE,
        PAGINATED_A4,
        AUTO
    }

    fun exportHtmlToPdf(
        context: Context,
        pageTitle: String,
        visitDate: String,
        htmlBodyContent: String,
        viewportWidthPx: Int,
        mode: PdfMode = PdfMode.AUTO,
        onSuccess: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onError(IllegalStateException("Contexto sem Activity para exportar PDF."))
            return
        }

        activity.runOnUiThread {
            try {
                val safeTitle = pageTitle.ifBlank { "Relatorio" }
                val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outFile = File(outDir, "${safeTitle.sanitizeFileName()}_${UUID.randomUUID()}.pdf")
                val metrics = PdfTypographyMetrics.fromViewportWidth(viewportWidthPx)
                val blocks = buildRenderableBlocks(context, normalizeImgTags(htmlBodyContent), metrics)
                val pdfMode = if (mode == PdfMode.AUTO) PdfMode.PAGINATED_A4 else mode

                when (pdfMode) {
                    PdfMode.CONTINUOUS_SINGLE_PAGE -> renderContinuousPdf(outFile, safeTitle, visitDate, blocks, metrics)
                    PdfMode.PAGINATED_A4, PdfMode.AUTO -> renderPaginatedPdf(outFile, safeTitle, visitDate, blocks, metrics)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
                onSuccess(uri)
            } catch (t: Throwable) {
                Log.e(TAG, "exportHtmlToPdf failed", t)
                onError(t)
            }
        }
    }

    private fun renderContinuousPdf(
        outFile: File,
        title: String,
        visitDate: String,
        blocks: List<RenderableBlock>,
        metrics: PdfTypographyMetrics
    ) {
        val header = buildHeaderLayouts(title, visitDate, metrics)
        val bodyTop = header.bodyTop
        val bodyHeight = blocks.sumOf { it.totalHeight() + it.spacingAfter }
        val pageHeight = max(bodyTop + bodyHeight + 28 + PAGE_MARGIN_PT, PAGE_HEIGHT_A4_PT)

        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, pageHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            drawPageBackground(canvas, pageInfo.pageWidth.toFloat(), pageInfo.pageHeight.toFloat())
            drawHeader(canvas, header)
            drawBlocksOnSinglePage(canvas, blocks, bodyTop.toFloat())
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun renderPaginatedPdf(
        outFile: File,
        title: String,
        visitDate: String,
        blocks: List<RenderableBlock>,
        metrics: PdfTypographyMetrics
    ) {
        val pdf = PdfDocument()
        try {
            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_A4_PT, pageNumber).create())
            var canvas = page.canvas
            var y = drawPageFrameAndMaybeTitle(canvas, title, visitDate, pageNumber == 1, metrics)
            val bodyBottom = PAGE_HEIGHT_A4_PT - PAGE_MARGIN_PT - 28
            val queue = ArrayDeque(blocks.map { it.copyForRender() })

            while (queue.isNotEmpty()) {
                val block = queue.removeFirst()
                when (block) {
                    is RenderableBlock.TextBlock -> {
                        val remainingHeight = bodyBottom - y.toInt()
                        val consumed = drawTextBlockChunk(canvas, block, y.toInt(), remainingHeight)
                        y += consumed.drawnHeight + block.spacingAfter
                        if (!consumed.finished) {
                            queue.addFirst(block.copy(consumedHeight = consumed.nextConsumedHeight))
                        }
                    }

                    is RenderableBlock.ImageBlock -> {
                        val needed = block.totalHeight()
                        if (y + needed > bodyBottom && y > PAGE_MARGIN_PT + 40) {
                            pdf.finishPage(page)
                            pageNumber += 1
                            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_A4_PT, pageNumber).create())
                            canvas = page.canvas
                            y = drawPageFrameAndMaybeTitle(canvas, title, visitDate, false, metrics)
                        }
                        drawImageBlock(canvas, block, y.toInt())
                        y += needed + block.spacingAfter
                    }
                }

                if (queue.isNotEmpty() && y >= bodyBottom) {
                    pdf.finishPage(page)
                    pageNumber += 1
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_A4_PT, pageNumber).create())
                    canvas = page.canvas
                    y = drawPageFrameAndMaybeTitle(canvas, title, visitDate, false, metrics)
                }
            }

            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun drawPageFrameAndMaybeTitle(
        canvas: Canvas,
        title: String,
        visitDate: String,
        includeTitle: Boolean,
        metrics: PdfTypographyMetrics
    ): Float {
        drawPageBackground(canvas, PAGE_WIDTH_PT.toFloat(), PAGE_HEIGHT_A4_PT.toFloat())
        if (!includeTitle) return (PAGE_MARGIN_PT + 18).toFloat()

        val header = buildHeaderLayouts(title, visitDate, metrics)
        drawHeader(canvas, header)
        return header.bodyTop.toFloat()
    }

    private fun drawPageBackground(canvas: Canvas, pageWidth: Float, pageHeight: Float) {
        val shellRect = RectF(
            PAGE_MARGIN_PT.toFloat(),
            PAGE_MARGIN_PT.toFloat(),
            pageWidth - PAGE_MARGIN_PT,
            pageHeight - PAGE_MARGIN_PT
        )
        val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFDF8") }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6DCC8")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#ECE3D3") }

        canvas.drawRoundRect(shellRect, 10f, 10f, shellPaint)
        canvas.drawRoundRect(shellRect, 10f, 10f, borderPaint)
        canvas.drawRect(shellRect.left, shellRect.top, shellRect.right, shellRect.top + 12f, headerPaint)
        canvas.drawLine(shellRect.left, shellRect.top + 12f, shellRect.right, shellRect.top + 12f, borderPaint)
    }

    private fun drawHeader(canvas: Canvas, header: HeaderLayouts) {
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EFE4D2")
            strokeWidth = 1f
        }
        canvas.save()
        canvas.translate((PAGE_MARGIN_PT + 22).toFloat(), (PAGE_MARGIN_PT + 12 + 26).toFloat())
        header.titleLayout.draw(canvas)
        canvas.restore()
        header.visitDateLayout?.let { visitDateLayout ->
            canvas.save()
            canvas.translate(
                (PAGE_MARGIN_PT + 22).toFloat(),
                (PAGE_MARGIN_PT + 12 + 26 + header.titleLayout.height + 8).toFloat()
            )
            visitDateLayout.draw(canvas)
            canvas.restore()
        }
        val dividerY = header.dividerY
        canvas.drawLine(
            (PAGE_MARGIN_PT + 1).toFloat(),
            dividerY.toFloat(),
            (PAGE_WIDTH_PT - PAGE_MARGIN_PT - 1).toFloat(),
            dividerY.toFloat(),
            dividerPaint
        )
    }

    private fun drawBlocksOnSinglePage(canvas: Canvas, blocks: List<RenderableBlock>, startY: Float) {
        var y = startY.toInt()
        blocks.forEach { block ->
            when (block) {
                is RenderableBlock.TextBlock -> {
                    drawTextBlockChunk(canvas, block, y, Int.MAX_VALUE)
                    y += block.layout.height + block.spacingAfter
                }
                is RenderableBlock.ImageBlock -> {
                    drawImageBlock(canvas, block, y)
                    y += block.totalHeight() + block.spacingAfter
                }
            }
        }
    }

    private data class TextDrawResult(val drawnHeight: Int, val nextConsumedHeight: Int, val finished: Boolean)

    private fun drawTextBlockChunk(canvas: Canvas, block: RenderableBlock.TextBlock, top: Int, availableHeight: Int): TextDrawResult {
        if (availableHeight <= 0) return TextDrawResult(0, block.consumedHeight, false)
        val layout = block.layout
        if (block.consumedHeight >= layout.height) return TextDrawResult(0, layout.height, true)

        val remainingHeight = layout.height - block.consumedHeight
        if (remainingHeight <= availableHeight) {
            canvas.save()
            canvas.translate((PAGE_MARGIN_PT + 22).toFloat(), (top - block.consumedHeight).toFloat())
            layout.draw(canvas)
            canvas.restore()
            return TextDrawResult(remainingHeight, layout.height, true)
        }

        val targetBottom = block.consumedHeight + availableHeight
        val endLine = min(layout.lineCount - 1, layout.getLineForVertical(targetBottom))
        val endHeight = layout.getLineBottom(endLine)
        val drawnHeight = max(endHeight - block.consumedHeight, 0)

        canvas.save()
        canvas.clipRect(
            (PAGE_MARGIN_PT + 22).toFloat(),
            top.toFloat(),
            (PAGE_WIDTH_PT - PAGE_MARGIN_PT - 22).toFloat(),
            (top + drawnHeight).toFloat()
        )
        canvas.translate((PAGE_MARGIN_PT + 22).toFloat(), (top - block.consumedHeight).toFloat())
        layout.draw(canvas)
        canvas.restore()

        return TextDrawResult(drawnHeight, endHeight, endHeight >= layout.height)
    }

    private fun drawImageBlock(canvas: Canvas, block: RenderableBlock.ImageBlock, top: Int) {
        val left = PAGE_MARGIN_PT + 22
        val right = PAGE_WIDTH_PT - PAGE_MARGIN_PT - 22
        val rect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), (top + block.totalHeight()).toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFAF2") }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EADFCA")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EFE4D2")
            strokeWidth = 1f
        }

        canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
        canvas.drawRoundRect(rect, 16f, 16f, borderPaint)

        val imageLeft = left + 10
        val imageTop = top + 10
        val imageRight = right - 10
        val imageBottom = imageTop + block.imageHeight
        canvas.drawBitmap(block.bitmap, null, RectF(imageLeft.toFloat(), imageTop.toFloat(), imageRight.toFloat(), imageBottom.toFloat()), null)

        val dividerY = imageBottom + 10
        canvas.drawLine((left + 10).toFloat(), dividerY.toFloat(), (right - 10).toFloat(), dividerY.toFloat(), dividerPaint)

        canvas.save()
        canvas.translate((left + 10).toFloat(), (dividerY + 10).toFloat())
        block.captionLayout.draw(canvas)
        canvas.restore()
    }

    private sealed class RenderableBlock(open val spacingAfter: Int) {
        abstract fun totalHeight(): Int
        abstract fun copyForRender(): RenderableBlock

        data class TextBlock(
            val layout: StaticLayout,
            val consumedHeight: Int = 0,
            override val spacingAfter: Int = TEXT_GAP_PT
        ) : RenderableBlock(spacingAfter) {
            override fun totalHeight(): Int = layout.height
            override fun copyForRender(): RenderableBlock = copy()
        }

        data class ImageBlock(
            val bitmap: Bitmap,
            val imageHeight: Int,
            val captionLayout: StaticLayout,
            override val spacingAfter: Int = IMAGE_GAP_PT
        ) : RenderableBlock(spacingAfter) {
            override fun totalHeight(): Int = 10 + imageHeight + 10 + 10 + captionLayout.height + 10
            override fun copyForRender(): RenderableBlock = this
        }
    }

    private fun buildRenderableBlocks(
        context: Context,
        html: String,
        metrics: PdfTypographyMetrics
    ): List<RenderableBlock> {
        val blocks = mutableListOf<RenderableBlock>()
        parsePageHtmlTokens(html).forEach { token ->
            when (token) {
                is PageHtmlToken.Image -> parseImageBlock(context, token.src, token.caption, metrics)?.let(blocks::add)
                is PageHtmlToken.Text -> buildTextBlock(token, metrics)?.let(blocks::add)
            }
        }
        return blocks
    }

    private fun buildTextBlock(token: PageHtmlToken.Text, metrics: PdfTypographyMetrics): RenderableBlock.TextBlock? {
        if (token.text.isBlank()) return null
        val styledText = htmlToPdfText(token)
        return when (token.kind) {
            PageHtmlTextKind.HEADING_2 -> RenderableBlock.TextBlock(
                buildTextLayout(styledText, heading2Paint(metrics), CONTENT_WIDTH_PT),
                spacingAfter = metrics.headingGap
            )
            PageHtmlTextKind.HEADING_3 -> RenderableBlock.TextBlock(
                buildTextLayout(styledText, heading3Paint(metrics), CONTENT_WIDTH_PT),
                spacingAfter = metrics.headingGap
            )
            PageHtmlTextKind.LIST_ITEM -> RenderableBlock.TextBlock(
                buildTextLayout(styledText, bodyPaint(metrics), CONTENT_WIDTH_PT),
                spacingAfter = metrics.textGap
            )
            PageHtmlTextKind.PARAGRAPH -> RenderableBlock.TextBlock(
                buildTextLayout(styledText, bodyPaint(metrics), CONTENT_WIDTH_PT),
                spacingAfter = metrics.textGap
            )
        }
    }

    private fun parseImageBlock(
        context: Context,
        src: String,
        caption: String,
        metrics: PdfTypographyMetrics
    ): RenderableBlock.ImageBlock? {
        val bitmap = loadBitmapForPdf(context, src) ?: return null
        val imageWidth = CONTENT_WIDTH_PT - 20
        val imageHeight = max((bitmap.height.toFloat() / bitmap.width.toFloat() * imageWidth.toFloat()).toInt(), 120)
        val captionLayout = buildTextLayout(
            caption.ifBlank { "toque para descrever" },
            captionPaint(metrics),
            CONTENT_WIDTH_PT - 20
        )
        return RenderableBlock.ImageBlock(
            bitmap = bitmap,
            imageHeight = imageHeight,
            captionLayout = captionLayout,
            spacingAfter = metrics.imageGap
        )
    }

    private fun buildTextLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

    private fun htmlToPdfText(token: PageHtmlToken.Text): CharSequence {
        val html = when (token.kind) {
            PageHtmlTextKind.LIST_ITEM -> "<ul>${token.html}</ul>"
            else -> token.html
        }
        val spanned = HtmlCompat.fromHtml(
            html,
            HtmlCompat.FROM_HTML_MODE_LEGACY or HtmlCompat.FROM_HTML_OPTION_USE_CSS_COLORS
        )
        return trimTrailingBreaks(spanned)
    }

    private fun trimTrailingBreaks(text: Spanned): CharSequence {
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) {
            end--
        }
        return text.subSequence(0, end)
    }

    private fun titlePaint(metrics: PdfTypographyMetrics): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        textSize = metrics.titleSize
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    private fun visitDatePaint(metrics: PdfTypographyMetrics): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A6F4D")
        textSize = metrics.visitDateSize
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    private fun bodyPaint(metrics: PdfTypographyMetrics): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        textSize = metrics.bodySize
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private fun heading2Paint(metrics: PdfTypographyMetrics): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = metrics.heading2Size
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private fun heading3Paint(metrics: PdfTypographyMetrics): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = metrics.heading3Size
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private fun captionPaint(metrics: PdfTypographyMetrics): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B4636")
        textSize = metrics.captionSize
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }

    private fun buildHeaderLayouts(
        title: String,
        visitDate: String,
        metrics: PdfTypographyMetrics
    ): HeaderLayouts {
        val titleLayout = buildTextLayout(title, titlePaint(metrics), CONTENT_WIDTH_PT)
        val visitDateLayout = visitDate
            .takeIf { it.isNotBlank() }
            ?.let { buildTextLayout(it, visitDatePaint(metrics), CONTENT_WIDTH_PT) }
        val dividerY = PAGE_MARGIN_PT + 12 + 26 + titleLayout.height +
            if (visitDateLayout != null) 8 + visitDateLayout.height + 12 else 14
        val bodyTop = dividerY + 18
        return HeaderLayouts(
            titleLayout = titleLayout,
            visitDateLayout = visitDateLayout,
            dividerY = dividerY,
            bodyTop = bodyTop
        )
    }

    private data class HeaderLayouts(
        val titleLayout: StaticLayout,
        val visitDateLayout: StaticLayout?,
        val dividerY: Int,
        val bodyTop: Int
    )

    private data class PdfTypographyMetrics(
        val titleSize: Float,
        val visitDateSize: Float,
        val bodySize: Float,
        val heading2Size: Float,
        val heading3Size: Float,
        val captionSize: Float,
        val textGap: Int,
        val headingGap: Int,
        val imageGap: Int
    ) {
        companion object {
            fun fromViewportWidth(viewportWidthPx: Int): PdfTypographyMetrics {
                val editorShellWidth = viewportWidthPx
                    .coerceAtMost(DEFAULT_EDITOR_WIDTH_PX)
                    .coerceAtLeast(MIN_EDITOR_CONTENT_WIDTH_PX + 44)
                val editorContentWidth = (editorShellWidth - 44)
                    .coerceAtLeast(MIN_EDITOR_CONTENT_WIDTH_PX)
                val scale = (CONTENT_WIDTH_PT.toFloat() / editorContentWidth.toFloat())
                    .coerceIn(1.0f, 1.65f)
                return PdfTypographyMetrics(
                    titleSize = 35f * scale,
                    visitDateSize = 20f * scale,
                    bodySize = 22f * scale,
                    heading2Size = 28f * scale,
                    heading3Size = 24f * scale,
                    captionSize = 14f * scale,
                    textGap = max((TEXT_GAP_PT * scale).toInt(), TEXT_GAP_PT),
                    headingGap = max((8f * scale).toInt(), 8),
                    imageGap = max((IMAGE_GAP_PT * scale).toInt(), IMAGE_GAP_PT)
                )
            }
        }
    }

    private fun loadBitmapForPdf(context: Context, rawUri: String): Bitmap? {
        return runCatching {
            val uri = Uri.parse(rawUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, CONTENT_WIDTH_PT)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int): Int {
        var sample = 1
        if (width <= 0 || height <= 0) return sample
        while (width / sample > reqWidth * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun normalizeImgTags(html: String): String {
        if (html.isBlank()) return html
        return html
            .replace(
                Regex(
                    """<div\b[^>]*class\s*=\s*["'][^"']*image-card-toolbar[^"']*["'][^>]*>.*?</div>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ),
                ""
            )
            .replace(Regex("""\scontenteditable\s*=\s*["'][^"']*["']""", RegexOption.IGNORE_CASE), "")
    }

    private fun String.sanitizeFileName(): String {
        return replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), "_")
            .trim('_')
            .ifBlank { "Relatorio" }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
