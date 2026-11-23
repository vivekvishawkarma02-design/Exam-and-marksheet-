package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.SchoolEntity
import com.example.domain.Phase4Validator
import com.example.domain.Phase6DateUtils
import com.example.domain.SubstitutionNeedItem
import java.io.File
import java.io.FileOutputStream

data class SubstitutionSlipPrintBundle(
    val school: SchoolEntity,
    val session: String,
    val date: String,
    val items: List<SubstitutionNeedItem>
)

/**
 * Native Android A4 Portrait Vector PDF Generator for Daily Substitution Slips (Phase 6).
 * Matches TimetablePdfGenerator's clean high-contrast dark-slate style and works 100% offline.
 *
 * Columns: Period & Time | Class | Subject | Absent Teacher | Substitute Teacher | Signature
 */
object SubstitutionPdfGenerator {

    const val PAGE_W = 595
    const val PAGE_H = 842
    private const val MARGIN = 22f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.02f

    private val COLOR_BAND = Color.parseColor("#243447")
    private val COLOR_SUB_BAND = Color.parseColor("#3B4D61")
    private val COLOR_ZEBRA = Color.parseColor("#F7F9FB")
    private val COLOR_WARN_BG = Color.parseColor("#FFF4E5")
    private val COLOR_BORDER = Color.parseColor("#526171")
    private val COLOR_TEXT = Color.parseColor("#111111")
    private val COLOR_MUTED = Color.parseColor("#475569")
    private val COLOR_ALERT = Color.parseColor("#B42318")

    fun generateSubstitutionSlipPdf(
        context: Context,
        bundle: SubstitutionSlipPrintBundle
    ): File {
        val dir = File(context.cacheDir, "timetable_pdfs").apply { mkdirs() }
        val safeSchool = bundle.school.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "School" }
        val safeDate = bundle.date.replace(Regex("[^0-9-]+"), "")
        val outFile = File(dir, "Substitution_Slip_${safeSchool}_${safeDate}.pdf")

