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
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionReportRow
import com.example.domain.DayCollectionRegisterSummary
import com.example.domain.FeeHeadType
import com.example.domain.FeeReceiptRecord
import com.example.domain.OptionalItemSalesRow
import com.example.domain.PaymentMode
import com.example.domain.SessionMonthCode
import com.example.domain.StudentDueCellBreakdown
import com.example.domain.StudentDuesListRow
import com.example.domain.StudentFeeAccountSummary
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class FeeReceiptPdfBundle(
    val school: SchoolEntity,
    val receipt: FeeReceiptRecord,
    val totalStudentRemainingDueAfterReceipt: Int? = null
)

data class StudentDuesSlipPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val slipDateIso: String,
    val student: StudentEntity,
    val dueBreakdowns: List<StudentDueCellBreakdown>,
    val totalDueAmount: Int,
    val preparedByName: String = ""
)

data class DayCollectionPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val summary: DayCollectionRegisterSummary
)

data class DuesListPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val filterTitle: String,
    val asOfDateIso: String,
    val rows: List<StudentDuesListRow>
)

data class ConcessionReportPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val filterTitle: String,
    val rows: List<ConcessionReportRow>
)

data class OptionalSalesPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val filterTitle: String,
    val rows: List<OptionalItemSalesRow>
)

data class AdmissionFormPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val student: StudentEntity,
    val siblingNamesSummary: String = ""
)

data class StudentIdCardsPdfBundle(
    val school: SchoolEntity,
    val session: String,
    val students: List<StudentEntity>
)

/**
 * Phase 9 Offline Native Vector PDF Generators:
 * - A5 Fee Receipt (420 x 595 pt)
 * - A5 Per-Student Dues Slip (420 x 595 pt)
 * - A4 Day Collection Register (595 x 842 pt)
 * - A4 Dues List Report (595 x 842 pt)
 * - A4 Concession Report (595 x 842 pt)
 * - A4 Optional-Item Sales Report (595 x 842 pt)
 * - A4 Student Admission Form (595 x 842 pt)
 * - A4 Multi-Card Student ID Cards (595 x 842 pt, 8 cards per A4 page in 2x4 grid)
 *
 * Uses 4x Paint text scaling (TEXT_SCALE = 4f) for sharp vector typography without overlap.
 * Works 100% offline.
 */
object Phase9PdfGenerator {

    const val A4_W = 595
    const val A4_H = 842
    const val A5_W = 420
    const val A5_H = 595

    private const val A4_MARGIN = 24f
    private const val A5_MARGIN = 18f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.015f

    private val COLOR_BAND = Color.parseColor("#1E3A5F")
    private val COLOR_SUB_BAND = Color.parseColor("#334E68")
    private val COLOR_TINT = Color.parseColor("#E6EEF8")
    private val COLOR_ZEBRA = Color.parseColor("#F8FAFC")
    private val COLOR_BORDER = Color.parseColor("#64748B")
    private val COLOR_LIGHT_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_TEXT = Color.parseColor("#0F172A")
    private val COLOR_MUTED = Color.parseColor("#475569")
    private val COLOR_PAID = Color.parseColor("#166534")
    private val COLOR_DUE = Color.parseColor("#B91C1C")
    private val COLOR_CONCESSION = Color.parseColor("#1D4ED8")
    private val COLOR_CANCELLED_BG = Color.parseColor("#FEE2E2")

