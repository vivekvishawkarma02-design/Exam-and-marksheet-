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
import com.example.domain.Phase6DateUtils
import com.example.domain.TeacherMonthlyAttendanceSummaryRow
import com.example.domain.TeacherWorkloadReportRow
import com.example.domain.WorkloadReportPeriodMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class WorkloadReportPrintBundle(
    val headerSchool: SchoolEntity,
    val scopeTitle: String,
    val session: String,
    val referenceDateIso: String,
    val periodMode: WorkloadReportPeriodMode,
    val rows: List<TeacherWorkloadReportRow>
)

data class TeacherAttendanceMonthlyPrintBundle(
    val school: SchoolEntity,
    val session: String,
    val monthLabel: String,
    val year: Int,
    val month1Based: Int,
    val rows: List<TeacherMonthlyAttendanceSummaryRow>
)

/**
 * Native Android A4 Landscape Vector PDF Generator for Teacher Workload Reports (Phase 7).
 * Matches the dark navy-slate header + school logo style of existing report cards and timetable PDFs.
 * Works 100% offline.
 */
object WorkloadReportPdfGenerator {

    const val PAGE_W = 842
    const val PAGE_H = 595
    private const val MARGIN = 22f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.02f

    private val COLOR_BAND = Color.parseColor("#243447")
    private val COLOR_SUB_BAND = Color.parseColor("#3B4D61")
    private val COLOR_TINT = Color.parseColor("#E6ECF2")
    private val COLOR_ZEBRA = Color.parseColor("#F7F9FB")
    private val COLOR_BORDER = Color.parseColor("#526171")
    private val COLOR_TEXT = Color.parseColor("#111111")
    private val COLOR_MUTED = Color.parseColor("#475569")

    fun generateWorkloadPdf(
        context: Context,
        bundle: WorkloadReportPrintBundle
    ): File {
        val dir = File(context.cacheDir, "phase7_reports").apply { mkdirs() }
        val safeSchool = bundle.headerSchool.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "Organisation" }
        val safeDate = bundle.referenceDateIso.replace(Regex("[^0-9-]+"), "")
        val outFile = File(dir, "Teacher_Workload_${safeSchool}_${bundle.periodMode.name}_${safeDate}.pdf")

        val pdf = PdfDocument()
        try {
            val rowsPerPage = 12
            val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawWorkloadPage(page.canvas, bundle, chunk, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderWorkloadPagesToBitmapsForVerification(
        bundle: WorkloadReportPrintBundle
    ): List<Bitmap> {
        val rowsPerPage = 12
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(PAGE_W, PAGE_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawWorkloadPage(canvas, bundle, chunk, idx + 1, chunks.size)
            bmp
        }
    }

    private fun drawWorkloadPage(
        canvas: Canvas,
        bundle: WorkloadReportPrintBundle,
        pageRows: List<TeacherWorkloadReportRow>,
        pageNumber: Int,
        totalPages: Int
    ) {
        canvas.drawColor(Color.WHITE)
        val contentW = PAGE_W - 2f * MARGIN
        var y = MARGIN

        // 1. Header Band with optional school logo
        val headerH = 62f
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BAND }
        canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + contentW, y + headerH), 4f, 4f, bandPaint)

