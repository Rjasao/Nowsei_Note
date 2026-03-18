package com.rjasao.nowsei.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Otimizador único para TODA imagem que entra no relatório (ANTES do PDF).
 *
 * ✅ Hard cap: garante bater o alvo de bytes (reduz qualidade e, se preciso, reduz resolução).
 * ✅ Grayscale opcional (para scan/documento) reduz muito o tamanho sem perder legibilidade.
 */
object ImageOptimizer {

    private const val TAG = "NowseiImgOpt"

    enum class Target(
        val maxSidePx: Int,
        val targetBytes: Int,
        val grayscale: Boolean
    ) {
        // Bem agressivo (meta ~400KB no PDF)
        GALLERY_20KB(maxSidePx = 720, targetBytes = 20 * 1024, grayscale = false),
        CAMERA_30KB(maxSidePx = 820, targetBytes = 30 * 1024, grayscale = false),
        SCAN_70KB(maxSidePx = 900, targetBytes = 70 * 1024, grayscale = true),
    }

    fun persistOptimizedJpeg(
        context: Context,
        sourceUri: Uri,
        target: Target
    ): Uri? {
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() } ?: return null

        val optimized = compressJpegHardCap(
            input = bytes,
            maxSidePx = target.maxSidePx,
            targetBytes = target.targetBytes,
            grayscale = target.grayscale
        )

        val dir = File(context.filesDir, "page_images").apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

        FileOutputStream(file).use { out ->
            out.write(optimized)
            out.flush()
        }

        Log.d(TAG, "saved ${file.name} size=${optimized.size}B target=${target.name}")
        return file.toUri()
    }

    private fun compressJpegHardCap(
        input: ByteArray,
        maxSidePx: Int,
        targetBytes: Int,
        grayscale: Boolean
    ): ByteArray {
        var side = maxSidePx
        var best: ByteArray = input

        repeat(10) { attempt ->
            val out = compressJpegToTargetOnce(
                input = input,
                maxSidePx = side,
                targetBytes = targetBytes,
                startQuality = 86,
                minQuality = 22,
                grayscale = grayscale
            )
            best = out
            if (out.size <= targetBytes) return out

            side = max((side * 0.78).toInt(), 520)
            Log.d(TAG, "hardcap retry=$attempt newMaxSide=$side size=${out.size} target=$targetBytes")
        }

        return best
    }

    private fun compressJpegToTargetOnce(
        input: ByteArray,
        maxSidePx: Int,
        targetBytes: Int,
        startQuality: Int,
        minQuality: Int,
        grayscale: Boolean
    ): ByteArray {
        // 1) dimensões
        val opts0 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, opts0)

        val srcW = opts0.outWidth.coerceAtLeast(1)
        val srcH = opts0.outHeight.coerceAtLeast(1)

        // 2) downsample no decode
        var sample = 1
        while (srcW / sample > maxSidePx || srcH / sample > maxSidePx) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            // RGB_565 reduz memória e tende a gerar JPG menor
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val decoded = BitmapFactory.decodeByteArray(input, 0, input.size, opts) ?: return input

        // 3) resize fino
        val w = decoded.width.coerceAtLeast(1)
        val h = decoded.height.coerceAtLeast(1)
        val scale = minOf(
            maxSidePx.toFloat() / w.toFloat(),
            maxSidePx.toFloat() / h.toFloat(),
            1f
        )

        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
                true
            )
        } else decoded

        if (resized !== decoded) decoded.recycle()

        val finalBmp = if (grayscale) toGrayscale(resized) else resized
        if (finalBmp !== resized) resized.recycle()

        // 4) qualidade iterativa
        var q = startQuality.coerceIn(minQuality, 95)
        lateinit var out: ByteArray

        while (true) {
            val bos = ByteArrayOutputStream()
            finalBmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
            out = bos.toByteArray()

            if (out.size <= targetBytes || q <= minQuality) break
            q -= 6
        }

        finalBmp.recycle()
        return out
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = android.graphics.Canvas(out)
        val paint = android.graphics.Paint()
        val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        c.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
