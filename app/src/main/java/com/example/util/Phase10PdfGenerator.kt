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
import com.example.domain.CashBookDaySummary
import com.example.domain.CashBookMonthSummary
import com.example.domain.CertificateType
import com.example.domain.SchoolCertificateRecord
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class CertificatePdfBundle(
    val school: SchoolEntity,
    val certificate: SchoolCertificateRecord
)

data class CashBookDayBookPdfBundle(
    val school: SchoolEntity,
    val summary: CashBookDaySummary
)

data class CashBookMonthSummaryPdfBundle(
    val school: SchoolEntity,
    val summary: CashBookMonthSummary
)

data class CashBookCategoryExpensePdfBundle(
    val school: SchoolEntity,
    val summary: CashBookMonthSummary
)

/**
 * Phase 10 Offline Native Vector A4 PDF Generators:
 * - Official A4 Portrait Transfer Certificate (TC), Bonafide Certificate, and Character Certificate
 *   (with school logo, header, decorative border, unique certificate number, student particulars,
 *   date, Principal signature line, Office Seal box, and diagonal red CANCELLED watermark if cancelled)
 * - A4 Portrait Cash Book Day Book (opening balance, Phase 9 fee receipts, manual other income,
 *   expenses, closing balance, counted cash & difference)
 * - A4 Portrait Cash Book Month Summary (daily totals + monthly totals)
 * - A4 Portrait Cash Book Category-Wise Expense Report
 *
 * Uses 4x Paint text scaling (TEXT_SCALE = 4f) for sharp, zero-overlap typography completely offline.
 */
object Phase10PdfGenerator {

    const val A4_W = 595
    const val A4_H = 842
    private const val MARGIN = 28f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.015f

    private val COLOR_PRIMARY = Color.parseColor("#1E3A5F")
    private val COLOR_GOLD = Color.parseColor("#B47B1B")
    private val COLOR_TINT = Color.parseColor("#EEF4FA")
    private val COLOR_ZEBRA = Color.parseColor("#F8FAFC")
    private val COLOR_BORDER = Color.parseColor("#64748B")
    private val COLOR_LIGHT_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_TEXT = Color.parseColor("#0F172A")
    private val COLOR_MUTED = Color.parseColor("#475569")
    private val COLOR_GREEN = Color.parseColor("#166534")
    private val COLOR_RED = Color.parseColor("#B91C1C")
    private val COLOR_CANCELLED_BG = Color.parseColor("#FEE2E2")