        var textStartX = MARGIN + 12f
        val logoBmp = ImageUtils.decodeBase64ToBitmap(bundle.headerSchool.logoBase64)
        if (logoBmp != null) {
            val logoSize = 44f
            canvas.drawBitmap(
                logoBmp,
                null,
                RectF(MARGIN + 10f, y + 9f, MARGIN + 10f + logoSize, y + 9f + logoSize),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            textStartX = MARGIN + 62f
            logoBmp.recycle()
        }

        val maxHeaderTextW = contentW - (textStartX - MARGIN) - 12f
        drawScaledText(
            canvas,
            bundle.headerSchool.name.ifBlank { "School" }.uppercase(),
            textStartX,
            y + 22f,
            13f,
            Color.WHITE,
            bold = true,
            maxWidth = maxHeaderTextW
        )
        val subtitle = "TEACHER WORKLOAD REPORT  •  ${bundle.scopeTitle}  •  ${bundle.periodMode.label} (${Phase6DateUtils.formatDisplayDate(bundle.referenceDateIso)})  •  Session ${bundle.session}"
        drawScaledText(
            canvas,
            subtitle,
            textStartX,
            y + 40f,
            9.0f,
            Color.parseColor("#E2E8F0"),
            bold = true,
            maxWidth = maxHeaderTextW
        )
        val totalsLine = "Teachers: ${bundle.rows.size}   |   Weekly Scheduled: ${bundle.rows.sumOf { it.scheduledPeriodsPerWeek }}   |   Subs (Week / Month): ${bundle.rows.sumOf { it.substitutionsThisWeek }} / ${bundle.rows.sumOf { it.substitutionsThisMonth }}"
        drawScaledText(
            canvas,
            totalsLine,
            textStartX,
            y + 54f,
            8.0f,
            Color.parseColor("#CBD5E1"),
            bold = false,
            maxWidth = maxHeaderTextW
        )

        y += headerH + 10f

        // 2. Table Headers
        val cols = floatArrayOf(128f, 118f, 248f, 58f, 54f, 58f, 62f, contentW - 726f)
        val headers = listOf(
            "Teacher",
            "Schools",
            "Periods/Week (Class & Subject)",
            "Sched/Wk",
            "Free/Wk",
            "Subs Wk",
            "Subs Mo",
            if (bundle.periodMode == WorkloadReportPeriodMode.WEEK) "Total (Wk)" else "Total (Mo)"
        )
        val headerRowH = 24f
        drawTableRow(canvas, MARGIN, y, headerRowH, cols, headers, COLOR_SUB_BAND, Color.WHITE, bold = true, textSize = 7.6f, leftCols = setOf(0, 1, 2))
        y += headerRowH

        if (pageRows.isEmpty()) {
            val emptyRowH = 32f
            drawTableRow(
                canvas,
                MARGIN,
                y,
                emptyRowH,
                floatArrayOf(contentW),
                listOf("No teachers match the selected filters."),
                COLOR_ZEBRA,
                COLOR_MUTED,
                bold = false,
                textSize = 8.5f,
                leftCols = emptySet()
            )
            y += emptyRowH
        } else {
            val rowH = 34f
            pageRows.forEachIndexed { idx, row ->
                val bg = if (idx % 2 == 1) COLOR_ZEBRA else Color.WHITE
                val schoolsText = row.schoolNames.joinToString(", ").ifBlank { "—" }
                val breakdownText = if (row.classSubjectBreakdown.isEmpty()) {
                    "No periods assigned"
                } else {
                    row.classSubjectBreakdown.joinToString("; ") {
                        "${it.className}-${it.section} ${it.subjectName} (${it.periodsPerWeek})"
                    }
                }
                val totalLoad = if (bundle.periodMode == WorkloadReportPeriodMode.WEEK) {
                    row.totalLoadThisWeek
                } else {
                    row.totalLoadThisMonth
                }
                val values = listOf(
                    row.teacherName,
                    schoolsText,
                    breakdownText,
                    row.scheduledPeriodsPerWeek.toString(),
                    row.freePeriodsPerWeek.toString(),
                    row.substitutionsThisWeek.toString(),
                    row.substitutionsThisMonth.toString(),
                    totalLoad.toString()
                )
                drawTableRow(canvas, MARGIN, y, rowH, cols, values, bg, COLOR_TEXT, bold = false, textSize = 7.3f, leftCols = setOf(0, 1, 2))
                y += rowH
            }
        }

        // 3. Footer
        val footerY = PAGE_H - MARGIN
        drawScaledText(
            canvas,
            "Generated offline  •  Page $pageNumber of $totalPages",
            MARGIN,
            footerY,
            7.5f,
            COLOR_MUTED,
            bold = false,
            maxWidth = contentW / 2f
        )
        drawScaledText(
            canvas,
            "Principal / Timetable Coordinator Signature",
            MARGIN + contentW - 190f,
            footerY,
            7.5f,
            COLOR_BAND,
            bold = true,
            maxWidth = 190f
        )
    }

