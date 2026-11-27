package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamRoomRecord
import com.example.domain.ExamScheduleEntry
import com.example.domain.ExamScheduleRecord
import com.example.domain.ExamSeatAllocation
import com.example.domain.StockItemSummary
import com.example.domain.TransportDuesSummary
import com.example.domain.TransportMonthDueStudentRow
import com.example.domain.TransportMonthlyProfitLossReport
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportRouteSummary
import com.example.domain.TransportTripType
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class ExamTimetablePdfBundle(
    val school: SchoolEntity,
    val exam: ExamScheduleRecord,
    val className: String,
    val entries: List<ExamScheduleEntry>
)

data class AdmitCardStudentItem(
    val student: StudentEntity,
    val seat: ExamSeatAllocation?,
    val entries: List<ExamScheduleEntry>
)

data class AdmitCardsPdfBundle(
    val school: SchoolEntity,
    val exam: ExamScheduleRecord,
    val className: String,
    val items: List<AdmitCardStudentItem>
)

data class RoomSeatingPdfBundle(
    val school: SchoolEntity,
    val exam: ExamScheduleRecord,
    val room: ExamRoomRecord,
    val allocations: List<ExamSeatAllocation>
)

data class DutyListPdfBundle(
    val school: SchoolEntity,
    val exam: ExamScheduleRecord,
    val duties: List<ExamInvigilatorDutyRecord>
)

data class StockReportPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val generatedDateIso: String,
    val summaries: List<StockItemSummary>
)

data class TransportRoutePdfBundle(
    val school: SchoolEntity,
    val session: String,
    val generatedDateIso: String,
    val summary: TransportRouteSummary
)

data class TransportDuesPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val generatedDateIso: String,
    val duesSummary: TransportDuesSummary,
    val unassignedStudents: List<StudentEntity>,
    val routes: List<TransportRouteRecord>
)

data class TransportMonthlySummaryPdfBundle(
    val school: SchoolEntity,
    val generatedDateIso: String,
    val report: TransportMonthlyProfitLossReport
)

/**
 * Phase 11 Offline Native Vector A4 PDF Generators:
 * 1. Class Exam Timetable (A4 Portrait)
 * 2. Student Admit Cards (A4 Portrait, 2 cards per page with dashed cut line, logo, student photo,
 *    roll no, class, subjects with dates & times, room & seat, instructions, signatures)
 * 3. Seating List per Room (A4 Portrait, seat numbers + Student/Class/Roll + Invigilator Signature column)
 * 4. Invigilator Duty List (A4 Portrait, slot-wise room duties + per-teacher duty load summary)
 * 5. Stock Summary & Low-Stock Report (A4 Portrait)
 * 6. Transport Route-Wise Student List (A4 Portrait)
 * 7. Transport Driver's List with Guardian Mobiles (A4 Portrait, large print for bus/van drivers)
 */
object Phase11PdfGenerator {

    const val A4_W = 595
    const val A4_H = 842
    private const val MARGIN = 26f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.015f

    private val COLOR_PRIMARY = Color.parseColor("#1E3A5F")
    private val COLOR_ACCENT = Color.parseColor("#0F766E")
    private val COLOR_TINT = Color.parseColor("#EEF4FA")
    private val COLOR_ZEBRA = Color.parseColor("#F8FAFC")
    private val COLOR_BORDER = Color.parseColor("#64748B")
    private val COLOR_LIGHT_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_TEXT = Color.parseColor("#0F172A")
    private val COLOR_MUTED = Color.parseColor("#475569")
    private val COLOR_GREEN = Color.parseColor("#166534")
    private val COLOR_RED = Color.parseColor("#B91C1C")
    private val COLOR_AMBER_BG = Color.parseColor("#FEF3C7")
    private val COLOR_AMBER_TEXT = Color.parseColor("#92400E")

