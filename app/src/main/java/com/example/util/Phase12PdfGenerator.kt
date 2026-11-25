package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.example.data.SchoolEntity
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryPaymentStatus
import com.example.domain.SalaryRuleConfig
import com.example.domain.SalarySlipPageSize
import java.io.File
import java.io.FileOutputStream

data class SalarySlipPdfBundle(
    val school: SchoolEntity,
    val record: SalaryMonthlyRecord,
    val ruleConfig: SalaryRuleConfig,
    val remainingAdvanceBalanceRs: Int = 0,
    val pageSize: SalarySlipPageSize = SalarySlipPageSize.A4,
    val schoolLogoBase64: String = ""
)

data class SalaryMonthSummaryPdfBundle(
    val school: SchoolEntity,
    val yearMonth: String,
    val records: List<SalaryMonthlyRecord>,
    val ruleConfig: SalaryRuleConfig,
    val isLocked: Boolean = false,
    val schoolLogoBase64: String = ""
)

/**
 * Phase 12 Offline PDF Generator:
 * 1. A4 (`595 x 842`) or A5 (`595 x 420`) Staff Salary Slip PDF with school name, logo, month,
 *    attendance days, earnings, deductions, advances, manual adjustment, and net salary.
 * 2. A4 (`595 x 842`) Monthly Salary Summary Sheet PDF.
 */
object Phase12PdfGenerator {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val A5_WIDTH = 595
    private const val A5_HEIGHT = 420

    private val COLOR_NAVY = Color.rgb(22, 49, 92)
    private val COLOR_SLATE = Color.rgb(75, 85, 99)
    private val COLOR_BG_TINT = Color.rgb(241, 245, 249)
    private val COLOR_GREEN_BG = Color.rgb(220, 252, 231)
    private val COLOR_GREEN_TEXT = Color.rgb(21, 128, 61)
    private val COLOR_AMBER_BG = Color.rgb(254, 243, 199)
    private val COLOR_AMBER_TEXT = Color.rgb(180, 83, 9)
    private val COLOR_BORDER = Color.rgb(203, 213, 225)