    // =========================================================================
    // 1. A4 CERTIFICATES (TC, BONAFIDE, CHARACTER)
    // =========================================================================
    fun generateCertificatePdf(context: Context, bundle: CertificatePdfBundle): File {
        val dir = File(context.cacheDir, "phase10_pdfs").apply { mkdirs() }
        val safeNo = sanitize(bundle.certificate.certificateNumber, "Cert")
        val outFile = File(dir, "${bundle.certificate.certificateType.numberPrefix}_${safeNo}.pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
            drawCertificatePage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderCertificatePreviewBitmap(bundle: CertificatePdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawCertificatePage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawCertificatePage(canvas: Canvas, bundle: CertificatePdfBundle) {
        val school = bundle.school
        val c = bundle.certificate
        val left = MARGIN
        val right = A4_W - MARGIN
        val contentW = right - left

        // Double official border frame
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_PRIMARY
            strokeWidth = 2.4f
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_GOLD
            strokeWidth = 1.0f
        }
        canvas.drawRect(RectF(16f, 16f, A4_W - 16f, A4_H - 16f), outerPaint)
        canvas.drawRect(RectF(21f, 21f, A4_W - 21f, A4_H - 21f), innerPaint)

        // Header band with school logo + details
        var y = 34f
        val headerH = 88f
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY }
        canvas.drawRect(RectF(left, y, right, y + headerH), bandPaint)

        // Logo box on left
        val logoBox = RectF(left + 12f, y + 10f, left + 76f, y + 74f)
        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(logoBox, 6f, 6f, whiteFill)
        val logoBmp = ImageUtils.decodeBase64ToBitmap(school.logoBase64)
        if (logoBmp != null) {
            canvas.drawBitmap(logoBmp, null, RectF(logoBox.left + 4f, logoBox.top + 4f, logoBox.right - 4f, logoBox.bottom - 4f), null)
        } else {
            val initPaint = makePaint(16f, COLOR_PRIMARY, bold = true, align = Paint.Align.CENTER)
            drawScaledText(canvas, school.name.take(3).uppercase(Locale.US), logoBox.centerX(), logoBox.centerY() + 6f, initPaint)
        }

        val textCenterX = (left + 84f + right - 12f) / 2f
        val schoolTitlePaint = makePaint(17f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
        val schoolSubPaint = makePaint(9.5f, Color.parseColor("#E2E8F0"), bold = false, align = Paint.Align.CENTER)
        drawScaledText(canvas, ellipsize(school.name.uppercase(Locale.US), 42), textCenterX, y + 30f, schoolTitlePaint)
        if (school.address.isNotBlank()) {
            drawScaledText(canvas, ellipsize(school.address, 65), textCenterX, y + 49f, schoolSubPaint)
        }
        val contact = buildList {
            if (school.mobile.isNotBlank()) add("Mob: ${school.mobile}")
            if (school.email.isNotBlank()) add("Email: ${school.email}")
        }.joinToString("  |  ")
        if (contact.isNotBlank()) {
            drawScaledText(canvas, ellipsize(contact, 72), textCenterX, y + 66f, schoolSubPaint)
        }
        y += headerH + 18f

        // Official Certificate Title Ribbon
        val ribbonW = 290f
        val ribbonLeft = (A4_W - ribbonW) / 2f
        val ribbonRect = RectF(ribbonLeft, y, ribbonLeft + ribbonW, y + 30f)
        val ribbonFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TINT }
        val ribbonStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_GOLD
            strokeWidth = 1.4f
        }
        canvas.drawRoundRect(ribbonRect, 5f, 5f, ribbonFill)
        canvas.drawRoundRect(ribbonRect, 5f, 5f, ribbonStroke)
        drawScaledText(
            canvas,
            c.certificateType.englishTitle.uppercase(Locale.US),
            A4_W / 2f,
            y + 20f,
            makePaint(13f, COLOR_PRIMARY, bold = true, align = Paint.Align.CENTER)
        )
        y += 44f

        // Metadata row: Certificate No. | Session | Date
        val metaBold = makePaint(10f, COLOR_TEXT, bold = true)
        val metaRight = makePaint(10f, COLOR_TEXT, bold = true, align = Paint.Align.RIGHT)
        drawScaledText(canvas, "Certificate No.: ${c.certificateNumber}", left + 6f, y, metaBold)
        drawScaledText(canvas, "Session: ${c.session}", A4_W / 2f, y, makePaint(10f, COLOR_MUTED, bold = true, align = Paint.Align.CENTER))
        drawScaledText(canvas, "Date: ${c.issueDateIso}", right - 6f, y, metaRight)
        y += 12f
        drawHorizontalRule(canvas, left + 4f, right - 4f, y, COLOR_LIGHT_BORDER, 1f)
        y += 18f

        if (c.reissuedFromCertNumber.isNotBlank()) {
            drawScaledText(
                canvas,
                "Re-issued in place of cancelled certificate: ${c.reissuedFromCertNumber}",
                left + 6f,
                y,
                makePaint(8.5f, COLOR_MUTED, italic = true)
            )
            y += 16f
        }

        // Body tailored per certificate type
        when (c.certificateType) {
            CertificateType.TC -> y = drawTransferCertificateBody(canvas, school, c, left + 6f, right - 6f, y)
            CertificateType.BONAFIDE -> y = drawBonafideCertificateBody(canvas, school, c, left + 6f, right - 6f, y)
            CertificateType.CHARACTER -> y = drawCharacterCertificateBody(canvas, school, c, left + 6f, right - 6f, y)
        }

        // Cancelled banner & diagonal watermark if cancelled
        if (c.isCancelled) {
            val warnRect = RectF(left + 12f, y.coerceAtMost(625f), right - 12f, (y + 38f).coerceAtMost(665f))
            canvas.drawRoundRect(warnRect, 4f, 4f, Paint().apply { color = COLOR_CANCELLED_BG })
            drawScaledText(
                canvas,
                "CANCELLED ON ${formatEpochDate(c.cancelledAtMs)} BY ${c.cancelledByName} — REASON: ${ellipsize(c.cancelReason, 54)}",
                warnRect.centerX(),
                warnRect.centerY() + 4f,
                makePaint(8.5f, COLOR_RED, bold = true, align = Paint.Align.CENTER)
            )

            canvas.save()
            canvas.rotate(-32f, A4_W / 2f, A4_H / 2f)
            val wmPaint = makePaint(62f, Color.argb(46, 185, 28, 28), bold = true, align = Paint.Align.CENTER)
            drawScaledText(canvas, "CANCELLED", A4_W / 2f, A4_H / 2f + 16f, wmPaint)
            canvas.restore()
        }

        // Signature Block at bottom
        val sigY = A4_H - 122f
        drawHorizontalRule(canvas, left + 10f, left + 150f, sigY, COLOR_BORDER, 0.9f)
        drawScaledText(canvas, "Prepared / Checked By", left + 80f, sigY + 14f, makePaint(9f, COLOR_MUTED, bold = true, align = Paint.Align.CENTER))
        drawScaledText(canvas, ellipsize(c.issuedByName, 26), left + 80f, sigY + 27f, makePaint(8.5f, COLOR_TEXT, align = Paint.Align.CENTER))

        // Office Seal box in center
        val sealRect = RectF(A4_W / 2f - 46f, sigY - 46f, A4_W / 2f + 46f, sigY + 8f)
        val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_LIGHT_BORDER
            strokeWidth = 0.9f
        }
        canvas.drawRoundRect(sealRect, 4f, 4f, sealPaint)
        drawScaledText(canvas, "SCHOOL SEAL", sealRect.centerX(), sealRect.centerY() + 3f, makePaint(8f, COLOR_MUTED, bold = true, align = Paint.Align.CENTER))

        // Principal signature on right
        drawHorizontalRule(canvas, right - 165f, right - 10f, sigY, COLOR_BORDER, 0.9f)
        drawScaledText(canvas, "Principal / Headmaster", right - 87.5f, sigY + 14f, makePaint(9.5f, COLOR_PRIMARY, bold = true, align = Paint.Align.CENTER))
        drawScaledText(canvas, ellipsize(school.name, 30), right - 87.5f, sigY + 27f, makePaint(8.5f, COLOR_MUTED, align = Paint.Align.CENTER))