    // =========================================================================
    // 1. CLASS EXAM TIMETABLE (A4 PORTRAIT)
    // =========================================================================
    fun generateClassTimetablePdf(context: Context, bundle: ExamTimetablePdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "Timetable_${sanitize(bundle.exam.title, "Exam")}_${sanitize(bundle.className, "Class")}.pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
            drawClassTimetablePage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderClassTimetablePreviewBitmap(bundle: ExamTimetablePdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawClassTimetablePage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawClassTimetablePage(canvas: Canvas, bundle: ExamTimetablePdfBundle) {
        val left = MARGIN
        val right = A4_W - MARGIN
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "${bundle.exam.title.uppercase(Locale.US)} — EXAM TIMETABLE",
            subtitle = "Class ${bundle.className}  •  Session ${bundle.exam.session}"
        )
        y += 10f

        val cols = floatArrayOf(34f, 110f, 195f, 130f, (right - left) - 469f)
        val headers = listOf("S.No", "Date", "Subject", "Time", "Max Marks")
        y = drawTableHeader(canvas, left, y, cols, headers)

        val sorted = bundle.entries.sortedWith(compareBy({ it.dateIso }, { it.startTime }))
        if (sorted.isEmpty()) {
            drawScaledText(canvas, "No papers scheduled for Class ${bundle.className}.", left + 8f, y + 18f, makeTextPaint(10f, COLOR_MUTED))
            y += 30f
        } else {
            sorted.forEachIndexed { idx, entry ->
                val rowH = 22f
                y = drawTableRow(
                    canvas = canvas,
                    left = left,
                    y = y,
                    rowH = rowH,
                    cols = cols,
                    values = listOf(
                        "${idx + 1}",
                        entry.dateIso,
                        entry.subjectName,
                        "${entry.startTime} - ${entry.endTime}",
                        "${entry.maxMarks}"
                    ),
                    shaded = idx % 2 == 1
                )
            }
        }

        y += 18f
        drawScaledText(canvas, "Instructions:", left, y, makeTextPaint(9.5f, COLOR_PRIMARY, bold = true))
        y += 13f
        bundle.exam.admitCardInstructions.lines().filter { it.isNotBlank() }.take(5).forEach { line ->
            drawScaledText(canvas, line.trim(), left + 4f, y, makeTextPaint(8.5f, COLOR_MUTED))
            y += 12f
        }

        drawFooterSignatures(canvas, "Exam Controller", "Principal")
    }

    // =========================================================================
    // 2. STUDENT ADMIT CARDS (A4 PORTRAIT, 2 CARDS PER PAGE)
    // =========================================================================
    fun generateAdmitCardsPdf(context: Context, bundle: AdmitCardsPdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "AdmitCards_${sanitize(bundle.exam.title, "Exam")}_${sanitize(bundle.className, "Class")}.pdf")
        val pairs = bundle.items.chunked(2).ifEmpty { listOf(emptyList()) }
        val pdf = PdfDocument()
        try {
            pairs.forEachIndexed { idx, pair ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawAdmitCardPage(page.canvas, bundle.school, bundle.exam, pair)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderAdmitCardPreviewBitmaps(bundle: AdmitCardsPdfBundle, scale: Int = 2): List<Bitmap> {
        val pairs = bundle.items.chunked(2).ifEmpty { listOf(emptyList()) }
        return pairs.map { pair ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawAdmitCardPage(canvas, bundle.school, bundle.exam, pair)
            canvas.restore()
            bmp
        }
    }

    private fun drawAdmitCardPage(
        canvas: Canvas,
        school: SchoolEntity,
        exam: ExamScheduleRecord,
        pair: List<AdmitCardStudentItem>
    ) {
        val cardTop1 = 18f
        val cardH = 392f
        val cardTop2 = 432f

        if (pair.isNotEmpty()) {
            drawSingleAdmitCard(canvas, school, exam, pair[0], cardTop1, cardH)
        }
        // Dashed cut line in middle
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        canvas.drawLine(14f, 421f, A4_W - 14f, 421f, dashPaint)
        drawScaledText(canvas, "✂ CUT HERE", A4_W / 2f, 419f, makeTextPaint(7f, COLOR_MUTED, align = Paint.Align.CENTER))

        if (pair.size > 1) {
            drawSingleAdmitCard(canvas, school, exam, pair[1], cardTop2, cardH)
        }
    }

    private fun drawSingleAdmitCard(
        canvas: Canvas,
        school: SchoolEntity,
        exam: ExamScheduleRecord,
        item: AdmitCardStudentItem,
        topY: Float,
        cardH: Float
    ) {
        val left = 20f
        val right = A4_W - 20f
        val bottomY = topY + cardH
        val cardW = right - left

        // Outer border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_PRIMARY
            strokeWidth = 1.6f
        }
        canvas.drawRect(RectF(left, topY, right, bottomY), borderPaint)

        // Header bar
        val hdrH = 50f
        val hdrBg = Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.FILL }
        canvas.drawRect(RectF(left, topY, right, topY + hdrH), hdrBg)

        // Logo box
        val logoBox = RectF(left + 8f, topY + 6f, left + 46f, topY + 44f)
        canvas.drawRoundRect(logoBox, 4f, 4f, Paint().apply { color = Color.WHITE })
        val logoBmp = ImageUtils.decodeBase64ToBitmap(school.logoBase64)
        if (logoBmp != null) {
            canvas.drawBitmap(logoBmp, null, RectF(logoBox.left + 2f, logoBox.top + 2f, logoBox.right - 2f, logoBox.bottom - 2f), null)
        } else {
            drawScaledText(
                canvas,
                school.name.take(3).uppercase(Locale.US),
                logoBox.centerX(),
                logoBox.centerY() + 4f,
                makeTextPaint(9f, COLOR_PRIMARY, bold = true, align = Paint.Align.CENTER)
            )
        }

        // School title + badge
        drawScaledText(
            canvas,
            truncateText(school.name.uppercase(Locale.US), 44),
            left + 54f,
            topY + 19f,
            makeTextPaint(11f, Color.WHITE, bold = true)
        )
        drawScaledText(
            canvas,
            truncateText("${school.address.ifBlank { school.mobile }} • Session ${exam.session}", 68),
            left + 54f,
            topY + 32f,
            makeTextPaint(7.8f, Color.parseColor("#E2E8F0"))
        )
        drawScaledText(
            canvas,
            "ADMIT CARD — ${exam.title.uppercase(Locale.US)}",
            left + 54f,
            topY + 44f,
            makeTextPaint(8.8f, Color.parseColor("#FDE68A"), bold = true)
        )

        // Student Info + Photo box
        val infoTop = topY + hdrH + 7f
        val photoW = 54f
        val photoH = 64f
        val photoRect = RectF(right - 10f - photoW, infoTop, right - 10f, infoTop + photoH)
        canvas.drawRect(photoRect, Paint().apply { color = COLOR_ZEBRA; style = Paint.Style.FILL })
        canvas.drawRect(photoRect, Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.8f })
        val photoBmp = ImageUtils.decodeBase64ToBitmap(item.student.photoBase64)
        if (photoBmp != null) {
            canvas.drawBitmap(photoBmp, null, RectF(photoRect.left + 1.5f, photoRect.top + 1.5f, photoRect.right - 1.5f, photoRect.bottom - 1.5f), null)
        } else {
            drawScaledText(canvas, "PHOTO", photoRect.centerX(), photoRect.centerY() + 3f, makeTextPaint(7.5f, COLOR_MUTED, align = Paint.Align.CENTER))
        }

        val col1X = left + 10f
        val col2X = left + 255f
        var iy = infoTop + 11f
        val labelPaint = makeTextPaint(8.2f, COLOR_MUTED, bold = true)
        val valPaint = makeTextPaint(8.8f, COLOR_TEXT, bold = true)

        drawScaledText(canvas, "Student:", col1X, iy, labelPaint)
        drawScaledText(canvas, truncateText(item.student.name, 26), col1X + 44f, iy, valPaint)
        drawScaledText(canvas, "Class:", col2X, iy, labelPaint)
        drawScaledText(canvas, "${item.student.className} - ${item.student.section}", col2X + 36f, iy, valPaint)

        iy += 15f
        drawScaledText(canvas, "Father:", col1X, iy, labelPaint)
        drawScaledText(canvas, truncateText(item.student.fatherName.ifBlank { "-" }, 26), col1X + 44f, iy, makeTextPaint(8.5f, COLOR_TEXT))
        drawScaledText(canvas, "Roll No:", col2X, iy, labelPaint)
        drawScaledText(canvas, "${item.student.rollNo}  (Adm: ${item.student.admNo})", col2X + 36f, iy, valPaint)

        iy += 15f
        val roomLabel = item.seat?.roomName?.ifBlank { "TBA" } ?: "TBA"
        val seatLabel = item.seat?.let { "Seat #${it.seatNumber}" } ?: "TBA"
        drawScaledText(canvas, "Room:", col1X, iy, labelPaint)
        drawScaledText(canvas, roomLabel, col1X + 44f, iy, makeTextPaint(8.8f, COLOR_ACCENT, bold = true))
        drawScaledText(canvas, "Seat No:", col2X, iy, labelPaint)
        drawScaledText(canvas, seatLabel, col2X + 36f, iy, makeTextPaint(8.8f, COLOR_ACCENT, bold = true))

        iy += 15f
        drawScaledText(canvas, "D.O.B.:", col1X, iy, labelPaint)
        drawScaledText(canvas, item.student.dob.ifBlank { "-" }, col1X + 44f, iy, makeTextPaint(8.2f, COLOR_TEXT))
        drawScaledText(canvas, "Mobile:", col2X, iy, labelPaint)
        drawScaledText(canvas, item.student.mobile.ifBlank { "-" }, col2X + 36f, iy, makeTextPaint(8.2f, COLOR_TEXT))

        // Subject Timetable grid inside Admit Card (fits up to 8 subjects cleanly; 2-column compact if > 7)
        val tableTop = infoTop + photoH + 6f
        val sortedEntries = item.entries.sortedWith(compareBy({ it.dateIso }, { it.startTime }))
        val tableLeft = left + 8f
        val tableW = cardW - 16f

        if (sortedEntries.size <= 7) {
            val cols = floatArrayOf(80f, tableW - 220f, 100f, 40f)
            var ty = drawMiniTableHeader(canvas, tableLeft, tableTop, cols, listOf("Date", "Subject", "Time", "Invig."))
            sortedEntries.forEachIndexed { idx, e ->
                ty = drawMiniTableRow(
                    canvas, tableLeft, ty, 17f, cols,
                    listOf(e.dateIso, truncateText(e.subjectName, 34), "${e.startTime}-${e.endTime}", ""),
                    shaded = idx % 2 == 1
                )
            }
        } else {
            // Two-column compact schedule when a class has 8-14 papers
            val halfW = (tableW - 6f) / 2f
            val cols = floatArrayOf(58f, halfW - 122f, 64f)
            val halfCount = (sortedEntries.size + 1) / 2
            var ty1 = drawMiniTableHeader(canvas, tableLeft, tableTop, cols, listOf("Date", "Subject", "Time"))
            var ty2 = drawMiniTableHeader(canvas, tableLeft + halfW + 6f, tableTop, cols, listOf("Date", "Subject", "Time"))
            sortedEntries.take(halfCount).forEachIndexed { idx, e ->
                ty1 = drawMiniTableRow(
                    canvas, tableLeft, ty1, 16f, cols,
                    listOf(e.dateIso.drop(5), truncateText(e.subjectName, 18), "${e.startTime}-${e.endTime}"),
                    shaded = idx % 2 == 1
                )
            }
            sortedEntries.drop(halfCount).take(halfCount).forEachIndexed { idx, e ->
                ty2 = drawMiniTableRow(
                    canvas, tableLeft + halfW + 6f, ty2, 16f, cols,
                    listOf(e.dateIso.drop(5), truncateText(e.subjectName, 18), "${e.startTime}-${e.endTime}"),
                    shaded = idx % 2 == 1
                )
            }
        }

        // Bottom instructions + Signatures
        val sigY = bottomY - 14f
        val instrY = bottomY - 46f
        val firstInstr = exam.admitCardInstructions.lines().firstOrNull { it.isNotBlank() }.orEmpty()
        drawScaledText(canvas, truncateText("Note: $firstInstr", 92), left + 10f, instrY, makeTextPaint(7.2f, COLOR_MUTED))

        val linePaint = Paint().apply { color = COLOR_BORDER; strokeWidth = 0.7f }
        canvas.drawLine(left + 14f, sigY - 11f, left + 125f, sigY - 11f, linePaint)
        drawScaledText(canvas, "Class Teacher", left + 70f, sigY, makeTextPaint(7.8f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER))

        canvas.drawLine(left + cardW / 2f - 55f, sigY - 11f, left + cardW / 2f + 55f, sigY - 11f, linePaint)
        drawScaledText(canvas, "Exam Controller", left + cardW / 2f, sigY, makeTextPaint(7.8f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER))

        canvas.drawLine(right - 125f, sigY - 11f, right - 14f, sigY - 11f, linePaint)
        drawScaledText(canvas, "Principal Signature", right - 70f, sigY, makeTextPaint(7.8f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER))
    }

