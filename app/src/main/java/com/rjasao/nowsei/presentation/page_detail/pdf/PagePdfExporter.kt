package com.rjasao.nowsei.presentation.page_detail.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.rjasao.nowsei.presentation.page_detail.ImageSizeMode
import com.rjasao.nowsei.presentation.page_detail.PageBlockUi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PagePdfExporter {

    /**
     * Exporta a página para PDF no cache do app e retorna um content:// Uri via FileProvider.
     */
    fun exportToPdf(
        context: Context,
        pageTitle: String,
        blocks: List<PageBlockUi>,
        fileNameHint: String = "nowsei_page"
    ): android.net.Uri {
        val doc = PdfDocument()

        // A4 aproximado em pontos (72dpi)
        val pageWidth = 595
        val pageHeight = 842

        val margin = 36
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
        }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = margin

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun ensureSpace(heightNeeded: Int) {
            if (y + heightNeeded > pageHeight - margin) newPage()
        }

        // Título
        ensureSpace(40)
        canvas.drawText(pageTitle.ifBlank { "Página" }, margin.toFloat(), (y + 20).toFloat(), titlePaint)
        y += 34

        // Data/hora
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Gerado em ${sdf.format(Date())}", margin.toFloat(), (y + 12).toFloat(), smallPaint)
        y += 22

        // Conteúdo
        blocks.forEach { block ->
            when (block) {
                is PageBlockUi.TextBlock -> {
                    val text = block.value.text
                    if (text.isBlank()) {
                        y += 8
                        return@forEach
                    }

                    val layout = staticLayout(text, bodyPaint, contentWidth)
                    ensureSpace(layout.height + 12)

                    canvas.save()
                    canvas.translate(margin.toFloat(), y.toFloat())
                    layout.draw(canvas)
                    canvas.restore()

                    y += layout.height + 12
                }

                is PageBlockUi.ImageBlock -> {
                    val bmp = runCatching { BitmapFactory.decodeFile(block.storedPath) }.getOrNull()
                    if (bmp != null) {
                        val maxW = when (block.sizeMode) {
                            ImageSizeMode.FIT_WIDTH -> contentWidth
                            ImageSizeMode.MEDIUM -> (contentWidth * 0.75f).toInt()
                            ImageSizeMode.SMALL -> (contentWidth * 0.55f).toInt()
                        }

                        val scale = maxW.toFloat() / bmp.width.toFloat()
                        val outW = (bmp.width * scale).toInt()
                        val outH = (bmp.height * scale).toInt()

                        ensureSpace(outH + 22)

                        val dst = Rect(margin, y, margin + outW, y + outH)
                        canvas.drawBitmap(bmp, null, dst, null)
                        y += outH + 8

                        if (block.caption.isNotBlank()) {
                            val capLayout = staticLayout(block.caption, smallPaint, contentWidth)
                            ensureSpace(capLayout.height + 10)

                            canvas.save()
                            canvas.translate(margin.toFloat(), y.toFloat())
                            capLayout.draw(canvas)
                            canvas.restore()

                            y += capLayout.height + 10
                        } else {
                            y += 8
                        }
                    } else {
                        // se falhar a imagem, pula um espaço
                        y += 10
                    }
                }
            }
        }

        doc.finishPage(page)

        val outDir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val safeName = fileNameHint.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(outDir, "$safeName.pdf")

        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    private fun staticLayout(text: String, paint: Paint, width: Int): StaticLayout {
        val textPaint = TextPaint(paint)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                1.15f,
                0f,
                false
            )
        }
    }
}