    // =========================================================================
    // 1. A5 FEE RECEIPT PDF (420 x 595 pt)
    // =========================================================================
    fun generateFeeReceiptPdf(context: Context, bundle: FeeReceiptPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_fee_pdfs").apply { mkdirs() }
        val safeRcp = sanitize(bundle.receipt.receiptNumber, "Receipt")
        val outFile = File(dir, "Fee_Receipt_${safeRcp}.pdf")
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A5_W, A5_H, 1).create()
            val page = pdf.startPage(pageInfo)
            drawFeeReceiptPage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderFeeReceiptPreviewBitmap(bundle: FeeReceiptPdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A5_W * scale, A5_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawFeeReceiptPage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawFeeReceiptPage(canvas: Canvas, bundle: FeeReceiptPdfBundle) {
        val r = bundle.receipt
        val left = A5_MARGIN
        val right = A5_W - A5_MARGIN
        val contentW = right - left

        val borderPaint = strokePaint(COLOR_BORDER, 1f)
        val lightStroke = strokePaint(COLOR_LIGHT_BORDER, 0.7f)
        val bandFill = fillPaint(COLOR_BAND)
        val tintFill = fillPaint(COLOR_TINT)
        val zebraFill = fillPaint(COLOR_ZEBRA)

        val schoolTitleP = textPaint(11.5f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER)
        val schoolSubP = textPaint(7.2f, COLOR_MUTED, bold = false, align = Paint.Align.CENTER)
        val bandTitleP = textPaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
        val labelP = textPaint(7.2f, COLOR_MUTED, bold = true)
        val valP = textPaint(7.6f, COLOR_TEXT, bold = true)
        val thP = textPaint(7f, Color.WHITE, bold = true)
        val tdP = textPaint(7f, COLOR_TEXT, bold = false)
        val tdBoldP = textPaint(7.2f, COLOR_TEXT, bold = true)
        val bigTotalP = textPaint(9.5f, COLOR_PAID, bold = true)
        val dueBoldP = textPaint(8f, COLOR_DUE, bold = true)
        val footP = textPaint(6.5f, COLOR_MUTED, bold = false)

        // Outer border
        canvas.drawRect(left, A5_MARGIN, right, A5_H - A5_MARGIN, borderPaint)

        var y = A5_MARGIN + 8f
        // School Logo & Header
        val logoBmp = ImageUtils.decodeBase64ToBitmap(bundle.school.logoBase64)
        if (logoBmp != null) {
            val dst = RectF(left + 8f, y, left + 40f, y + 32f)
            canvas.drawBitmap(logoBmp, null, dst, null)
        }
        val centerX = (left + right) / 2f
        drawScaledText(canvas, bundle.school.name.ifBlank { "SCHOOL FEE RECEIPT" }.uppercase(Locale.US), centerX, y + 12f, schoolTitleP)
        val addrLine = listOf(bundle.school.address, bundle.school.mobile.takeIf { it.isNotBlank() }?.let { "Ph: $it" }.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" | ")
        if (addrLine.isNotBlank()) {
            drawScaledText(canvas, fitText(addrLine, schoolSubP, contentW - 76f), centerX, y + 23f, schoolSubP)
        }
        y += 36f

        // Title Banner
        val bannerTitle = if (r.isCancelled) "FEE RECEIPT (CANCELLED)" else "FEE RECEIPT — SESSION ${r.session}"
        val bannerFill = if (r.isCancelled) fillPaint(COLOR_DUE) else bandFill
        canvas.drawRect(left, y, right, y + 18f, bannerFill)
        drawScaledText(canvas, bannerTitle, centerX, y + 12.5f, bandTitleP)
        y += 22f

        if (r.isCancelled) {
            canvas.drawRect(left + 6f, y, right - 6f, y + 18f, fillPaint(COLOR_CANCELLED_BG))
            val cancelP = textPaint(7f, COLOR_DUE, bold = true)
            drawScaledText(
                canvas,
                fitText("CANCELLED: ${r.cancelReason} (By ${r.cancelledByName})", cancelP, contentW - 20f),
                left + 10f,
                y + 12f,
                cancelP
            )
            y += 22f
        }

        // Student & Receipt Metadata Box
        val metaTop = y
        val metaH = 54f
        canvas.drawRect(left + 6f, metaTop, right - 6f, metaTop + metaH, zebraFill)
        canvas.drawRect(left + 6f, metaTop, right - 6f, metaTop + metaH, lightStroke)

        val col1X = left + 12f
        val col2X = left + contentW * 0.54f

        drawScaledText(canvas, "Receipt No:", col1X, metaTop + 13f, labelP)
        drawScaledText(canvas, fitText(r.receiptNumber, valP, contentW * 0.35f), col1X + 48f, metaTop + 13f, valP)

        drawScaledText(canvas, "Date:", col2X, metaTop + 13f, labelP)
        drawScaledText(canvas, r.receiptDateIso, col2X + 38f, metaTop + 13f, valP)

        drawScaledText(canvas, "Student:", col1X, metaTop + 26f, labelP)
        drawScaledText(canvas, fitText(r.studentName, valP, contentW * 0.38f), col1X + 48f, metaTop + 26f, valP)

        drawScaledText(canvas, "Adm No:", col2X, metaTop + 26f, labelP)
        drawScaledText(canvas, fitText(r.studentAdmNo.ifBlank { "-" }, valP, contentW * 0.28f), col2X + 38f, metaTop + 26f, valP)

        drawScaledText(canvas, "Father:", col1X, metaTop + 39f, labelP)
        drawScaledText(canvas, fitText(r.fatherName.ifBlank { "-" }, tdP, contentW * 0.38f), col1X + 48f, metaTop + 39f, tdP)

        drawScaledText(canvas, "Class:", col2X, metaTop + 39f, labelP)
        drawScaledText(canvas, "${r.className}-${r.section} (Roll ${r.rollNo})", col2X + 38f, metaTop + 39f, valP)

        drawScaledText(canvas, "Months:", col1X, metaTop + 50f, labelP)
        drawScaledText(canvas, fitText(r.paidMonthsSummary(), tdBoldP, contentW * 0.38f), col1X + 48f, metaTop + 50f, tdBoldP)

        drawScaledText(canvas, "Mode:", col2X, metaTop + 50f, labelP)
        drawScaledText(canvas, r.paymentMode.label, col2X + 38f, metaTop + 50f, valP)

        y = metaTop + metaH + 8f

        // Line Items Table
        val tLeft = left + 6f
        val tRight = right - 6f
        val tW = tRight - tLeft
        // Columns: S.No (22), Particular (150), Month (38), Gross (44), Conc (38), Paid (48), Bal (32)
        val cSno = 20f
        val cPart = tW - (20f + 38f + 44f + 38f + 46f + 38f)
        val cMon = 38f
        val cGross = 44f
        val cConc = 38f
        val cPaid = 46f

        val x0 = tLeft
        val x1 = x0 + cSno
        val x2 = x1 + cPart
        val x3 = x2 + cMon
        val x4 = x3 + cGross
        val x5 = x4 + cConc
        val x6 = x5 + cPaid
        val x7 = tRight

        val thH = 16f
        canvas.drawRect(x0, y, x7, y + thH, fillPaint(COLOR_SUB_BAND))
        drawScaledText(canvas, "#", x0 + 4f, y + 11f, thP)
        drawScaledText(canvas, "Particular / Fee Head", x1 + 4f, y + 11f, thP)
        drawScaledText(canvas, "Month", x2 + 4f, y + 11f, thP)
        drawScaledText(canvas, "Amount", x3 + 4f, y + 11f, thP)
        drawScaledText(canvas, "Conc.", x4 + 4f, y + 11f, thP)
        drawScaledText(canvas, "Paid", x5 + 4f, y + 11f, thP)
        drawScaledText(canvas, "Due", x6 + 4f, y + 11f, thP)
        y += thH

        val rowH = 15.5f
        val maxRows = 14
        val displayLines = r.lineItems.take(maxRows)
        displayLines.forEachIndexed { idx, item ->
            if (idx % 2 == 1) {
                canvas.drawRect(x0, y, x7, y + rowH, zebraFill)
            }
            canvas.drawRect(x0, y, x7, y + rowH, lightStroke)
            val qtySuffix = if (item.quantity > 1) " (${item.quantity}x${item.unitPrice})" else ""
            val titleStr = fitText(item.title + qtySuffix, tdP, cPart - 8f)
            val mShort = SessionMonthCode.shortLabelFor(item.monthCode)
            drawScaledText(canvas, "${idx + 1}", x0 + 4f, y + 11f, tdP)
            drawScaledText(canvas, titleStr, x1 + 4f, y + 11f, tdP)
            drawScaledText(canvas, mShort, x2 + 4f, y + 11f, tdP)
            drawScaledText(canvas, "Rs.${item.lineGrossAmount}", x3 + 4f, y + 11f, tdP)
            drawScaledText(canvas, if (item.concessionAllocated > 0) "-${item.concessionAllocated}" else "-", x4 + 4f, y + 11f, tdP)
            drawScaledText(canvas, "Rs.${item.paidAllocated}", x5 + 4f, y + 11f, tdBoldP)
            drawScaledText(canvas, if (item.balanceDueAfterLine > 0) "Rs.${item.balanceDueAfterLine}" else "0", x6 + 4f, y + 11f, tdP)
            y += rowH
        }
        if (r.lineItems.size > maxRows) {
            canvas.drawRect(x0, y, x7, y + rowH, lightStroke)
            drawScaledText(canvas, "... +${r.lineItems.size - maxRows} more item(s)", x1 + 4f, y + 11f, tdP)
            y += rowH
        }

        y += 6f

        // Totals & Concession Summary Box
        val sumTop = y
        val sumH = 74f
        canvas.drawRect(tLeft, sumTop, tRight, sumTop + sumH, tintFill)
        canvas.drawRect(tLeft, sumTop, tRight, sumTop + sumH, borderPaint)

        val leftSumX = tLeft + 8f
        val rightSumLabelX = tLeft + tW * 0.52f
        val rightSumValX = tRight - 8f
        val rightValP = textPaint(7.6f, COLOR_TEXT, bold = true, align = Paint.Align.RIGHT)
        val rightConcP = textPaint(7.6f, COLOR_CONCESSION, bold = true, align = Paint.Align.RIGHT)
        val rightBigPaidP = textPaint(9.5f, COLOR_PAID, bold = true, align = Paint.Align.RIGHT)
        val rightDueP = textPaint(7.8f, COLOR_DUE, bold = true, align = Paint.Align.RIGHT)

        // Left side of summary box: Concession remark, payment note, received by
        if (r.concessionAmount > 0) {
            val modeDesc = if (r.concessionMode == ConcessionMode.PERCENTAGE) {
                "Concession (${String.format(Locale.US, "%.1f", r.concessionInputValue)}%)"
            } else {
                "Concession (Rs. ${r.concessionAmount})"
            }
            drawScaledText(canvas, modeDesc, leftSumX, sumTop + 14f, textPaint(7.2f, COLOR_CONCESSION, bold = true))
            drawScaledText(
                canvas,
                fitText("Remark: ${r.concessionRemark.ifBlank { "-" }}", tdP, tW * 0.48f),
                leftSumX,
                sumTop + 25f,
                tdP
            )
            val givenBy = buildString {
                if (r.concessionGivenByName.isNotBlank()) append("By: ${r.concessionGivenByName}")
                if (r.concessionApprovedByAdminName.isNotBlank()) append(" (Appr: ${r.concessionApprovedByAdminName})")
            }
            if (givenBy.isNotBlank()) {
                drawScaledText(canvas, fitText(givenBy, footP, tW * 0.48f), leftSumX, sumTop + 35f, footP)
            }
        } else {
            drawScaledText(canvas, "Concession: Nil", leftSumX, sumTop + 14f, labelP)
        }

        if (r.paymentNote.isNotBlank()) {
            drawScaledText(canvas, fitText("Note: ${r.paymentNote}", tdP, tW * 0.48f), leftSumX, sumTop + 48f, tdP)
        }
        if (bundle.totalStudentRemainingDueAfterReceipt != null) {
            drawScaledText(
                canvas,
                "Overall Account Due: Rs. ${bundle.totalStudentRemainingDueAfterReceipt}",
                leftSumX,
                sumTop + 62f,
                dueBoldP
            )
        }

        // Right side of summary box: Numbers
        drawScaledText(canvas, "Total Before Concession:", rightSumLabelX, sumTop + 14f, labelP)
        drawScaledText(canvas, "Rs. ${r.totalBeforeConcession}", rightSumValX, sumTop + 14f, rightValP)

        drawScaledText(canvas, "Less Concession:", rightSumLabelX, sumTop + 27f, labelP)
        drawScaledText(canvas, "- Rs. ${r.concessionAmount}", rightSumValX, sumTop + 27f, rightConcP)

        drawScaledText(canvas, "Net Payable Amount:", rightSumLabelX, sumTop + 40f, labelP)
        drawScaledText(canvas, "Rs. ${r.netPayableAmount}", rightSumValX, sumTop + 40f, rightValP)

        drawScaledText(canvas, "AMOUNT PAID (${r.paymentMode.label}):", rightSumLabelX, sumTop + 55f, bigTotalP)
        drawScaledText(canvas, "Rs. ${r.amountPaid}", rightSumValX, sumTop + 55f, rightBigPaidP)

        drawScaledText(canvas, "Balance Due on Receipt:", rightSumLabelX, sumTop + 68f, labelP)
        drawScaledText(canvas, "Rs. ${r.balanceDueOnReceipt}", rightSumValX, sumTop + 68f, rightDueP)

        // Footer Signatures
        val sigY = (A5_H - A5_MARGIN - 12f).coerceAtLeast(sumTop + sumH + 22f)
        drawScaledText(canvas, "Received By: ${r.receivedByName.ifBlank { "Authorized Clerk" }}", tLeft + 4f, sigY - 10f, tdBoldP)
        drawScaledText(canvas, "Computer generated fee receipt • Offline-First Sync", tLeft + 4f, sigY, footP)
        val sigRightP = textPaint(7.2f, COLOR_TEXT, bold = true, align = Paint.Align.RIGHT)
        drawScaledText(canvas, "Cashier / Authorized Signatory", tRight - 4f, sigY, sigRightP)
    }

    // =========================================================================
    // 2. A5 PER-STUDENT DUES SLIP PDF (420 x 595 pt)
    // =========================================================================
    fun generateStudentDuesSlipPdf(context: Context, bundle: StudentDuesSlipPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_fee_pdfs").apply { mkdirs() }
        val safeName = sanitize(bundle.student.name, "Student")
        val outFile = File(dir, "Dues_Slip_${safeName}_${sanitize(bundle.slipDateIso, "Date")}.pdf")
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A5_W, A5_H, 1).create()
            val page = pdf.startPage(pageInfo)
            drawStudentDuesSlipPage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderStudentDuesSlipPreviewBitmap(bundle: StudentDuesSlipPdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A5_W * scale, A5_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawStudentDuesSlipPage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawStudentDuesSlipPage(canvas: Canvas, bundle: StudentDuesSlipPdfBundle) {
        val st = bundle.student
        val left = A5_MARGIN
        val right = A5_W - A5_MARGIN
        val contentW = right - left

        val borderPaint = strokePaint(COLOR_BORDER, 1f)
        val lightStroke = strokePaint(COLOR_LIGHT_BORDER, 0.7f)
        val bandFill = fillPaint(COLOR_BAND)
        val tintFill = fillPaint(COLOR_TINT)
        val zebraFill = fillPaint(COLOR_ZEBRA)

        val schoolTitleP = textPaint(11.5f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER)
        val schoolSubP = textPaint(7.2f, COLOR_MUTED, bold = false, align = Paint.Align.CENTER)
        val bandTitleP = textPaint(8.5f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
        val labelP = textPaint(7.2f, COLOR_MUTED, bold = true)
        val valP = textPaint(7.6f, COLOR_TEXT, bold = true)
        val thP = textPaint(7.2f, Color.WHITE, bold = true)
        val tdP = textPaint(7.2f, COLOR_TEXT, bold = false)
        val tdBoldP = textPaint(7.4f, COLOR_TEXT, bold = true)
        val dueBigP = textPaint(9.5f, COLOR_DUE, bold = true)
        val footP = textPaint(6.8f, COLOR_MUTED, bold = false)

        canvas.drawRect(left, A5_MARGIN, right, A5_H - A5_MARGIN, borderPaint)

        var y = A5_MARGIN + 8f
        val logoBmp = ImageUtils.decodeBase64ToBitmap(bundle.school.logoBase64)
        if (logoBmp != null) {
            val dst = RectF(left + 8f, y, left + 40f, y + 32f)
            canvas.drawBitmap(logoBmp, null, dst, null)
        }
        val centerX = (left + right) / 2f
        drawScaledText(canvas, bundle.school.name.ifBlank { "FEE DUES SLIP" }.uppercase(Locale.US), centerX, y + 12f, schoolTitleP)
        val addrLine = listOf(bundle.school.address, bundle.school.mobile.takeIf { it.isNotBlank() }?.let { "Ph: $it" }.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" | ")
        if (addrLine.isNotBlank()) {
            drawScaledText(canvas, fitText(addrLine, schoolSubP, contentW - 76f), centerX, y + 23f, schoolSubP)
        }
        y += 36f

        canvas.drawRect(left, y, right, y + 18f, bandFill)
        drawScaledText(canvas, "FEE DUES REMINDER SLIP — SESSION ${bundle.session}", centerX, y + 12.5f, bandTitleP)
        y += 22f

        // Student Info Box
        val metaTop = y
        val metaH = 44f
        canvas.drawRect(left + 6f, metaTop, right - 6f, metaTop + metaH, zebraFill)
        canvas.drawRect(left + 6f, metaTop, right - 6f, metaTop + metaH, lightStroke)

        val col1X = left + 12f
        val col2X = left + contentW * 0.56f

        drawScaledText(canvas, "Student:", col1X, metaTop + 13f, labelP)
        drawScaledText(canvas, fitText(st.name, valP, contentW * 0.38f), col1X + 46f, metaTop + 13f, valP)

        drawScaledText(canvas, "Date:", col2X, metaTop + 13f, labelP)
        drawScaledText(canvas, bundle.slipDateIso, col2X + 38f, metaTop + 13f, valP)

        drawScaledText(canvas, "Father:", col1X, metaTop + 26f, labelP)
        drawScaledText(canvas, fitText(st.fatherName.ifBlank { "-" }, tdP, contentW * 0.38f), col1X + 46f, metaTop + 26f, tdP)

        drawScaledText(canvas, "Class:", col2X, metaTop + 26f, labelP)
        drawScaledText(canvas, "${st.className}-${st.section} (Roll ${st.rollNo})", col2X + 38f, metaTop + 26f, valP)

        drawScaledText(canvas, "Adm No:", col1X, metaTop + 39f, labelP)
        drawScaledText(canvas, st.admNo.ifBlank { "-" }, col1X + 46f, metaTop + 39f, tdP)

        drawScaledText(canvas, "Mobile:", col2X, metaTop + 39f, labelP)
        drawScaledText(canvas, st.mobile.ifBlank { "-" }, col2X + 38f, metaTop + 39f, tdP)

        y = metaTop + metaH + 8f

        // Dues Table
        val tLeft = left + 6f
        val tRight = right - 6f
        val tW = tRight - tLeft
        val cSno = 26f
        val cMon = 72f
        val cHead = tW - (26f + 72f + 76f)
        val x0 = tLeft
        val x1 = x0 + cSno
        val x2 = x1 + cMon
        val x3 = x2 + cHead
        val x4 = tRight

        val thH = 16f
        canvas.drawRect(x0, y, x4, y + thH, fillPaint(COLOR_SUB_BAND))
        drawScaledText(canvas, "S.No", x0 + 4f, y + 11f, thP)
        drawScaledText(canvas, "Month", x1 + 6f, y + 11f, thP)
        drawScaledText(canvas, "Fee Head", x2 + 6f, y + 11f, thP)
        drawScaledText(canvas, "Amount Due", x3 + 6f, y + 11f, thP)
        y += thH

        val rowH = 15.5f
        val maxRows = 18
        if (bundle.dueBreakdowns.isEmpty()) {
            canvas.drawRect(x0, y, x4, y + 22f, lightStroke)
            drawScaledText(canvas, "No pending fee dues. All billed heads are paid.", x1 + 6f, y + 14f, tdBoldP)
            y += 22f
        } else {
            bundle.dueBreakdowns.take(maxRows).forEachIndexed { idx, cell ->
                if (idx % 2 == 1) {
                    canvas.drawRect(x0, y, x4, y + rowH, zebraFill)
                }
                canvas.drawRect(x0, y, x4, y + rowH, lightStroke)
                drawScaledText(canvas, "${idx + 1}", x0 + 6f, y + 11f, tdP)
                drawScaledText(canvas, SessionMonthCode.fullLabelFor(cell.monthCode), x1 + 6f, y + 11f, tdP)
                drawScaledText(canvas, cell.headType.label, x2 + 6f, y + 11f, tdP)
                drawScaledText(canvas, "Rs. ${cell.dueAmount}", x3 + 6f, y + 11f, tdBoldP)
                y += rowH
            }
            if (bundle.dueBreakdowns.size > maxRows) {
                canvas.drawRect(x0, y, x4, y + rowH, lightStroke)
                drawScaledText(canvas, "... +${bundle.dueBreakdowns.size - maxRows} more due item(s)", x2 + 6f, y + 11f, tdP)
                y += rowH
            }
        }

        y += 8f
        canvas.drawRect(tLeft, y, tRight, y + 28f, tintFill)
        canvas.drawRect(tLeft, y, tRight, y + 28f, borderPaint)
        drawScaledText(canvas, "TOTAL FEE DUE:", tLeft + 10f, y + 18f, dueBigP)
        val rightDueP = textPaint(10.5f, COLOR_DUE, bold = true, align = Paint.Align.RIGHT)
        drawScaledText(canvas, "Rs. ${bundle.totalDueAmount}", tRight - 10f, y + 18f, rightDueP)
        y += 38f

        drawScaledText(
            canvas,
            "Dear Parent/Guardian, kindly clear the above pending fee dues at the school office at the earliest.",
            tLeft + 4f,
            y,
            footP
        )

        val sigY = A5_H - A5_MARGIN - 12f
        drawScaledText(canvas, "Prepared By: ${bundle.preparedByName.ifBlank { "Fee Clerk" }}", tLeft + 4f, sigY, tdBoldP)
        val sigRightP = textPaint(7.2f, COLOR_TEXT, bold = true, align = Paint.Align.RIGHT)
        drawScaledText(canvas, "School Office Signatory", tRight - 4f, sigY, sigRightP)
    }

    // =========================================================================
    // 3. A4 DAY COLLECTION REGISTER PDF (595 x 842 pt)
    // =========================================================================
    fun generateDayCollectionPdf(context: Context, bundle: DayCollectionPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_fee_pdfs").apply { mkdirs() }
        val safeRange = sanitize("${bundle.summary.startDateIso}_to_${bundle.summary.endDateIso}", "Date")
        val outFile = File(dir, "Day_Collection_${safeRange}.pdf")
        val pages = buildDayCollectionPages(bundle)
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, pageSlice ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, index + 1).create()
                val page = pdf.startPage(pageInfo)
                drawDayCollectionPage(page.canvas, bundle, pageSlice, index + 1, pages.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderDayCollectionPreviewBitmaps(bundle: DayCollectionPdfBundle, scale: Int = 2): List<Bitmap> {
        val pages = buildDayCollectionPages(bundle)
        return pages.mapIndexed { index, slice ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawDayCollectionPage(canvas, bundle, slice, index + 1, pages.size)
            canvas.restore()
            bmp
        }
    }

    private data class DayCollectionPageSlice(
        val activeRows: List<FeeReceiptRecord>,
        val cancelledRows: List<FeeReceiptRecord>,
        val startActiveIndex: Int
    )

    private fun buildDayCollectionPages(bundle: DayCollectionPdfBundle): List<DayCollectionPageSlice> {
        val active = bundle.summary.activeReceipts
        val cancelled = bundle.summary.cancelledReceipts
        if (active.isEmpty() && cancelled.isEmpty()) {
            return listOf(DayCollectionPageSlice(emptyList(), emptyList(), 0))
        }
        val rowsPerPage = 24
        val slices = mutableListOf<DayCollectionPageSlice>()
        var offset = 0
        while (offset < active.size) {
            val chunk = active.subList(offset, (offset + rowsPerPage).coerceAtMost(active.size))
            val isLastActiveChunk = (offset + chunk.size) >= active.size
            val canFitCancelled = isLastActiveChunk && (chunk.size + cancelled.size + 3 <= rowsPerPage)
            slices.add(
                DayCollectionPageSlice(
                    activeRows = chunk,
                    cancelledRows = if (canFitCancelled) cancelled else emptyList(),
                    startActiveIndex = offset
                )
            )
            offset += chunk.size
            if (isLastActiveChunk && !canFitCancelled && cancelled.isNotEmpty()) {
                slices.add(DayCollectionPageSlice(emptyList(), cancelled, active.size))
            }
        }
        if (active.isEmpty() && cancelled.isNotEmpty()) {
            slices.add(DayCollectionPageSlice(emptyList(), cancelled, 0))
        }
        return slices
    }

    private fun drawDayCollectionPage(
        canvas: Canvas,
        bundle: DayCollectionPdfBundle,
        slice: DayCollectionPageSlice,
        pageNo: Int,
        totalPages: Int
    ) {
        val left = A4_MARGIN
        val right = A4_W - A4_MARGIN
        val contentW = right - left
        val s = bundle.summary

        var y = drawA4SchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "DAY COLLECTION REGISTER (${s.startDateIso} to ${s.endDateIso})",
            subtitle = "Session ${bundle.session} • Page $pageNo of $totalPages"
        )

        val borderPaint = strokePaint(COLOR_BORDER, 0.9f)
        val lightStroke = strokePaint(COLOR_LIGHT_BORDER, 0.7f)
        val zebraFill = fillPaint(COLOR_ZEBRA)
        val tintFill = fillPaint(COLOR_TINT)
        val thP = textPaint(7.2f, Color.WHITE, bold = true)
        val tdP = textPaint(7.1f, COLOR_TEXT, bold = false)
        val tdBoldP = textPaint(7.3f, COLOR_TEXT, bold = true)

        // Summary Bar on Page 1
        val cashAmt = s.totalByMode[PaymentMode.CASH] ?: 0
        val upiAmt = s.totalByMode[PaymentMode.UPI] ?: 0
        val chqAmt = s.totalByMode[PaymentMode.CHEQUE] ?: 0
        val othAmt = s.totalByMode[PaymentMode.OTHER] ?: 0

        canvas.drawRect(left, y, right, y + 32f, tintFill)
        canvas.drawRect(left, y, right, y + 32f, borderPaint)
        drawScaledText(
            canvas,
            "Active Receipts: ${s.activeReceipts.size}   |   Gross: Rs. ${s.totalGrossBeforeConcession}   |   Concession: Rs. ${s.totalConcessionGiven}   |   NET COLLECTED: Rs. ${s.totalNetCollected}",
            left + 8f,
            y + 13f,
            tdBoldP
        )
        drawScaledText(
            canvas,
            "By Mode — Cash: Rs. $cashAmt   |   UPI: Rs. $upiAmt   |   Cheque: Rs. $chqAmt   |   Other: Rs. $othAmt   |   Cancelled Receipts: ${s.cancelledReceipts.size}",
            left + 8f,
            y + 26f,
            tdP
        )
        y += 40f

        if (slice.activeRows.isNotEmpty() || s.activeReceipts.isEmpty()) {
            // Active Receipts Table Header
            val x0 = left
            val x1 = x0 + 24f   // #
            val x2 = x1 + 92f   // Receipt No
            val x3 = x2 + 54f   // Date
            val x4 = x3 + 116f  // Student
            val x5 = x4 + 48f   // Class
            val x6 = x5 + 72f   // Months
            val x7 = x6 + 42f   // Conc
            val x8 = x7 + 52f   // Paid
            val x9 = right      // Mode

            canvas.drawRect(x0, y, x9, y + 17f, fillPaint(COLOR_SUB_BAND))
            drawScaledText(canvas, "#", x0 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Receipt No.", x1 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Date", x2 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Student Name", x3 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Class", x4 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Months", x5 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Conc.", x6 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Paid", x7 + 4f, y + 12f, thP)
            drawScaledText(canvas, "Mode", x8 + 4f, y + 12f, thP)
            y += 17f

            if (slice.activeRows.isEmpty()) {
                canvas.drawRect(x0, y, x9, y + 20f, lightStroke)
                drawScaledText(canvas, "No active receipts issued in this date range.", x1 + 4f, y + 14f, tdP)
                y += 24f
            } else {
                val rowH = 16f
                slice.activeRows.forEachIndexed { idx, r ->
                    if (idx % 2 == 1) canvas.drawRect(x0, y, x9, y + rowH, zebraFill)
                    canvas.drawRect(x0, y, x9, y + rowH, lightStroke)
                    drawScaledText(canvas, "${slice.startActiveIndex + idx + 1}", x0 + 4f, y + 11.5f, tdP)
                    drawScaledText(canvas, fitText(r.receiptNumber, tdBoldP, 86f), x1 + 4f, y + 11.5f, tdBoldP)
                    drawScaledText(canvas, r.receiptDateIso, x2 + 4f, y + 11.5f, tdP)
                    drawScaledText(canvas, fitText(r.studentName, tdP, 110f), x3 + 4f, y + 11.5f, tdP)
                    drawScaledText(canvas, "${r.className}-${r.section}", x4 + 4f, y + 11.5f, tdP)
                    drawScaledText(canvas, fitText(r.paidMonthsSummary(), tdP, 66f), x5 + 4f, y + 11.5f, tdP)
                    drawScaledText(canvas, if (r.concessionAmount > 0) "Rs.${r.concessionAmount}" else "-", x6 + 4f, y + 11.5f, tdP)
                    drawScaledText(canvas, "Rs.${r.amountPaid}", x7 + 4f, y + 11.5f, tdBoldP)
                    drawScaledText(canvas, r.paymentMode.label, x8 + 4f, y + 11.5f, tdP)
                    y += rowH
                }
                y += 10f
            }
        }

        // Cancelled Receipts Section (Shown separately as required)
        if (slice.cancelledRows.isNotEmpty()) {
            drawScaledText(
                canvas,
                "CANCELLED RECEIPTS (${slice.cancelledRows.size}) — Excluded from Net Collection Totals",
                left,
                y + 10f,
                textPaint(8f, COLOR_DUE, bold = true)
            )
            y += 15f
            val x0 = left
            val x1 = x0 + 96f  // Receipt No
            val x2 = x1 + 54f  // Date
            val x3 = x2 + 108f // Student
            val x4 = x3 + 56f  // Orig Paid
            val x5 = right     // Reason & Cancelled By

            canvas.drawRect(x0, y, x5, y + 16f, fillPaint(COLOR_DUE))
            drawScaledText(canvas, "Receipt No.", x0 + 4f, y + 11.5f, thP)
            drawScaledText(canvas, "Date", x1 + 4f, y + 11.5f, thP)
            drawScaledText(canvas, "Student", x2 + 4f, y + 11.5f, thP)
            drawScaledText(canvas, "Amount", x3 + 4f, y + 11.5f, thP)
            drawScaledText(canvas, "Cancellation Reason & Cancelled By", x4 + 4f, y + 11.5f, thP)
            y += 16f

            val rowH = 16f
            slice.cancelledRows.take(15).forEach { r ->
                canvas.drawRect(x0, y, x5, y + rowH, fillPaint(COLOR_CANCELLED_BG))
                canvas.drawRect(x0, y, x5, y + rowH, lightStroke)
                drawScaledText(canvas, fitText(r.receiptNumber, tdBoldP, 90f), x0 + 4f, y + 11.5f, tdBoldP)
                drawScaledText(canvas, r.receiptDateIso, x1 + 4f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText("${r.studentName} (${r.className}-${r.section})", tdP, 102f), x2 + 4f, y + 11.5f, tdP)
                drawScaledText(canvas, "Rs.${r.amountPaid}", x3 + 4f, y + 11.5f, tdP)
                drawScaledText(
                    canvas,
                    fitText("${r.cancelReason} [By: ${r.cancelledByName}]", tdP, contentW - 320f),
                    x4 + 4f,
                    y + 11.5f,
                    tdP
                )
                y += rowH
            }
        }

        drawA4Footer(canvas, pageNo, totalPages)
    }

    // =========================================================================
    // 4. A4 DUES LIST REPORT PDF (595 x 842 pt)
    // =========================================================================
    fun generateDuesListPdf(context: Context, bundle: DuesListPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_fee_pdfs").apply { mkdirs() }
        val safeTitle = sanitize(bundle.filterTitle, "Dues")
        val outFile = File(dir, "Fee_Dues_List_${safeTitle}.pdf")
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(28)
        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawDuesListPage(page.canvas, bundle, chunk, idx * 28, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderDuesListPreviewBitmaps(bundle: DuesListPdfBundle, scale: Int = 2): List<Bitmap> {
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(28)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawDuesListPage(canvas, bundle, chunk, idx * 28, idx + 1, chunks.size)
            canvas.restore()
            bmp
        }
    }

    private fun drawDuesListPage(
        canvas: Canvas,
        bundle: DuesListPdfBundle,
        rows: List<StudentDuesListRow>,
        startOffset: Int,
        pageNo: Int,
        totalPages: Int
    ) {
        val left = A4_MARGIN
        val right = A4_W - A4_MARGIN
        var y = drawA4SchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "STUDENT FEE DUES LIST — ${bundle.filterTitle}",
            subtitle = "Session ${bundle.session} • As of ${bundle.asOfDateIso} • Page $pageNo of $totalPages"
        )

        val borderPaint = strokePaint(COLOR_BORDER, 0.9f)
        val lightStroke = strokePaint(COLOR_LIGHT_BORDER, 0.7f)
        val zebraFill = fillPaint(COLOR_ZEBRA)
        val tintFill = fillPaint(COLOR_TINT)
        val thP = textPaint(7.1f, Color.WHITE, bold = true)
        val tdP = textPaint(7f, COLOR_TEXT, bold = false)
        val tdBoldP = textPaint(7.2f, COLOR_TEXT, bold = true)

        val grandTotal = bundle.rows.sumOf { it.totalDueAmount }
        val totalTuition = bundle.rows.sumOf { it.dueByHead[FeeHeadType.TUITION] ?: 0 }
        val totalTransport = bundle.rows.sumOf { it.dueByHead[FeeHeadType.TRANSPORT] ?: 0 }
        val totalExam = bundle.rows.sumOf { row ->
            FeeHeadType.examHeads.sumOf { row.dueByHead[it] ?: 0 }
        }

        canvas.drawRect(left, y, right, y + 22f, tintFill)
        canvas.drawRect(left, y, right, y + 22f, borderPaint)
        drawScaledText(
            canvas,
            "Students with Dues: ${bundle.rows.size}   |   Tuition Due: Rs. $totalTuition   |   Transport Due: Rs. $totalTransport   |   Exam Due: Rs. $totalExam   |   TOTAL DUE: Rs. $grandTotal",
            left + 8f,
            y + 14.5f,
            tdBoldP
        )
        y += 28f

        val x0 = left
        val x1 = x0 + 22f   // #
        val x2 = x1 + 52f   // AdmNo
        val x3 = x2 + 108f  // Name
        val x4 = x3 + 46f   // Class
        val x5 = x4 + 66f   // Mobile
        val x6 = x5 + 88f   // Due Months
        val x7 = x6 + 44f   // Tuition
        val x8 = x7 + 44f   // Trans
        val x9 = x8 + 38f   // Exam
        val x10 = right     // Total

        canvas.drawRect(x0, y, x10, y + 17f, fillPaint(COLOR_SUB_BAND))
        drawScaledText(canvas, "#", x0 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Adm No", x1 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Student Name", x2 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Class", x3 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Mobile", x4 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Due Months", x5 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Tuition", x6 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Trans.", x7 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Exam", x8 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Total", x9 + 3f, y + 12f, thP)
        y += 17f

        if (rows.isEmpty()) {
            canvas.drawRect(x0, y, x10, y + 22f, lightStroke)
            drawScaledText(canvas, "No pending fee dues match the selected filter.", x2 + 4f, y + 15f, tdP)
        } else {
            val rowH = 16f
            rows.forEachIndexed { idx, row ->
                if (idx % 2 == 1) canvas.drawRect(x0, y, x10, y + rowH, zebraFill)
                canvas.drawRect(x0, y, x10, y + rowH, lightStroke)
                val tuit = row.dueByHead[FeeHeadType.TUITION] ?: 0
                val tran = row.dueByHead[FeeHeadType.TRANSPORT] ?: 0
                val exam = FeeHeadType.examHeads.sumOf { row.dueByHead[it] ?: 0 }
                val monthsStr = row.dueMonths.joinToString(",") { SessionMonthCode.shortLabelFor(it) }
                drawScaledText(canvas, "${startOffset + idx + 1}", x0 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText(row.student.admNo, tdP, 48f), x1 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText(row.student.name, tdBoldP, 102f), x2 + 3f, y + 11.5f, tdBoldP)
                drawScaledText(canvas, "${row.student.className}-${row.student.section}", x3 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText(row.student.mobile, tdP, 62f), x4 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText(monthsStr, tdP, 82f), x5 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, "$tuit", x6 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, "$tran", x7 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, "$exam", x8 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, "Rs.${row.totalDueAmount}", x9 + 3f, y + 11.5f, tdBoldP)
                y += rowH
            }
        }
        drawA4Footer(canvas, pageNo, totalPages)
    }

    // =========================================================================
    // 5. A4 CONCESSION REPORT PDF (595 x 842 pt)
    // =========================================================================
    fun generateConcessionReportPdf(context: Context, bundle: ConcessionReportPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_fee_pdfs").apply { mkdirs() }
        val outFile = File(dir, "Concession_Report_${sanitize(bundle.session, "Session")}.pdf")
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(26)
        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawConcessionReportPage(page.canvas, bundle, chunk, idx * 26, idx + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderConcessionReportPreviewBitmaps(bundle: ConcessionReportPdfBundle, scale: Int = 2): List<Bitmap> {
        val chunks = if (bundle.rows.isEmpty()) listOf(emptyList()) else bundle.rows.chunked(26)
        return chunks.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawConcessionReportPage(canvas, bundle, chunk, idx * 26, idx + 1, chunks.size)
            canvas.restore()
            bmp
        }
    }

    private fun drawConcessionReportPage(
        canvas: Canvas,
        bundle: ConcessionReportPdfBundle,
        rows: List<ConcessionReportRow>,
        startOffset: Int,
        pageNo: Int,
        totalPages: Int
    ) {
        val left = A4_MARGIN
        val right = A4_W - A4_MARGIN
        var y = drawA4SchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "FEE CONCESSION REPORT — ${bundle.filterTitle}",
            subtitle = "Session ${bundle.session} • Page $pageNo of $totalPages"
        )

        val borderPaint = strokePaint(COLOR_BORDER, 0.9f)
        val lightStroke = strokePaint(COLOR_LIGHT_BORDER, 0.7f)
        val zebraFill = fillPaint(COLOR_ZEBRA)
        val tintFill = fillPaint(COLOR_TINT)
        val thP = textPaint(7.1f, Color.WHITE, bold = true)
        val tdP = textPaint(7f, COLOR_TEXT, bold = false)
        val tdBoldP = textPaint(7.2f, COLOR_TEXT, bold = true)

        val totalConc = bundle.rows.sumOf { it.concessionAmount }
        canvas.drawRect(left, y, right, y + 22f, tintFill)
        canvas.drawRect(left, y, right, y + 22f, borderPaint)
        drawScaledText(
            canvas,
            "Total Concession Receipts: ${bundle.rows.size}   |   TOTAL CONCESSION GRANTED: Rs. $totalConc",
            left + 8f,
            y + 14.5f,
            tdBoldP
        )
        y += 28f

        val x0 = left
        val x1 = x0 + 22f   // #
        val x2 = x1 + 88f   // Receipt No
        val x3 = x2 + 54f   // Date
        val x4 = x3 + 104f  // Student
        val x5 = x4 + 44f   // Class
        val x6 = x5 + 52f   // Conc Amt
        val x7 = x6 + 105f  // Remark
        val x8 = right      // Given / Approved By

        canvas.drawRect(x0, y, x8, y + 17f, fillPaint(COLOR_SUB_BAND))
        drawScaledText(canvas, "#", x0 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Receipt No.", x1 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Date", x2 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Student Name", x3 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Class", x4 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Concession", x5 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Remark", x6 + 3f, y + 12f, thP)
        drawScaledText(canvas, "Given / Approved By", x7 + 3f, y + 12f, thP)
        y += 17f

        if (rows.isEmpty()) {
            canvas.drawRect(x0, y, x8, y + 22f, lightStroke)
            drawScaledText(canvas, "No concessions granted in this period.", x1 + 4f, y + 15f, tdP)
        } else {
            val rowH = 16.5f
            rows.forEachIndexed { idx, row ->
                if (idx % 2 == 1) canvas.drawRect(x0, y, x8, y + rowH, zebraFill)
                canvas.drawRect(x0, y, x8, y + rowH, lightStroke)
                val givenStr = buildString {
                    append(row.givenByName.ifBlank { "-" })
                    if (row.approvedByAdminName.isNotBlank()) append(" (${row.approvedByAdminName})")
                }
                drawScaledText(canvas, "${startOffset + idx + 1}", x0 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText(row.receiptNumber, tdP, 82f), x1 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, row.receiptDateIso, x2 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText(row.studentName, tdBoldP, 98f), x3 + 3f, y + 11.5f, tdBoldP)
                drawScaledText(canvas, "${row.className}-${row.section}", x4 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, "Rs.${row.concessionAmount}", x5 + 3f, y + 11.5f, tdBoldP)
                drawScaledText(canvas, fitText(row.remark, tdP, 99f), x6 + 3f, y + 11.5f, tdP)
                drawScaledText(canvas, fitText(givenStr, tdP, x8 - x7 - 6f), x7 + 3f, y + 11.5f, tdP)
                y += rowH
            }
        }
        drawA4Footer(canvas, pageNo, totalPages)
    }

    // =========================================================================
    // 6. A4 OPTIONAL-ITEM SALES REPORT PDF (595 x 842 pt)
    // =========================================================================
    fun generateOptionalSalesPdf(context: Context, bundle: OptionalSalesPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_fee_pdfs").apply { mkdirs() }
        val outFile = File(dir, "Optional_Item_Sales_${sanitize(bundle.session, "Session")}.pdf")
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create()
            val page = pdf.startPage(pageInfo)
            drawOptionalSalesPage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderOptionalSalesPreviewBitmap(bundle: OptionalSalesPdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawOptionalSalesPage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawOptionalSalesPage(canvas: Canvas, bundle: OptionalSalesPdfBundle) {
        val left = A4_MARGIN
        val right = A4_W - A4_MARGIN
        var y = drawA4SchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "OPTIONAL ITEMS & OTHER CHARGES SALES REPORT",
            subtitle = "Session ${bundle.session} • ${bundle.filterTitle}"
        )

        val borderPaint = strokePaint(COLOR_BORDER, 0.9f)
        val lightStroke = strokePaint(COLOR_LIGHT_BORDER, 0.7f)
        val zebraFill = fillPaint(COLOR_ZEBRA)
        val tintFill = fillPaint(COLOR_TINT)
        val thP = textPaint(7.4f, Color.WHITE, bold = true)
        val tdP = textPaint(7.4f, COLOR_TEXT, bold = false)
        val tdBoldP = textPaint(7.6f, COLOR_TEXT, bold = true)

        val totalQty = bundle.rows.sumOf { it.totalQuantitySold }
        val totalGross = bundle.rows.sumOf { it.totalGrossAmount }
        val totalNet = bundle.rows.sumOf { it.totalNetCollected }

        canvas.drawRect(left, y, right, y + 22f, tintFill)
        canvas.drawRect(left, y, right, y + 22f, borderPaint)
        drawScaledText(
            canvas,
            "Items Sold: ${bundle.rows.size}   |   Total Quantity: $totalQty   |   Gross Sales: Rs. $totalGross   |   Net Collected: Rs. $totalNet",
            left + 8f,
            y + 14.5f,
            tdBoldP
        )
        y += 28f

        val x0 = left
        val x1 = x0 + 34f
        val x2 = x1 + 220f
        val x3 = x2 + 80f
        val x4 = x3 + 80f
        val x5 = x4 + 80f
        val x6 = right

        canvas.drawRect(x0, y, x6, y + 18f, fillPaint(COLOR_SUB_BAND))
        drawScaledText(canvas, "S.No", x0 + 4f, y + 12.5f, thP)
        drawScaledText(canvas, "Optional Item / Charge Name", x1 + 6f, y + 12.5f, thP)
        drawScaledText(canvas, "Qty Sold", x2 + 6f, y + 12.5f, thP)
        drawScaledText(canvas, "Receipts", x3 + 6f, y + 12.5f, thP)
        drawScaledText(canvas, "Gross Value", x4 + 6f, y + 12.5f, thP)
        drawScaledText(canvas, "Net Collected", x5 + 6f, y + 12.5f, thP)
        y += 18f

        if (bundle.rows.isEmpty()) {
            canvas.drawRect(x0, y, x6, y + 22f, lightStroke)
            drawScaledText(canvas, "No optional items or custom charges sold in this period.", x1 + 6f, y + 15f, tdP)
        } else {
            val rowH = 18f
            bundle.rows.take(32).forEachIndexed { idx, row ->
                if (idx % 2 == 1) canvas.drawRect(x0, y, x6, y + rowH, zebraFill)
                canvas.drawRect(x0, y, x6, y + rowH, lightStroke)
                drawScaledText(canvas, "${idx + 1}", x0 + 6f, y + 12.5f, tdP)
                drawScaledText(canvas, fitText(row.itemName, tdBoldP, 210f), x1 + 6f, y + 12.5f, tdBoldP)
                drawScaledText(canvas, "${row.totalQuantitySold}", x2 + 6f, y + 12.5f, tdP)
                drawScaledText(canvas, "${row.receiptCount}", x3 + 6f, y + 12.5f, tdP)
                drawScaledText(canvas, "Rs. ${row.totalGrossAmount}", x4 + 6f, y + 12.5f, tdP)
                drawScaledText(canvas, "Rs. ${row.totalNetCollected}", x5 + 6f, y + 12.5f, tdBoldP)
                y += rowH
            }
        }
        drawA4Footer(canvas, 1, 1)
    }

    // =========================================================================
    // 7. A4 STUDENT ADMISSION FORM PDF (595 x 842 pt)
    // =========================================================================
    fun generateAdmissionFormPdf(context: Context, bundle: AdmissionFormPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_register_pdfs").apply { mkdirs() }
        val safeName = sanitize(bundle.student.name, "Student")
        val outFile = File(dir, "Admission_Form_${safeName}.pdf")
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create()
            val page = pdf.startPage(pageInfo)
            drawAdmissionFormPage(page.canvas, bundle)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderAdmissionFormPreviewBitmap(bundle: AdmissionFormPdfBundle, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat())
        drawAdmissionFormPage(canvas, bundle)
        canvas.restore()
        return bmp
    }

    private fun drawAdmissionFormPage(canvas: Canvas, bundle: AdmissionFormPdfBundle) {
        val st = bundle.student
        val left = A4_MARGIN
        val right = A4_W - A4_MARGIN
        val contentW = right - left

        val borderPaint = strokePaint(COLOR_BORDER, 1f)
        val lightStroke = strokePaint(COLOR_LIGHT_BORDER, 0.8f)
        val tintFill = fillPaint(COLOR_TINT)
        val zebraFill = fillPaint(COLOR_ZEBRA)

        canvas.drawRect(left, A4_MARGIN, right, A4_H - A4_MARGIN, borderPaint)

        var y = drawA4SchoolHeader(
            canvas = canvas,
            school = bundle.school,
            title = "STUDENT ADMISSION & REGISTER RECORD",
            subtitle = "Academic Session: ${st.session.ifBlank { bundle.session }}"
        )

        val secTitleP = textPaint(8.5f, Color.WHITE, bold = true)
        val labelP = textPaint(8f, COLOR_MUTED, bold = true)
        val valP = textPaint(8.5f, COLOR_TEXT, bold = true)
        val normP = textPaint(8f, COLOR_TEXT, bold = false)

        // Photo Box on top-right
        val photoBoxW = 86f
        val photoBoxH = 104f
        val photoLeft = right - 14f - photoBoxW
        val photoTop = y + 4f
        val photoRect = RectF(photoLeft, photoTop, photoLeft + photoBoxW, photoTop + photoBoxH)
        canvas.drawRect(photoRect, zebraFill)
        canvas.drawRect(photoRect, borderPaint)
        val studentPhotoBmp = ImageUtils.decodeBase64ToBitmap(st.photoBase64)
        if (studentPhotoBmp != null) {
            val inset = RectF(photoLeft + 2f, photoTop + 2f, photoLeft + photoBoxW - 2f, photoTop + photoBoxH - 2f)
            canvas.drawBitmap(studentPhotoBmp, null, inset, null)
        } else {
            val phP = textPaint(7.2f, COLOR_MUTED, bold = true, align = Paint.Align.CENTER)
            drawScaledText(canvas, "STUDENT", photoRect.centerX(), photoRect.centerY() - 4f, phP)
            drawScaledText(canvas, "PHOTO", photoRect.centerX(), photoRect.centerY() + 8f, phP)
        }

        // Top Enrollment Fields (left of photo box)
        val infoW = photoLeft - left - 20f
        fun drawFieldRow(yPos: Float, label1: String, val1: String, label2: String? = null, val2: String? = null) {
            canvas.drawRect(left + 12f, yPos, left + 12f + infoW, yPos + 22f, lightStroke)
            drawScaledText(canvas, label1, left + 18f, yPos + 14.5f, labelP)
            drawScaledText(canvas, fitText(val1.ifBlank { "-" }, valP, if (label2 == null) infoW - 110f else infoW * 0.34f), left + 115f, yPos + 14.5f, valP)
            if (label2 != null && val2 != null) {
                val midX = left + 12f + infoW * 0.54f
                drawScaledText(canvas, label2, midX, yPos + 14.5f, labelP)
                drawScaledText(canvas, fitText(val2.ifBlank { "-" }, valP, infoW * 0.25f), midX + 82f, yPos + 14.5f, valP)
            }
        }

        drawFieldRow(y + 4f, "Admission No:", st.admNo, "Adm. Date:", st.admissionDate.ifBlank { "-" })
        drawFieldRow(y + 28f, "Student Name:", st.name)
        drawFieldRow(y + 52f, "Class & Section:", "${st.className} - ${st.section}", "Roll No:", st.rollNo.toString())
        drawFieldRow(y + 76f, "Date of Birth:", st.dob.ifBlank { "-" }, "Status:", st.status.ifBlank { "ACTIVE" })
        y += 116f

        // Section 2: Family & Contact Details
        canvas.drawRect(left + 12f, y, right - 12f, y + 18f, fillPaint(COLOR_SUB_BAND))
        drawScaledText(canvas, "PARENT / GUARDIAN & CONTACT INFORMATION", left + 18f, y + 12.5f, secTitleP)
        y += 22f

        val fullRowW = contentW - 24f
        fun drawFullRow(label1: String, val1: String, label2: String? = null, val2: String? = null) {
            canvas.drawRect(left + 12f, y, right - 12f, y + 22f, lightStroke)
            drawScaledText(canvas, label1, left + 18f, y + 14.5f, labelP)
            drawScaledText(
                canvas,
                fitText(val1.ifBlank { "-" }, valP, if (label2 == null) fullRowW - 130f else fullRowW * 0.35f),
                left + 135f,
                y + 14.5f,
                valP
            )
            if (label2 != null && val2 != null) {
                val midX = left + 12f + fullRowW * 0.54f
                drawScaledText(canvas, label2, midX, y + 14.5f, labelP)
                drawScaledText(canvas, fitText(val2.ifBlank { "-" }, valP, fullRowW * 0.26f), midX + 95f, y + 14.5f, valP)
            }
            y += 24f
        }

        drawFullRow("Father's Name:", st.fatherName)
        drawFullRow("Mother's Name:", st.motherName)
        drawFullRow("Guardian Mobile 1:", st.mobile, "Second Mobile:", st.secondMobile.ifBlank { "-" })
        drawFullRow("Residential Address:", st.address)
        drawFullRow("Previous School:", st.previousSchool.ifBlank { "-" })
        drawFullRow(
            "Sibling Link / Family:",
            if (bundle.siblingNamesSummary.isNotBlank()) "${st.siblingGroupId} (${bundle.siblingNamesSummary})"
            else st.siblingGroupId.ifBlank { "None linked" }
        )

        y += 6f
        // Section 3: Transport & Status Details
        canvas.drawRect(left + 12f, y, right - 12f, y + 18f, fillPaint(COLOR_SUB_BAND))
        drawScaledText(canvas, "TRANSPORT & ENROLLMENT STATUS DETAILS", left + 18f, y + 12.5f, secTitleP)
        y += 22f

        drawFullRow(
            "Uses School Transport:",
            if (st.usesTransport) "YES" else "NO",
            "Monthly Transport:",
            if (st.usesTransport) "Rs. ${st.transportMonthlyAmount}" else "N/A"
        )
        drawFullRow("Transport Route Name:", if (st.usesTransport) st.transportRouteName.ifBlank { "-" } else "Not Applicable")
        if (!st.isActiveStatus) {
            drawFullRow("Left School Date:", st.leftDate.ifBlank { "-" }, "Reason for Leaving:", st.leftReason.ifBlank { "-" })
        }

        y += 14f
        // Declaration Box
        canvas.drawRect(left + 12f, y, right - 12f, y + 54f, tintFill)
        canvas.drawRect(left + 12f, y, right - 12f, y + 54f, lightStroke)
        drawScaledText(canvas, "DECLARATION BY PARENT / GUARDIAN", left + 18f, y + 14f, labelP)
        drawScaledText(
            canvas,
            "I hereby declare that the particulars furnished above including the student's date of birth are true and correct,",
            left + 18f,
            y + 28f,
            normP
        )
        drawScaledText(
            canvas,
            "and I agree to abide by the rules, fee schedule, and discipline guidelines of the school.",
            left + 18f,
            y + 41f,
            normP
        )

        val sigY = A4_H - A4_MARGIN - 36f
        canvas.drawLine(left + 24f, sigY, left + 160f, sigY, borderPaint)
        drawScaledText(canvas, "Parent / Guardian Signature", left + 32f, sigY + 13f, labelP)

        canvas.drawLine(left + 225f, sigY, left + 355f, sigY, borderPaint)
        drawScaledText(canvas, "Admission Clerk", left + 255f, sigY + 13f, labelP)

        canvas.drawLine(right - 165f, sigY, right - 24f, sigY, borderPaint)
        drawScaledText(canvas, "Principal Signature & Seal", right - 152f, sigY + 13f, labelP)
    }

    // =========================================================================
    // 8. A4 MULTI-CARD STUDENT ID CARDS PDF (595 x 842 pt, 8 cards per A4 page)
    // =========================================================================
    fun generateStudentIdCardsPdf(context: Context, bundle: StudentIdCardsPdfBundle): File {
        val dir = File(context.cacheDir, "phase9_register_pdfs").apply { mkdirs() }
        val safeSchool = sanitize(bundle.school.name, "School")
        val outFile = File(dir, "Student_ID_Cards_${safeSchool}.pdf")
        val pages = if (bundle.students.isEmpty()) listOf(emptyList()) else bundle.students.chunked(8)
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                drawStudentIdCardsPage(page.canvas, bundle, chunk, idx + 1, pages.size)
                pdf.finishPage(page)
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return outFile
    }

    fun renderStudentIdCardsPreviewBitmaps(bundle: StudentIdCardsPdfBundle, scale: Int = 2): List<Bitmap> {
        val pages = if (bundle.students.isEmpty()) listOf(emptyList()) else bundle.students.chunked(8)
        return pages.mapIndexed { idx, chunk ->
            val bmp = Bitmap.createBitmap(A4_W * scale, A4_H * scale, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale.toFloat(), scale.toFloat())
            drawStudentIdCardsPage(canvas, bundle, chunk, idx + 1, pages.size)
            canvas.restore()
            bmp
        }
    }

    private fun drawStudentIdCardsPage(
        canvas: Canvas,
        bundle: StudentIdCardsPdfBundle,
        students: List<StudentEntity>,
        pageNo: Int,
        totalPages: Int
    ) {
        val marginX = 22f
        val marginY = 22f
        val gapX = 15f
        val gapY = 12f
        val cardW = (A4_W - 2 * marginX - gapX) / 2f      // ~268 pt
        val cardH = (A4_H - 2 * marginY - 22f - 3 * gapY) / 4f // ~185 pt

        val logoBmp = ImageUtils.decodeBase64ToBitmap(bundle.school.logoBase64)

        if (students.isEmpty()) {
            val p = textPaint(10f, COLOR_MUTED, bold = true, align = Paint.Align.CENTER)
            drawScaledText(canvas, "No students selected for ID Cards.", A4_W / 2f, A4_H / 2f, p)
            return
        }

        students.forEachIndexed { index, st ->
            val col = index % 2
            val row = index / 2
            val cLeft = marginX + col * (cardW + gapX)
            val cTop = marginY + row * (cardH + gapY)
            drawSingleStudentIdCard(
                canvas = canvas,
                school = bundle.school,
                session = st.session.ifBlank { bundle.session },
                student = st,
                logoBmp = logoBmp,
                left = cLeft,
                top = cTop,
                width = cardW,
                height = cardH
            )
        }

        drawA4Footer(canvas, pageNo, totalPages)
    }

    private fun drawSingleStudentIdCard(
        canvas: Canvas,
        school: SchoolEntity,
        session: String,
        student: StudentEntity,
        logoBmp: Bitmap?,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val right = left + width
        val bottom = top + height
        val borderPaint = strokePaint(COLOR_BAND, 1.2f)
        val bandFill = fillPaint(COLOR_BAND)
        val tintFill = fillPaint(COLOR_TINT)

        // Card outer border
        val cardRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(cardRect, 6f, 6f, fillPaint(Color.WHITE))
        canvas.drawRoundRect(cardRect, 6f, 6f, borderPaint)

        // Header Band (36 pt)
        val headerH = 36f
        canvas.drawRect(left, top, right, top + headerH, bandFill)
        if (logoBmp != null) {
            val dst = RectF(left + 6f, top + 4f, left + 34f, top + 32f)
            canvas.drawBitmap(logoBmp, null, dst, null)
        }
        val titleX = if (logoBmp != null) left + 38f else left + 8f
        val availTitleW = width - (titleX - left) - 6f
        val schTitleP = textPaint(8.5f, Color.WHITE, bold = true)
        val schSubP = textPaint(6.2f, Color.parseColor("#E2E8F0"), bold = false)
        drawScaledText(canvas, fitText(school.name.ifBlank { "SCHOOL ID CARD" }.uppercase(Locale.US), schTitleP, availTitleW), titleX, top + 14f, schTitleP)
        val subLine = listOf(school.address, school.mobile.takeIf { it.isNotBlank() }?.let { "Ph: $it" }.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" | ")
        if (subLine.isNotBlank()) {
            drawScaledText(canvas, fitText(subLine, schSubP, availTitleW), titleX, top + 24f, schSubP)
        }
        drawScaledText(
            canvas,
            "IDENTITY CARD • SESSION $session",
            titleX,
            top + 33f,
            textPaint(6.2f, Color.parseColor("#FDE047"), bold = true)
        )

        // Photo Box on left of body
        val bodyTop = top + headerH + 8f
        val photoW = 58f
        val photoH = 72f
        val photoRect = RectF(left + 8f, bodyTop, left + 8f + photoW, bodyTop + photoH)
        canvas.drawRect(photoRect, tintFill)
        canvas.drawRect(photoRect, strokePaint(COLOR_BORDER, 0.8f))

        val stPhoto = ImageUtils.decodeBase64ToBitmap(student.photoBase64)
        if (stPhoto != null) {
            val inset = RectF(photoRect.left + 1.5f, photoRect.top + 1.5f, photoRect.right - 1.5f, photoRect.bottom - 1.5f)
            canvas.drawBitmap(stPhoto, null, inset, null)
        } else {
            val initials = student.name.trim().split(Regex("\\s+"))
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
                .ifBlank { "ID" }
            val initP = textPaint(13f, COLOR_BAND, bold = true, align = Paint.Align.CENTER)
            drawScaledText(canvas, initials, photoRect.centerX(), photoRect.centerY() + 5f, initP)
        }

        // Adm No badge below photo
        val admBadgeTop = photoRect.bottom + 4f
        canvas.drawRect(photoRect.left, admBadgeTop, photoRect.right, admBadgeTop + 14f, tintFill)
        val admBadgeP = textPaint(6f, COLOR_BAND, bold = true, align = Paint.Align.CENTER)
        drawScaledText(canvas, fitText(student.admNo, admBadgeP, photoW - 4f), photoRect.centerX(), admBadgeTop + 10f, admBadgeP)

        // Details on right of photo
        val dLeft = photoRect.right + 8f
        val dWidth = right - dLeft - 6f
        val nameP = textPaint(8.6f, COLOR_BAND, bold = true)
        val lblP = textPaint(6.6f, COLOR_MUTED, bold = true)
        val valP = textPaint(6.8f, COLOR_TEXT, bold = true)

        var dy = bodyTop + 10f
        drawScaledText(canvas, fitText(student.name.uppercase(Locale.US), nameP, dWidth), dLeft, dy, nameP)
        dy += 13f

        fun drawIdLine(label: String, value: String) {
            drawScaledText(canvas, label, dLeft, dy, lblP)
            drawScaledText(canvas, fitText(value.ifBlank { "-" }, valP, dWidth - 46f), dLeft + 46f, dy, valP)
            dy += 11.5f
        }

        drawIdLine("Class:", "${student.className} - ${student.section}  (Roll ${student.rollNo})")
        drawIdLine("Father:", student.fatherName)
        drawIdLine("DOB:", student.dob)
        drawIdLine("Mobile:", listOf(student.mobile, student.secondMobile).filter { it.isNotBlank() }.joinToString(" / "))
        drawIdLine("Address:", student.address)
        if (student.usesTransport) {
            drawIdLine("Bus Rte:", student.transportRouteName.ifBlank { "School Bus" })
        }

        // Bottom bar with Principal Signatory
        val footTop = bottom - 16f
        canvas.drawLine(left + 6f, footTop, right - 6f, footTop, strokePaint(COLOR_LIGHT_BORDER, 0.6f))
        val footLeftP = textPaint(6f, COLOR_MUTED, bold = false)
        val footRightP = textPaint(6.2f, COLOR_BAND, bold = true, align = Paint.Align.RIGHT)
        drawScaledText(canvas, if (student.usesTransport) "TRANSPORT PASS" else "REGULAR STUDENT", left + 8f, bottom - 5f, footLeftP)
        drawScaledText(canvas, "Principal Signatory", right - 8f, bottom - 5f, footRightP)
    }

    // =========================================================================
    // SHARED DRAWING & TEXT HELPERS
    // =========================================================================
    private fun drawA4SchoolHeader(
        canvas: Canvas,
        school: SchoolEntity,
        title: String,
        subtitle: String
    ): Float {
        val left = A4_MARGIN
        val right = A4_W - A4_MARGIN
        val contentW = right - left
        val centerX = (left + right) / 2f

        var y = A4_MARGIN
        val logoBmp = ImageUtils.decodeBase64ToBitmap(school.logoBase64)
        if (logoBmp != null) {
            val dst = RectF(left + 4f, y, left + 40f, y + 36f)
            canvas.drawBitmap(logoBmp, null, dst, null)
        }

        val schoolP = textPaint(13f, COLOR_TEXT, bold = true, align = Paint.Align.CENTER)
        val addrP = textPaint(7.8f, COLOR_MUTED, bold = false, align = Paint.Align.CENTER)
        drawScaledText(canvas, school.name.ifBlank { "SCHOOL REPORT" }.uppercase(Locale.US), centerX, y + 14f, schoolP)
        val addrLine = listOf(school.address, school.mobile.takeIf { it.isNotBlank() }?.let { "Ph: $it" }.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" | ")
        if (addrLine.isNotBlank()) {
            drawScaledText(canvas, fitText(addrLine, addrP, contentW - 84f), centerX, y + 26f, addrP)
        }
        y += 40f

        canvas.drawRect(left, y, right, y + 20f, fillPaint(COLOR_BAND))
        val bandP = textPaint(8.8f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
        drawScaledText(canvas, fitText(title, bandP, contentW - 16f), centerX, y + 13.5f, bandP)
        y += 24f

        val subP = textPaint(7.6f, COLOR_MUTED, bold = true)
        drawScaledText(canvas, subtitle, left + 2f, y + 8f, subP)
        return y + 14f
    }

    private fun drawA4Footer(canvas: Canvas, pageNo: Int, totalPages: Int) {
        val y = A4_H - 12f
        val p = textPaint(6.8f, COLOR_MUTED, bold = false)
        drawScaledText(canvas, "Generated offline • School Fee & Register Module", A4_MARGIN, y, p)
        val rp = textPaint(6.8f, COLOR_MUTED, bold = true, align = Paint.Align.RIGHT)
        drawScaledText(canvas, "Page $pageNo of $totalPages", A4_W - A4_MARGIN, y, rp)
    }

    private fun textPaint(
        sizePt: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = sizePt * TEXT_SCALE
        this.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        this.textAlign = align
        this.isSubpixelText = true
        this.letterSpacing = LETTER_SPACING_EM
    }

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.FILL
        this.color = color
    }

    private fun strokePaint(color: Int, widthPt: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        this.color = color
        this.strokeWidth = widthPt
    }

    private fun measureTextPt(paint: Paint, text: String): Float =
        paint.measureText(text) / TEXT_SCALE

    private fun fitText(text: String, paint: Paint, maxWidthPt: Float): String {
        val clean = text.trim()
        if (clean.isEmpty() || maxWidthPt <= 6f) return ""
        if (measureTextPt(paint, clean) <= maxWidthPt) return clean
        val ellipsis = "..."
        var low = 0
        var high = clean.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            val candidate = clean.substring(0, mid).trimEnd() + ellipsis
            if (measureTextPt(paint, candidate) <= maxWidthPt) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return if (low <= 0) ellipsis else clean.substring(0, low).trimEnd() + ellipsis
    }

    private fun drawScaledText(canvas: Canvas, text: String, xPt: Float, yPt: Float, paint: Paint) {
        if (text.isEmpty()) return
        canvas.save()
        canvas.translate(xPt, yPt)
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(text, 0f, 0f, paint)
        canvas.restore()
    }

    private fun sanitize(raw: String, fallback: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { fallback }
}
