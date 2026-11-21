package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.ResolvedSchoolIdentity
import com.example.domain.SchoolIdentityCode
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {
    private const val MAX_BYTES = 145 * 1024 // Strictly under 150 KB
    private const val MAX_DIMENSION_PX = 480

    fun encodeUriToBase64Under150Kb(context: Context, uri: Uri): String? {
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

            val scaled = scaleDownIfNeeded(rawBitmap, MAX_DIMENSION_PX)
            if (scaled !== rawBitmap) rawBitmap.recycle()

            var quality = 85
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 12
            } while (bytes.size > MAX_BYTES && quality >= 25)

            if (bytes.size > MAX_BYTES) {
                val smaller = scaleDownIfNeeded(scaled, 280)
                val out = ByteArrayOutputStream()
                smaller.compress(Bitmap.CompressFormat.JPEG, 60, out)
                bytes = out.toByteArray()
                if (smaller !== scaled) smaller.recycle()
            }
            scaled.recycle()
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

    @DrawableRes
    fun defaultLogoResForIdentity(code: SchoolIdentityCode): Int =
        if (code == SchoolIdentityCode.CPS) R.drawable.logo_cps else R.drawable.logo_cpi

    /**
     * Resolves the logo Bitmap for a school identity:
     * 1. Uploaded custom Base64 logo (if present)
     * 2. Null if explicitly cleared (triggers neat "LOGO" placeholder box)
     * 3. Bundled official CPS or CPI PNG logo otherwise.
     */
    fun resolveSchoolLogoBitmap(context: Context, identity: ResolvedSchoolIdentity): Bitmap? {
        if (identity.logoBase64.isNotBlank()) {
            decodeBase64ToBitmap(identity.logoBase64)?.let { return it }
        }
        if (identity.logoCleared) return null
        val resId = defaultLogoResForIdentity(identity.code)
        return try {
            BitmapFactory.decodeResource(context.resources, resId)
        } catch (_: Exception) {
            vectorDrawableToBitmap(context, R.drawable.ic_school_crest, 144)
        }
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
