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
import com.example.data.StudentEntity
import com.example.domain.DayCalendarStatus
import com.example.domain.LowAttendanceStudentRow
import com.example.domain.Phase6DateUtils
import com.example.domain.SchoolCalendarConfig
import com.example.domain.SchoolCalendarEngine
import com.example.domain.StudentAttendanceEntry
import com.example.domain.StudentAttendanceSheet
import com.example.domain.StudentAttendanceStatus
import com.example.domain.StudentRangeAttendanceSummaryRow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class DailyAttendancePdfBundle(
    val school: SchoolEntity,
    val session: String,
    val className: String,
    val section: String,
    val dateIso: String,
    val classTeacherName: String,
    val dayStatus: DayCalendarStatus,
    val students: List<Pair<String, StudentEntity>>, // (studentDocId, entity)
    val sheet: StudentAttendanceSheet?
)

data class MonthlyRegisterPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val className: String,
    val section: String,
    val year: Int,
    val month1Based: Int,
    val monthLabel: String,
    val classTeacherName: String,
    val calendar: SchoolCalendarConfig,
    val students: List<Pair<String, StudentEntity>>,
    val sheetsByDate: Map<String, StudentAttendanceSheet>
)

data class StudentSummaryRangePdfBundle(
    val school: SchoolEntity,
    val session: String,
    val scopeTitle: String,
    val startDateIso: String,
    val endDateIso: String,
    val calendarWorkingDays: Int,
    val rows: List<StudentRangeAttendanceSummaryRow>
)

data class LowAttendancePdfBundle(
    val school: SchoolEntity,
    val session: String,
    val scopeTitle: String,
    val startDateIso: String,
    val endDateIso: String,
    val thresholdPercent: Double,
    val rows: List<LowAttendanceStudentRow>
)

/**
 * Phase 8 Offline Native Android Vector PDF Generators for Student Attendance:
 * 1. Daily attendance sheet (A4 Portrait)
 * 2. Monthly attendance register (A4 Landscape, 31-day support + 50+ students, fixed row heights,
 *    holidays shaded, P/A/L/Lt/H codes, per-student & per-day totals, paginated with repeated headers)
 * 3. Student-wise summary for a date range (A4 Portrait)
 * 4. Low attendance list for section or whole school (A4 Portrait)
 *
 * Uses 4x Paint text scaling (TEXT_SCALE = 4f) for crisp vector glyphs with zero overlap.
 * Works 100% offline.
 */
object StudentAttendancePdfGenerator {

    const val PORTRAIT_W = 595
    const val PORTRAIT_H = 842
    const val LANDSCAPE_W = 842
    const val LANDSCAPE_H = 595

    private const val PORTRAIT_MARGIN = 24f
    private const val LANDSCAPE_MARGIN = 18f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.015f

    private val COLOR_BAND = Color.parseColor("#243447")
    private val COLOR_SUB_BAND = Color.parseColor("#3B4D61")
    private val COLOR_TINT = Color.parseColor("#E6ECF2")
    private val COLOR_ZEBRA = Color.parseColor("#F8FAFC")
    private val COLOR_HOLIDAY_BG = Color.parseColor("#E2E8F0")
    private val COLOR_HALF_DAY_BG = Color.parseColor("#FEF9C3")
    private val COLOR_BORDER = Color.parseColor("#64748B")
    private val COLOR_LIGHT_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_TEXT = Color.parseColor("#0F172A")
    private val COLOR_MUTED = Color.parseColor("#475569")

    private val COLOR_PRESENT = Color.parseColor("#166534")
    private val COLOR_ABSENT = Color.parseColor("#B91C1C")
    private val COLOR_LEAVE = Color.parseColor("#1D4ED8")
    private val COLOR_LATE = Color.parseColor("#B45309")
    private val COLOR_HALF = Color.parseColor("#6D28D9")