    // =========================================================================
    // 3. SEATING LIST PER ROOM (A4 PORTRAIT, MULTI-PAGE)
    // =========================================================================
    fun generateSeatingListPdf(context: Context, bundle: RoomSeatingPdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "Seating_${sanitize(bundle.exam.title, "Exam")}_${sanitize(bundle.room.roomName, "Room")}.pdf")
        val pages = bundle.allocations.sortedBy { it.seatNumber }.chunked(26).ifEmpty { listOf(emptyList()) }
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { idx, chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawSeatingListPage(page.canvas, bundle, chunk, idx + 1, pages.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderSeatingListPreviewBitmaps(bundle: RoomSeatingPdfBundle, scale: Int = 2): List<Bitmap> {
        val pages = bundle.allocations.sortedBy { it.seatNumber }.chunked(26).ifEmpty { listOf(emptyList()) }
        return pages.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawSeatingListPage(canvas, bundle, chunk, idx + 1, pages.size)
            canvas.restore()
            bmp
        }
    }

    private fun drawSeatingListPage(
        canvas: Canvas,
        bundle: RoomSeatingPdfBundle,
        rows: List<ExamSeatAllocation>,
        pageNo: Int,
        totalPages: Int
    ) {
        val left = MARGIN
        val right = A4_W - MARGIN
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "SEATING PLAN — ${bundle.room.roomName.uppercase(Locale.US)}",
            subtitle = "${bundle.exam.title} (${bundle.exam.session})  •  Occupied: ${bundle.allocations.size}/${bundle.room.capacity}  •  Page $pageNo/$totalPages"
        )
        y += 8f

        val totalW = right - left
        val cols = floatArrayOf(46f, 80f, 60f, totalW - 356f, 80f, 90f)
        y = drawTableHeader(canvas, left, y, cols, listOf("Seat", "Class", "Roll No", "Student Name", "Adm No", "Sign"))
        rows.forEachIndexed { idx, alloc ->
            y = drawTableRow(
                canvas = canvas,
                left = left,
                y = y,
                rowH = 22f,
                cols = cols,
                values = listOf(
                    "#${alloc.seatNumber}",
                    "${alloc.className}-${alloc.section}",
                    "${alloc.rollNo}",
                    truncateText(alloc.studentName, 26),
                    alloc.studentAdmNo,
                    ""
                ),
                shaded = idx % 2 == 1
            )
        }
        drawFooterSignatures(canvas, "Invigilator Signature", "Exam Controller")
    }

