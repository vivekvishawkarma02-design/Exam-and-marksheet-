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
import com.example.domain.BulkImportExecutionReport
import com.example.domain.ImportRowAction
import com.example.domain.PromotionDecision
import com.example.domain.PromotionRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PromotionRegisterPdfBundle(
    val school: SchoolEntity,
    val fromSession: String,
    val toSession: String,
    val className: String,
    val records: List<PromotionRecord>,
    val confirmedByName: String,
    val confirmedAtMs: Long
)

/**
 * Phase 14 Offline A4 PDF Generators:
 * 1. Promotion Register PDF (A4 `595 x 842`) per class: Promoted, By Grace (with remarks & approver),
 *    Detained, Left, Passed Out / Alumni, Carried-forward dues, confirmation date & confirmer.
 * 2. Bulk Student Import Result Report PDF (A4 `595 x 842`).
 */
object Phase14PdfGenerators {

    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842

    private val COLOR_NAVY = Color.rgb(22, 49, 92)
    private val COLOR_SLATE = Color.rgb(75, 85, 99)
    private val COLOR_BG_TINT = Color.rgb(241, 245, 249)
    private val COLOR_BORDER = Color.rgb(203, 213, 225)
    private val COLOR_GREEN = Color.rgb(21, 128, 61)
    private val COLOR_AMBER = Color.rgb(180, 83, 9)
    private val COLOR_RED = Color.rgb(185, 28, 28)