    private fun drawTableRow(
        canvas: Canvas,
        startX: Float,
        topY: Float,
        rowH: Float,
        colWidths: FloatArray,
        values: List<String>,
        bgColor: Int,
        textColor: Int,
        bold: Boolean,
        textSize: Float,
        leftCols: Set<Int>
    ) {
        val fillPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        var x = startX
        colWidths.forEachIndexed { idx, w ->
            canvas.drawRect(x, topY, x + w, topY + rowH, fillPaint)
            canvas.drawRect(x, topY, x + w, topY + rowH, strokePaint)
            val txt = values.getOrElse(idx) { "" }
            if (txt.isNotEmpty()) {
                val maxW = (w - 8f).coerceAtLeast(10f)
                if (idx in leftCols && rowH >= 30f && txt.length > 34) {
                    val lines = wrapTwoLines(txt, maxW, textSize, bold)
                    val firstY = topY + (rowH / 2f) - 2f
                    drawScaledText(canvas, lines.first(), x + 4f, firstY, textSize, textColor, bold, maxW)
                    if (lines.size > 1) {
                        drawScaledText(canvas, lines[1], x + 4f, firstY + textSize + 3.5f, textSize, textColor, bold, maxW)
                    }
                } else {
                    val baseline = topY + (rowH / 2f) + (textSize * 0.35f)
                    if (idx in leftCols) {
                        drawScaledText(canvas, txt, x + 4f, baseline, textSize, textColor, bold, maxW)
                    } else {
                        drawScaledCenteredText(canvas, txt, x + w / 2f, baseline, textSize, textColor, bold, maxW)
                    }
                }
            }
            x += w
        }
    }

    private fun wrapTwoLines(text: String, maxWidth: Float, sizePt: Float, bold: Boolean): List<String> {
        val paint = makePaint(sizePt, COLOR_TEXT, bold)
        if (measureTextPt(text, paint) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val line1 = StringBuilder()
        val line2 = StringBuilder()
        var onSecond = false
        for (w in words) {
            if (!onSecond) {
                val cand = if (line1.isEmpty()) w else "$line1 $w"
                if (measureTextPt(cand, paint) <= maxWidth) {
                    if (line1.isNotEmpty()) line1.append(' ')
                    line1.append(w)
                } else {
                    onSecond = true
                    line2.append(w)
                }
            } else {
                if (line2.isNotEmpty()) line2.append(' ')
                line2.append(w)
            }
        }
        return listOf(line1.toString().ifBlank { text }, line2.toString()).filter { it.isNotBlank() }
    }

    private fun makePaint(sizePt: Float, color: Int, bold: Boolean, align: Paint.Align = Paint.Align.LEFT): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            textSize = sizePt * TEXT_SCALE
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
            letterSpacing = LETTER_SPACING_EM
            hinting = Paint.HINTING_OFF
        }

    private fun measureTextPt(text: String, paint: Paint): Float = paint.measureText(text) / TEXT_SCALE

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (measureTextPt(text, paint) <= maxWidth) return text
        var end = text.length
        while (end > 1 && measureTextPt(text.substring(0, end) + "…", paint) > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun drawScaledText(canvas: Canvas, text: String, x: Float, baselineY: Float, sizePt: Float, color: Int, bold: Boolean, maxWidth: Float) {
        val paint = makePaint(sizePt, color, bold, Paint.Align.LEFT)
        val safe = ellipsize(text, paint, maxWidth)
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(safe, x * TEXT_SCALE, baselineY * TEXT_SCALE, paint)
        canvas.restore()
    }

    private fun drawScaledCenteredText(canvas: Canvas, text: String, cx: Float, baselineY: Float, sizePt: Float, color: Int, bold: Boolean, maxWidth: Float) {
        val paint = makePaint(sizePt, color, bold, Paint.Align.CENTER)
        val safe = ellipsize(text, paint, maxWidth)
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(safe, cx * TEXT_SCALE, baselineY * TEXT_SCALE, paint)
        canvas.restore()
    }
}

/**
 * Native Android A4 Portrait Vector PDF Generator for Monthly Teacher Attendance Summary (Phase 7).
 * Works 100% offline with crisp dark-slate styling and school logo header.
 */
object TeacherAttendancePdfGenerator {