        val pdf = PdfDocument()
        try {
            val rowsPerPage = 16
            val itemChunks = if (bundle.items.isEmpty()) {
                listOf(emptyList())
            } else {
                bundle.items.chunked(rowsPerPage)
            }
            val totalPages = itemChunks.size

            itemChunks.forEachIndexed { pageIdx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIdx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawSlipPage(
                    canvas = page.canvas,
                    bundle = bundle,
                    pageItems = chunk,
                    pageNumber = pageIdx + 1,
                    totalPages = totalPages
                )
                pdf.finishPage(page)
            }

            FileOutputStream(outFile).use { fos ->
                pdf.writeTo(fos)
            }
        } finally {
            pdf.close()
        }
        return outFile
    }

    /**
     * Renders every A4 Portrait Substitution Slip page to an Android Bitmap using the exact same
     * [drawSlipPage] routine as [generateSubstitutionSlipPdf], enabling pixel-verified Robolectric tests.
     */
    fun renderSlipPagesToBitmapsForVerification(
        bundle: SubstitutionSlipPrintBundle
    ): List<Bitmap> {
        val rowsPerPage = 16
        val itemChunks = if (bundle.items.isEmpty()) {
            listOf(emptyList())
        } else {
            bundle.items.chunked(rowsPerPage)
        }
        val totalPages = itemChunks.size
        return itemChunks.mapIndexed { pageIdx, chunk ->
            val bmp = Bitmap.createBitmap(PAGE_W, PAGE_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawSlipPage(
                canvas = canvas,
                bundle = bundle,
                pageItems = chunk,
                pageNumber = pageIdx + 1,
                totalPages = totalPages
            )
            bmp
        }
    }

    private fun drawSlipPage(
        canvas: Canvas,
        bundle: SubstitutionSlipPrintBundle,
        pageItems: List<SubstitutionNeedItem>,
        pageNumber: Int,
        totalPages: Int
    ) {
        canvas.drawColor(Color.WHITE)
        val contentW = PAGE_W - 2f * MARGIN
        var y = MARGIN

        // 1. Header Band
        val headerH = 66f
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BAND }
        canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + contentW, y + headerH), 4f, 4f, bandPaint)

        // Optional school logo on left
        var textStartX = MARGIN + 12f
        val logoBitmap: Bitmap? = decodeLogoBitmap(bundle.school.logoBase64)
        if (logoBitmap != null) {
            val logoSize = 46f
            val dest = RectF(MARGIN + 10f, y + 10f, MARGIN + 10f + logoSize, y + 10f + logoSize)
            canvas.drawBitmap(logoBitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
            textStartX = MARGIN + 64f
            logoBitmap.recycle()
        }

        drawScaledText(
            canvas = canvas,
            text = bundle.school.name.ifBlank { "School" }.uppercase(),
            x = textStartX,
            baselineY = y + 24f,
            sizePt = 13f,
            color = Color.WHITE,
            bold = true,
            maxWidth = contentW - (textStartX - MARGIN) - 12f
        )
        val addressLine = listOf(bundle.school.address, bundle.school.mobile)
            .filter { it.isNotBlank() }
            .joinToString("  •  ")
        if (addressLine.isNotBlank()) {
            drawScaledText(
                canvas = canvas,
                text = addressLine,
                x = textStartX,
                baselineY = y + 40f,
                sizePt = 8.5f,
                color = Color.parseColor("#E2E8F0"),
                bold = false,
                maxWidth = contentW - (textStartX - MARGIN) - 12f
            )
        }
        drawScaledText(
            canvas = canvas,
            text = "DAILY SUBSTITUTION SLIP  •  ${Phase6DateUtils.formatDisplayDate(bundle.date)}  •  Session ${bundle.session}",
            x = textStartX,
            baselineY = y + 56f,
            sizePt = 9.5f,
            color = Color.parseColor("#F8FAFC"),
            bold = true,
            maxWidth = contentW - (textStartX - MARGIN) - 12f
        )

        y += headerH + 10f

        // Summary strip
        val assignedCount = bundle.items.count { !it.needsSubstitute }
        val pendingCount = bundle.items.count { it.needsSubstitute }
        val summaryText = "Total Affected Periods: ${bundle.items.size}   |   Assigned: $assignedCount   |   Needs Substitute: $pendingCount"
        drawScaledText(
            canvas = canvas,
            text = summaryText,
            x = MARGIN,
            baselineY = y + 10f,
            sizePt = 8.5f,
            color = COLOR_MUTED,
            bold = true,
            maxWidth = contentW
        )
        y += 18f

        // 2. Table Header
        val colWidths = floatArrayOf(
            contentW * 0.17f, // Period & Time
            contentW * 0.11f, // Class
            contentW * 0.16f, // Subject
            contentW * 0.18f, // Absent Teacher
            contentW * 0.22f, // Substitute Teacher
            contentW * 0.16f  // Signature
        )
        val headers = listOf("Period / Time", "Class", "Subject", "Absent Teacher", "Substitute Teacher", "Signature")
        val headerRowH = 26f

        val subBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SUB_BAND }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        canvas.drawRect(MARGIN, y, MARGIN + contentW, y + headerRowH, subBandPaint)
        canvas.drawRect(MARGIN, y, MARGIN + contentW, y + headerRowH, borderPaint)

        var cx = MARGIN
        headers.forEachIndexed { idx, hText ->
            if (idx > 0) canvas.drawLine(cx, y, cx, y + headerRowH, borderPaint)
            drawScaledText(
                canvas = canvas,
                text = hText,
                x = cx + 4f,
                baselineY = y + 17f,
                sizePt = 8.5f,
                color = Color.WHITE,
                bold = true,
                maxWidth = colWidths[idx] - 8f
            )
            cx += colWidths[idx]
        }
        y += headerRowH

        // 3. Data Rows
        if (pageItems.isEmpty()) {
            val emptyH = 44f
            canvas.drawRect(MARGIN, y, MARGIN + contentW, y + emptyH, borderPaint)
            drawScaledText(
                canvas = canvas,
                text = "No absent teachers or substitutions recorded for ${Phase6DateUtils.formatDisplayDate(bundle.date)}.",
                x = MARGIN + 10f,
                baselineY = y + 26f,
                sizePt = 9.5f,
                color = COLOR_MUTED,
                bold = false,
                maxWidth = contentW - 20f
            )
            y += emptyH
        } else {
            val rowH = 38f
            val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ZEBRA }
            val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_WARN_BG }

            pageItems.forEachIndexed { idx, item ->
                val fillPaint = when {
                    item.needsSubstitute -> warnPaint
                    idx % 2 == 1 -> zebraPaint
                    else -> null
                }
                if (fillPaint != null) {
                    canvas.drawRect(MARGIN, y, MARGIN + contentW, y + rowH, fillPaint)
                }
                canvas.drawRect(MARGIN, y, MARGIN + contentW, y + rowH, borderPaint)

                val timeStr = "${Phase4Validator.formatMinutes(item.startMinutes)}-${Phase4Validator.formatMinutes(item.endMinutes)}"
                val subLine1 = when {
                    item.needsSubstitute -> "NEEDS SUBSTITUTE"
                    else -> item.existingSubstitution?.substituteTeacherName.orEmpty()
                }
                val subLine2 = when {
                    item.assignedSubstituteAlsoAbsent -> "(Prev. sub absent)"
                    item.existingSubstitution?.overrideUsed == true -> "[OVERRIDE]"
                    else -> ""
                }

                var rx = MARGIN
                // Col 0: Period & Time
                drawScaledText(canvas, item.slotLabel, rx + 4f, y + 15f, 8.5f, COLOR_TEXT, true, colWidths[0] - 8f)
                drawScaledText(canvas, timeStr, rx + 4f, y + 29f, 7.5f, COLOR_MUTED, false, colWidths[0] - 8f)
                rx += colWidths[0]
                canvas.drawLine(rx, y, rx, y + rowH, borderPaint)

                // Col 1: Class-Section
                drawScaledText(canvas, "${item.className}-${item.section}", rx + 4f, y + 22f, 8.5f, COLOR_TEXT, true, colWidths[1] - 8f)
                rx += colWidths[1]
                canvas.drawLine(rx, y, rx, y + rowH, borderPaint)

                // Col 2: Subject
                drawScaledText(canvas, item.subjectName, rx + 4f, y + 22f, 8.5f, COLOR_TEXT, false, colWidths[2] - 8f)
                rx += colWidths[2]
                canvas.drawLine(rx, y, rx, y + rowH, borderPaint)

                // Col 3: Absent Teacher
                drawScaledText(canvas, item.absentTeacherName, rx + 4f, y + 16f, 8.5f, COLOR_TEXT, false, colWidths[3] - 8f)
                drawScaledText(canvas, item.leaveReason.label, rx + 4f, y + 29f, 7f, COLOR_MUTED, false, colWidths[3] - 8f)
                rx += colWidths[3]
                canvas.drawLine(rx, y, rx, y + rowH, borderPaint)

                // Col 4: Substitute Teacher
                val subColor = if (item.needsSubstitute) COLOR_ALERT else COLOR_TEXT
                drawScaledText(canvas, subLine1, rx + 4f, y + (if (subLine2.isNotEmpty()) 16f else 22f), 8.5f, subColor, true, colWidths[4] - 8f)
                if (subLine2.isNotEmpty()) {
                    drawScaledText(canvas, subLine2, rx + 4f, y + 29f, 7f, COLOR_ALERT, true, colWidths[4] - 8f)
                }
                rx += colWidths[4]
                canvas.drawLine(rx, y, rx, y + rowH, borderPaint)

                // Col 5: Signature box (blank line for teacher signature)
                val sigLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#CBD5E1")
                    strokeWidth = 0.6f
                }
                canvas.drawLine(rx + 8f, y + rowH - 10f, rx + colWidths[5] - 8f, y + rowH - 10f, sigLinePaint)

                y += rowH
            }
        }

        // 4. Footer: Principal / Coordinator sign-off & Page indicator
        val footerY = PAGE_H - MARGIN - 24f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            strokeWidth = 0.7f
        }
        canvas.drawLine(MARGIN, footerY - 6f, MARGIN + 150f, footerY - 6f, linePaint)
        drawScaledText(canvas, "Timetable In-Charge", MARGIN, footerY + 6f, 8f, COLOR_MUTED, false, 150f)

        canvas.drawLine(MARGIN + contentW - 150f, footerY - 6f, MARGIN + contentW, footerY - 6f, linePaint)
        drawScaledText(canvas, "Principal / Head Signature", MARGIN + contentW - 150f, footerY + 6f, 8f, COLOR_MUTED, false, 150f)

        drawScaledText(
            canvas = canvas,
            text = "Page $pageNumber of $totalPages",
            x = MARGIN + (contentW / 2f) - 26f,
            baselineY = PAGE_H - MARGIN + 4f,
            sizePt = 7.5f,
            color = COLOR_MUTED,
            bold = false,
            maxWidth = 80f
        )
    }

    private fun drawScaledText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        sizePt: Float,
        color: Int,
        bold: Boolean,
        maxWidth: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = sizePt * TEXT_SCALE
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.letterSpacing = LETTER_SPACING_EM
        }
        val clean = ellipsize(text, paint, maxWidth * TEXT_SCALE)
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(clean, x * TEXT_SCALE, baselineY * TEXT_SCALE, paint)
        canvas.restore()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidthPx: Float): String {
        if (text.isEmpty() || maxWidthPx <= 0f) return ""
        if (paint.measureText(text) <= maxWidthPx) return text
        val ellipsis = "…"
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (paint.measureText(text.substring(0, mid) + ellipsis) <= maxWidthPx) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return if (low > 0) text.substring(0, low) + ellipsis else ellipsis
    }

    private fun decodeLogoBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val pure = base64.substringAfter("base64,", base64)
            val bytes = android.util.Base64.decode(pure, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