    fun generatePromotionRegisterPdf(
        context: Context,
        bundle: PromotionRegisterPdfBundle
    ): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeCls = bundle.className.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "All" }
        val safeSes = "${bundle.fromSession}_to_${bundle.toSession}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "Promotion_Register_${safeCls}_$safeSes.pdf")

        val pdf = PdfDocument()
        try {
            val rows = bundle.records.sortedWith(
                compareBy<PromotionRecord> { it.fromSection }
                    .thenBy { it.fromRollNo }
                    .thenBy { it.studentName.lowercase(Locale.US) }
            )
            val rowsPerPage = 24
            val chunks = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(rowsPerPage)

            chunks.forEachIndexed { pageIndex, pageRows ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIndex + 1).create()
                val page = pdf.startPage(pageInfo)
                drawPromotionRegisterPage(
                    canvas = page.canvas,
                    bundle = bundle,
                    pageRows = pageRows,
                    pageNumber = pageIndex + 1,
                    totalPages = chunks.size,
                    startSerial = pageIndex * rowsPerPage + 1
                )
                pdf.finishPage(page)
            }
            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return file
    }

    fun renderPromotionRegisterPreviewBitmaps(bundle: PromotionRegisterPdfBundle): List<Bitmap> {
        val rows = bundle.records.sortedWith(
            compareBy<PromotionRecord> { it.fromSection }
                .thenBy { it.fromRollNo }
                .thenBy { it.studentName.lowercase(Locale.US) }
        )
        val rowsPerPage = 24
        val chunks = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(rowsPerPage)
        return chunks.mapIndexed { pageIndex, pageRows ->
            val bmp = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            drawPromotionRegisterPage(
                canvas = canvas,
                bundle = bundle,
                pageRows = pageRows,
                pageNumber = pageIndex + 1,
                totalPages = chunks.size,
                startSerial = pageIndex * rowsPerPage + 1
            )
            bmp
        }
    }

    private fun drawPromotionRegisterPage(
        canvas: Canvas,
        bundle: PromotionRegisterPdfBundle,
        pageRows: List<PromotionRecord>,
        pageNumber: Int,
        totalPages: Int,
        startSerial: Int
    ) {
        val margin = 26f
        val contentWidth = A4_WIDTH - margin * 2

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SLATE
            textSize = 9.5f
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            style = Paint.Style.FILL
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8f
        }
        val boldCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BG_TINT
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }

        var y = margin + 12f
        canvas.drawText(bundle.school.name.ifBlank { "School" }.uppercase(Locale.US), margin, y, titlePaint)
        y += 14f
        canvas.drawText(
            "PROMOTION REGISTER — Class ${bundle.className}  |  Session ${bundle.fromSession} -> ${bundle.toSession}",
            margin,
            y,
            boldCellPaint.apply { textSize = 10f; color = COLOR_NAVY }
        )
        boldCellPaint.textSize = 8f
        boldCellPaint.color = Color.BLACK
        y += 13f

        val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        val confDate = if (bundle.confirmedAtMs > 0L) dateFmt.format(Date(bundle.confirmedAtMs)) else dateFmt.format(Date())
        canvas.drawText(
            "Confirmed By: ${bundle.confirmedByName.ifBlank { "Admin" }}   |   Date: $confDate   |   Page $pageNumber of $totalPages",
            margin,
            y,
            subtitlePaint
        )
        y += 12f

        // Summary chips bar on page 1
        if (pageNumber == 1) {
            val all = bundle.records
            val promoted = all.count { it.decision == PromotionDecision.PROMOTED }
            val grace = all.count { it.decision == PromotionDecision.PROMOTED_BY_GRACE }
            val detained = all.count { it.decision == PromotionDecision.DETAINED }
            val left = all.count { it.decision == PromotionDecision.LEFT }
            val passedOut = all.count { it.decision == PromotionDecision.PASSED_OUT }
            val totalDues = all.sumOf { it.carriedForwardDueRs }

            val boxRect = RectF(margin, y, margin + contentWidth, y + 24f)
            canvas.drawRect(boxRect, stripePaint)
            canvas.drawRect(boxRect, borderPaint)
            canvas.drawText(
                "Total: ${all.size}  |  Promoted: $promoted  |  Grace: $grace  |  Detained: $detained  |  Left: $left  |  Passed Out: $passedOut  |  Dues C/F: Rs.$totalDues",
                margin + 8f,
                y + 15f,
                boldCellPaint
            )
            y += 32f
        }

        // Table Header
        val colWidths = floatArrayOf(24f, 52f, 108f, 44f, 52f, 84f, 64f, 48f, contentWidth - 476f)
        val headers = listOf("Sr", "Adm No", "Student / Father", "Old", "Annual%", "Decision", "New Class", "Due C/F", "Remarks / Approver")
        val headerHeight = 20f
        canvas.drawRect(margin, y, margin + contentWidth, y + headerHeight, headerBgPaint)

        var x = margin
        headers.forEachIndexed { idx, label ->
            canvas.drawText(label, x + 3f, y + 13f, headerTextPaint)
            x += colWidths[idx]
        }
        y += headerHeight

        val rowHeight = 26f
        pageRows.forEachIndexed { idx, rec ->
            if (idx % 2 == 1) {
                canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, stripePaint)
            }
            canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, borderPaint)

            val decisionColor = when (rec.decision) {
                PromotionDecision.PROMOTED, PromotionDecision.PASSED_OUT -> COLOR_GREEN
                PromotionDecision.PROMOTED_BY_GRACE -> COLOR_AMBER
                PromotionDecision.DETAINED, PromotionDecision.LEFT -> COLOR_RED
                PromotionDecision.NEEDS_REVIEW -> COLOR_SLATE
            }
            val newClassLabel = when {
                rec.isAlumni || rec.decision == PromotionDecision.PASSED_OUT -> "Alumni"
                rec.toClassName.isBlank() -> "—"
                else -> "${rec.toClassName}-${rec.toSection} #${rec.toRollNo}"
            }
            val remarkLine = buildString {
                if (rec.graceRemark.isNotBlank()) {
                    append(rec.graceRemark.take(32))
                    if (rec.graceApprovedByName.isNotBlank()) append(" (${rec.graceApprovedByName.take(14)})")
                } else {
                    append("—")
                }
            }

            var cx = margin
            canvas.drawText((startSerial + idx).toString(), cx + 3f, y + 14f, cellPaint); cx += colWidths[0]
            canvas.drawText(rec.admNo.ifBlank { "—" }.take(10), cx + 3f, y + 14f, cellPaint); cx += colWidths[1]
            canvas.drawText(rec.studentName.take(20), cx + 3f, y + 11f, boldCellPaint)
            canvas.drawText(rec.fatherName.take(20), cx + 3f, y + 21f, subtitlePaint.apply { textSize = 7f })
            subtitlePaint.textSize = 9.5f
            cx += colWidths[2]
            canvas.drawText("${rec.fromSection}#${rec.fromRollNo}", cx + 3f, y + 14f, cellPaint); cx += colWidths[3]
            canvas.drawText("${String.format(Locale.US, "%.1f", rec.annualPercent)}% ${rec.annualResultStatus.name.take(1)}", cx + 3f, y + 14f, cellPaint); cx += colWidths[4]
            boldCellPaint.color = decisionColor
            canvas.drawText(rec.decision.labelEn.take(16), cx + 3f, y + 14f, boldCellPaint)
            boldCellPaint.color = Color.BLACK
            cx += colWidths[5]
            canvas.drawText(newClassLabel.take(13), cx + 3f, y + 14f, cellPaint); cx += colWidths[6]
            canvas.drawText(if (rec.carriedForwardDueRs > 0) "Rs.${rec.carriedForwardDueRs}" else "—", cx + 3f, y + 14f, cellPaint); cx += colWidths[7]
            canvas.drawText(remarkLine.take(22), cx + 3f, y + 14f, cellPaint)

            y += rowHeight
        }

        // Signature footer on last page
        if (pageNumber == totalPages) {
            val sigY = (A4_HEIGHT - 48f).coerceAtLeast(y + 26f)
            canvas.drawLine(margin, sigY, margin + 150f, sigY, borderPaint)
            canvas.drawText("Class Teacher / Operator", margin, sigY + 12f, subtitlePaint)
            canvas.drawLine(A4_WIDTH - margin - 150f, sigY, A4_WIDTH - margin, sigY, borderPaint)
            canvas.drawText("Principal / Owner Signature", A4_WIDTH - margin - 150f, sigY + 12f, subtitlePaint)
        }
    }

    fun generateBulkImportReportPdf(
        context: Context,
        report: BulkImportExecutionReport
    ): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(report.importedAtMs))
        val file = File(dir, "Bulk_Import_Report_$stamp.pdf")

        val pdf = PdfDocument()
        try {
            val rowsPerPage = 26
            val chunks = if (report.rows.isEmpty()) listOf(emptyList()) else report.rows.chunked(rowsPerPage)
            chunks.forEachIndexed { pageIndex, pageRows ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIndex + 1).create()
                val page = pdf.startPage(pageInfo)
                drawBulkImportReportPage(page.canvas, report, pageRows, pageIndex + 1, chunks.size)
                pdf.finishPage(page)
            }
            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return file
    }

    fun renderBulkImportReportPreviewBitmaps(report: BulkImportExecutionReport): List<Bitmap> {
        val rowsPerPage = 26
        val chunks = if (report.rows.isEmpty()) listOf(emptyList()) else report.rows.chunked(rowsPerPage)
        return chunks.mapIndexed { pageIndex, pageRows ->
            val bmp = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            drawBulkImportReportPage(canvas, report, pageRows, pageIndex + 1, chunks.size)
            bmp
        }
    }

    private fun drawBulkImportReportPage(
        canvas: Canvas,
        report: BulkImportExecutionReport,
        pageRows: List<com.example.domain.BulkImportPreviewRow>,
        pageNumber: Int,
        totalPages: Int
    ) {
        val margin = 26f
        val contentWidth = A4_WIDTH - margin * 2
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SLATE
            textSize = 9f
        }
        val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_NAVY }
        val headerTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 8f }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BG_TINT }

        var y = margin + 12f
        canvas.drawText("${report.schoolName.ifBlank { "School" }} — BULK STUDENT IMPORT REPORT", margin, y, titlePaint)
        y += 14f
        val dateText = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(report.importedAtMs))
        canvas.drawText(
            "Session: ${report.session}  |  File: ${report.fileName.take(40)}  |  By: ${report.actorName}  |  $dateText  |  Page $pageNumber/$totalPages",
            margin,
            y,
            subPaint
        )
        y += 14f

        if (pageNumber == 1) {
            canvas.drawRect(margin, y, margin + contentWidth, y + 24f, stripePaint)
            canvas.drawRect(margin, y, margin + contentWidth, y + 24f, borderPaint)
            canvas.drawText(
                "Total Rows: ${report.totalRows}   |   Added: ${report.addedCount}   |   Updated: ${report.updatedCount}   |   Skipped: ${report.skippedCount}   |   Errors: ${report.errorCount}",
                margin + 8f,
                y + 15f,
                Paint(cellPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            )
            y += 32f
        }

        val colWidths = floatArrayOf(30f, 54f, 115f, 52f, 64f, 82f, contentWidth - 397f)
        val headers = listOf("Row", "Status", "Student Name", "Class", "Adm No", "Mobile (+91)", "Notes / Problems")
        canvas.drawRect(margin, y, margin + contentWidth, y + 18f, headerBg)
        var x = margin
        headers.forEachIndexed { idx, h ->
            canvas.drawText(h, x + 3f, y + 12f, headerTxt)
            x += colWidths[idx]
        }
        y += 18f

        val rowHeight = 23f
        pageRows.forEachIndexed { idx, r ->
            if (idx % 2 == 1) canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, stripePaint)
            canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, borderPaint)
            val note = when (r.action) {
                ImportRowAction.ADD -> "Imported"
                ImportRowAction.UPDATE -> r.updateDiffs.joinToString("; ") { "${it.fieldLabel}: ${it.newValue}" }.ifBlank { "Updated" }
                ImportRowAction.SKIP -> "Skipped"
                ImportRowAction.ERROR -> r.problems.joinToString("; ") { it.message }
            }
            var cx = margin
            canvas.drawText(r.rowNumber.toString(), cx + 3f, y + 14f, cellPaint); cx += colWidths[0]
            cellPaint.color = when (r.action) {
                ImportRowAction.ADD -> COLOR_GREEN
                ImportRowAction.UPDATE -> COLOR_AMBER
                ImportRowAction.ERROR -> COLOR_RED
                ImportRowAction.SKIP -> COLOR_SLATE
            }
            canvas.drawText(r.action.name, cx + 3f, y + 14f, cellPaint)
            cellPaint.color = Color.BLACK
            cx += colWidths[1]
            canvas.drawText(r.values.name.take(22), cx + 3f, y + 14f, cellPaint); cx += colWidths[2]
            canvas.drawText("${r.values.className}-${r.values.section}".take(10), cx + 3f, y + 14f, cellPaint); cx += colWidths[3]
            canvas.drawText(r.values.admNo.ifBlank { "—" }.take(12), cx + 3f, y + 14f, cellPaint); cx += colWidths[4]
            canvas.drawText(r.normalizedMobile.ifBlank { r.values.mobile }.take(14), cx + 3f, y + 14f, cellPaint); cx += colWidths[5]
            canvas.drawText(note.take(36), cx + 3f, y + 14f, cellPaint)
            y += rowHeight
        }
    }
}