    const val PAGE_W = 595
    const val PAGE_H = 842
    private const val MARGIN = 22f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.02f

    private val COLOR_BAND = Color.parseColor("#243447")
    private val COLOR_SUB_BAND = Color.parseColor("#3B4D61")
    private val COLOR_ZEBRA = Color.parseColor("#F7F9FB")
    private val COLOR_BORDER = Color.parseColor("#526171")
    private val COLOR_TEXT = Color.parseColor("#111111")
    private val COLOR_MUTED = Color.parseColor("#475569")

    fun generateMonthlyAttendancePdf(
        context: Context,
        bundle: TeacherAttendanceMonthlyPrintBundle
    ): File {
        val dir = File(context.cacheDir, "phase7_reports").apply { mkdirs() }
        val safeSchool = bundle.school.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "School" }
        val safeMonth = String.format(Locale.US, "%04d_%02d", bundle.year, bundle.month1Based)
        val outFile = File(dir, "Teacher_Attendance_${safeSchool}_${safeMonth}.pdf")

        val pdf = PdfDocument()
        try {
            val rowsPerPage = 22
            val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawAttendancePage(page.canvas, bundle, chunk, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderMonthlyAttendancePagesToBitmapsForVerification(
        bundle: TeacherAttendanceMonthlyPrintBundle
    ): List<Bitmap> {
        val rowsPerPage = 22
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(PAGE_W, PAGE_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawAttendancePage(canvas, bundle, chunk, idx + 1, chunks.size)
            bmp
        }
    }

    private fun drawAttendancePage(
        canvas: Canvas,
        bundle: TeacherAttendanceMonthlyPrintBundle,
        pageRows: List<TeacherMonthlyAttendanceSummaryRow>,
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

        var textStartX = MARGIN + 12f
        val logoBmp = ImageUtils.decodeBase64ToBitmap(bundle.school.logoBase64)
        if (logoBmp != null) {
            val logoSize = 46f
            canvas.drawBitmap(
                logoBmp,
                null,
                RectF(MARGIN + 10f, y + 10f, MARGIN + 10f + logoSize, y + 10f + logoSize),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            textStartX = MARGIN + 64f
            logoBmp.recycle()
        }

        val maxW = contentW - (textStartX - MARGIN) - 12f
        drawScaledText(canvas, bundle.school.name.ifBlank { "School" }.uppercase(), textStartX, y + 23f, 13f, Color.WHITE, true, maxW)
        drawScaledText(
            canvas,
            "MONTHLY TEACHER ATTENDANCE SUMMARY  •  ${bundle.monthLabel}  •  Session ${bundle.session}",
            textStartX,
            y + 41f,
            8.8f,
            Color.parseColor("#E2E8F0"),
            true,
            maxW
        )
        val totals = "Teachers: ${bundle.rows.size}  |  Present: ${bundle.rows.sumOf { it.presentDays }}  |  Half-Day: ${bundle.rows.sumOf { it.halfDays }}  |  On Leave: ${bundle.rows.sumOf { it.onLeaveDays }}  |  Absent: ${bundle.rows.sumOf { it.absentDays }}"
        drawScaledText(canvas, totals, textStartX, y + 56f, 7.8f, Color.parseColor("#CBD5E1"), false, maxW)

        y += headerH + 12f

        // 2. Table Header
        val cols = floatArrayOf(175f, 52f, 54f, 54f, 50f, 54f, 52f, contentW - 491f)
        val headers = listOf("Teacher Name", "Present", "Half-Day", "Leave", "Absent", "Unmarked", "Working", "Att. %")
        val headerHRow = 24f
        drawRow(canvas, MARGIN, y, headerHRow, cols, headers, COLOR_SUB_BAND, Color.WHITE, true, 7.6f)
        y += headerHRow

        if (pageRows.isEmpty()) {
            drawRow(canvas, MARGIN, y, 28f, floatArrayOf(contentW), listOf("No active teachers found for this school."), COLOR_ZEBRA, COLOR_MUTED, false, 8.2f)
        } else {
            val rowH = 26f
            pageRows.forEachIndexed { idx, r ->
                val bg = if (idx % 2 == 1) COLOR_ZEBRA else Color.WHITE
                val pctStr = String.format(Locale.US, "%.1f%%", r.attendancePercent)
                val vals = listOf(
                    r.teacherName,
                    r.presentDays.toString(),
                    r.halfDays.toString(),
                    r.onLeaveDays.toString(),
                    r.absentDays.toString(),
                    r.unmarkedWorkingDays.toString(),
                    r.totalWorkingDays.toString(),
                    pctStr
                )
                drawRow(canvas, MARGIN, y, rowH, cols, vals, bg, COLOR_TEXT, false, 7.8f)
                y += rowH
            }
        }

        // 3. Footer
        val footerY = PAGE_H - MARGIN
        drawScaledText(canvas, "Generated offline  •  Page $pageNumber of $totalPages", MARGIN, footerY, 7.6f, COLOR_MUTED, false, contentW / 2f)
        drawScaledText(canvas, "Principal's Signature", MARGIN + contentW - 120f, footerY, 7.6f, COLOR_BAND, true, 120f)
    }

    private fun drawRow(
        canvas: Canvas,
        startX: Float,
        topY: Float,
        rowH: Float,
        colWidths: FloatArray,
        values: List<String>,
        bgColor: Int,
        textColor: Int,
        bold: Boolean,
        textSize: Float
    ) {
        val fillPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val baseline = topY + (rowH / 2f) + (textSize * 0.35f)
        var x = startX
        colWidths.forEachIndexed { idx, w ->
            canvas.drawRect(x, topY, x + w, topY + rowH, fillPaint)
            canvas.drawRect(x, topY, x + w, topY + rowH, strokePaint)
            val txt = values.getOrElse(idx) { "" }
            val maxW = (w - 8f).coerceAtLeast(8f)
            if (idx == 0) {
                drawScaledText(canvas, txt, x + 5f, baseline, textSize, textColor, bold, maxW)
            } else {
                drawScaledCenteredText(canvas, txt, x + w / 2f, baseline, textSize, textColor, bold, maxW)
            }
            x += w
        }
    }

    private fun makePaint(sizePt: Float, color: Int, bold: Boolean, align: Paint.Align): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            textSize = sizePt * TEXT_SCALE
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
            letterSpacing = LETTER_SPACING_EM
            hinting = Paint.HINTING_OFF
        }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) / TEXT_SCALE <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") / TEXT_SCALE > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun drawScaledText(canvas: Canvas, text: String, x: Float, baselineY: Float, sizePt: Float, color: Int, bold: Boolean, maxWidth: Float) {
        val paint = makePaint(sizePt, color, bold, Paint.Align.LEFT)
        val safe = ellipsize(text, paint, maxWidth)
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(safe, x * TEXT_SCALE, baselineY * TEXT_SCALE, paint)
        canvas.restore()
    }

    private fun drawScaledCenteredText(canvas: Canvas, text: String, cx: Float, baselineY: Float, sizePt: Float, color: Int, bold: Boolean, maxWidth: Float) {
        val paint = makePaint(sizePt, color, bold, Paint.Align.CENTER)
        val safe = ellipsize(text, paint, maxWidth)
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(safe, cx * TEXT_SCALE, baselineY * TEXT_SCALE, paint)
        canvas.restore()
    }
}