    // =========================================================================
    // 4. INVIGILATOR DUTY LIST (A4 PORTRAIT)
    // =========================================================================
    fun generateDutyListPdf(context: Context, bundle: DutyListPdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "DutyList_${sanitize(bundle.exam.title, "Exam")}.pdf")
        val sorted = bundle.duties.sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.roomName }))
        val pages = sorted.chunked(24).ifEmpty { listOf(emptyList()) }
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { idx, chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawDutyListPage(page.canvas, bundle, chunk, idx + 1, pages.size, isLastPage = idx == pages.lastIndex)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderDutyListPreviewBitmap(bundle: DutyListPdfBundle, scale: Int = 2): Bitmap {
        val sorted = bundle.duties.sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.roomName }))
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawDutyListPage(canvas, bundle, sorted.take(24), 1, 1, isLastPage = true)
        canvas.restore()
        return bmp
    }

    private fun drawDutyListPage(
        canvas: Canvas,
        bundle: DutyListPdfBundle,
        rows: List<ExamInvigilatorDutyRecord>,
        pageNo: Int,
        totalPages: Int,
        isLastPage: Boolean
    ) {
        val left = MARGIN
        val right = A4_W - MARGIN
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "INVIGILATOR DUTY ROSTER",
            subtitle = "${bundle.exam.title} (${bundle.exam.session})  •  Total Duties: ${bundle.duties.size}  •  Page $pageNo/$totalPages"
        )
        y += 8f

        val totalW = right - left
        val cols = floatArrayOf(78f, 95f, 100f, totalW - 363f, 90f)
        y = drawTableHeader(canvas, left, y, cols, listOf("Date", "Time", "Room", "Invigilator Teacher", "Signature"))
        rows.forEachIndexed { idx, d ->
            y = drawTableRow(
                canvas = canvas,
                left = left,
                y = y,
                rowH = 22f,
                cols = cols,
                values = listOf(
                    d.dateIso,
                    "${d.startTime}-${d.endTime}",
                    truncateText(d.roomName, 16),
                    truncateText(d.teacherName, 28),
                    ""
                ),
                shaded = idx % 2 == 1
            )
        }

        if (isLastPage && bundle.duties.isNotEmpty() && y < A4_H - 125f) {
            y += 14f
            drawScaledText(canvas, "Teacher-Wise Duty Count Summary:", left, y, makeTextPaint(9f, COLOR_PRIMARY, bold = true))
            y += 13f
            val counts = bundle.duties.groupBy { it.teacherName }.entries.sortedBy { it.key }
            val lineText = counts.joinToString("   |   ") { "${it.key}: ${it.value.size}" }
            drawScaledText(canvas, truncateText(lineText, 95), left, y, makeTextPaint(8.3f, COLOR_TEXT))
        }
        drawFooterSignatures(canvas, "Exam Controller", "Principal")
    }

    // =========================================================================
    // 5. STOCK SUMMARY & LOW-STOCK REPORT (A4 PORTRAIT)
    // =========================================================================
    fun generateStockReportPdf(context: Context, bundle: StockReportPdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "StockReport_${sanitize(bundle.session, "Session")}_${sanitize(bundle.generatedDateIso, "Today")}.pdf")
        val pdf = PdfDocument()
        try {
            val pages = bundle.summaries.chunked(24).ifEmpty { listOf(emptyList()) }
            pages.forEachIndexed { idx, chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawStockReportPage(page.canvas, bundle, chunk, idx + 1, pages.size, isLastPage = idx == pages.lastIndex)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderStockReportPreviewBitmap(bundle: StockReportPdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawStockReportPage(canvas, bundle, bundle.summaries.take(24), 1, 1, isLastPage = true)
        canvas.restore()
        return bmp
    }

    private fun drawStockReportPage(
        canvas: Canvas,
        bundle: StockReportPdfBundle,
        rows: List<StockItemSummary>,
        pageNo: Int,
        totalPages: Int,
        isLastPage: Boolean
    ) {
        val left = MARGIN
        val right = A4_W - MARGIN
        val lowCount = bundle.summaries.count { it.isLowStock }
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "SCHOOL STOCK SUMMARY REPORT",
            subtitle = "Session ${bundle.session}  •  As on ${bundle.generatedDateIso}  •  Items: ${bundle.summaries.size}  •  Low Stock: $lowCount  •  Page $pageNo/$totalPages"
        )
        y += 8f

        val totalW = right - left
        val cols = floatArrayOf(totalW - 392f, 44f, 44f, 44f, 44f, 44f, 52f, 52f, 68f)
        y = drawTableHeader(
            canvas, left, y, cols,
            listOf("Item Name", "Price", "Open", "In", "Sold", "Adj", "Stock", "Min", "Status")
        )
        rows.forEachIndexed { idx, s ->
            val statusText = when {
                s.currentStock <= 0 -> "OUT"
                s.isLowStock -> "LOW"
                else -> "OK"
            }
            val bgColor = if (s.isLowStock) COLOR_AMBER_BG else if (idx % 2 == 1) COLOR_ZEBRA else Color.WHITE
            y = drawTableRow(
                canvas = canvas,
                left = left,
                y = y,
                rowH = 22f,
                cols = cols,
                values = listOf(
                    truncateText("${s.item.name} (${s.item.unit})", 24),
                    "₹${s.item.price}",
                    "${s.openingStock}",
                    "+${s.purchasedIn}",
                    "-${s.soldOnReceipts}",
                    if (s.manualAdjustmentsNet >= 0) "+${s.manualAdjustmentsNet}" else "${s.manualAdjustmentsNet}",
                    "${s.currentStock}",
                    "${s.item.lowStockLevel}",
                    statusText
                ),
                shaded = false,
                explicitBgColor = bgColor,
                lastColColor = if (s.currentStock <= 0) COLOR_RED else if (s.isLowStock) COLOR_AMBER_TEXT else COLOR_GREEN
            )
        }

        if (isLastPage) {
            y += 12f
            val totalVal = bundle.summaries.sumOf { it.estimatedStockValueRs }
            val totalSpend = bundle.summaries.sumOf { it.totalPurchaseSpendRs }
            val box = RectF(left, y, right, y + 32f)
            canvas.drawRect(box, Paint().apply { color = COLOR_TINT; style = Paint.Style.FILL })
            canvas.drawRect(box, Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.STROKE; strokeWidth = 0.8f })
            drawScaledText(
                canvas,
                "Total Stock Value (at Selling Price): ₹$totalVal    •    Total Purchase Spend: ₹$totalSpend",
                left + 10f,
                y + 20f,
                makeTextPaint(9.2f, COLOR_PRIMARY, bold = true)
            )
        }
        drawFooterSignatures(canvas, "Store In-Charge", "Principal")
    }

    // =========================================================================
    // 6. TRANSPORT ROUTE-WISE STUDENT LIST (A4 PORTRAIT)
    // =========================================================================
    fun generateTransportRouteListPdf(context: Context, bundle: TransportRoutePdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "RouteList_${sanitize(bundle.summary.route.routeName, "Route")}.pdf")
        val pages = bundle.summary.assignedStudents.chunked(24).ifEmpty { listOf(emptyList()) }
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { idx, chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawTransportRouteListPage(page.canvas, bundle, chunk, idx + 1, pages.size, isLastPage = idx == pages.lastIndex)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderTransportRouteListPreviewBitmap(bundle: TransportRoutePdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawTransportRouteListPage(canvas, bundle, bundle.summary.assignedStudents.take(24), 1, 1, isLastPage = true)
        canvas.restore()
        return bmp
    }

    private fun drawTransportRouteListPage(
        canvas: Canvas,
        bundle: TransportRoutePdfBundle,
        rows: List<StudentEntity>,
        pageNo: Int,
        totalPages: Int,
        isLastPage: Boolean
    ) {
        val left = MARGIN
        val right = A4_W - MARGIN
        val route = bundle.summary.route
        val ownerTxt = "${route.vehicleOwnership.labelEn}${if (route.monthlyRentRs > 0) " (₹${route.monthlyRentRs}/mo)" else ""}"
        val licTxt = route.driverLicenceNo.takeIf { it.isNotBlank() }?.let { " • DL: $it" }.orEmpty()
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "TRANSPORT ROUTE LIST — ${route.routeName.uppercase(Locale.US)}",
            subtitle = "${route.vehicleType} ${route.vehicleNumber.ifBlank { "-" }} ($ownerTxt)  •  Driver: ${route.driverName.ifBlank { "-" }} (${route.driverPhone.ifBlank { "-" }})$licTxt  •  Seats: ${bundle.summary.seatsUsed}/${route.seatCapacity}  •  P$pageNo/$totalPages"
        )
        y += 8f

        val totalW = right - left
        val cols = floatArrayOf(28f, totalW - 398f, 55f, 118f, 65f, 76f, 56f)
        y = drawTableHeader(canvas, left, y, cols, listOf("#", "Student Name", "Class", "Stop (Time)", "Trip", "Mob.", "Fee/Mo"))
        val startOffset = (pageNo - 1) * 24
        rows.forEachIndexed { idx, st ->
            val stopObj = route.findStop(st.transportStopId, st.transportStopName)
            val stopOrder = stopObj?.let { s ->
                val ord = route.stops.indexOfFirst { it.stopId == s.stopId } + 1
                val tm = s.pickupTime.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                if (ord > 0) "$ord.${s.stopName}$tm" else "${s.stopName}$tm"
            } ?: st.transportStopName.ifBlank { "-" }
            val tripShort = when (TransportTripType.fromRaw(st.transportTripType)) {
                TransportTripType.BOTH_WAYS -> "Both"
                TransportTripType.PICKUP_ONLY -> "Pickup"
                TransportTripType.DROP_ONLY -> "Drop"
            }
            y = drawTableRow(
                canvas = canvas,
                left = left,
                y = y,
                rowH = 22f,
                cols = cols,
                values = listOf(
                    "${startOffset + idx + 1}",
                    truncateText(st.name, 20),
                    "${st.className}-${st.section}",
                    truncateText(stopOrder, 18),
                    tripShort,
                    st.mobile.ifBlank { st.secondMobile.ifBlank { "-" } },
                    "₹${st.transportMonthlyAmount}"
                ),
                shaded = idx % 2 == 1
            )
        }

        if (isLastPage) {
            y += 12f
            val box = RectF(left, y, right, y + 30f)
            canvas.drawRect(box, Paint().apply { color = COLOR_TINT; style = Paint.Style.FILL })
            canvas.drawRect(box, Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.STROKE; strokeWidth = 0.8f })
            drawScaledText(
                canvas,
                "Students on Route: ${bundle.summary.seatsUsed}/${route.seatCapacity}    •    Expected Monthly Income: ₹${bundle.summary.expectedMonthlyIncomeRs}",
                left + 10f,
                y + 19f,
                makeTextPaint(9.2f, COLOR_PRIMARY, bold = true)
            )
        }
        drawFooterSignatures(canvas, "Transport In-Charge", "Principal")
    }

    // =========================================================================
    // 7. TRANSPORT DRIVER'S LIST WITH GUARDIAN MOBILES (A4 PORTRAIT)
    // =========================================================================
    fun generateTransportDriverListPdf(context: Context, bundle: TransportRoutePdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "DriverList_${sanitize(bundle.summary.route.routeName, "Route")}.pdf")
        val pages = bundle.summary.assignedStudents.chunked(22).ifEmpty { listOf(emptyList()) }
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { idx, chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawDriverListPage(page.canvas, bundle, chunk, idx + 1, pages.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderTransportDriverListPreviewBitmap(bundle: TransportRoutePdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawDriverListPage(canvas, bundle, bundle.summary.assignedStudents.take(22), 1, 1)
        canvas.restore()
        return bmp
    }

    private fun drawDriverListPage(
        canvas: Canvas,
        bundle: TransportRoutePdfBundle,
        rows: List<StudentEntity>,
        pageNo: Int,
        totalPages: Int
    ) {
        val left = MARGIN
        val right = A4_W - MARGIN
        val route = bundle.summary.route
        val condInfo = route.conductorName.takeIf { it.isNotBlank() }?.let { "  •  Conductor: $it (${route.conductorPhone.ifBlank { "-" }})" }.orEmpty()
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "DRIVER'S PICKUP & DROP CONTACT SHEET",
            subtitle = "Route: ${route.routeName} (${route.vehicleType} ${route.vehicleNumber.ifBlank { "-" }})  •  Driver: ${route.driverName.ifBlank { "-" }} (${route.driverPhone.ifBlank { "-" }})$condInfo  •  P$pageNo/$totalPages"
        )
        y += 8f

        val totalW = right - left
        val cols = floatArrayOf(26f, 118f, 54f, totalW - 418f, 108f, 112f)
        y = drawTableHeader(canvas, left, y, cols, listOf("#", "Stop & Time", "Trip", "Student (Class)", "Guardian", "Mobile(s)"))
        val startOffset = (pageNo - 1) * 22
        rows.forEachIndexed { idx, st ->
            val stopObj = route.findStop(st.transportStopId, st.transportStopName)
            val stopDisp = stopObj?.let { s ->
                val ord = route.stops.indexOfFirst { it.stopId == s.stopId } + 1
                val tm = s.pickupTime.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                if (ord > 0) "$ord.${s.stopName}$tm" else "${s.stopName}$tm"
            } ?: st.transportStopName.ifBlank { route.routeName }
            val tripShort = when (TransportTripType.fromRaw(st.transportTripType)) {
                TransportTripType.BOTH_WAYS -> "Both"
                TransportTripType.PICKUP_ONLY -> "Pickup"
                TransportTripType.DROP_ONLY -> "Drop"
            }
            val phones = listOf(st.mobile.trim(), st.secondMobile.trim()).filter { it.isNotEmpty() }.joinToString(" / ").ifBlank { "-" }
            y = drawTableRow(
                canvas = canvas,
                left = left,
                y = y,
                rowH = 24f,
                cols = cols,
                values = listOf(
                    "${startOffset + idx + 1}",
                    truncateText(stopDisp, 19),
                    tripShort,
                    truncateText("${st.name} (${st.className}-${st.section})", 22),
                    truncateText(st.fatherName.ifBlank { st.motherName.ifBlank { "-" } }, 17),
                    truncateText(phones, 20)
                ),
                shaded = idx % 2 == 1
            )
        }
        drawFooterSignatures(canvas, "Driver Signature", "Transport In-Charge")
    }

    // =========================================================================
    // 8. TRANSPORT DUES, UNASSIGNED STUDENTS & FEE LIST PDF (A4 PORTRAIT)
    // =========================================================================
    fun generateTransportDuesAndFeeListPdf(context: Context, bundle: TransportDuesPdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val outFile = File(dir, "TransportDues_${sanitize(bundle.duesSummary.monthCode, "Month")}_${sanitize(bundle.session, "Ses")}.pdf")
        val rowPages = bundle.duesSummary.rows.chunked(22).ifEmpty { listOf(emptyList()) }
        val pdf = PdfDocument()
        try {
            var pageIdx = 1
            val totalPages = rowPages.size + 1
            rowPages.forEach { chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, pageIdx).create())
                drawTransportDuesRowsPage(page.canvas, bundle, chunk, pageIdx, totalPages, isLastDuesPage = pageIdx == rowPages.size)
                pdf.finishPage(page)
                pageIdx++
            }
            val schedulePage = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, pageIdx).create())
            drawTransportUnassignedAndFeeSchedulePage(schedulePage.canvas, bundle, pageIdx, totalPages)
            pdf.finishPage(schedulePage)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderTransportDuesPreviewBitmaps(bundle: TransportDuesPdfBundle, scale: Int = 2): List<Bitmap> {
        val rowPages = bundle.duesSummary.rows.chunked(22).ifEmpty { listOf(emptyList()) }
        val totalPages = rowPages.size + 1
        val out = mutableListOf<Bitmap>()
        rowPages.forEachIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawTransportDuesRowsPage(canvas, bundle, chunk, idx + 1, totalPages, isLastDuesPage = idx == rowPages.lastIndex)
            canvas.restore()
            out += bmp
        }
        val bmp2 = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val c2 = Canvas(bmp2)
        c2.drawColor(Color.WHITE)
        c2.save()
        c2.scale(scale.toFloat(), scale.toFloat())
        drawTransportUnassignedAndFeeSchedulePage(c2, bundle, totalPages, totalPages)
        c2.restore()
        out += bmp2
        return out
    }

    private fun drawTransportDuesRowsPage(
        canvas: Canvas,
        bundle: TransportDuesPdfBundle,
        rows: List<TransportMonthDueStudentRow>,
        pageNo: Int,
        totalPages: Int,
        isLastDuesPage: Boolean
    ) {
        val left = MARGIN
        val right = A4_W - MARGIN
        val d = bundle.duesSummary
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "TRANSPORT DUES REPORT — ${d.monthCode}",
            subtitle = "Session ${bundle.session}  •  As on ${bundle.generatedDateIso}  •  Pending Students: ${d.totalStudentsDue}  •  Total Due: ₹${d.totalDueRs}  •  P$pageNo/$totalPages"
        )
        y += 8f

        val totalW = right - left
        val cols = floatArrayOf(totalW - 392f, 62f, 108f, 62f, 52f, 54f, 54f)
        y = drawTableHeader(canvas, left, y, cols, listOf("Student Name", "Class", "Route / Stop", "Trip", "Fee", "Paid", "Due"))
        if (rows.isEmpty()) {
            drawScaledText(canvas, "No pending transport dues for ${d.monthCode}.", left + 8f, y + 18f, makeTextPaint(9.5f, COLOR_GREEN, bold = true))
            y += 28f
        } else {
            rows.forEachIndexed { idx, row ->
                val tripShort = when (row.tripType) {
                    TransportTripType.BOTH_WAYS -> "Both"
                    TransportTripType.PICKUP_ONLY -> "Pickup"
                    TransportTripType.DROP_ONLY -> "Drop"
                }
                y = drawTableRow(
                    canvas = canvas,
                    left = left,
                    y = y,
                    rowH = 22f,
                    cols = cols,
                    values = listOf(
                        truncateText(row.student.name, 22),
                        "${row.student.className}-${row.student.section}",
                        truncateText("${row.routeName} • ${row.stopName}", 18),
                        tripShort,
                        "₹${row.scheduledRs}",
                        "₹${row.paidRs}",
                        "₹${row.dueRs}"
                    ),
                    shaded = idx % 2 == 1,
                    lastColColor = COLOR_RED
                )
            }
        }

        if (isLastDuesPage && y < A4_H - 95f) {
            y += 10f
            val box = RectF(left, y, right, y + 32f)
            canvas.drawRect(box, Paint().apply { color = COLOR_TINT; style = Paint.Style.FILL })
            canvas.drawRect(box, Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.STROKE; strokeWidth = 0.8f })
            drawScaledText(
                canvas,
                "Expected (${d.monthCode}): ₹${d.totalExpectedRs}    •    Collected: ₹${d.totalCollectedRs}    •    Total Due: ₹${d.totalDueRs}",
                left + 10f,
                y + 20f,
                makeTextPaint(9f, COLOR_PRIMARY, bold = true)
            )
        }
        drawFooterSignatures(canvas, "Transport Clerk", "Principal")
    }

    private fun drawTransportUnassignedAndFeeSchedulePage(
        canvas: Canvas,
        bundle: TransportDuesPdfBundle,
        pageNo: Int,
        totalPages: Int
    ) {
        val left = MARGIN
        val right = A4_W - MARGIN
        val totalW = right - left
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "TRANSPORT FEE SCHEDULE & STUDENTS NEEDING ROUTE",
            subtitle = "Session ${bundle.session}  •  Routes: ${bundle.routes.size}  •  Without Route: ${bundle.unassignedStudents.size}  •  P$pageNo/$totalPages"
        )
        y += 10f

        drawScaledText(canvas, "1. Route & Stop Monthly Fee List", left, y, makeTextPaint(10f, COLOR_PRIMARY, bold = true))
        y += 6f
        val rCols = floatArrayOf(130f, 88f, 64f, 68f, 64f, totalW - 414f)
        y = drawTableHeader(canvas, left, y, rCols, listOf("Route", "Vehicle No", "Both Ways", "Pickup Only", "Drop Only", "Stops & Custom Fees"))
        bundle.routes.take(14).forEachIndexed { idx, rt ->
            val pFee = if (rt.pickupOnlyFeeRs > 0) "₹${rt.pickupOnlyFeeRs}" else "₹${rt.monthlyFeeRs}"
            val dFee = if (rt.dropOnlyFeeRs > 0) "₹${rt.dropOnlyFeeRs}" else "₹${rt.monthlyFeeRs}"
            val stopsTxt = if (rt.stops.isEmpty()) "Route fee only" else rt.stops.joinToString(", ") {
                val sf = if (it.monthlyFeeRs > 0) " ₹${it.monthlyFeeRs}" else ""
                "${it.stopName}$sf"
            }
            y = drawTableRow(
                canvas, left, y, 22f, rCols,
                listOf(
                    truncateText(rt.routeName, 20),
                    truncateText(rt.vehicleNumber.ifBlank { "-" }, 13),
                    "₹${rt.monthlyFeeRs}",
                    pFee,
                    dFee,
                    truncateText(stopsTxt, 24)
                ),
                shaded = idx % 2 == 1
            )
        }

        y += 16f
        drawScaledText(
            canvas,
            "2. Students Marked on Transport Without a Route (${bundle.unassignedStudents.size})",
            left, y, makeTextPaint(10f, COLOR_RED, bold = true)
        )
        y += 6f
        if (bundle.unassignedStudents.isEmpty()) {
            drawScaledText(canvas, "All transport students are mapped to a route.", left + 6f, y + 14f, makeTextPaint(8.8f, COLOR_GREEN))
        } else {
            val uCols = floatArrayOf(30f, 160f, 75f, 95f, totalW - 360f)
            y = drawTableHeader(canvas, left, y, uCols, listOf("#", "Student Name", "Class", "Mobile", "Current Remark / Saved Text"))
            bundle.unassignedStudents.take(10).forEachIndexed { idx, st ->
                y = drawTableRow(
                    canvas, left, y, 20f, uCols,
                    listOf(
                        "${idx + 1}",
                        truncateText(st.name, 25),
                        "${st.className}-${st.section}",
                        st.mobile.ifBlank { "-" },
                        truncateText(st.transportRouteName.ifBlank { "No route / ₹${st.transportMonthlyAmount}" }, 28)
                    ),
                    shaded = idx % 2 == 1
                )
            }
        }
        drawFooterSignatures(canvas, "Transport Clerk", "Principal")
    }

    // =========================================================================
    // 9. TRANSPORT MONTHLY SUMMARY (INCOME VS EXPENSE PROFIT/LOSS A4 PDF)
    // =========================================================================
    fun generateTransportMonthlySummaryPdf(context: Context, bundle: TransportMonthlySummaryPdfBundle): File {
        val dir = File(context.cacheDir, "phase11_pdfs").apply { mkdirs() }
        val rpt = bundle.report
        val outFile = File(dir, "TransportSummary_${sanitize(rpt.monthCode, "Month")}_${sanitize(rpt.session, "Ses")}.pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
            drawTransportMonthlySummaryPage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderTransportMonthlySummaryPreviewBitmap(bundle: TransportMonthlySummaryPdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawTransportMonthlySummaryPage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawTransportMonthlySummaryPage(canvas: Canvas, bundle: TransportMonthlySummaryPdfBundle) {
        val left = MARGIN
        val right = A4_W - MARGIN
        val totalW = right - left
        val rpt = bundle.report
        var y = drawCompactSchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "TRANSPORT MONTHLY INCOME & EXPENSE SUMMARY — ${rpt.monthCode}",
            subtitle = "Session ${rpt.session} (${rpt.yearMonthIso})  •  Generated ${bundle.generatedDateIso}  •  Cash Book Linked"
        )
        y += 10f

        val cols = floatArrayOf(totalW - 392f, 82f, 62f, 62f, 62f, 62f, 62f)
        y = drawTableHeader(canvas, left, y, cols, listOf("Route", "Vehicle", "Expected", "Collected", "Dues", "Expense", "Net P/L"))
        rpt.vehicleRows.take(22).forEachIndexed { idx, row ->
            val plColor = if (row.profitOrLossRs >= 0) COLOR_GREEN else COLOR_RED
            val plText = if (row.profitOrLossRs >= 0) "+₹${row.profitOrLossRs}" else "-₹${-row.profitOrLossRs}"
            y = drawTableRow(
                canvas, left, y, 22f, cols,
                listOf(
                    truncateText(row.routeName, 22),
                    truncateText(row.vehicleNumber, 13),
                    "₹${row.expectedIncomeRs}",
                    "₹${row.collectedIncomeRs}",
                    "₹${row.duesRs}",
                    "₹${row.expenseRs}",
                    plText
                ),
                shaded = idx % 2 == 1,
                lastColColor = plColor
            )
        }
        if (rpt.unallocatedExpensesRs > 0) {
            y = drawTableRow(
                canvas, left, y, 22f, cols,
                listOf("General / Unallocated", "-", "₹0", "₹0", "₹0", "₹${rpt.unallocatedExpensesRs}", "-₹${rpt.unallocatedExpensesRs}"),
                shaded = false,
                explicitBgColor = COLOR_AMBER_BG,
                lastColColor = COLOR_RED
            )
        }

        y += 14f
        val netColor = if (rpt.netProfitOrLossRs >= 0) COLOR_GREEN else COLOR_RED
        val netText = if (rpt.netProfitOrLossRs >= 0) "PROFIT +₹${rpt.netProfitOrLossRs}" else "LOSS -₹${-rpt.netProfitOrLossRs}"
        val box = RectF(left, y, right, y + 48f)
        canvas.drawRect(box, Paint().apply { color = COLOR_TINT; style = Paint.Style.FILL })
        canvas.drawRect(box, Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.STROKE; strokeWidth = 0.9f })
        drawScaledText(
            canvas,
            "Expected Income: ₹${rpt.totalExpectedIncomeRs}    •    Collected: ₹${rpt.totalCollectedIncomeRs}    •    Pending Dues: ₹${rpt.totalDuesRs}",
            left + 10f, y + 19f, makeTextPaint(9.2f, COLOR_PRIMARY, bold = true)
        )
        drawScaledText(
            canvas,
            "Total Vehicle Expenses (Cash Book): ₹${rpt.totalExpenseRs}        •        School Net Result: $netText",
            left + 10f, y + 37f, makeTextPaint(9.5f, netColor, bold = true)
        )
        drawFooterSignatures(canvas, "Transport Clerk", "Principal / Treasurer")
    }

    // =========================================================================
    // SHARED DRAWING PRIMITIVES
    // =========================================================================
    private fun drawCompactSchoolHeader(
        canvas: Canvas,
        school: SchoolEntity,
        title: String,
        subtitle: String
    ): Float {
        val left = MARGIN
        val right = A4_W - MARGIN
        val top = 22f
        val hdrH = 66f

        canvas.drawRect(RectF(left, top, right, top + hdrH), Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.FILL })

        val logoBox = RectF(left + 8f, top + 8f, left + 56f, top + 56f)
        canvas.drawRoundRect(logoBox, 5f, 5f, Paint().apply { color = Color.WHITE })
        val logoBmp = ImageUtils.decodeBase64ToBitmap(school.logoBase64)
        if (logoBmp != null) {
            canvas.drawBitmap(logoBmp, null, RectF(logoBox.left + 3f, logoBox.top + 3f, logoBox.right - 3f, logoBox.bottom - 3f), null)
        } else {
            drawScaledText(
                canvas,
                school.name.take(3).uppercase(Locale.US),
                logoBox.centerX(),
                logoBox.centerY() + 5f,
                makeTextPaint(10f, COLOR_PRIMARY, bold = true, align = Paint.Align.CENTER)
            )
        }

        drawScaledText(
            canvas,
            truncateText(school.name.uppercase(Locale.US), 48),
            left + 66f,
            top + 22f,
            makeTextPaint(12.5f, Color.WHITE, bold = true)
        )
        drawScaledText(
            canvas,
            truncateText(title, 62),
            left + 66f,
            top + 40f,
            makeTextPaint(10.5f, Color.parseColor("#FDE68A"), bold = true)
        )
        drawScaledText(
            canvas,
            truncateText(subtitle, 82),
            left + 66f,
            top + 55f,
            makeTextPaint(8.2f, Color.parseColor("#E2E8F0"))
        )
        return top + hdrH
    }

    private fun drawTableHeader(
        canvas: Canvas,
        left: Float,
        y: Float,
        cols: FloatArray,
        headers: List<String>
    ): Float {
        val rowH = 22f
        val totalW = cols.sum()
        canvas.drawRect(RectF(left, y, left + totalW, y + rowH), Paint().apply { color = COLOR_TINT; style = Paint.Style.FILL })
        val borderPaint = Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        var x = left
        headers.forEachIndexed { idx, h ->
            val w = cols[idx]
            canvas.drawRect(RectF(x, y, x + w, y + rowH), borderPaint)
            drawScaledText(canvas, h, x + 5f, y + 14.5f, makeTextPaint(8.5f, COLOR_PRIMARY, bold = true))
            x += w
        }
        return y + rowH
    }

    private fun drawTableRow(
        canvas: Canvas,
        left: Float,
        y: Float,
        rowH: Float,
        cols: FloatArray,
        values: List<String>,
        shaded: Boolean,
        explicitBgColor: Int? = null,
        lastColColor: Int? = null
    ): Float {
        val totalW = cols.sum()
        val bg = explicitBgColor ?: if (shaded) COLOR_ZEBRA else Color.WHITE
        canvas.drawRect(RectF(left, y, left + totalW, y + rowH), Paint().apply { color = bg; style = Paint.Style.FILL })
        val borderPaint = Paint().apply { color = COLOR_LIGHT_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        var x = left
        values.forEachIndexed { idx, v ->
            val w = cols[idx]
            canvas.drawRect(RectF(x, y, x + w, y + rowH), borderPaint)
            val txtColor = if (idx == values.lastIndex && lastColColor != null) lastColColor else COLOR_TEXT
            val bold = idx == values.lastIndex && lastColColor != null
            drawScaledText(canvas, v, x + 5f, y + rowH * 0.65f, makeTextPaint(8.3f, txtColor, bold = bold))
            x += w
        }
        return y + rowH
    }

    private fun drawMiniTableHeader(
        canvas: Canvas,
        left: Float,
        y: Float,
        cols: FloatArray,
        headers: List<String>
    ): Float {
        val rowH = 16f
        val totalW = cols.sum()
        canvas.drawRect(RectF(left, y, left + totalW, y + rowH), Paint().apply { color = COLOR_TINT; style = Paint.Style.FILL })
        val borderPaint = Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.STROKE; strokeWidth = 0.7f }
        var x = left
        headers.forEachIndexed { idx, h ->
            val w = cols[idx]
            canvas.drawRect(RectF(x, y, x + w, y + rowH), borderPaint)
            drawScaledText(canvas, h, x + 4f, y + 11f, makeTextPaint(7.4f, COLOR_PRIMARY, bold = true))
            x += w
        }
        return y + rowH
    }

    private fun drawMiniTableRow(
        canvas: Canvas,
        left: Float,
        y: Float,
        rowH: Float,
        cols: FloatArray,
        values: List<String>,
        shaded: Boolean
    ): Float {
        val totalW = cols.sum()
        val bg = if (shaded) COLOR_ZEBRA else Color.WHITE
        canvas.drawRect(RectF(left, y, left + totalW, y + rowH), Paint().apply { color = bg; style = Paint.Style.FILL })
        val borderPaint = Paint().apply { color = COLOR_LIGHT_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        var x = left
        values.forEachIndexed { idx, v ->
            val w = cols[idx]
            canvas.drawRect(RectF(x, y, x + w, y + rowH), borderPaint)
            drawScaledText(canvas, v, x + 4f, y + rowH * 0.68f, makeTextPaint(7.4f, COLOR_TEXT))
            x += w
        }
        return y + rowH
    }

    private fun drawFooterSignatures(canvas: Canvas, leftLabel: String, rightLabel: String) {
        val sigY = A4_H - 36f
        val linePaint = Paint().apply { color = COLOR_BORDER; strokeWidth = 0.8f }
        canvas.drawLine(MARGIN + 10f, sigY - 12f, MARGIN + 150f, sigY - 12f, linePaint)
        drawScaledText(canvas, leftLabel, MARGIN + 80f, sigY, makeTextPaint(8.8f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER))

        canvas.drawLine(A4_W - MARGIN - 150f, sigY - 12f, A4_W - MARGIN - 10f, sigY - 12f, linePaint)
        drawScaledText(canvas, rightLabel, A4_W - MARGIN - 80f, sigY, makeTextPaint(8.8f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER))
    }

    private fun makeTextPaint(
        sizePt: Float,
        textColor: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sizePt * TEXT_SCALE
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        textAlign = align
        letterSpacing = LETTER_SPACING_EM
    }

    private fun drawScaledText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(text, 0f, 0f, paint)
        canvas.restore()
    }

    private fun truncateText(text: String, maxChars: Int): String {
        val clean = text.trim()
        return if (clean.length <= maxChars) clean else clean.take((maxChars - 1).coerceAtLeast(1)) + "…"
    }

    private fun sanitize(raw: String, fallback: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").take(36).ifBlank { fallback }
}