    // =========================================================================
    // 1. DAILY ATTENDANCE SHEET (A4 PORTRAIT)
    // =========================================================================
    fun generateDailyAttendancePdf(
        context: Context,
        bundle: DailyAttendancePdfBundle
    ): File {
        val dir = File(context.cacheDir, "phase8_attendance_pdfs").apply { mkdirs() }
        val safeSchool = sanitize(bundle.school.name, "School")
        val safeClass = sanitize("${bundle.className}_${bundle.section}", "Class")
        val safeDate = bundle.dateIso.replace(Regex("[^0-9-]+"), "")
        val outFile = File(dir, "Daily_Attendance_${safeSchool}_${safeClass}_${safeDate}.pdf")

        val sortedStudents = bundle.students.sortedWith(
            compareBy<Pair<String, StudentEntity>> { it.second.rollNo }.thenBy { it.second.name.lowercase() }
        )
        val rowsPerPage = 32
        val chunks = if (sortedStudents.isEmpty()) listOf(emptyList()) else sortedStudents.chunked(rowsPerPage)

        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PORTRAIT_W, PORTRAIT_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawDailySheetPage(page.canvas, bundle, sortedStudents, chunk, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderDailyPagesToBitmapsForVerification(bundle: DailyAttendancePdfBundle): List<Bitmap> {
        val sortedStudents = bundle.students.sortedWith(
            compareBy<Pair<String, StudentEntity>> { it.second.rollNo }.thenBy { it.second.name.lowercase() }
        )
        val rowsPerPage = 32
        val chunks = if (sortedStudents.isEmpty()) listOf(emptyList()) else sortedStudents.chunked(rowsPerPage)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(PORTRAIT_W, PORTRAIT_H, Bitmap.Config.ARGB_8888)
            drawDailySheetPage(Canvas(bmp), bundle, sortedStudents, chunk, idx + 1, chunks.size)
            bmp
        }
    }

    private fun drawDailySheetPage(
        canvas: Canvas,
        bundle: DailyAttendancePdfBundle,
        allStudents: List<Pair<String, StudentEntity>>,
        pageStudents: List<Pair<String, StudentEntity>>,
        pageNo: Int,
        totalPages: Int
    ) {
        canvas.drawColor(Color.WHITE)
        val contentW = PORTRAIT_W - 2f * PORTRAIT_MARGIN
        var y = drawHeaderBand(
            canvas = canvas,
            school = bundle.school,
            x = PORTRAIT_MARGIN,
            y = PORTRAIT_MARGIN,
            width = contentW,
            height = 56f,
            title = "DAILY STUDENT ATTENDANCE SHEET",
            subtitle = "Class ${bundle.className} - ${bundle.section}  |  Date: ${bundle.dateIso} (${SchoolCalendarEngine.formatShortHumanDate(bundle.dateIso)})  |  Session: ${bundle.session}"
        )
        y += 6f

        // Compute counts across all students
        var pCount = 0
        var aCount = 0
        var lCount = 0
        var ltCount = 0
        var hCount = 0
        val entries = bundle.sheet?.entries.orEmpty()
        for ((docId, _) in allStudents) {
            when (entries[docId]?.status) {
                StudentAttendanceStatus.PRESENT -> pCount++
                StudentAttendanceStatus.ABSENT -> aCount++
                StudentAttendanceStatus.LEAVE -> lCount++
                StudentAttendanceStatus.LATE -> ltCount++
                StudentAttendanceStatus.HALF_DAY -> hCount++
                null -> if (bundle.sheet != null) pCount++
            }
        }

        // Summary bar
        val metaH = 24f
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TINT }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + metaH, tintPaint)
        canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + metaH, borderPaint)

        val statusNote = if (bundle.sheet == null) {
            "Attendance NOT YET TAKEN"
        } else {
            "Total: ${allStudents.size}   |   Present: $pCount   |   Late: $ltCount   |   Half-day: $hCount   |   Leave: $lCount   |   Absent: $aCount"
        }
        drawText(canvas, statusNote, PORTRAIT_MARGIN + 8f, y + 10.5f, 7.8f, true, COLOR_TEXT)
        val ctLine = buildString {
            if (bundle.classTeacherName.isNotBlank()) append("Class Teacher: ${bundle.classTeacherName}   |   ")
            if (bundle.sheet?.updatedByName?.isNotBlank() == true) append("Recorded by: ${bundle.sheet.updatedByName}")
            when (val ds = bundle.dayStatus) {
                is DayCalendarStatus.Working -> if (ds.isHalfWorkingDay) append("   [Half Working Day]")
                is DayCalendarStatus.NonWorking -> append("   [${ds.reasonTitle}]")
            }
        }
        if (ctLine.isNotBlank()) {
            drawText(canvas, ctLine, PORTRAIT_MARGIN + 8f, y + 20.5f, 6.8f, false, COLOR_MUTED)
        }
        y += metaH + 8f

        // Table Header
        val cols = floatArrayOf(38f, 68f, 185f, 76f, contentW - 367f)
        val headers = listOf("Roll", "Adm. No.", "Student Name", "Status", "Reason / Notes")
        val headerH = 20f
        val subBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SUB_BAND }
        canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + headerH, subBandPaint)
        var cx = PORTRAIT_MARGIN
        headers.forEachIndexed { idx, label ->
            val align = if (idx == 0 || idx == 3) Paint.Align.CENTER else Paint.Align.LEFT
            val tx = if (align == Paint.Align.CENTER) cx + cols[idx] / 2f else cx + 5f
            drawText(canvas, label, tx, y + 13.5f, 8f, true, Color.WHITE, align)
            cx += cols[idx]
        }
        y += headerH

        // Rows
        val rowH = 19.5f
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ZEBRA }
        val lightBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LIGHT_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        if (pageStudents.isEmpty()) {
            canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + 28f, lightBorder)
            drawText(canvas, "No students enrolled in this class and section.", PORTRAIT_MARGIN + 10f, y + 17f, 8.5f, false, COLOR_MUTED)
            y += 28f
        } else {
            pageStudents.forEachIndexed { idx, (docId, student) ->
                if (idx % 2 == 1) {
                    canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + rowH, zebraPaint)
                }
                canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + rowH, lightBorder)

                val entry: StudentAttendanceEntry? = entries[docId]
                val statusLabel = when {
                    bundle.sheet == null -> "—"
                    entry == null -> "P (Present)"
                    else -> "${entry.status.code} (${entry.status.label})"
                }
                val statusColor = when (entry?.status) {
                    StudentAttendanceStatus.PRESENT -> COLOR_PRESENT
                    StudentAttendanceStatus.ABSENT -> COLOR_ABSENT
                    StudentAttendanceStatus.LEAVE -> COLOR_LEAVE
                    StudentAttendanceStatus.LATE -> COLOR_LATE
                    StudentAttendanceStatus.HALF_DAY -> COLOR_HALF
                    null -> if (bundle.sheet == null) COLOR_MUTED else COLOR_PRESENT
                }

                cx = PORTRAIT_MARGIN
                drawText(canvas, student.rollNo.toString(), cx + cols[0] / 2f, y + 13f, 8f, true, COLOR_TEXT, Paint.Align.CENTER)
                cx += cols[0]
                canvas.drawLine(cx, y, cx, y + rowH, lightBorder)

                drawText(canvas, fitText(student.admNo, cols[1] - 8f, 7.6f), cx + 4f, y + 13f, 7.6f, false, COLOR_MUTED)
                cx += cols[1]
                canvas.drawLine(cx, y, cx, y + rowH, lightBorder)

                drawText(canvas, fitText(student.name, cols[2] - 8f, 8.2f, true), cx + 5f, y + 13f, 8.2f, true, COLOR_TEXT)
                cx += cols[2]
                canvas.drawLine(cx, y, cx, y + rowH, lightBorder)

                drawText(canvas, statusLabel, cx + cols[3] / 2f, y + 13f, 7.6f, true, statusColor, Paint.Align.CENTER)
                cx += cols[3]
                canvas.drawLine(cx, y, cx, y + rowH, lightBorder)

                drawText(canvas, fitText(entry?.reason.orEmpty(), cols[4] - 8f, 7.5f), cx + 5f, y + 13f, 7.5f, false, COLOR_MUTED)
                y += rowH
            }
        }

        // Signature & Page Footer
        val footerY = PORTRAIT_H - PORTRAIT_MARGIN - 12f
        drawText(canvas, "Class Teacher Signature: _______________________", PORTRAIT_MARGIN, footerY, 7.5f, false, COLOR_MUTED)
        drawText(canvas, "Principal Signature: _______________________", PORTRAIT_MARGIN + 225f, footerY, 7.5f, false, COLOR_MUTED)
        drawText(canvas, "Page $pageNo of $totalPages", PORTRAIT_MARGIN + contentW, footerY, 7.5f, true, COLOR_MUTED, Paint.Align.RIGHT)
    }

    // =========================================================================
    // 2. MONTHLY ATTENDANCE REGISTER (A4 LANDSCAPE — 31 DAYS + 50+ STUDENTS)
    // =========================================================================
    fun generateMonthlyRegisterPdf(
        context: Context,
        bundle: MonthlyRegisterPdfBundle
    ): File {
        val dir = File(context.cacheDir, "phase8_attendance_pdfs").apply { mkdirs() }
        val safeSchool = sanitize(bundle.school.name, "School")
        val safeClass = sanitize("${bundle.className}_${bundle.section}", "Class")
        val safeMonth = String.format(Locale.US, "%04d_%02d", bundle.year, bundle.month1Based)
        val outFile = File(dir, "Monthly_Register_${safeSchool}_${safeClass}_${safeMonth}.pdf")

        val sortedStudents = bundle.students.sortedWith(
            compareBy<Pair<String, StudentEntity>> { it.second.rollNo }.thenBy { it.second.name.lowercase() }
        )
        val rowsPerPage = 24
        val chunks = if (sortedStudents.isEmpty()) listOf(emptyList()) else sortedStudents.chunked(rowsPerPage)
        val dates = SchoolCalendarEngine.datesOfMonth(bundle.year, bundle.month1Based)
        val dayStatuses = dates.map { SchoolCalendarEngine.dayStatus(bundle.calendar, it) }
        val dayTotals = computeMonthlyPerDayTotals(sortedStudents, dates, bundle.sheetsByDate)

        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_W, LANDSCAPE_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawMonthlyRegisterPage(
                    canvas = page.canvas,
                    bundle = bundle,
                    dates = dates,
                    dayStatuses = dayStatuses,
                    pageStudents = chunk,
                    dayTotals = dayTotals,
                    pageNo = idx + 1,
                    totalPages = chunks.size
                )
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderMonthlyRegisterPagesToBitmapsForVerification(bundle: MonthlyRegisterPdfBundle): List<Bitmap> {
        val sortedStudents = bundle.students.sortedWith(
            compareBy<Pair<String, StudentEntity>> { it.second.rollNo }.thenBy { it.second.name.lowercase() }
        )
        val rowsPerPage = 24
        val chunks = if (sortedStudents.isEmpty()) listOf(emptyList()) else sortedStudents.chunked(rowsPerPage)
        val dates = SchoolCalendarEngine.datesOfMonth(bundle.year, bundle.month1Based)
        val dayStatuses = dates.map { SchoolCalendarEngine.dayStatus(bundle.calendar, it) }
        val dayTotals = computeMonthlyPerDayTotals(sortedStudents, dates, bundle.sheetsByDate)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(LANDSCAPE_W, LANDSCAPE_H, Bitmap.Config.ARGB_8888)
            drawMonthlyRegisterPage(
                canvas = Canvas(bmp),
                bundle = bundle,
                dates = dates,
                dayStatuses = dayStatuses,
                pageStudents = chunk,
                dayTotals = dayTotals,
                pageNo = idx + 1,
                totalPages = chunks.size
            )
            bmp
        }
    }

    private data class MonthlyPerDayTotal(
        val taken: Boolean,
        val presentLike: Int, // P + Lt + H
        val absentLike: Int   // A + L
    )

    private fun computeMonthlyPerDayTotals(
        students: List<Pair<String, StudentEntity>>,
        dates: List<String>,
        sheetsByDate: Map<String, StudentAttendanceSheet>
    ): List<MonthlyPerDayTotal> {
        return dates.map { dateIso ->
            val sheet = sheetsByDate[dateIso]
            if (sheet == null) {
                MonthlyPerDayTotal(taken = false, presentLike = 0, absentLike = 0)
            } else {
                var p = 0
                var a = 0
                for ((docId, _) in students) {
                    when (sheet.entries[docId]?.status) {
                        StudentAttendanceStatus.PRESENT,
                        StudentAttendanceStatus.LATE,
                        StudentAttendanceStatus.HALF_DAY -> p++
                        StudentAttendanceStatus.ABSENT,
                        StudentAttendanceStatus.LEAVE -> a++
                        null -> {}
                    }
                }
                MonthlyPerDayTotal(taken = true, presentLike = p, absentLike = a)
            }
        }
    }

    private fun drawMonthlyRegisterPage(
        canvas: Canvas,
        bundle: MonthlyRegisterPdfBundle,
        dates: List<String>,
        dayStatuses: List<DayCalendarStatus>,
        pageStudents: List<Pair<String, StudentEntity>>,
        dayTotals: List<MonthlyPerDayTotal>,
        pageNo: Int,
        totalPages: Int
    ) {
        canvas.drawColor(Color.WHITE)
        val contentW = LANDSCAPE_W - 2f * LANDSCAPE_MARGIN // 806f
        val monthWorkingDays = dayStatuses.count { it is DayCalendarStatus.Working }
        val halfWorkingCount = dayStatuses.count { it is DayCalendarStatus.Working && it.isHalfWorkingDay }

        var y = drawHeaderBand(
            canvas = canvas,
            school = bundle.school,
            x = LANDSCAPE_MARGIN,
            y = LANDSCAPE_MARGIN,
            width = contentW,
            height = 46f,
            title = "MONTHLY STUDENT ATTENDANCE REGISTER — ${bundle.monthLabel.uppercase(Locale.US)}",
            subtitle = "Class ${bundle.className} - ${bundle.section}  |  Session: ${bundle.session}  |  Working Days: $monthWorkingDays" +
                (if (halfWorkingCount > 0) " ($halfWorkingCount half-day)" else "") +
                (if (bundle.classTeacherName.isNotBlank()) "  |  Class Teacher: ${bundle.classTeacherName}" else "")
        )
        y += 4f

        // Legend Strip
        drawText(
            canvas,
            "Legend:  P = Present (1.0)   Lt = Late (1.0)   H = Half-day (0.5)   L = Leave (0)   A = Absent (0)   |   Shaded Grey = Holiday/Off   Yellow = Half Working Day",
            LANDSCAPE_MARGIN,
            y + 9f,
            6.6f,
            false,
            COLOR_MUTED
        )
        y += 13f

        // Column widths engineered to fit up to 31 days with zero overlap
        val dayCount = dates.size.coerceAtLeast(28)
        val rollW = 22f
        val nameW = 104f
        val sumPW = 28f
        val sumHW = 20f
        val sumAW = 22f
        val sumLW = 20f
        val sumPctW = 36f
        val summaryTotalW = sumPW + sumHW + sumAW + sumLW + sumPctW // 126f
        val daysBlockW = contentW - rollW - nameW - summaryTotalW // 554f -> 17.87f per day for 31 days!
        val dayW = daysBlockW / dayCount.toFloat()

        val headerH = 24f
        val subBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SUB_BAND }
        val holidayHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#64748B") }
        val holidayCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_HOLIDAY_BG }
        val halfDayCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_HALF_DAY_BG }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ZEBRA }
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TINT }
        val gridBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.55f
        }
        val lightGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LIGHT_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.45f
        }

        // Draw Table Header
        canvas.drawRect(LANDSCAPE_MARGIN, y, LANDSCAPE_MARGIN + contentW, y + headerH, subBandPaint)
        var cx = LANDSCAPE_MARGIN
        drawText(canvas, "Roll", cx + rollW / 2f, y + 15f, 6.8f, true, Color.WHITE, Paint.Align.CENTER)
        cx += rollW
        canvas.drawLine(cx, y, cx, y + headerH, lightGrid)

        drawText(canvas, "Student Name", cx + 4f, y + 15f, 7.2f, true, Color.WHITE)
        cx += nameW
        canvas.drawLine(cx, y, cx, y + headerH, lightGrid)

        dates.forEachIndexed { dIdx, dateIso ->
            val st = dayStatuses[dIdx]
            if (st is DayCalendarStatus.NonWorking) {
                canvas.drawRect(cx, y, cx + dayW, y + headerH, holidayHeaderPaint)
            }
            val dayNum = (dIdx + 1).toString()
            val dowShort = Phase6DateUtils.dayOfWeekForDate(dateIso)?.shortLabel?.take(1) ?: ""
            drawText(canvas, dayNum, cx + dayW / 2f, y + 10.5f, 6.4f, true, Color.WHITE, Paint.Align.CENTER)
            drawText(canvas, dowShort, cx + dayW / 2f, y + 19.5f, 5.6f, false, Color.parseColor("#E2E8F0"), Paint.Align.CENTER)
            cx += dayW
            canvas.drawLine(cx, y, cx, y + headerH, lightGrid)
        }

        // Summary headers
        val sumHeaders = listOf("P" to sumPW, "H" to sumHW, "A" to sumAW, "L" to sumLW, "%" to sumPctW)
        sumHeaders.forEach { (lbl, w) ->
            drawText(canvas, lbl, cx + w / 2f, y + 15f, 6.8f, true, Color.WHITE, Paint.Align.CENTER)
            cx += w
            canvas.drawLine(cx, y, cx, y + headerH, lightGrid)
        }
        canvas.drawRect(LANDSCAPE_MARGIN, y, LANDSCAPE_MARGIN + contentW, y + headerH, gridBorder)
        y += headerH

        // Fixed-height student rows (16.5f * 24 = 396f)
        val rowH = 16.5f
        val tableTopY = y
        if (pageStudents.isEmpty()) {
            canvas.drawRect(LANDSCAPE_MARGIN, y, LANDSCAPE_MARGIN + contentW, y + 24f, gridBorder)
            drawText(canvas, "No students enrolled in Class ${bundle.className}-${bundle.section}.", LANDSCAPE_MARGIN + 8f, y + 15f, 8f, false, COLOR_MUTED)
            y += 24f
        } else {
            pageStudents.forEachIndexed { rIdx, (docId, student) ->
                if (rIdx % 2 == 1) {
                    canvas.drawRect(LANDSCAPE_MARGIN, y, LANDSCAPE_MARGIN + contentW, y + rowH, zebraPaint)
                }

                cx = LANDSCAPE_MARGIN
                drawText(canvas, student.rollNo.toString(), cx + rollW / 2f, y + 11.2f, 6.7f, true, COLOR_TEXT, Paint.Align.CENTER)
                cx += rollW

                drawText(canvas, fitText(student.name, nameW - 6f, 6.7f, true), cx + 3f, y + 11.2f, 6.7f, true, COLOR_TEXT)
                cx += nameW

                var pCount = 0
                var ltCount = 0
                var hCount = 0
                var aCount = 0
                var lCount = 0
                var markedDays = 0

                dates.forEachIndexed { dIdx, dateIso ->
                    val dayStatus = dayStatuses[dIdx]
                    when (dayStatus) {
                        is DayCalendarStatus.NonWorking -> {
                            canvas.drawRect(cx, y, cx + dayW, y + rowH, holidayCellPaint)
                        }
                        is DayCalendarStatus.Working -> {
                            if (dayStatus.isHalfWorkingDay) {
                                canvas.drawRect(cx, y, cx + dayW, y + rowH, halfDayCellPaint)
                            }
                        }
                    }

                    val entry = bundle.sheetsByDate[dateIso]?.entries?.get(docId)
                    if (entry != null) {
                        markedDays++
                        when (entry.status) {
                            StudentAttendanceStatus.PRESENT -> pCount++
                            StudentAttendanceStatus.LATE -> ltCount++
                            StudentAttendanceStatus.HALF_DAY -> hCount++
                            StudentAttendanceStatus.ABSENT -> aCount++
                            StudentAttendanceStatus.LEAVE -> lCount++
                        }
                        val codeColor = when (entry.status) {
                            StudentAttendanceStatus.PRESENT -> COLOR_PRESENT
                            StudentAttendanceStatus.ABSENT -> COLOR_ABSENT
                            StudentAttendanceStatus.LEAVE -> COLOR_LEAVE
                            StudentAttendanceStatus.LATE -> COLOR_LATE
                            StudentAttendanceStatus.HALF_DAY -> COLOR_HALF
                        }
                        drawText(canvas, entry.status.code, cx + dayW / 2f, y + 11.2f, 6.2f, true, codeColor, Paint.Align.CENTER)
                    } else if (dayStatus is DayCalendarStatus.NonWorking) {
                        val marker = if (dayStatus.isHoliday) "·" else ""
                        if (marker.isNotEmpty()) {
                            drawText(canvas, marker, cx + dayW / 2f, y + 11.2f, 6.2f, false, COLOR_MUTED, Paint.Align.CENTER)
                        }
                    }
                    cx += dayW
                }

                // Per-student totals
                val weightedPresent = pCount + ltCount + hCount * 0.5
                val denom = if (monthWorkingDays > 0) monthWorkingDays else markedDays
                val pct = if (denom > 0) (weightedPresent * 100.0 / denom) else 0.0
                val pText = if (hCount > 0) String.format(Locale.US, "%.1f", weightedPresent) else "${pCount + ltCount}"

                drawText(canvas, pText, cx + sumPW / 2f, y + 11.2f, 6.5f, true, COLOR_PRESENT, Paint.Align.CENTER)
                cx += sumPW
                drawText(canvas, hCount.toString(), cx + sumHW / 2f, y + 11.2f, 6.4f, false, COLOR_HALF, Paint.Align.CENTER)
                cx += sumHW
                drawText(canvas, aCount.toString(), cx + sumAW / 2f, y + 11.2f, 6.5f, true, if (aCount > 0) COLOR_ABSENT else COLOR_MUTED, Paint.Align.CENTER)
                cx += sumAW
                drawText(canvas, lCount.toString(), cx + sumLW / 2f, y + 11.2f, 6.4f, false, COLOR_LEAVE, Paint.Align.CENTER)
                cx += sumLW
                val pctColor = if (denom > 0 && pct < 75.0) COLOR_ABSENT else COLOR_TEXT
                drawText(
                    canvas,
                    if (denom > 0) String.format(Locale.US, "%.0f%%", pct) else "—",
                    cx + sumPctW / 2f,
                    y + 11.2f,
                    6.5f,
                    true,
                    pctColor,
                    Paint.Align.CENTER
                )

                canvas.drawLine(LANDSCAPE_MARGIN, y + rowH, LANDSCAPE_MARGIN + contentW, y + rowH, lightGrid)
                y += rowH
            }
        }

        // Per-Day Totals Rows (Class Present & Class Absent across ALL students of section)
        val totalRowH = 15.5f
        // Present Row
        canvas.drawRect(LANDSCAPE_MARGIN, y, LANDSCAPE_MARGIN + contentW, y + totalRowH, tintPaint)
        drawText(canvas, "Day Present (P/Lt/H)", LANDSCAPE_MARGIN + 4f, y + 10.5f, 6.4f, true, COLOR_PRESENT)
        cx = LANDSCAPE_MARGIN + rollW + nameW
        var sumTotalP = 0
        dates.forEachIndexed { dIdx, _ ->
            val dt = dayTotals[dIdx]
            if (dt.taken) {
                sumTotalP += dt.presentLike
                drawText(canvas, dt.presentLike.toString(), cx + dayW / 2f, y + 10.5f, 6.1f, true, COLOR_PRESENT, Paint.Align.CENTER)
            }
            cx += dayW
        }
        drawText(canvas, "Σ $sumTotalP", cx + summaryTotalW / 2f, y + 10.5f, 6.3f, true, COLOR_PRESENT, Paint.Align.CENTER)
        canvas.drawLine(LANDSCAPE_MARGIN, y + totalRowH, LANDSCAPE_MARGIN + contentW, y + totalRowH, gridBorder)
        y += totalRowH

        // Absent Row
        canvas.drawRect(LANDSCAPE_MARGIN, y, LANDSCAPE_MARGIN + contentW, y + totalRowH, zebraPaint)
        drawText(canvas, "Day Absent (A/L)", LANDSCAPE_MARGIN + 4f, y + 10.5f, 6.4f, true, COLOR_ABSENT)
        cx = LANDSCAPE_MARGIN + rollW + nameW
        var sumTotalA = 0
        dates.forEachIndexed { dIdx, _ ->
            val dt = dayTotals[dIdx]
            if (dt.taken) {
                sumTotalA += dt.absentLike
                drawText(canvas, dt.absentLike.toString(), cx + dayW / 2f, y + 10.5f, 6.1f, true, if (dt.absentLike > 0) COLOR_ABSENT else COLOR_MUTED, Paint.Align.CENTER)
            }
            cx += dayW
        }
        drawText(canvas, "Σ $sumTotalA", cx + summaryTotalW / 2f, y + 10.5f, 6.3f, true, COLOR_ABSENT, Paint.Align.CENTER)
        y += totalRowH

        // Draw vertical grid lines cleanly over the full body + totals
        var gx = LANDSCAPE_MARGIN + rollW
        canvas.drawLine(gx, tableTopY, gx, y - 2 * totalRowH, lightGrid)
        gx += nameW
        canvas.drawLine(gx, tableTopY, gx, y, gridBorder)
        repeat(dayCount) {
            gx += dayW
            canvas.drawLine(gx, tableTopY, gx, y, lightGrid)
        }
        canvas.drawLine(LANDSCAPE_MARGIN + rollW + nameW + daysBlockW, tableTopY, LANDSCAPE_MARGIN + rollW + nameW + daysBlockW, y, gridBorder)
        var sx = LANDSCAPE_MARGIN + rollW + nameW + daysBlockW
        listOf(sumPW, sumHW, sumAW, sumLW).forEach { w ->
            sx += w
            canvas.drawLine(sx, tableTopY, sx, y - 2 * totalRowH, lightGrid)
        }
        canvas.drawRect(LANDSCAPE_MARGIN, tableTopY, LANDSCAPE_MARGIN + contentW, y, gridBorder)

        // Footer
        val footerY = LANDSCAPE_H - LANDSCAPE_MARGIN - 4f
        drawText(canvas, "Class Teacher Signature: _______________________", LANDSCAPE_MARGIN, footerY, 7f, false, COLOR_MUTED)
        drawText(canvas, "Principal Signature: _______________________", LANDSCAPE_MARGIN + 260f, footerY, 7f, false, COLOR_MUTED)
        drawText(
            canvas,
            "Page $pageNo of $totalPages  |  Total Students: ${bundle.students.size}",
            LANDSCAPE_MARGIN + contentW,
            footerY,
            7f,
            true,
            COLOR_MUTED,
            Paint.Align.RIGHT
        )
    }

    // =========================================================================
    // 3. STUDENT-WISE SUMMARY FOR A DATE RANGE (A4 PORTRAIT)
    // =========================================================================
    fun generateStudentSummaryPdf(
        context: Context,
        bundle: StudentSummaryRangePdfBundle
    ): File {
        val dir = File(context.cacheDir, "phase8_attendance_pdfs").apply { mkdirs() }
        val safeSchool = sanitize(bundle.school.name, "School")
        val safeScope = sanitize(bundle.scopeTitle, "Summary")
        val outFile = File(dir, "Attendance_Summary_${safeSchool}_${safeScope}_${bundle.startDateIso}_to_${bundle.endDateIso}.pdf")

        val rowsPerPage = 32
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)

        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PORTRAIT_W, PORTRAIT_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawStudentSummaryPage(page.canvas, bundle, chunk, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderStudentSummaryPagesToBitmapsForVerification(bundle: StudentSummaryRangePdfBundle): List<Bitmap> {
        val rowsPerPage = 32
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(PORTRAIT_W, PORTRAIT_H, Bitmap.Config.ARGB_8888)
            drawStudentSummaryPage(Canvas(bmp), bundle, chunk, idx + 1, chunks.size)
            bmp
        }
    }

    private fun drawStudentSummaryPage(
        canvas: Canvas,
        bundle: StudentSummaryRangePdfBundle,
        pageRows: List<StudentRangeAttendanceSummaryRow>,
        pageNo: Int,
        totalPages: Int
    ) {
        canvas.drawColor(Color.WHITE)
        val contentW = PORTRAIT_W - 2f * PORTRAIT_MARGIN
        var y = drawHeaderBand(
            canvas = canvas,
            school = bundle.school,
            x = PORTRAIT_MARGIN,
            y = PORTRAIT_MARGIN,
            width = contentW,
            height = 56f,
            title = "STUDENT-WISE ATTENDANCE SUMMARY",
            subtitle = "${bundle.scopeTitle}  |  ${bundle.startDateIso} to ${bundle.endDateIso}  |  Working Days: ${bundle.calendarWorkingDays}  |  Session: ${bundle.session}"
        )
        y += 8f

        val cols = floatArrayOf(30f, 134f, 44f, 34f, 32f, 32f, 32f, 34f, 52f, 55f, contentW - 479f)
        val headers = listOf("Roll", "Student Name", "Class", "P", "Lt", "H", "L", "A", "Wtd P", "Work", "%")
        val headerH = 20f
        val subBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SUB_BAND }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ZEBRA }
        val lightBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LIGHT_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + headerH, subBandPaint)
        var cx = PORTRAIT_MARGIN
        headers.forEachIndexed { idx, h ->
            val align = if (idx == 1) Paint.Align.LEFT else Paint.Align.CENTER
            val tx = if (align == Paint.Align.LEFT) cx + 4f else cx + cols[idx] / 2f
            drawText(canvas, h, tx, y + 13.5f, 7.2f, true, Color.WHITE, align)
            cx += cols[idx]
        }
        y += headerH

        val rowH = 19.5f
        if (pageRows.isEmpty()) {
            canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + 28f, lightBorder)
            drawText(canvas, "No student attendance records found for this date range.", PORTRAIT_MARGIN + 8f, y + 17f, 8.2f, false, COLOR_MUTED)
        } else {
            pageRows.forEachIndexed { idx, row ->
                if (idx % 2 == 1) canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + rowH, zebraPaint)
                canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + rowH, lightBorder)

                cx = PORTRAIT_MARGIN
                val values = listOf(
                    row.student.rollNo.toString(),
                    fitText(row.student.name, cols[1] - 6f, 7.6f, true),
                    "${row.student.className}-${row.student.section}",
                    row.presentCount.toString(),
                    row.lateCount.toString(),
                    row.halfDayCount.toString(),
                    row.leaveCount.toString(),
                    row.absentCount.toString(),
                    String.format(Locale.US, "%.1f", row.weightedDaysPresent),
                    row.totalWorkingDays.toString(),
                    String.format(Locale.US, "%.1f%%", row.attendancePercent)
                )
                values.forEachIndexed { cIdx, v ->
                    val align = if (cIdx == 1) Paint.Align.LEFT else Paint.Align.CENTER
                    val tx = if (align == Paint.Align.LEFT) cx + 4f else cx + cols[cIdx] / 2f
                    val color = when (cIdx) {
                        7 -> if (row.absentCount > 0) COLOR_ABSENT else COLOR_MUTED
                        10 -> if (row.totalWorkingDays > 0 && row.attendancePercent < 75.0) COLOR_ABSENT else COLOR_PRESENT
                        else -> COLOR_TEXT
                    }
                    drawText(canvas, v, tx, y + 13f, 7.4f, cIdx == 1 || cIdx == 10, color, align)
                    cx += cols[cIdx]
                    if (cIdx < cols.lastIndex) canvas.drawLine(cx, y, cx, y + rowH, lightBorder)
                }
                y += rowH
            }
        }

        val footerY = PORTRAIT_H - PORTRAIT_MARGIN - 10f
        drawText(canvas, "Weighting: Present = 1.0, Late = 1.0, Half-day = 0.5, Leave/Absent = 0.0", PORTRAIT_MARGIN, footerY, 7f, false, COLOR_MUTED)
        drawText(canvas, "Page $pageNo of $totalPages", PORTRAIT_MARGIN + contentW, footerY, 7.2f, true, COLOR_MUTED, Paint.Align.RIGHT)
    }

    // =========================================================================
    // 4. LOW ATTENDANCE LIST (A4 PORTRAIT — SECTION OR WHOLE SCHOOL)
    // =========================================================================
    fun generateLowAttendancePdf(
        context: Context,
        bundle: LowAttendancePdfBundle
    ): File {
        val dir = File(context.cacheDir, "phase8_attendance_pdfs").apply { mkdirs() }
        val safeSchool = sanitize(bundle.school.name, "School")
        val safeScope = sanitize(bundle.scopeTitle, "School")
        val pctInt = bundle.thresholdPercent.toInt()
        val outFile = File(dir, "Low_Attendance_Below${pctInt}pct_${safeSchool}_${safeScope}.pdf")

        val rowsPerPage = 32
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)

        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PORTRAIT_W, PORTRAIT_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawLowAttendancePage(page.canvas, bundle, chunk, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderLowAttendancePagesToBitmapsForVerification(bundle: LowAttendancePdfBundle): List<Bitmap> {
        val rowsPerPage = 32
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(rowsPerPage)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(PORTRAIT_W, PORTRAIT_H, Bitmap.Config.ARGB_8888)
            drawLowAttendancePage(Canvas(bmp), bundle, chunk, idx + 1, chunks.size)
            bmp
        }
    }

    private fun drawLowAttendancePage(
        canvas: Canvas,
        bundle: LowAttendancePdfBundle,
        pageRows: List<LowAttendanceStudentRow>,
        pageNo: Int,
        totalPages: Int
    ) {
        canvas.drawColor(Color.WHITE)
        val contentW = PORTRAIT_W - 2f * PORTRAIT_MARGIN
        var y = drawHeaderBand(
            canvas = canvas,
            school = bundle.school,
            x = PORTRAIT_MARGIN,
            y = PORTRAIT_MARGIN,
            width = contentW,
            height = 56f,
            title = "LOW ATTENDANCE REPORT (BELOW ${String.format(Locale.US, "%.0f%%", bundle.thresholdPercent)})",
            subtitle = "${bundle.scopeTitle}  |  Period: ${bundle.startDateIso} to ${bundle.endDateIso}  |  Flagged: ${bundle.rows.size} student(s)  |  Session: ${bundle.session}"
        )
        y += 8f

        val cols = floatArrayOf(58f, 32f, 132f, 108f, 82f, 74f, contentW - 486f)
        val headers = listOf("Class", "Roll", "Student Name", "Father's Name", "Mobile", "Present/Work", "%")
        val headerH = 20f
        val subBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SUB_BAND }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ZEBRA }
        val lightBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LIGHT_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + headerH, subBandPaint)
        var cx = PORTRAIT_MARGIN
        headers.forEachIndexed { idx, h ->
            val align = if (idx in listOf(2, 3)) Paint.Align.LEFT else Paint.Align.CENTER
            val tx = if (align == Paint.Align.LEFT) cx + 4f else cx + cols[idx] / 2f
            drawText(canvas, h, tx, y + 13.5f, 7.3f, true, Color.WHITE, align)
            cx += cols[idx]
        }
        y += headerH

        val rowH = 19.5f
        if (pageRows.isEmpty()) {
            canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + 32f, lightBorder)
            drawText(
                canvas,
                "All students meet or exceed the ${String.format(Locale.US, "%.0f%%", bundle.thresholdPercent)} attendance threshold.",
                PORTRAIT_MARGIN + 10f,
                y + 19f,
                8.3f,
                true,
                COLOR_PRESENT
            )
        } else {
            pageRows.forEachIndexed { idx, row ->
                if (idx % 2 == 1) canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + rowH, zebraPaint)
                canvas.drawRect(PORTRAIT_MARGIN, y, PORTRAIT_MARGIN + contentW, y + rowH, lightBorder)

                cx = PORTRAIT_MARGIN
                val values = listOf(
                    "${row.student.className}-${row.student.section}",
                    row.student.rollNo.toString(),
                    fitText(row.student.name, cols[2] - 6f, 7.6f, true),
                    fitText(row.student.fatherName, cols[3] - 6f, 7.3f),
                    fitText(row.student.mobile, cols[4] - 6f, 7.2f),
                    "${String.format(Locale.US, "%.1f", row.weightedDaysPresent)} / ${row.totalWorkingDays}",
                    String.format(Locale.US, "%.1f%%", row.attendancePercent)
                )
                values.forEachIndexed { cIdx, v ->
                    val align = if (cIdx in listOf(2, 3)) Paint.Align.LEFT else Paint.Align.CENTER
                    val tx = if (align == Paint.Align.LEFT) cx + 4f else cx + cols[cIdx] / 2f
                    val color = if (cIdx == 6) COLOR_ABSENT else COLOR_TEXT
                    drawText(canvas, v, tx, y + 13f, 7.4f, cIdx == 2 || cIdx == 6, color, align)
                    cx += cols[cIdx]
                    if (cIdx < cols.lastIndex) canvas.drawLine(cx, y, cx, y + rowH, lightBorder)
                }
                y += rowH
            }
        }

        val footerY = PORTRAIT_H - PORTRAIT_MARGIN - 10f
        drawText(canvas, "Principal Signature: _______________________", PORTRAIT_MARGIN, footerY, 7.3f, false, COLOR_MUTED)
        drawText(canvas, "Page $pageNo of $totalPages", PORTRAIT_MARGIN + contentW, footerY, 7.3f, true, COLOR_MUTED, Paint.Align.RIGHT)
    }

    // =========================================================================
    // SHARED VECTOR DRAWING HELPERS (4x TEXT_SCALE — ZERO OVERLAP)
    // =========================================================================
    private fun drawHeaderBand(
        canvas: Canvas,
        school: SchoolEntity,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        title: String,
        subtitle: String
    ): Float {
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BAND }
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 4f, 4f, bandPaint)

        var textX = x + 12f
        val logoBmp = ImageUtils.decodeBase64ToBitmap(school.logoBase64)
        if (logoBmp != null) {
            val logoSize = (height - 12f).coerceIn(28f, 42f)
            val top = y + (height - logoSize) / 2f
            canvas.drawBitmap(logoBmp, null, RectF(x + 8f, top, x + 8f + logoSize, top + logoSize), null)
            textX = x + 16f + logoSize
        }

        val maxW = (x + width - 10f) - textX
        drawText(canvas, fitText(school.name.uppercase(Locale.US), maxW, 11f, true), textX, y + 16f, 11f, true, Color.WHITE)
        drawText(canvas, fitText(title, maxW, 8.8f, true), textX, y + 29f, 8.8f, true, Color.parseColor("#CBD5E1"))
        drawText(canvas, fitText(subtitle, maxW, 7.2f, false), textX, y + height - 7f, 7.2f, false, Color.parseColor("#E2E8F0"))
        return y + height
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        sizePt: Float,
        bold: Boolean,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        if (text.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = sizePt * TEXT_SCALE
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.textAlign = align
            this.letterSpacing = LETTER_SPACING_EM
        }
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(text, x * TEXT_SCALE, y * TEXT_SCALE, paint)
        canvas.restore()
    }

    private fun fitText(text: String, maxWidthPt: Float, sizePt: Float, bold: Boolean = false): String {
        if (text.isEmpty() || maxWidthPt <= 4f) return ""
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = sizePt * TEXT_SCALE
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.letterSpacing = LETTER_SPACING_EM
        }
        val maxScaled = maxWidthPt * TEXT_SCALE
        if (paint.measureText(text) <= maxScaled) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxScaled) {
            end--
        }
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun sanitize(input: String, fallback: String): String =
        input.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(32).ifEmpty { fallback }
}
