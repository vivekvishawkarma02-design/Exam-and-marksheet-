package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {
    const val MAX_LOGO_BYTES = 195 * 1024 // Phase 1 local under 200 KB
    const val MAX_CLOUD_IMAGE_BYTES = 145 * 1024 // Phase 2 Firestore under 150 KB
    private const val MAX_DIMENSION_PX = 512

    fun encodeUriToBase64Under200Kb(context: Context, uri: Uri): String? =
        encodeUriToBase64UnderLimit(context, uri, MAX_LOGO_BYTES)

    fun encodeUriToBase64Under150Kb(context: Context, uri: Uri): String? =
        encodeUriToBase64UnderLimit(context, uri, MAX_CLOUD_IMAGE_BYTES)

    fun encodeUriToBase64UnderLimit(context: Context, uri: Uri, maxBytes: Int): String? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val encoded = encodeBitmapToBase64UnderLimit(rawBitmap, maxBytes)
            rawBitmap.recycle()
            encoded
        } catch (_: Exception) {
            null
        }
    }

    fun encodeBitmapToBase64Under200Kb(source: Bitmap): String? =
        encodeBitmapToBase64UnderLimit(source, MAX_LOGO_BYTES)

    fun encodeBitmapToBase64Under150Kb(source: Bitmap): String? =
        encodeBitmapToBase64UnderLimit(source, MAX_CLOUD_IMAGE_BYTES)

    fun encodeBitmapToBase64UnderLimit(source: Bitmap, maxBytes: Int = MAX_LOGO_BYTES): String? {
        return try {
            val scaled = scaleDownIfNeeded(source, MAX_DIMENSION_PX)
            val usePng = source.hasAlpha() && scaled.width <= 300 && scaled.height <= 300
            var bytes: ByteArray

            if (usePng) {
                val pngOut = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 100, pngOut)
                bytes = pngOut.toByteArray()
                if (bytes.size <= maxBytes) {
                    if (scaled !== source) scaled.recycle()
                    return Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }

            var quality = 86
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > maxBytes && quality >= 25)

            if (bytes.size > maxBytes) {
                val smaller = scaleDownIfNeeded(scaled, 280)
                val out = ByteArrayOutputStream()
                smaller.compress(Bitmap.CompressFormat.JPEG, 60, out)
                bytes = out.toByteArray()
                if (smaller !== scaled) smaller.recycle()
            }
            if (scaled !== source) scaled.recycle()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val newW = (w * ratio).roundToInt().coerceAtLeast(1)
        val newH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    fun decodeBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val cleaned = if (base64.contains(",")) base64.substringAfter(",") else base64
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun estimateDecodedBytes(base64: String?): Int {
        if (base64.isNullOrBlank()) return 0
        val cleaned = if (base64.contains(",")) base64.substringAfter(",") else base64
        return try {
            Base64.decode(cleaned, Base64.DEFAULT).size
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Resolves the logo Bitmap for a school from its stored Base64 image.
     * Returns null when no custom logo is uploaded so reports draw the clean "LOGO" placeholder box.
     */
    fun resolveSchoolLogoBitmap(logoBase64: String?): Bitmap? {
        if (logoBase64.isNullOrBlank()) return null
        return decodeBase64ToBitmap(logoBase64)
    }

    fun vectorDrawableToBitmap(
        context: Context,
        @DrawableRes drawableRes: Int,
        sizePx: Int = 144
    ): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