    fun generateSalarySlipPdf(context: Context, bundle: SalarySlipPdfBundle): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeStaff = bundle.record.staffName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Staff" }
        val file = File(
            dir,
            "Salary_Slip_${bundle.pageSize.wireValue}_${bundle.record.yearMonth}_$safeStaff.pdf"
        )
        val width = if (bundle.pageSize == SalarySlipPageSize.A5) A5_WIDTH else A4_WIDTH
        val height = if (bundle.pageSize == SalarySlipPageSize.A5) A5_HEIGHT else A4_HEIGHT

        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdf.startPage(pageInfo)
            drawSalarySlipCanvas(page.canvas, bundle, width.toFloat(), height.toFloat())
            pdf.finishPage(page)
            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return file
    }

    fun renderSalarySlipPreviewBitmap(bundle: SalarySlipPdfBundle, scale: Float = 2f): Bitmap {
        val width = if (bundle.pageSize == SalarySlipPageSize.A5) A5_WIDTH else A4_WIDTH
        val height = if (bundle.pageSize == SalarySlipPageSize.A5) A5_HEIGHT else A4_HEIGHT
        val bmp = Bitmap.createBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale, scale)
        drawSalarySlipCanvas(canvas, bundle, width.toFloat(), height.toFloat())
        canvas.restore()
        return bmp
    }

    private fun drawSalarySlipCanvas(
        canvas: Canvas,
        bundle: SalarySlipPdfBundle,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val isA5 = bundle.pageSize == SalarySlipPageSize.A5
        val margin = if (isA5) 20f else 28f
        val rec = bundle.record

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = COLOR_NAVY
        }
        val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = COLOR_BG_TINT
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = COLOR_BORDER
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isA5) 14f else 17f
            color = COLOR_NAVY
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = if (isA5) 8.5f else 10f
            color = COLOR_SLATE
        }
        val sectionBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isA5) 9.5f else 11f
            color = COLOR_NAVY
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = if (isA5) 8.5f else 10f
            color = Color.BLACK
        }
        val bodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isA5) 9f else 10.5f
            color = Color.BLACK
        }

        // Outer border
        canvas.drawRoundRect(
            RectF(margin, margin, pageWidth - margin, pageHeight - margin),
            8f,
            8f,
            borderPaint
        )

        val contentLeft = margin + 14f
        val contentRight = pageWidth - margin - 14f
        var y = margin + (if (isA5) 14f else 20f)

        // Header with school logo & name
        val logoSize = if (isA5) 34f else 44f
        drawSchoolLogoOrEmblem(
            canvas = canvas,
            logoBase64 = bundle.schoolLogoBase64,
            schoolName = bundle.school.name,
            left = contentLeft,
            top = y,
            size = logoSize
        )

        val headerTextLeft = contentLeft + logoSize + 10f
        canvas.drawText(
            bundle.school.name.ifBlank { "School Name" }.take(52),
            headerTextLeft,
            y + (if (isA5) 13f else 16f),
            titlePaint
        )
        val addrLine = listOfNotNull(
            bundle.school.address.takeIf { it.isNotBlank() },
            bundle.school.mobile.takeIf { it.isNotBlank() }?.let { "Tel: $it" }
        ).joinToString(" • ")
        if (addrLine.isNotBlank()) {
            canvas.drawText(
                addrLine.take(78),
                headerTextLeft,
                y + (if (isA5) 25f else 30f),
                subPaint
            )
        }
        canvas.drawText(
            "STAFF SALARY SLIP / वेतन पर्ची  •  Month: ${rec.yearMonth}  •  Format: ${bundle.pageSize.wireValue}",
            headerTextLeft,
            y + (if (isA5) 36f else 43f),
            sectionBold
        )

        // Status pill on top right
        val isPaid = rec.status == SalaryPaymentStatus.PAID
        val badgeRect = RectF(contentRight - 96f, y + 4f, contentRight, y + 26f)
        val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isPaid) COLOR_GREEN_BG else COLOR_AMBER_BG
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = if (isPaid) COLOR_GREEN_TEXT else COLOR_AMBER_TEXT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgeFill)
        canvas.drawText(
            if (isPaid) "PAID / भुगतान" else "PENDING / बकाया",
            badgeRect.centerX(),
            badgeRect.centerY() + 3.5f,
            badgeTextPaint
        )

        y += logoSize + (if (isA5) 10f else 16f)
        canvas.drawLine(contentLeft, y, contentRight, y, linePaint)
        y += if (isA5) 8f else 12f

        // Staff profile box
        val staffBoxHeight = if (isA5) 38f else 48f
        val staffBox = RectF(contentLeft, y, contentRight, y + staffBoxHeight)
        canvas.drawRoundRect(staffBox, 6f, 6f, boxFillPaint)
        val colMid = (contentLeft + contentRight) / 2f
        val row1Y = y + (if (isA5) 15f else 18f)
        val row2Y = y + (if (isA5) 30f else 36f)

        canvas.drawText("Staff Name: ${rec.staffName}", contentLeft + 10f, row1Y, bodyBold)
        canvas.drawText("Designation: ${rec.designation}", colMid, row1Y, bodyPaint)
        canvas.drawText(
            "Staff ID: ${rec.staffId}   •   Joining: ${rec.joiningDateIso.ifBlank { "—" }}",
            contentLeft + 10f,
            row2Y,
            subPaint
        )
        val payInfoText = if (isPaid) {
            "Paid Date: ${rec.paidDateIso.ifBlank { "—" }}  •  Mode: ${rec.paymentMode.label}"
        } else {
            "Payment Status: Pending"
        }
        canvas.drawText(payInfoText, colMid, row2Y, subPaint)

        y += staffBoxHeight + (if (isA5) 8f else 14f)

        // Attendance Summary Box
        val attBoxHeight = if (isA5) 38f else 48f
        val attBox = RectF(contentLeft, y, contentRight, y + attBoxHeight)
        canvas.drawRoundRect(attBox, 6f, 6f, linePaint)
        canvas.drawText(
            "Attendance Summary (Phase 7)  •  " +
                "Working: ${rec.workingDaysInMonth}  |  Present: ${rec.presentDays}  |  Absent: ${rec.absentDays}  |  " +
                "Leave: ${rec.onLeaveDays}  |  Half-Day: ${rec.halfDays}  |  Late: ${rec.lateDays}",
            contentLeft + 10f,
            y + (if (isA5) 15f else 19f),
            bodyBold
        )
        canvas.drawText(
            "Free Leave Used: ${rec.freeLeavesApplied}  |  Unpaid Absence Days: ${rec.unpaidAbsenceDays}  |  " +
                "Per-Day Rate: ₹${rec.perDayRateRs}  (${bundle.ruleConfig.perDayBasis.englishLabel})",
            contentLeft + 10f,
            y + (if (isA5) 30f else 37f),
            subPaint
        )

        y += attBoxHeight + (if (isA5) 8f else 14f)

        // Side-by-side Earnings & Deductions tables
        val tableTop = y
        val colGap = 12f
        val leftColRight = colMid - colGap / 2f
        val rightColLeft = colMid + colGap / 2f
        val rowStep = if (isA5) 14f else 18f

        // Table headers
        val headerBarHeight = if (isA5) 18f else 22f
        canvas.drawRoundRect(RectF(contentLeft, y, leftColRight, y + headerBarHeight), 4f, 4f, boxFillPaint)
        canvas.drawRoundRect(RectF(rightColLeft, y, contentRight, y + headerBarHeight), 4f, 4f, boxFillPaint)
        canvas.drawText("EARNINGS / आय (₹)", contentLeft + 8f, y + (if (isA5) 13f else 15f), sectionBold)
        canvas.drawText("DEDUCTIONS / कटौती (₹)", rightColLeft + 8f, y + (if (isA5) 13f else 15f), sectionBold)

        val rightAlignPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightAlignBold = Paint(bodyBold).apply { textAlign = Paint.Align.RIGHT }

        var earnY = y + headerBarHeight + rowStep
        canvas.drawText("Basic Monthly Salary", contentLeft + 8f, earnY, bodyPaint)
        canvas.drawText("₹${rec.basicSalaryRs}", leftColRight - 8f, earnY, rightAlignPaint)

        val maxComponentRows = if (isA5) 3 else 7
        val displayedAllowances = rec.allowances.filter { it.amountRs > 0 }.take(maxComponentRows)
        for (item in displayedAllowances) {
            earnY += rowStep
            canvas.drawText(item.name.take(28), contentLeft + 8f, earnY, bodyPaint)
            canvas.drawText("₹${item.amountRs}", leftColRight - 8f, earnY, rightAlignPaint)
        }
        if (displayedAllowances.isEmpty() && rec.totalAllowancesRs > 0) {
            earnY += rowStep
            canvas.drawText("Allowances", contentLeft + 8f, earnY, bodyPaint)
            canvas.drawText("₹${rec.totalAllowancesRs}", leftColRight - 8f, earnY, rightAlignPaint)
        }

        var dedY = y + headerBarHeight + rowStep
        canvas.drawText(
            "Attendance Ded. (${rec.unpaidAbsenceDays}d)",
            rightColLeft + 8f,
            dedY,
            bodyPaint
        )
        canvas.drawText("₹${rec.attendanceDeductionRs}", contentRight - 8f, dedY, rightAlignPaint)

        val displayedDeductions = rec.fixedDeductions.filter { it.amountRs > 0 }.take(maxComponentRows - 1)
        for (item in displayedDeductions) {
            dedY += rowStep
            canvas.drawText(item.name.take(28), rightColLeft + 8f, dedY, bodyPaint)
            canvas.drawText("₹${item.amountRs}", contentRight - 8f, dedY, rightAlignPaint)
        }
        dedY += rowStep
        canvas.drawText("Advance / Loan Instalment", rightColLeft + 8f, dedY, bodyPaint)
        canvas.drawText("₹${rec.advanceDeductionRs}", contentRight - 8f, dedY, rightAlignPaint)

        val totalsY = maxOf(earnY, dedY) + rowStep + 4f
        canvas.drawLine(contentLeft, totalsY - rowStep + 4f, leftColRight, totalsY - rowStep + 4f, linePaint)
        canvas.drawLine(rightColLeft, totalsY - rowStep + 4f, contentRight, totalsY - rowStep + 4f, linePaint)

        canvas.drawText("Gross Earnings", contentLeft + 8f, totalsY, bodyBold)
        canvas.drawText("₹${rec.grossEarningsRs}", leftColRight - 8f, totalsY, rightAlignBold)

        canvas.drawText("Total Deductions", rightColLeft + 8f, totalsY, bodyBold)
        canvas.drawText("₹${rec.totalDeductionsBeforeManualRs}", contentRight - 8f, totalsY, rightAlignBold)

        val tableBottom = totalsY + 8f
        canvas.drawRoundRect(RectF(contentLeft, tableTop, leftColRight, tableBottom), 4f, 4f, linePaint)
        canvas.drawRoundRect(RectF(rightColLeft, tableTop, contentRight, tableBottom), 4f, 4f, linePaint)

        y = tableBottom + (if (isA5) 8f else 14f)

        // Manual Adjustment & Advance Balance line
        if (rec.manualAdjustmentRs != 0 || bundle.remainingAdvanceBalanceRs > 0) {
            val adjSign = if (rec.manualAdjustmentRs >= 0) "+" else ""
            val adjText = if (rec.manualAdjustmentRs != 0) {
                "Manual Adjustment: ${adjSign}₹${rec.manualAdjustmentRs} (${rec.adjustmentRemark.ifBlank { "Adjusted" }})"
            } else {
                "Manual Adjustment: ₹0"
            }
            canvas.drawText(adjText.take(64), contentLeft + 4f, y + 10f, bodyPaint)
            if (bundle.remainingAdvanceBalanceRs > 0) {
                canvas.drawText(
                    "Remaining Advance Balance: ₹${bundle.remainingAdvanceBalanceRs}",
                    contentRight - 4f,
                    y + 10f,
                    rightAlignPaint
                )
            }
            y += if (isA5) 16f else 22f
        }

        // Net Salary Banner
        val netBannerHeight = if (isA5) 28f else 38f
        val netBanner = RectF(contentLeft, y, contentRight, y + netBannerHeight)
        val netFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = COLOR_NAVY
        }
        val netTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isA5) 11.5f else 14f
            color = Color.WHITE
        }
        val netAmountPaint = Paint(netTextPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawRoundRect(netBanner, 6f, 6f, netFill)
        canvas.drawText(
            "NET SALARY PAYABLE / शुद्ध देय वेतन",
            contentLeft + 12f,
            netBanner.centerY() + (if (isA5) 4f else 5f),
            netTextPaint
        )
        canvas.drawText(
            "₹${rec.netPayableRs}",
            contentRight - 12f,
            netBanner.centerY() + (if (isA5) 4f else 5f),
            netAmountPaint
        )

        // Signature footer at bottom
        val sigY = pageHeight - margin - (if (isA5) 12f else 22f)
        canvas.drawLine(contentLeft + 10f, sigY - 14f, contentLeft + 150f, sigY - 14f, linePaint)
        canvas.drawText("Staff Signature / कर्मचारी हस्ताक्षर", contentLeft + 14f, sigY, subPaint)

        canvas.drawLine(contentRight - 160f, sigY - 14f, contentRight - 10f, sigY - 14f, linePaint)
        canvas.drawText("Principal / Authorised Signatory", contentRight - 155f, sigY, subPaint)
    }

    fun generateSalaryMonthSummaryPdf(context: Context, bundle: SalaryMonthSummaryPdfBundle): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "Salary_Month_Summary_${bundle.yearMonth}.pdf")
        val sortedRecords = bundle.records.sortedBy { it.staffName.lowercase() }
        val rowsPerPage = 20
        val chunks = if (sortedRecords.isEmpty()) listOf(emptyList()) else sortedRecords.chunked(rowsPerPage)

        val pdf = PdfDocument()
        try {
            chunks.forEachIndexed { index, pageRecords ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                val page = pdf.startPage(pageInfo)
                drawMonthSummaryPage(
                    canvas = page.canvas,
                    bundle = bundle,
                    pageRecords = pageRecords,
                    pageNumber = index + 1,
                    totalPages = chunks.size,
                    startSerial = index * rowsPerPage + 1
                )
                pdf.finishPage(page)
            }
            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return file
    }

    fun renderSalaryMonthSummaryPreviewBitmaps(
        bundle: SalaryMonthSummaryPdfBundle,
        scale: Float = 2f
    ): List<Bitmap> {
        val sortedRecords = bundle.records.sortedBy { it.staffName.lowercase() }
        val rowsPerPage = 20
        val chunks = if (sortedRecords.isEmpty()) listOf(emptyList()) else sortedRecords.chunked(rowsPerPage)
        return chunks.mapIndexed { index, pageRecords ->
            val bmp = Bitmap.createBitmap(
                (A4_WIDTH * scale).toInt().coerceAtLeast(1),
                (A4_HEIGHT * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.save()
            canvas.scale(scale, scale)
            drawMonthSummaryPage(
                canvas = canvas,
                bundle = bundle,
                pageRecords = pageRecords,
                pageNumber = index + 1,
                totalPages = chunks.size,
                startSerial = index * rowsPerPage + 1
            )
            canvas.restore()
            bmp
        }
    }

    private fun drawMonthSummaryPage(
        canvas: Canvas,
        bundle: SalaryMonthSummaryPdfBundle,
        pageRecords: List<SalaryMonthlyRecord>,
        pageNumber: Int,
        totalPages: Int,
        startSerial: Int
    ) {
        val margin = 24f
        val left = margin
        val right = A4_WIDTH - margin

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = COLOR_NAVY
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9.5f
            color = COLOR_SLATE
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = COLOR_NAVY
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 8.5f
            color = Color.BLACK
        }
        val cellBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = Color.BLACK
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = COLOR_BORDER
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = COLOR_BG_TINT
        }

        var y = margin + 10f
        drawSchoolLogoOrEmblem(
            canvas = canvas,
            logoBase64 = bundle.schoolLogoBase64,
            schoolName = bundle.school.name,
            left = left,
            top = y,
            size = 40f
        )
        canvas.drawText(
            bundle.school.name.ifBlank { "School Name" }.take(55),
            left + 50f,
            y + 15f,
            titlePaint
        )
        val lockTag = if (bundle.isLocked) "LOCKED MONTH / लॉक किया गया माह" else "OPEN MONTH"
        canvas.drawText(
            "MONTHLY STAFF SALARY SHEET / मासिक वेतन पत्रक  •  Month: ${bundle.yearMonth}  •  $lockTag",
            left + 50f,
            y + 30f,
            headerPaint
        )
        canvas.drawText(
            bundle.ruleConfig.ruleSummaryText(isHindi = false).take(95),
            left + 50f,
            y + 42f,
            subPaint
        )

        y += 54f

        // Overall summary KPI bar
        val all = bundle.records
        val totalGross = all.sumOf { it.grossEarningsRs }
        val totalAttDed = all.sumOf { it.attendanceDeductionRs }
        val totalFixDed = all.sumOf { it.totalFixedDeductionsRs }
        val totalAdvDed = all.sumOf { it.advanceDeductionRs }
        val totalNet = all.sumOf { it.netPayableRs }
        val paidCount = all.count { it.status == SalaryPaymentStatus.PAID }

        val kpiBox = RectF(left, y, right, y + 30f)
        canvas.drawRoundRect(kpiBox, 6f, 6f, fillPaint)
        canvas.drawText(
            "Staff: ${all.size}  |  Gross: ₹$totalGross  |  Att. Ded: ₹$totalAttDed  |  Fixed Ded: ₹$totalFixDed  |  " +
                "Advance Ded: ₹$totalAdvDed  |  Net Payable: ₹$totalNet  |  Paid: $paidCount/${all.size}",
            left + 10f,
            y + 19f,
            headerPaint
        )

        y += 40f

        // Table columns
        val cols = floatArrayOf(
            left,          // #
            left + 22f,    // Staff Name & Role
            left + 155f,   // P/A/L/HD
            left + 220f,   // Gross
            left + 275f,   // Att. Ded
            left + 328f,   // Fix. Ded
            left + 380f,   // Adv. Ded
            left + 432f,   // Manual Adj
            left + 485f,   // Net Salary
            right          // Status
        )

        val thRect = RectF(left, y, right, y + 22f)
        canvas.drawRect(thRect, fillPaint)
        canvas.drawRect(thRect, linePaint)
        val headers = arrayOf("#", "Staff Name (Role)", "P/A/Lv/HD", "Gross", "Att.Ded", "Fix.Ded", "Adv.Ded", "Adj", "Net (₹)", "Status")
        for (i in headers.indices) {
            canvas.drawText(headers[i], cols[i] + 4f, y + 14f, headerPaint)
        }
        y += 22f

        val rowHeight = 26f
        pageRecords.forEachIndexed { idx, rec ->
            val rowRect = RectF(left, y, right, y + rowHeight)
            canvas.drawRect(rowRect, linePaint)
            val textY = y + 16f
            canvas.drawText("${startSerial + idx}", cols[0] + 4f, textY, cellPaint)
            canvas.drawText(
                "${rec.staffName.take(18)} (${rec.designation.take(8)})",
                cols[1] + 4f,
                textY,
                cellBold
            )
            canvas.drawText(
                "${rec.presentDays}/${rec.absentDays}/${rec.onLeaveDays}/${rec.halfDays}",
                cols[2] + 4f,
                textY,
                cellPaint
            )
            canvas.drawText("₹${rec.grossEarningsRs}", cols[3] + 4f, textY, cellPaint)
            canvas.drawText("₹${rec.attendanceDeductionRs}", cols[4] + 4f, textY, cellPaint)
            canvas.drawText("₹${rec.totalFixedDeductionsRs}", cols[5] + 4f, textY, cellPaint)
            canvas.drawText("₹${rec.advanceDeductionRs}", cols[6] + 4f, textY, cellPaint)
            val adjStr = if (rec.manualAdjustmentRs > 0) "+${rec.manualAdjustmentRs}" else "${rec.manualAdjustmentRs}"
            canvas.drawText(adjStr, cols[7] + 4f, textY, cellPaint)
            canvas.drawText("₹${rec.netPayableRs}", cols[8] + 4f, textY, cellBold)
            canvas.drawText(rec.status.wireValue, cols[9] - 42f, textY, cellPaint)
            y += rowHeight
        }

        // Footer
        val footerY = A4_HEIGHT - margin - 14f
        canvas.drawLine(left, footerY - 22f, left + 160f, footerY - 22f, linePaint)
        canvas.drawText("Prepared By / तैयारकर्ता", left + 10f, footerY - 8f, subPaint)

        canvas.drawLine(right - 170f, footerY - 22f, right, footerY - 22f, linePaint)
        canvas.drawText("Principal / Owner Signature", right - 155f, footerY - 8f, subPaint)

        val pagePaint = Paint(subPaint).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("Page $pageNumber of $totalPages", A4_WIDTH / 2f, footerY - 8f, pagePaint)
    }

    private fun drawSchoolLogoOrEmblem(
        canvas: Canvas,
        logoBase64: String,
        schoolName: String,
        left: Float,
        top: Float,
        size: Float
    ) {
        val decoded = decodeLogoBitmap(logoBase64)
        val dst = RectF(left, top, left + size, top + size)
        if (decoded != null) {
            canvas.drawBitmap(decoded, null, dst, null)
            return
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = COLOR_NAVY
        }
        canvas.drawOval(dst, circlePaint)
        val initials = schoolName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "SC" }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * 0.38f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(initials, dst.centerX(), dst.centerY() + (size * 0.14f), textPaint)
    }

    private fun decodeLogoBitmap(base64Raw: String): Bitmap? {
        val cleaned = base64Raw.trim().substringAfter("base64,", base64Raw.trim())
        if (cleaned.length < 32) return null
        return try {
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Throwable) {
            null
        }
    }
}