        // Footer bar
        val footerY = A4_H - 36f
        drawHorizontalRule(canvas, left, right, footerY - 12f, COLOR_LIGHT_BORDER, 0.7f)
        drawScaledText(
            canvas,
            "Issued Date: ${c.issueDateIso}  |  Admission No.: ${c.studentAdmNo}  |  Cert No.: ${c.certificateNumber}",
            A4_W / 2f,
            footerY,
            makePaint(8f, COLOR_MUTED, align = Paint.Align.CENTER)
        )
    }

    private fun drawTransferCertificateBody(
        canvas: Canvas,
        school: SchoolEntity,
        c: SchoolCertificateRecord,
        left: Float,
        right: Float,
        startY: Float
    ): Float {
        var y = startY
        val intro = "This is to certify that the following particulars of the student are true and correct as per the School Admission Register of ${school.name}."
        y = drawWrappedParagraph(canvas, intro, left, right, y, 10f, COLOR_TEXT, lineHeight = 15f)
        y += 10f

        val rows = listOf(
            "1. Admission No." to c.studentAdmNo.ifBlank { "-" },
            "2. Name of Pupil" to c.studentName.ifBlank { "-" },
            "3. Father's / Guardian's Name" to c.fatherName.ifBlank { "-" },
            "4. Mother's Name" to c.motherName.ifBlank { "-" },
            "5. Date of Birth (as per register)" to c.dob.ifBlank { "-" },
            "6. Date of First Admission" to c.admissionDate.ifBlank { "-" },
            "7. Class & Section Last Studied" to "${c.className} - Section ${c.section} (Roll No. ${c.rollNo})",
            "8. Academic Session" to c.session,
            "9. Last Date of Attendance" to c.lastAttendanceDateIso.ifBlank { c.issueDateIso },
            "10. Promotion / Result Status" to c.promotionStatus.ifBlank { "As per annual record" },
            "11. Fee & School Dues Status" to c.feeDuesStatus.ifBlank { "All school dues cleared" },
            "12. Reason for Leaving School" to c.reasonForLeaving.ifBlank { "Parent's request" },
            "13. General Conduct & Behaviour" to c.conduct.ifBlank { "Good" },
            "14. Residential Address" to c.address.ifBlank { "-" },
            "15. Any Other Remarks" to c.remarks.ifBlank { "Wishing the student bright success ahead." }
        )

        val rowH = 26f
        val splitX = left + (right - left) * 0.44f
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            color = COLOR_LIGHT_BORDER
            strokeWidth = 0.7f
        }
        val zebra = Paint().apply { color = COLOR_ZEBRA }
        val labelPaint = makePaint(9.5f, COLOR_PRIMARY, bold = true)
        val valPaint = makePaint(9.5f, COLOR_TEXT, bold = false)

        rows.forEachIndexed { idx, (label, value) ->
            val rect = RectF(left, y, right, y + rowH)
            if (idx % 2 == 1) canvas.drawRect(rect, zebra)
            canvas.drawRect(rect, stroke)
            canvas.drawLine(splitX, y, splitX, y + rowH, stroke)
            drawScaledText(canvas, label, left + 8f, y + 17f, labelPaint)
            drawScaledText(canvas, ellipsize(value, 48), splitX + 8f, y + 17f, valPaint)
            y += rowH
        }
        return y + 14f
    }

    private fun drawBonafideCertificateBody(
        canvas: Canvas,
        school: SchoolEntity,
        c: SchoolCertificateRecord,
        left: Float,
        right: Float,
        startY: Float
    ): Float {
        var y = startY + 12f
        drawScaledText(
            canvas,
            "TO WHOMSOEVER IT MAY CONCERN",
            (left + right) / 2f,
            y,
            makePaint(12f, COLOR_PRIMARY, bold = true, align = Paint.Align.CENTER)
        )
        y += 28f

        val p1 = "This is to certify that Master / Kumari ${c.studentName.uppercase(Locale.US)}, " +
            "Son / Daughter of Shri ${c.fatherName.ifBlank { "____________________" }}" +
            (if (c.motherName.isNotBlank()) " and Smt. ${c.motherName}" else "") +
            ", bearing Admission No. ${c.studentAdmNo.ifBlank { "-" }}, is a bonafide student of " +
            "${school.name} studying in Class ${c.className} (Section ${c.section}, Roll No. ${c.rollNo}) " +
            "during the academic session ${c.session}."
        y = drawWrappedParagraph(canvas, p1, left + 6f, right - 6f, y, 11.5f, COLOR_TEXT, lineHeight = 20f)
        y += 18f

        val details = listOf(
            "Student Full Name" to c.studentName,
            "Admission Number" to c.studentAdmNo.ifBlank { "-" },
            "Father's Name" to c.fatherName.ifBlank { "-" },
            "Mother's Name" to c.motherName.ifBlank { "-" },
            "Date of Birth" to c.dob.ifBlank { "-" },
            "Class & Section" to "${c.className} - ${c.section}",
            "Academic Session" to c.session,
            "Residential Address" to c.address.ifBlank { "-" },
            "Conduct" to c.conduct.ifBlank { "Good" }
        )
        y = drawKeyValueBox(canvas, details, left + 6f, right - 6f, y)
        y += 18f

        val p2 = if (c.remarks.isNotBlank()) {
            "Remarks / Purpose: ${c.remarks}"
        } else {
            "To the best of our knowledge, the student bears a ${c.conduct.lowercase(Locale.US)} moral character. " +
                "This certificate is issued on request for official / scholarship / verification purposes."
        }
        y = drawWrappedParagraph(canvas, p2, left + 6f, right - 6f, y, 11f, COLOR_TEXT, lineHeight = 19f)
        return y + 14f
    }

    private fun drawCharacterCertificateBody(
        canvas: Canvas,
        school: SchoolEntity,
        c: SchoolCertificateRecord,
        left: Float,
        right: Float,
        startY: Float
    ): Float {
        var y = startY + 12f
        drawScaledText(
            canvas,
            "TO WHOMSOEVER IT MAY CONCERN",
            (left + right) / 2f,
            y,
            makePaint(12f, COLOR_PRIMARY, bold = true, align = Paint.Align.CENTER)
        )
        y += 28f

        val p1 = "This is to certify that Master / Kumari ${c.studentName.uppercase(Locale.US)}, " +
            "Son / Daughter of Shri ${c.fatherName.ifBlank { "____________________" }}" +
            (if (c.motherName.isNotBlank()) " and Smt. ${c.motherName}" else "") +
            ", Admission No. ${c.studentAdmNo.ifBlank { "-" }}, has been a student of ${school.name} " +
            "in Class ${c.className} (Section ${c.section}) during the academic session ${c.session}."
        y = drawWrappedParagraph(canvas, p1, left + 6f, right - 6f, y, 11.5f, COLOR_TEXT, lineHeight = 20f)
        y += 16f

        val p2 = "During his / her stay in the institution, his / her general conduct, discipline, and moral character " +
            "have been found to be ${c.conduct.uppercase(Locale.US)}. He / She has not been involved in any act of indiscipline."
        y = drawWrappedParagraph(canvas, p2, left + 6f, right - 6f, y, 11.5f, COLOR_TEXT, lineHeight = 20f)
        y += 18f

        val details = listOf(
            "Student Name" to c.studentName,
            "Admission No." to c.studentAdmNo.ifBlank { "-" },
            "Father's Name" to c.fatherName.ifBlank { "-" },
            "Date of Birth" to c.dob.ifBlank { "-" },
            "Class & Session" to "${c.className} (${c.section}) • ${c.session}",
            "Conduct & Character" to c.conduct.ifBlank { "Good" },
            "Remarks" to c.remarks.ifBlank { "We wish the student every success in future endeavours." }
        )
        y = drawKeyValueBox(canvas, details, left + 6f, right - 6f, y)
        return y + 18f
    }

    private fun drawKeyValueBox(
        canvas: Canvas,
        rows: List<Pair<String, String>>,
        left: Float,
        right: Float,
        startY: Float
    ): Float {
        var y = startY
        val rowH = 27f
        val splitX = left + (right - left) * 0.36f
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            color = COLOR_LIGHT_BORDER
            strokeWidth = 0.8f
        }
        val zebra = Paint().apply { color = COLOR_ZEBRA }
        rows.forEachIndexed { idx, (k, v) ->
            val rect = RectF(left, y, right, y + rowH)
            if (idx % 2 == 0) canvas.drawRect(rect, zebra)
            canvas.drawRect(rect, stroke)
            canvas.drawLine(splitX, y, splitX, y + rowH, stroke)
            drawScaledText(canvas, k, left + 8f, y + 18f, makePaint(10f, COLOR_PRIMARY, bold = true))
            drawScaledText(canvas, ellipsize(v, 52), splitX + 8f, y + 18f, makePaint(10f, COLOR_TEXT))
            y += rowH
        }
        return y
    }

    // =========================================================================
    // 2. A4 CASH BOOK DAY BOOK PDF
    // =========================================================================
    private data class DayBookPdfRow(
        val typeLabel: String,
        val particulars: String,
        val mode: String,
        val moneyIn: Int,
        val moneyOut: Int,
        val note: String
    )

    private fun buildDayBookRows(summary: CashBookDaySummary): List<DayBookPdfRow> {
        val list = mutableListOf<DayBookPdfRow>()
        for (rcp in summary.feeIncomeReceipts) {
            list += DayBookPdfRow(
                typeLabel = "Fee Receipt",
                particulars = "${rcp.receiptNumber} • ${rcp.studentName} (${rcp.className}-${rcp.section})",
                mode = rcp.paymentMode.label,
                moneyIn = rcp.amountPaid,
                moneyOut = 0,
                note = rcp.receivedByName
            )
        }
        for (inc in summary.activeOtherIncomes) {
            list += DayBookPdfRow(
                typeLabel = "Other Income",
                particulars = inc.category,
                mode = inc.paymentMode.label,
                moneyIn = inc.amount,
                moneyOut = 0,
                note = inc.note.ifBlank { inc.createdByName }
            )
        }
        for (exp in summary.activeExpenses) {
            val appr = if (exp.approvedByAdminName.isNotBlank()) " [Appr: ${exp.approvedByAdminName}]" else ""
            list += DayBookPdfRow(
                typeLabel = "Expense",
                particulars = exp.category,
                mode = exp.paymentMode.label,
                moneyIn = 0,
                moneyOut = exp.amount,
                note = "${exp.note.ifBlank { exp.createdByName }}$appr"
            )
        }
        return list
    }

    fun generateCashBookDayBookPdf(context: Context, bundle: CashBookDayBookPdfBundle): File {
        val dir = File(context.cacheDir, "phase10_pdfs").apply { mkdirs() }
        val safeDate = sanitize(bundle.summary.dateIso, "Day")
        val outFile = File(dir, "CashBook_DayBook_${safeDate}.pdf")
        val rows = buildDayBookRows(bundle.summary)
        val chunks = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(24)
        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawDayBookPage(page.canvas, bundle, chunk, idx + 1, chunks.size, isLastPage = idx == chunks.lastIndex)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderCashBookDayBookPreviewBitmaps(bundle: CashBookDayBookPdfBundle, scale: Int = 2): List<Bitmap> {
        val rows = buildDayBookRows(bundle.summary)
        val chunks = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(24)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawDayBookPage(canvas, bundle, chunk, idx + 1, chunks.size, isLastPage = idx == chunks.lastIndex)
            canvas.restore()
            bmp
        }
    }

    private fun drawDayBookPage(
        canvas: Canvas,
        bundle: CashBookDayBookPdfBundle,
        rows: List<DayBookPdfRow>,
        pageNo: Int,
        totalPages: Int,
        isLastPage: Boolean
    ) {
        val s = bundle.summary
        var y = drawReportHeader(
            canvas,
            bundle.school,
            title = "CASH BOOK — DAY BOOK (${s.dateIso})",
            subtitle = "Fee Receipts: ${s.feeIncomeReceipts.size}  |  Other Income: ${s.activeOtherIncomes.size}  |  Expenses: ${s.activeExpenses.size}"
        )

        // Summary KPI boxes on page 1
        if (pageNo == 1) {
            y = drawKpiStrip(
                canvas,
                y,
                listOf(
                    Triple("Opening Balance", "Rs. ${s.openingBalance}", COLOR_PRIMARY),
                    Triple("Money In", "Rs. ${s.totalMoneyIn}", COLOR_GREEN),
                    Triple("Money Out", "Rs. ${s.totalMoneyOut}", COLOR_RED),
                    Triple("Closing Balance", "Rs. ${s.closingBalance}", COLOR_PRIMARY)
                )
            )
            y += 10f
        }

        // Table Header
        val left = MARGIN
        val right = A4_W - MARGIN
        val colType = left
        val colPart = left + 74f
        val colMode = left + 270f
        val colIn = left + 332f
        val colOut = left + 402f
        val colNote = left + 468f

        canvas.drawRect(RectF(left, y, right, y + 22f), Paint().apply { color = COLOR_PRIMARY })
        val th = makePaint(8.5f, Color.WHITE, bold = true)
        drawScaledText(canvas, "Type", colType + 4f, y + 15f, th)
        drawScaledText(canvas, "Particulars / Receipt", colPart + 4f, y + 15f, th)
        drawScaledText(canvas, "Mode", colMode + 4f, y + 15f, th)
        drawScaledText(canvas, "In (Rs)", colIn + 56f, y + 15f, makePaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Out (Rs)", colOut + 56f, y + 15f, makePaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Note", colNote + 4f, y + 15f, th)
        y += 22f

        val rowH = 21f
        val border = Paint().apply { style = Paint.Style.STROKE; color = COLOR_LIGHT_BORDER; strokeWidth = 0.6f }
        if (rows.isEmpty()) {
            canvas.drawRect(RectF(left, y, right, y + 28f), border)
            drawScaledText(canvas, "No cash book transactions recorded for ${s.dateIso}.", A4_W / 2f, y + 18f, makePaint(9.5f, COLOR_MUTED, align = Paint.Align.CENTER))
            y += 32f
        } else {
            rows.forEachIndexed { idx, row ->
                val rect = RectF(left, y, right, y + rowH)
                if (idx % 2 == 1) canvas.drawRect(rect, Paint().apply { color = COLOR_ZEBRA })
                canvas.drawRect(rect, border)
                drawScaledText(canvas, ellipsize(row.typeLabel, 13), colType + 4f, y + 14f, makePaint(8f, if (row.moneyOut > 0) COLOR_RED else COLOR_GREEN, bold = true))
                drawScaledText(canvas, ellipsize(row.particulars, 36), colPart + 4f, y + 14f, makePaint(8f, COLOR_TEXT))
                drawScaledText(canvas, ellipsize(row.mode, 10), colMode + 4f, y + 14f, makePaint(8f, COLOR_MUTED))
                drawScaledText(canvas, if (row.moneyIn > 0) "${row.moneyIn}" else "-", colIn + 56f, y + 14f, makePaint(8.5f, COLOR_GREEN, bold = row.moneyIn > 0, align = Paint.Align.RIGHT))
                drawScaledText(canvas, if (row.moneyOut > 0) "${row.moneyOut}" else "-", colOut + 56f, y + 14f, makePaint(8.5f, COLOR_RED, bold = row.moneyOut > 0, align = Paint.Align.RIGHT))
                drawScaledText(canvas, ellipsize(row.note, 16), colNote + 4f, y + 14f, makePaint(7.5f, COLOR_MUTED))
                y += rowH
            }
        }

        if (isLastPage) {
            y += 10f
            val boxRect = RectF(left, y, right, y + 72f)
            canvas.drawRoundRect(boxRect, 4f, 4f, Paint().apply { color = COLOR_TINT })
            canvas.drawRoundRect(boxRect, 4f, 4f, Paint().apply { style = Paint.Style.STROKE; color = COLOR_PRIMARY; strokeWidth = 0.9f })
            drawScaledText(canvas, "DAY CLOSING & CASH RECONCILIATION", left + 10f, y + 16f, makePaint(9f, COLOR_PRIMARY, bold = true))
            val line1 = "Cash In: Rs. ${s.totalCashIn}   |   Cash Out: Rs. ${s.totalCashOut}   |   Non-Cash In: Rs. ${s.nonCashInTotal}   |   Non-Cash Out: Rs. ${s.nonCashOutTotal}"
            drawScaledText(canvas, line1, left + 10f, y + 33f, makePaint(8.5f, COLOR_TEXT))
            val closing = s.savedClosing
            val line2 = if (closing != null) {
                "Expected Cash: Rs. ${closing.expectedClosingCash}   |   Actual Counted Cash: Rs. ${closing.countedCash}   |   Difference: Rs. ${closing.cashDifference}   (Closed by ${closing.closedByName})"
            } else {
                "Expected Closing Cash: Rs. ${s.expectedClosingCash}   |   Day Closing not yet finalised"
            }
            drawScaledText(canvas, ellipsize(line2, 96), left + 10f, y + 52f, makePaint(8.5f, if ((closing?.cashDifference ?: 0) != 0) COLOR_RED else COLOR_GREEN, bold = true))
        }

        drawReportFooter(canvas, pageNo, totalPages)
    }

    // =========================================================================
    // 3. A4 CASH BOOK MONTH SUMMARY PDF
    // =========================================================================
    fun generateCashBookMonthSummaryPdf(context: Context, bundle: CashBookMonthSummaryPdfBundle): File {
        val dir = File(context.cacheDir, "phase10_pdfs").apply { mkdirs() }
        val safeYm = sanitize(bundle.summary.yearMonth, "Month")
        val outFile = File(dir, "CashBook_MonthSummary_${safeYm}.pdf")
        val chunks = if (bundle.summary.dailyRows.isEmpty()) listOf(emptyList()) else bundle.summary.dailyRows.chunked(26)
        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create())
                drawMonthSummaryPage(page.canvas, bundle, chunk, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderCashBookMonthSummaryPreviewBitmaps(bundle: CashBookMonthSummaryPdfBundle, scale: Int = 2): List<Bitmap> {
        val chunks = if (bundle.summary.dailyRows.isEmpty()) listOf(emptyList()) else bundle.summary.dailyRows.chunked(26)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawMonthSummaryPage(canvas, bundle, chunk, idx + 1, chunks.size)
            canvas.restore()
            bmp
        }
    }

    private fun drawMonthSummaryPage(
        canvas: Canvas,
        bundle: CashBookMonthSummaryPdfBundle,
        rows: List<com.example.domain.CashBookDailyRollupRow>,
        pageNo: Int,
        totalPages: Int
    ) {
        val s = bundle.summary
        var y = drawReportHeader(
            canvas,
            bundle.school,
            title = "CASH BOOK — MONTHLY SUMMARY (${s.yearMonth})",
            subtitle = "Active Fee Receipts: ${s.activeReceiptsCount}  |  Active Cash Book Entries: ${s.activeEntries.size}"
        )
        if (pageNo == 1) {
            y = drawKpiStrip(
                canvas,
                y,
                listOf(
                    Triple("Fee Income", "Rs. ${s.totalFeeIncome}", COLOR_GREEN),
                    Triple("Other Income", "Rs. ${s.totalOtherIncome}", COLOR_PRIMARY),
                    Triple("Total Expenses", "Rs. ${s.totalMoneyOut}", COLOR_RED),
                    Triple("Net Month Balance", "Rs. ${s.netMonthBalance}", COLOR_PRIMARY)
                )
            )
            y += 10f
        }

        val left = MARGIN
        val right = A4_W - MARGIN
        val cols = listOf(left, left + 95f, left + 185f, left + 275f, left + 365f, left + 455f)
        canvas.drawRect(RectF(left, y, right, y + 22f), Paint().apply { color = COLOR_PRIMARY })
        val th = makePaint(8.5f, Color.WHITE, bold = true)
        drawScaledText(canvas, "Date", cols[0] + 6f, y + 15f, th)
        drawScaledText(canvas, "Fee In", cols[1] + 75f, y + 15f, makePaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Other In", cols[2] + 75f, y + 15f, makePaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Total In", cols[3] + 75f, y + 15f, makePaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Expenses", cols[4] + 75f, y + 15f, makePaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Net Day", right - 6f, y + 15f, makePaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        y += 22f

        val rowH = 22f
        val border = Paint().apply { style = Paint.Style.STROKE; color = COLOR_LIGHT_BORDER; strokeWidth = 0.6f }
        if (rows.isEmpty()) {
            canvas.drawRect(RectF(left, y, right, y + 28f), border)
            drawScaledText(canvas, "No transactions recorded in ${s.yearMonth}.", A4_W / 2f, y + 18f, makePaint(9.5f, COLOR_MUTED, align = Paint.Align.CENTER))
        } else {
            rows.forEachIndexed { idx, r ->
                val rect = RectF(left, y, right, y + rowH)
                if (idx % 2 == 1) canvas.drawRect(rect, Paint().apply { color = COLOR_ZEBRA })
                canvas.drawRect(rect, border)
                drawScaledText(canvas, r.dateIso, cols[0] + 6f, y + 15f, makePaint(8.5f, COLOR_TEXT, bold = true))
                drawScaledText(canvas, "${r.feeIncome}", cols[1] + 75f, y + 15f, makePaint(8.5f, COLOR_GREEN, align = Paint.Align.RIGHT))
                drawScaledText(canvas, "${r.otherIncome}", cols[2] + 75f, y + 15f, makePaint(8.5f, COLOR_TEXT, align = Paint.Align.RIGHT))
                drawScaledText(canvas, "${r.totalIn}", cols[3] + 75f, y + 15f, makePaint(8.5f, COLOR_GREEN, bold = true, align = Paint.Align.RIGHT))
                drawScaledText(canvas, "${r.totalOut}", cols[4] + 75f, y + 15f, makePaint(8.5f, COLOR_RED, bold = true, align = Paint.Align.RIGHT))
                drawScaledText(canvas, "${r.netDay}", right - 6f, y + 15f, makePaint(8.5f, if (r.netDay >= 0) COLOR_PRIMARY else COLOR_RED, bold = true, align = Paint.Align.RIGHT))
                y += rowH
            }
        }
        drawReportFooter(canvas, pageNo, totalPages)
    }

    // =========================================================================
    // 4. A4 CATEGORY-WISE EXPENSE REPORT PDF
    // =========================================================================
    fun generateCashBookCategoryExpensePdf(context: Context, bundle: CashBookCategoryExpensePdfBundle): File {
        val dir = File(context.cacheDir, "phase10_pdfs").apply { mkdirs() }
        val safeYm = sanitize(bundle.summary.yearMonth, "Month")
        val outFile = File(dir, "CashBook_CategoryExpenses_${safeYm}.pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
            drawCategoryExpensePage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderCashBookCategoryExpensePreviewBitmap(bundle: CashBookCategoryExpensePdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawCategoryExpensePage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawCategoryExpensePage(canvas: Canvas, bundle: CashBookCategoryExpensePdfBundle) {
        val s = bundle.summary
        var y = drawReportHeader(
            canvas,
            bundle.school,
            title = "CATEGORY-WISE EXPENSE REPORT (${s.yearMonth})",
            subtitle = "Total Expenses: Rs. ${s.totalMoneyOut}  across ${s.categoryTotals.size} categories"
        )

        val left = MARGIN
        val right = A4_W - MARGIN
        canvas.drawRect(RectF(left, y, right, y + 24f), Paint().apply { color = COLOR_PRIMARY })
        val th = makePaint(9f, Color.WHITE, bold = true)
        drawScaledText(canvas, "#", left + 8f, y + 16f, th)
        drawScaledText(canvas, "Expense Category", left + 36f, y + 16f, th)
        drawScaledText(canvas, "Entries", left + 310f, y + 16f, makePaint(9f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Amount (Rs)", left + 430f, y + 16f, makePaint(9f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        drawScaledText(canvas, "Share %", right - 8f, y + 16f, makePaint(9f, Color.WHITE, bold = true, align = Paint.Align.RIGHT))
        y += 24f

        val rowH = 26f
        val border = Paint().apply { style = Paint.Style.STROKE; color = COLOR_LIGHT_BORDER; strokeWidth = 0.7f }
        if (s.categoryTotals.isEmpty()) {
            canvas.drawRect(RectF(left, y, right, y + 32f), border)
            drawScaledText(canvas, "No expenses recorded in ${s.yearMonth}.", A4_W / 2f, y + 20f, makePaint(9.5f, COLOR_MUTED, align = Paint.Align.CENTER))
            y += 32f
        } else {
            s.categoryTotals.forEachIndexed { idx, cat ->
                val rect = RectF(left, y, right, y + rowH)
                if (idx % 2 == 1) canvas.drawRect(rect, Paint().apply { color = COLOR_ZEBRA })
                canvas.drawRect(rect, border)
                drawScaledText(canvas, "${idx + 1}", left + 8f, y + 17f, makePaint(9f, COLOR_MUTED))
                drawScaledText(canvas, ellipsize(cat.category, 36), left + 36f, y + 17f, makePaint(9.5f, COLOR_TEXT, bold = true))
                drawScaledText(canvas, "${cat.entryCount}", left + 310f, y + 17f, makePaint(9f, COLOR_TEXT, align = Paint.Align.RIGHT))
                drawScaledText(canvas, "Rs. ${cat.totalAmount}", left + 430f, y + 17f, makePaint(9.5f, COLOR_RED, bold = true, align = Paint.Align.RIGHT))
                drawScaledText(canvas, "${cat.sharePercent}%", right - 8f, y + 17f, makePaint(9f, COLOR_PRIMARY, bold = true, align = Paint.Align.RIGHT))
                y += rowH
            }
            // Total Row
            val totRect = RectF(left, y, right, y + 28f)
            canvas.drawRect(totRect, Paint().apply { color = COLOR_TINT })
            canvas.drawRect(totRect, Paint().apply { style = Paint.Style.STROKE; color = COLOR_PRIMARY; strokeWidth = 0.9f })
            drawScaledText(canvas, "TOTAL EXPENSES", left + 36f, y + 18f, makePaint(9.5f, COLOR_PRIMARY, bold = true))
            drawScaledText(canvas, "${s.categoryTotals.sumOf { it.entryCount }}", left + 310f, y + 18f, makePaint(9.5f, COLOR_PRIMARY, bold = true, align = Paint.Align.RIGHT))
            drawScaledText(canvas, "Rs. ${s.totalMoneyOut}", left + 430f, y + 18f, makePaint(10f, COLOR_RED, bold = true, align = Paint.Align.RIGHT))
            drawScaledText(canvas, "100%", right - 8f, y + 18f, makePaint(9.5f, COLOR_PRIMARY, bold = true, align = Paint.Align.RIGHT))
        }
        drawReportFooter(canvas, 1, 1)
    }

    // =========================================================================
    // SHARED DRAWING PRIMITIVES
    // =========================================================================
    private fun drawReportHeader(
        canvas: Canvas,
        school: SchoolEntity,
        title: String,
        subtitle: String
    ): Float {
        val left = MARGIN
        val right = A4_W - MARGIN
        var y = 26f
        canvas.drawRect(RectF(left, y, right, y + 58f), Paint().apply { color = COLOR_PRIMARY })
        val logoBmp = ImageUtils.decodeBase64ToBitmap(school.logoBase64)
        if (logoBmp != null) {
            val lBox = RectF(left + 8f, y + 7f, left + 52f, y + 51f)
            canvas.drawRoundRect(lBox, 4f, 4f, Paint().apply { color = Color.WHITE })
            canvas.drawBitmap(logoBmp, null, RectF(lBox.left + 2f, lBox.top + 2f, lBox.right - 2f, lBox.bottom - 2f), null)
        }
        val cx = A4_W / 2f
        drawScaledText(canvas, ellipsize(school.name.uppercase(Locale.US), 46), cx, y + 22f, makePaint(13.5f, Color.WHITE, bold = true, align = Paint.Align.CENTER))
        drawScaledText(canvas, ellipsize(title, 58), cx, y + 38f, makePaint(10f, Color.parseColor("#FDE68A"), bold = true, align = Paint.Align.CENTER))
        drawScaledText(canvas, ellipsize(subtitle, 72), cx, y + 51f, makePaint(8f, Color.parseColor("#E2E8F0"), align = Paint.Align.CENTER))
        y += 68f
        return y
    }

    private fun drawKpiStrip(
        canvas: Canvas,
        startY: Float,
        items: List<Triple<String, String, Int>>
    ): Float {
        val left = MARGIN
        val right = A4_W - MARGIN
        val gap = 8f
        val boxW = ((right - left) - gap * (items.size - 1)) / items.size
        val boxH = 42f
        items.forEachIndexed { i, (label, value, valueColor) ->
            val bx = left + i * (boxW + gap)
            val rect = RectF(bx, startY, bx + boxW, startY + boxH)
            canvas.drawRoundRect(rect, 4f, 4f, Paint().apply { color = COLOR_TINT })
            canvas.drawRoundRect(rect, 4f, 4f, Paint().apply { style = Paint.Style.STROKE; color = COLOR_LIGHT_BORDER; strokeWidth = 0.8f })
            drawScaledText(canvas, label, rect.centerX(), startY + 14f, makePaint(8f, COLOR_MUTED, bold = true, align = Paint.Align.CENTER))
            drawScaledText(canvas, value, rect.centerX(), startY + 32f, makePaint(11f, valueColor, bold = true, align = Paint.Align.CENTER))
        }
        return startY + boxH
    }

    private fun drawReportFooter(canvas: Canvas, pageNo: Int, totalPages: Int) {
        val left = MARGIN
        val right = A4_W - MARGIN
        val fy = A4_H - 26f
        drawHorizontalRule(canvas, left, right, fy - 12f, COLOR_LIGHT_BORDER, 0.7f)
        drawScaledText(canvas, "Generated Offline • Cash Book & Accounts Register", left, fy, makePaint(8f, COLOR_MUTED))
        drawScaledText(canvas, "Page $pageNo of $totalPages", right, fy, makePaint(8f, COLOR_MUTED, align = Paint.Align.RIGHT))
    }

    private fun drawWrappedParagraph(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        startY: Float,
        sizePt: Float,
        color: Int,
        lineHeight: Float
    ): Float {
        val paint = makePaint(sizePt, color)
        val maxWidthScaled = (right - left) * TEXT_SCALE
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var currentLine = StringBuilder()
        var y = startY
        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidthScaled) {
                currentLine = StringBuilder(candidate)
            } else {
                if (currentLine.isNotEmpty()) {
                    drawScaledText(canvas, currentLine.toString(), left, y, paint)
                    y += lineHeight
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            drawScaledText(canvas, currentLine.toString(), left, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun makePaint(
        sizePt: Float,
        color: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizePt * TEXT_SCALE
        textAlign = align
        letterSpacing = LETTER_SPACING_EM
        typeface = when {
            bold && italic -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            bold -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            italic -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }

    private fun drawScaledText(canvas: Canvas, text: String, xPt: Float, yPt: Float, paint: Paint) {
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(text, xPt * TEXT_SCALE, yPt * TEXT_SCALE, paint)
        canvas.restore()
    }

    private fun drawHorizontalRule(canvas: Canvas, x1: Float, x2: Float, y: Float, color: Int, width: Float) {
        canvas.drawLine(x1, y, x2, y, Paint().apply { this.color = color; strokeWidth = width })
    }

    private fun ellipsize(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take((maxChars - 1).coerceAtLeast(1)) + "…"

    private fun sanitize(raw: String, fallback: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { fallback }

    private fun formatEpochDate(epochMs: Long): String {
        if (epochMs <= 0L) return "-"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(java.util.Date(epochMs))
    }
}
