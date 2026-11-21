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
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import com.example.domain.AttendanceSummary
import com.example.domain.CoScholasticArea
import com.example.domain.ExamType
import com.example.domain.MarkComponent
import com.example.domain.ReportCalculations
import com.example.domain.ReportType
import com.example.domain.ResolvedSchoolIdentity
import com.example.domain.SlipLayoutMode
import com.example.domain.StudentPerformanceReport
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class StudentReportBundle(
    val student: StudentEntity,
    val schoolIdentity: ResolvedSchoolIdentity,
    val subjects: List<String>,
    val marksBySubjectAndKey: Map<Pair<String, String>, String>,
    val performance: StudentPerformanceReport,
    val attendance: AttendanceSummary,
    val coScholasticT1: Map<String, String>,
    val coScholasticT2: Map<String, String>,
    val term1Info: TermInfoEntity?,
    val term2Info: TermInfoEntity?
)

/**
 * Native Android A4 Vector PDF generator for C.P.S. School & C.P.I. School.
 * - UT1, UT2 & Half-Yearly Slips: A4 LANDSCAPE (842 x 595 pt, 6 mm margin, 2 A5 portrait slips side-by-side)
 * - Annual Report Card: A4 PORTRAIT (595 x 842 pt, 8 mm margin, guaranteed single-page fit for 6, 7 & 8 subjects)
 */
object PdfReportGenerator {

    // A4 Portrait = 210 x 297 mm -> 595 x 842 pt
    const val PORTRAIT_W = 595
    const val PORTRAIT_H = 842
    // A4 Landscape = 297 x 210 mm -> 842 x 595 pt
    const val LANDSCAPE_W = 842
    const val LANDSCAPE_H = 595

    private const val MARGIN_6MM = 17f   // 6 mm
    private const val MARGIN_8MM = 22.7f // 8 mm

    private val COLOR_BAND = Color.parseColor("#243447")     // Dark navy-slate banner (prints crisp in B&W)
    private val COLOR_SUB_BAND = Color.parseColor("#3B4D61") // Secondary dark slate
    private val COLOR_TINT = Color.parseColor("#E6ECF2")     // Subtle light header fill
    private val COLOR_ZEBRA = Color.parseColor("#F5F7FA")    // Soft alternating row
    private val COLOR_BORDER = Color.parseColor("#526171")   // Thin crisp print border
    private val COLOR_TEXT = Color.parseColor("#111111")
    private val COLOR_SUBTEXT = Color.parseColor("#333F4D")
    private val COLOR_WHITE = Color.WHITE

    fun generateReportPdf(
        context: Context,
        settings: SchoolSettingsEntity,
        reportType: ReportType,
        bundles: List<StudentReportBundle>,
        slipLayoutMode: SlipLayoutMode = SlipLayoutMode.TWO_PER_PAGE,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val fileName = buildPdfFileName(reportType, bundles)
        val outFile = File(reportsDir, fileName)
        writePdfToFile(context, settings, reportType, bundles, slipLayoutMode, outFile, onProgress)
        return outFile
    }

    fun generateBatchClassZip(
        context: Context,
        settings: SchoolSettingsEntity,
        reportType: ReportType,
        bundles: List<StudentReportBundle>,
        slipLayoutMode: SlipLayoutMode = SlipLayoutMode.TWO_PER_PAGE,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val first = bundles.firstOrNull()?.student
        val zipName = if (first != null) {
            "CPI_CPS_${reportType.name}_Class_${first.className}_${first.section}_${first.session}.zip"
        } else {
            "CPI_CPS_${reportType.name}_Batch.zip"
        }
        val zipFile = File(reportsDir, zipName)
        val tempDir = File(reportsDir, "zip_tmp_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                bundles.forEachIndexed { idx, bundle ->
                    val entryName = buildBatchZipEntryName(bundle.student, reportType)
                    val singleFile = File(tempDir, entryName)
                    val modeForSingle = if (slipLayoutMode == SlipLayoutMode.PAIR_UT_HY) {
                        SlipLayoutMode.PAIR_UT_HY
                    } else {
                        SlipLayoutMode.ONE_PER_PAGE
                    }
                    writePdfToFile(context, settings, reportType, listOf(bundle), modeForSingle, singleFile)
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(singleFile).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                    singleFile.delete()
                    onProgress(idx + 1, bundles.size)
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
        return zipFile
    }

    internal fun buildBatchZipEntryName(student: StudentEntity, reportType: ReportType): String {
        val safeName = student.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(24).ifEmpty { "Student" }
        val rollPadded = student.rollNo.toString().padStart(2, '0')
        return "${rollPadded}_${safeName}_${student.className}_${student.section}_${reportType.name}.pdf"
    }

    internal fun calculateExpectedPageCount(
        reportType: ReportType,
        studentCount: Int,
        slipLayoutMode: SlipLayoutMode
    ): Int {
        if (studentCount <= 0) return 0
        if (reportType == ReportType.ANNUAL) return studentCount
        val effective = if (reportType == ReportType.HALF_YEARLY) {
            slipLayoutMode
        } else {
            if (slipLayoutMode == SlipLayoutMode.ONE_PER_PAGE) SlipLayoutMode.ONE_PER_PAGE else SlipLayoutMode.TWO_PER_PAGE
        }
        return when (effective) {
            SlipLayoutMode.TWO_PER_PAGE -> (studentCount + 1) / 2
            SlipLayoutMode.ONE_PER_PAGE -> studentCount
            SlipLayoutMode.PAIR_UT_HY -> studentCount
        }
    }

    internal fun renderPagesToBitmapsForVerification(
        context: Context,
        settings: SchoolSettingsEntity,
        reportType: ReportType,
        bundles: List<StudentReportBundle>,
        slipLayoutMode: SlipLayoutMode = SlipLayoutMode.TWO_PER_PAGE
    ): List<Bitmap> {
        val baseTypeface = loadReportTypeface(context)
        val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)
        val logoCache = mutableMapOf<String, Bitmap?>()
        fun logoFor(identity: ResolvedSchoolIdentity): Bitmap? {
            val key = "${identity.code.code}_${identity.logoCleared}_${identity.logoBase64.hashCode()}"
            return logoCache.getOrPut(key) { ImageUtils.resolveSchoolLogoBitmap(context, identity) }
        }

        val pages = mutableListOf<Bitmap>()
        if (reportType == ReportType.ANNUAL) {
            bundles.forEach { bundle ->
                val bmp = Bitmap.createBitmap(PORTRAIT_W, PORTRAIT_H, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                drawAnnualPortraitCard(Canvas(bmp), settings, bundle, logoFor(bundle.schoolIdentity), baseTypeface, boldTypeface)
                pages.add(bmp)
            }
        } else {
            val effectiveMode = if (reportType == ReportType.HALF_YEARLY) {
                slipLayoutMode
            } else {
                if (slipLayoutMode == SlipLayoutMode.ONE_PER_PAGE) SlipLayoutMode.ONE_PER_PAGE else SlipLayoutMode.TWO_PER_PAGE
            }
            val leftSlipX = MARGIN_6MM
            val rightSlipX = LANDSCAPE_W / 2f + 8f
            val slipW = (LANDSCAPE_W / 2f) - MARGIN_6MM - 8f
            val slipTop = MARGIN_6MM
            val slipH = LANDSCAPE_H - (MARGIN_6MM * 2f)

            when (effectiveMode) {
                SlipLayoutMode.PAIR_UT_HY -> bundles.forEach { bundle ->
                    val bmp = Bitmap.createBitmap(LANDSCAPE_W, LANDSCAPE_H, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    val c = Canvas(bmp)
                    val logo = logoFor(bundle.schoolIdentity)
                    drawA5LandscapeHalfSlip(c, leftSlipX, slipTop, slipW, slipH, settings, bundle, ReportType.UT1, logo, baseTypeface, boldTypeface)
                    drawVerticalCutLine(c, LANDSCAPE_W / 2f)
                    drawA5LandscapeHalfSlip(c, rightSlipX, slipTop, slipW, slipH, settings, bundle, ReportType.HALF_YEARLY, logo, baseTypeface, boldTypeface)
                    pages.add(bmp)
                }
                SlipLayoutMode.ONE_PER_PAGE -> bundles.forEach { bundle ->
                    val bmp = Bitmap.createBitmap(LANDSCAPE_W, LANDSCAPE_H, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    val c = Canvas(bmp)
                    drawA5LandscapeHalfSlip(c, leftSlipX, slipTop, slipW, slipH, settings, bundle, reportType, logoFor(bundle.schoolIdentity), baseTypeface, boldTypeface)
                    drawVerticalCutLine(c, LANDSCAPE_W / 2f)
                    pages.add(bmp)
                }
                SlipLayoutMode.TWO_PER_PAGE -> bundles.chunked(2).forEach { pair ->
                    val bmp = Bitmap.createBitmap(LANDSCAPE_W, LANDSCAPE_H, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    val c = Canvas(bmp)
                    drawA5LandscapeHalfSlip(c, leftSlipX, slipTop, slipW, slipH, settings, pair[0], reportType, logoFor(pair[0].schoolIdentity), baseTypeface, boldTypeface)
                    drawVerticalCutLine(c, LANDSCAPE_W / 2f)
                    if (pair.size > 1) {
                        drawA5LandscapeHalfSlip(c, rightSlipX, slipTop, slipW, slipH, settings, pair[1], reportType, logoFor(pair[1].schoolIdentity), baseTypeface, boldTypeface)
                    }
                    pages.add(bmp)
                }
            }
        }
        return pages
    }

    private fun buildPdfFileName(reportType: ReportType, bundles: List<StudentReportBundle>): String = when {
        bundles.size == 1 -> {
            val st = bundles.first().student
            val schoolTag = bundles.first().schoolIdentity.code.code
            val safeName = st.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(24).ifEmpty { "Student" }
            "${schoolTag}_${reportType.name}_${st.className}_${st.section}_R${st.rollNo}_$safeName.pdf"
        }
        bundles.isNotEmpty() -> {
            val st = bundles.first().student
            val schoolTag = bundles.first().schoolIdentity.code.code
            "${schoolTag}_${reportType.name}_Class_${st.className}_${st.section}_${st.session}.pdf"
        }
        else -> "CPI_CPS_${reportType.name}.pdf"
    }

    private fun writePdfToFile(
        context: Context,
        settings: SchoolSettingsEntity,
        reportType: ReportType,
        bundles: List<StudentReportBundle>,
        slipLayoutMode: SlipLayoutMode,
        outFile: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        val baseTypeface = loadReportTypeface(context)
        val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)
        val logoCache = mutableMapOf<String, Bitmap?>()

        fun logoFor(identity: ResolvedSchoolIdentity): Bitmap? {
            val key = "${identity.code.code}_${identity.logoCleared}_${identity.logoBase64.hashCode()}"
            return logoCache.getOrPut(key) {
                ImageUtils.resolveSchoolLogoBitmap(context, identity)
            }
        }

        val pdf = PdfDocument()
        try {
            var pageNo = 1
            if (reportType == ReportType.ANNUAL) {
                bundles.forEachIndexed { idx, bundle ->
                    val pageInfo = PdfDocument.PageInfo.Builder(PORTRAIT_W, PORTRAIT_H, pageNo++).create()
                    val page = pdf.startPage(pageInfo)
                    drawAnnualPortraitCard(
                        canvas = page.canvas,
                        settings = settings,
                        bundle = bundle,
                        logo = logoFor(bundle.schoolIdentity),
                        regular = baseTypeface,
                        bold = boldTypeface
                    )
                    pdf.finishPage(page)
                    onProgress(idx + 1, bundles.size)
                }
            } else {
                // Landscape A4 slips: UT1, UT2, Half-Yearly
                val effectiveMode = if (reportType == ReportType.HALF_YEARLY) {
                    slipLayoutMode
                } else {
                    if (slipLayoutMode == SlipLayoutMode.ONE_PER_PAGE) SlipLayoutMode.ONE_PER_PAGE else SlipLayoutMode.TWO_PER_PAGE
                }

                val leftSlipX = MARGIN_6MM
                val rightSlipX = LANDSCAPE_W / 2f + 8f
                val slipW = (LANDSCAPE_W / 2f) - MARGIN_6MM - 8f // 396 pt (~140 mm)
                val slipTop = MARGIN_6MM
                val slipH = LANDSCAPE_H - (MARGIN_6MM * 2f)      // 561 pt (~198 mm)

                when (effectiveMode) {
                    SlipLayoutMode.PAIR_UT_HY -> {
                        // Left: UT1 slip, Right: Half-Yearly slip for the SAME student
                        bundles.forEachIndexed { idx, bundle ->
                            val pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_W, LANDSCAPE_H, pageNo++).create()
                            val page = pdf.startPage(pageInfo)
                            val logo = logoFor(bundle.schoolIdentity)
                            drawA5LandscapeHalfSlip(
                                canvas = page.canvas,
                                left = leftSlipX,
                                top = slipTop,
                                width = slipW,
                                height = slipH,
                                settings = settings,
                                bundle = bundle,
                                slipReportType = ReportType.UT1,
                                logo = logo,
                                regular = baseTypeface,
                                bold = boldTypeface
                            )
                            drawVerticalCutLine(page.canvas, LANDSCAPE_W / 2f)
                            drawA5LandscapeHalfSlip(
                                canvas = page.canvas,
                                left = rightSlipX,
                                top = slipTop,
                                width = slipW,
                                height = slipH,
                                settings = settings,
                                bundle = bundle,
                                slipReportType = ReportType.HALF_YEARLY,
                                logo = logo,
                                regular = baseTypeface,
                                bold = boldTypeface
                            )
                            pdf.finishPage(page)
                            onProgress(idx + 1, bundles.size)
                        }
                    }

                    SlipLayoutMode.ONE_PER_PAGE -> {
                        // Left half only
                        bundles.forEachIndexed { idx, bundle ->
                            val pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_W, LANDSCAPE_H, pageNo++).create()
                            val page = pdf.startPage(pageInfo)
                            drawA5LandscapeHalfSlip(
                                canvas = page.canvas,
                                left = leftSlipX,
                                top = slipTop,
                                width = slipW,
                                height = slipH,
                                settings = settings,
                                bundle = bundle,
                                slipReportType = reportType,
                                logo = logoFor(bundle.schoolIdentity),
                                regular = baseTypeface,
                                bold = boldTypeface
                            )
                            drawVerticalCutLine(page.canvas, LANDSCAPE_W / 2f)
                            pdf.finishPage(page)
                            onProgress(idx + 1, bundles.size)
                        }
                    }

                    SlipLayoutMode.TWO_PER_PAGE -> {
                        // Left then right in roll-number order; odd last student leaves right half blank
                        val pairs = bundles.chunked(2)
                        var processed = 0
                        pairs.forEach { pair ->
                            val pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_W, LANDSCAPE_H, pageNo++).create()
                            val page = pdf.startPage(pageInfo)
                            val leftBundle = pair[0]
                            drawA5LandscapeHalfSlip(
                                canvas = page.canvas,
                                left = leftSlipX,
                                top = slipTop,
                                width = slipW,
                                height = slipH,
                                settings = settings,
                                bundle = leftBundle,
                                slipReportType = reportType,
                                logo = logoFor(leftBundle.schoolIdentity),
                                regular = baseTypeface,
                                bold = boldTypeface
                            )
                            processed++
                            onProgress(processed, bundles.size)
                            drawVerticalCutLine(page.canvas, LANDSCAPE_W / 2f)
                            if (pair.size > 1) {
                                val rightBundle = pair[1]
                                drawA5LandscapeHalfSlip(
                                    canvas = page.canvas,
                                    left = rightSlipX,
                                    top = slipTop,
                                    width = slipW,
                                    height = slipH,
                                    settings = settings,
                                    bundle = rightBundle,
                                    slipReportType = reportType,
                                    logo = logoFor(rightBundle.schoolIdentity),
                                    regular = baseTypeface,
                                    bold = boldTypeface
                                )
                                processed++
                                onProgress(processed, bundles.size)
                            }
                            pdf.finishPage(page)
                        }
                    }
                }
            }

            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        } finally {
            pdf.close()
        }
    }

    private fun loadReportTypeface(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.noto_sans_devanagari)
                ?: ResourcesCompat.getFont(context, R.font.noto_sans)
                ?: Typeface.SANS_SERIF
        } catch (_: Exception) {
            Typeface.SANS_SERIF
        }
    }

    private fun drawVerticalCutLine(canvas: Canvas, x: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
        }
        canvas.drawLine(x, 10f, x, LANDSCAPE_H - 10f, paint)
    }

    // =========================================================================
    // 9A. A5 PORTRAIT SLIP INSIDE A4 LANDSCAPE HALF (396 x 561 pt)
    // =========================================================================
    private fun drawA5LandscapeHalfSlip(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        settings: SchoolSettingsEntity,
        bundle: StudentReportBundle,
        slipReportType: ReportType,
        logo: Bitmap?,
        regular: Typeface,
        bold: Typeface
    ) {
        val right = left + width
        val bottom = top + height
        val pad = 7f
        val inL = left + pad
        val inR = right - pad
        val inW = inR - inL // 382 pt
        val centerX = (inL + inR) / 2f
        val st = bundle.student
        val id = bundle.schoolIdentity
        val isHalfYearly = slipReportType == ReportType.HALF_YEARLY
        val isUt2 = slipReportType == ReportType.UT2

        // Outer slip border
        drawRectStroke(canvas, left, top, right, bottom, COLOR_BAND, 1.3f)

        var y = top + 6f

        // 1. HEADER: Logo left, large bold School Name, address, Mob. right, SESSION : 2026 - 2027
        val logoSize = 42f
        val logoRect = RectF(inL, y + 2f, inL + logoSize, y + 2f + logoSize)
        drawLogoOrPlaceholder(canvas, logoRect, logo, bold, 7.5f)

        val textAreaCenter = inL + logoSize + (inW - logoSize) / 2f
        if (id.mobile.isNotBlank()) {
            canvas.drawText(
                "Mob. ${id.mobile}",
                inR - 2f,
                y + 8.5f,
                makePaint(bold, 6.8f, COLOR_SUBTEXT, Paint.Align.RIGHT)
            )
        }
        canvas.drawText(
            id.schoolName.uppercase(),
            textAreaCenter,
            y + 20f,
            makePaint(bold, 13.5f, COLOR_BAND, Paint.Align.CENTER)
        )
        canvas.drawText(
            ellipsize(id.address, makePaint(regular, 7.4f, COLOR_TEXT), inW - logoSize - 8f),
            textAreaCenter,
            y + 31.5f,
            makePaint(regular, 7.4f, COLOR_TEXT, Paint.Align.CENTER)
        )
        val formattedSession = st.session.replace("-", " - ")
        canvas.drawText(
            "SESSION : $formattedSession",
            textAreaCenter,
            y + 43f,
            makePaint(bold, 7.8f, COLOR_SUB_BAND, Paint.Align.CENTER)
        )
        y += 50f

        // 2. DARK BAND: STUDENT'S PROFILE
        drawBand(canvas, inL, y, inR, 15.5f, "STUDENT'S PROFILE", bold, 7.8f)
        y += 18.5f

        val pLbl = makePaint(bold, 7.6f, COLOR_TEXT)
        val pVal = makePaint(regular, 7.6f, COLOR_TEXT)
        val lineP = Paint().apply { color = COLOR_BORDER; strokeWidth = 0.5f }
        val pRowH = 14.2f

        // Row 1: NAME
        drawUnderlinedField(canvas, inL, y, inW, "NAME : ", st.name, pLbl, pVal, lineP)
        y += pRowH
        // Row 2: CLASS, SEC, R.N.
        drawUnderlinedField(canvas, inL, y, inW * 0.36f, "CLASS : ", st.className, pLbl, pVal, lineP)
        drawUnderlinedField(canvas, inL + inW * 0.38f, y, inW * 0.28f, "SEC : ", st.section, pLbl, pVal, lineP)
        drawUnderlinedField(canvas, inL + inW * 0.68f, y, inW * 0.32f, "R.N. : ", st.rollNo.toString(), pLbl, pVal, lineP)
        y += pRowH
        // Row 3: FATHER'S NAME
        drawUnderlinedField(canvas, inL, y, inW, "FATHER'S NAME : ", st.fatherName, pLbl, pVal, lineP)
        y += pRowH
        // Row 4: ADDRESS
        drawUnderlinedField(canvas, inL, y, inW, "ADDRESS : ", st.address, pLbl, pVal, lineP)
        y += pRowH + 4f

        // 3. DARK BAND: ACADEMIC PERFORMANCE
        val perfBandTitle = when (slipReportType) {
            ReportType.HALF_YEARLY -> "ACADEMIC PERFORMANCE (TERM - I)"
            ReportType.UT1 -> "ACADEMIC PERFORMANCE (UNIT TEST 1)"
            ReportType.UT2 -> "ACADEMIC PERFORMANCE (UNIT TEST 2)"
            ReportType.ANNUAL -> "ACADEMIC PERFORMANCE"
        }
        drawBand(canvas, inL, y, inR, 15.5f, perfBandTitle, bold, 7.8f)
        y += 17.5f

        // 4. MARKS TABLE (Fixed uniform row height for 6, 7, 8 subjects so total fits cleanly)
        val subjectCount = bundle.subjects.size.coerceAtLeast(1)
        val rowH = 17.2f // 8 rows * 17.2 = 137.6 pt; always fits comfortably

        val percentageDisplay: String
        val attendanceDisplay: String

        if (isHalfYearly) {
            val cols = floatArrayOf(24f, 114f, 49f, 47f, 52f, 54f, inW - 340f)
            val headers = listOf("S.NO", "SUBJECT", "INT. ASS.\n(10)", "U.T. 1ST\n(30)", "H.Y. EXAM\n(60)", "TOTAL\n(100)", "GRADE")
            drawMultiLineTableRow(canvas, inL, y, 22f, cols, headers, COLOR_TINT, COLOR_BAND, bold, 6.4f, false)
            y += 22f

            bundle.performance.subjects.forEachIndexed { idx, sub ->
                val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
                val vals = listOf(
                    (idx + 1).toString(),
                    sub.subjectName.uppercase(),
                    ReportCalculations.displayMarkValue(sub.t1Components[MarkComponent.T1_IA]),
                    ReportCalculations.displayMarkValue(sub.t1Components[MarkComponent.T1_UT]),
                    ReportCalculations.displayMarkValue(sub.t1Components[MarkComponent.T1_EXAM]),
                    if (sub.t1HasAnyEntry) ReportCalculations.formatNumber(sub.t1Total) else "-",
                    sub.t1Grade
                )
                drawTableRow(canvas, inL, y, rowH, cols, vals, bg, COLOR_TEXT, regular, 7.2f, leftAlignCol = 1)
                y += rowH
            }

            // TOTAL row
            val totalVals = listOf(
                "",
                "TOTAL (MAX ${bundle.performance.term1Max})",
                "",
                "",
                "Result: ${bundle.performance.term1Result.label}",
                ReportCalculations.formatNumber(bundle.performance.term1Total),
                bundle.performance.term1Grade
            )
            drawTableRow(canvas, inL, y, 18.5f, cols, totalVals, COLOR_TINT, COLOR_BAND, bold, 7.2f, leftAlignCol = 1)
            y += 18.5f

            percentageDisplay = "${ReportCalculations.formatPercent(bundle.performance.term1Percentage)}%"
            attendanceDisplay = bundle.attendance.formatTerm1()
        } else {
            // UT1 or UT2 Slip
            val examType = if (isUt2) ExamType.UT2 else ExamType.UT1
            val utReport = ReportCalculations.calculateSingleExamReport(
                examType = examType,
                subjects = bundle.subjects,
                marksBySubjectAndKey = bundle.marksBySubjectAndKey,
                passPercent = settings.passPercent
            )
            val cols = floatArrayOf(28f, 166f, 72f, 58f, inW - 324f)
            val headers = listOf("S.NO", "SUBJECT", "MARKS\nOBTAINED", "MAX\n(30)", "GRADE")
            drawMultiLineTableRow(canvas, inL, y, 22f, cols, headers, COLOR_TINT, COLOR_BAND, bold, 6.8f, false)
            y += 22f

            utReport.rows.forEachIndexed { idx, row ->
                val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
                val vals = listOf(
                    (idx + 1).toString(),
                    row.subjectName.uppercase(),
                    ReportCalculations.displayMarkValue(row.rawValue),
                    row.maxMarks.toString(),
                    row.grade
                )
                drawTableRow(canvas, inL, y, rowH, cols, vals, bg, COLOR_TEXT, regular, 7.4f, leftAlignCol = 1)
                y += rowH
            }

            val totalVals = listOf(
                "",
                "TOTAL  (${utReport.resultStatus.label})",
                ReportCalculations.formatNumber(utReport.totalObtained),
                utReport.totalMax.toString(),
                utReport.grade
            )
            drawTableRow(canvas, inL, y, 18.5f, cols, totalVals, COLOR_TINT, COLOR_BAND, bold, 7.4f, leftAlignCol = 1)
            y += 18.5f

            percentageDisplay = "${ReportCalculations.formatPercent(utReport.percentage)}%"
            attendanceDisplay = if (isUt2) bundle.attendance.formatTerm2() else bundle.attendance.formatTerm1()
        }

        // Keep bottom section vertically consistent regardless of 6, 7 or 8 rows
        val tableReserveY = top + 345f
        y = maxOf(y + 6f, tableReserveY)

        // 5. PERCENTAGE & ATTENDANCE ROW
        val pctCols = floatArrayOf(inW * 0.50f, inW * 0.50f)
        drawTableRow(
            canvas,
            inL,
            y,
            18f,
            pctCols,
            listOf("PERCENTAGE :  $percentageDisplay", "ATTENDANCE :  $attendanceDisplay"),
            COLOR_ZEBRA,
            COLOR_BAND,
            bold,
            7.4f,
            leftAlignCol = -1
        )
        y += 23f

        // 6. TWO COLUMNS: Left = GRADING SCALE box | Right = ART & DISCIPLINE (H.Y. only) + REMARKS + DATE
        val colGap = 8f
        val leftW = inW * 0.46f
        val rightX = inL + leftW + colGap
        val rightW = inW - leftW - colGap

        // Left: GRADING SCALE
        var gy = y
        drawBand(canvas, inL, gy, inL + leftW, 14f, "GRADING SCALE", bold, 6.8f)
        gy += 14f
        val gCols = floatArrayOf(leftW * 0.34f, leftW * 0.66f)
        val compactScale = listOf(
            "A1" to "91 - 100",
            "A2" to "81 - 90",
            "B1" to "71 - 80",
            "B2" to "61 - 70",
            "C1" to "51 - 60",
            "C2" to "41 - 50",
            "D" to "33 - 40",
            "E" to "32 & BELOW"
        )
        compactScale.forEachIndexed { idx, (gr, range) ->
            val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
            drawTableRow(canvas, inL, gy, 12.2f, gCols, listOf(gr, range), bg, COLOR_TEXT, regular, 6.5f, leftAlignCol = -1)
            gy += 12.2f
        }

        // Right column
        var ry = y
        val termInfo = if (isUt2) bundle.term2Info else bundle.term1Info
        if (isHalfYearly) {
            val artVal = bundle.coScholasticT1[CoScholasticArea.ART_EDUCATION.code].orEmpty().ifEmpty { "-" }
            val discVal = bundle.coScholasticT1[CoScholasticArea.DISCIPLINE.code].orEmpty().ifEmpty { "-" }
            val adCols = floatArrayOf(rightW * 0.56f, rightW * 0.44f)
            drawTableRow(canvas, rightX, ry, 16f, adCols, listOf("ART", artVal), COLOR_WHITE, COLOR_TEXT, bold, 7.2f, leftAlignCol = 0)
            ry += 16f
            drawTableRow(canvas, rightX, ry, 16f, adCols, listOf("DISCIPLINE", discVal), COLOR_ZEBRA, COLOR_TEXT, bold, 7.2f, leftAlignCol = 0)
            ry += 20f
        }

        // REMARKS box
        val remBoxBottom = gy - 18f
        drawRectFillAndStroke(canvas, rightX, ry, rightX + rightW, remBoxBottom, COLOR_WHITE, COLOR_BORDER, 0.65f)
        canvas.drawText("REMARKS :", rightX + 4f, ry + 10f, makePaint(bold, 6.9f, COLOR_BAND))
        val remarksText = termInfo?.remarks.orEmpty().ifEmpty { "-" }
        drawWrappedText(
            canvas = canvas,
            text = remarksText,
            x = rightX + 4f,
            startY = ry + 20f,
            maxWidth = rightW - 8f,
            maxLines = if (isHalfYearly) 3 else 5,
            paint = makePaint(regular, 7.0f, COLOR_TEXT),
            lineHeight = 9.5f
        )

        // DATE line
        val dateText = termInfo?.reportDate.orEmpty().ifEmpty { "-" }
        drawUnderlinedField(canvas, rightX, gy - 4f, rightW, "DATE : ", dateText, makePaint(bold, 7.2f, COLOR_BAND), makePaint(regular, 7.2f, COLOR_TEXT), lineP)

        // 7. THREE SIGNATURE LINES AT BOTTOM
        drawSignatureStrip(
            canvas = canvas,
            left = inL,
            baselineY = bottom - 10f,
            width = inW,
            bold = bold,
            textSize = 6.7f,
            labels = listOf("GUARDIAN'S SIGN", "CLASS TEACHER'S SIGN", "PRINCIPAL'S SIGN"),
            lineHalfWidth = 48f
        )
    }

    // =========================================================================
    // 9B. ANNUAL (YEARLY) REPORT CARD — A4 PORTRAIT (595 x 842 pt, 8 mm margin)
    // Guaranteed single-page fit for 6, 7, and 8 subjects
    // =========================================================================
    private fun drawAnnualPortraitCard(
        canvas: Canvas,
        settings: SchoolSettingsEntity,
        bundle: StudentReportBundle,
        logo: Bitmap?,
        regular: Typeface,
        bold: Typeface
    ) {
        val left = MARGIN_8MM
        val top = MARGIN_8MM
        val right = PORTRAIT_W - MARGIN_8MM
        val bottom = PORTRAIT_H - MARGIN_8MM
        val inL = left + 8f
        val inR = right - 8f
        val inW = inR - inL // ~533.6 pt
        val centerX = PORTRAIT_W / 2f
        val st = bundle.student
        val id = bundle.schoolIdentity
        val perf = bundle.performance

        // Outer border frame
        drawRectStroke(canvas, left, top, right, bottom, COLOR_BAND, 1.8f)
        drawRectStroke(canvas, left + 3f, top + 3f, right - 3f, bottom - 3f, COLOR_BORDER, 0.6f)

        var y = top + 7f

        // 1. WIDE DARK BANNER HEADER (70 pt)
        val bannerH = 68f
        drawRectFill(canvas, inL, y, inR, y + bannerH, COLOR_BAND)

        // White logo box on left
        val logoBoxSize = 56f
        val logoRect = RectF(inL + 7f, y + 6f, inL + 7f + logoBoxSize, y + 6f + logoBoxSize)
        drawLogoOrPlaceholder(canvas, logoRect, logo, bold, 8.5f, fillWhite = true)

        // MOB & E-Mail at top-right inside banner
        val rightContactX = inR - 7f
        if (id.mobile.isNotBlank()) {
            canvas.drawText(
                "MOB : ${id.mobile}",
                rightContactX,
                y + 13f,
                makePaint(bold, 7.2f, COLOR_WHITE, Paint.Align.RIGHT)
            )
        }
        if (id.email.isNotBlank()) {
            canvas.drawText(
                "E-Mail : ${id.email}",
                rightContactX,
                y + 23f,
                makePaint(regular, 6.8f, Color.parseColor("#DCE5F0"), Paint.Align.RIGHT)
            )
        }

        // School Name & Address
        val headerTextCenterX = inL + logoBoxSize + 14f + (inW - logoBoxSize - 24f) / 2f
        canvas.drawText(
            id.schoolName.uppercase(),
            headerTextCenterX - 10f,
            y + 39f,
            makePaint(bold, 18.5f, COLOR_WHITE, Paint.Align.CENTER)
        )
        canvas.drawText(
            id.address,
            headerTextCenterX - 10f,
            y + 56f,
            makePaint(regular, 9.0f, Color.parseColor("#E6ECF2"), Paint.Align.CENTER)
        )
        y += bannerH + 8f

        // 2. SESSION LINE + CLASS / SEC / ROLL NO ROW
        canvas.drawText(
            "Report card (Session : ${st.session})",
            centerX,
            y + 11f,
            makePaint(bold, 11.2f, COLOR_BAND, Paint.Align.CENTER)
        )
        y += 18f

        val pInfoBold = makePaint(bold, 8.6f, COLOR_BAND)
        val pInfoVal = makePaint(regular, 8.6f, COLOR_TEXT)
        val lineP = Paint().apply { color = COLOR_BORDER; strokeWidth = 0.55f }

        drawUnderlinedField(canvas, inL, y + 11f, 150f, "Class : ", st.className, pInfoBold, pInfoVal, lineP)
        drawUnderlinedField(canvas, centerX - 60f, y + 11f, 120f, "Sec : ", st.section, pInfoBold, pInfoVal, lineP)
        drawUnderlinedField(canvas, inR - 140f, y + 11f, 140f, "Roll No : ", st.rollNo.toString(), pInfoBold, pInfoVal, lineP)
        y += 20f

        // 3. STUDENT'S PROFILE (Underlined Centered Heading)
        val profHeadingPaint = makePaint(bold, 9.6f, COLOR_BAND, Paint.Align.CENTER).apply {
            isUnderlineText = true
        }
        canvas.drawText("Student's Profile", centerX, y + 10f, profHeadingPaint)
        y += 16f

        val showPhotoAndAdm = settings.showPhotoAndAdmOnAnnual
        val profileWidth = if (showPhotoAndAdm) inW - 76f else inW
        val profileStartY = y
        val pGap = 15.2f

        if (showPhotoAndAdm) {
            drawUnderlinedField(canvas, inL, y + 10f, profileWidth * 0.62f, "Student's Name : ", st.name, pInfoBold, pInfoVal, lineP)
            drawUnderlinedField(canvas, inL + profileWidth * 0.65f, y + 10f, profileWidth * 0.35f, "Adm. No : ", st.admNo, pInfoBold, pInfoVal, lineP)
        } else {
            drawUnderlinedField(canvas, inL, y + 10f, profileWidth, "Student's Name : ", st.name, pInfoBold, pInfoVal, lineP)
        }
        y += pGap
        drawUnderlinedField(canvas, inL, y + 10f, profileWidth * 0.52f, "Date of Birth : ", st.dob, pInfoBold, pInfoVal, lineP)
        drawUnderlinedField(canvas, inL + profileWidth * 0.55f, y + 10f, profileWidth * 0.45f, "Mob No : ", st.mobile, pInfoBold, pInfoVal, lineP)
        y += pGap
        drawUnderlinedField(canvas, inL, y + 10f, profileWidth, "Mother's Name : ", st.motherName, pInfoBold, pInfoVal, lineP)
        y += pGap
        drawUnderlinedField(canvas, inL, y + 10f, profileWidth, "Father's Name : ", st.fatherName, pInfoBold, pInfoVal, lineP)
        y += pGap
        drawUnderlinedField(canvas, inL, y + 10f, profileWidth, "Address : ", st.address, pInfoBold, pInfoVal, lineP)
        y += pGap + 4f

        if (showPhotoAndAdm) {
            val photoRect = RectF(inR - 68f, profileStartY, inR, profileStartY + 76f)
            drawRectFillAndStroke(canvas, photoRect.left, photoRect.top, photoRect.right, photoRect.bottom, COLOR_WHITE, COLOR_BAND, 0.9f)
            val studentBmp = ImageUtils.decodeBase64ToBitmap(st.photoBase64)
            if (studentBmp != null) {
                canvas.drawBitmap(studentBmp, null, RectF(photoRect.left + 2f, photoRect.top + 2f, photoRect.right - 2f, photoRect.bottom - 2f), null)
                studentBmp.recycle()
            } else {
                canvas.drawText("PHOTO", photoRect.centerX(), photoRect.centerY() + 3f, makePaint(bold, 8.2f, COLOR_SUBTEXT, Paint.Align.CENTER))
            }
        }

        // 4. ATTENDANCE ROW (Text left + compact 3-column table right)
        val attTableW = 240f
        val attTableX = inR - attTableW
        val attCols = floatArrayOf(attTableW / 3f, attTableW / 3f, attTableW / 3f)
        canvas.drawText(
            "Attendance : (Total attendance of Students / Total working Days)",
            inL,
            y + 19f,
            makePaint(bold, 7.6f, COLOR_BAND)
        )
        drawTableRow(canvas, attTableX, y, 14f, attCols, listOf("Term-I", "Term-II", "Grand Total"), COLOR_TINT, COLOR_BAND, bold, 7.2f, -1)
        drawTableRow(
            canvas,
            attTableX,
            y + 14f,
            15.5f,
            attCols,
            listOf(bundle.attendance.formatTerm1(), bundle.attendance.formatTerm2(), bundle.attendance.formatGrandTotal()),
            COLOR_WHITE,
            COLOR_TEXT,
            bold,
            7.6f,
            -1
        )
        y += 36f

        // 5. SCHOLASTIC AREAS TABLE (Unified 10/30/60/100 for Term-I & Term-II + Grand Total 200; NO per-subject grade column)
        drawBand(canvas, inL, y, inR, 17f, "Academic Performance : Scholastic Areas (8 point scale)", bold, 8.6f)
        y += 17f

        val wSub = 115.6f
        val wIA = 41f
        val wUT = 41f
        val wEx = 44f
        val wFull = 51f // Term block = 177 pt
        val wGrand = inW - wSub - (177f * 2f) // 64 pt
        val cols = floatArrayOf(wSub, wIA, wUT, wEx, wFull, wIA, wUT, wEx, wFull, wGrand)

        // Super header row
        val superCols = floatArrayOf(wSub, 177f, 177f, wGrand)
        drawTableRow(canvas, inL, y, 15.5f, superCols, listOf("SUBJECT", "TERM - I (100)", "TERM - II (100)", "OVERALL"), COLOR_SUB_BAND, COLOR_WHITE, bold, 7.6f, -1)
        y += 15.5f

        // Sub header row
        val subHeaders = listOf(
            "Subject",
            "Internal\nAss. (10)", "U.T. 1\n(30)", "H.Y. Exam\n(60)", "FULL MARK'S\n(100)",
            "Internal\nAss. (10)", "U.T. 2\n(30)", "Yearly Exam\n(60)", "FULL MARK'S\n(100)",
            "Grand Total\n(200)"
        )
        drawMultiLineTableRow(canvas, inL, y, 25f, cols, subHeaders, COLOR_TINT, COLOR_BAND, bold, 6.7f, false)
        y += 25f

        // Subject rows (fixed heights so 6, 7, and 8 subjects always fit one page cleanly)
        val rowH = when {
            perf.subjects.size >= 8 -> 19.5f
            perf.subjects.size == 7 -> 21.5f
            else -> 23.5f
        }
        perf.subjects.forEachIndexed { idx, sub ->
            val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
            val vals = listOf(
                sub.subjectName.uppercase(),
                ReportCalculations.displayMarkValue(sub.t1Components[MarkComponent.T1_IA]),
                ReportCalculations.displayMarkValue(sub.t1Components[MarkComponent.T1_UT]),
                ReportCalculations.displayMarkValue(sub.t1Components[MarkComponent.T1_EXAM]),
                if (sub.t1HasAnyEntry) ReportCalculations.formatNumber(sub.t1Total) else "-",
                ReportCalculations.displayMarkValue(sub.t2Components[MarkComponent.T2_IA]),
                ReportCalculations.displayMarkValue(sub.t2Components[MarkComponent.T2_UT]),
                ReportCalculations.displayMarkValue(sub.t2Components[MarkComponent.T2_EXAM]),
                if (sub.t2HasAnyEntry) ReportCalculations.formatNumber(sub.t2Total) else "-",
                if (sub.t1HasAnyEntry || sub.t2HasAnyEntry) ReportCalculations.formatNumber(sub.grandTotal) else "-"
            )
            drawTableRow(canvas, inL, y, rowH, cols, vals, bg, COLOR_TEXT, regular, 7.6f, leftAlignCol = 0)
            y += rowH
        }

        // Term Totals row under each term's Full Marks
        val totalVals = listOf(
            "Total :",
            "", "", "", "${ReportCalculations.formatNumber(perf.term1Total)} / ${perf.term1Max}",
            "", "", "", "${ReportCalculations.formatNumber(perf.term2Total)} / ${perf.term2Max}",
            "${ReportCalculations.formatNumber(perf.overallTotal)} / ${perf.overallMax}"
        )
        drawTableRow(canvas, inL, y, 19.5f, cols, totalVals, COLOR_TINT, COLOR_BAND, bold, 7.4f, leftAlignCol = 0)
        y += 22.5f

        // Overall Total + Percentage + Grade + Result (+ optional Rank) strip
        val sumH = 24f
        drawRectFillAndStroke(canvas, inL, y, inR, y + sumH, COLOR_ZEBRA, COLOR_BORDER, 0.7f)
        val overallSummary = buildString {
            append("Overall Total : ${ReportCalculations.formatNumber(perf.overallTotal)} / ${perf.overallMax}")
            append("    |    Percentage : ${ReportCalculations.formatPercent(perf.overallPercentage)}%")
            append("    |    Grade : ${perf.overallGrade}")
            append("    |    Result : ${perf.overallResult.label}")
            if (settings.showClassRank) {
                val rStr = perf.classRank?.let { "#$it" } ?: "-"
                append("    |    Rank : $rStr")
            }
        }
        canvas.drawText(
            overallSummary,
            centerX,
            y + 15f,
            makePaint(bold, 8.2f, COLOR_BAND, Paint.Align.CENTER)
        )
        y += sumH + 9f

        // 6. CO-SCHOLASTIC AREAS + BORDERED MARKS RANGE / GRADE TABLE SIDE BY SIDE
        drawBand(canvas, inL, y, inR, 16.5f, "Co - Scholastic Areas [ on a 3-point (A-C) grading scale]", bold, 8.2f)
        y += 18.5f

        val gap = 10f
        val leftW = inW * 0.56f
        val rightX = inL + leftW + gap
        val rightW = inW - leftW - gap

        // Left: Co-Scholastic 5 areas (Term - I, Term - II)
        var ly = y
        val coCols = floatArrayOf(leftW * 0.64f, leftW * 0.18f, leftW * 0.18f)
        drawTableRow(canvas, inL, ly, 16f, coCols, listOf("Activity / Area", "Term - I", "Term - II"), COLOR_TINT, COLOR_BAND, bold, 7.4f, leftAlignCol = 0)
        ly += 16f
        CoScholasticArea.entries.forEachIndexed { idx, area ->
            val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
            val g1 = bundle.coScholasticT1[area.code].orEmpty().ifEmpty { "-" }
            val g2 = bundle.coScholasticT2[area.code].orEmpty().ifEmpty { "-" }
            drawTableRow(canvas, inL, ly, 19.8f, coCols, listOf(area.label, g1, g2), bg, COLOR_TEXT, regular, 7.2f, leftAlignCol = 0)
            ly += 19.8f
        }

        // Right: Bordered Marks Range / Grade table
        var ry = y
        val scaleCols = floatArrayOf(rightW * 0.40f, rightW * 0.60f)
        drawTableRow(canvas, rightX, ry, 16f, scaleCols, listOf("Marks Range", "Grade"), COLOR_TINT, COLOR_BAND, bold, 7.4f, -1)
        ry += 16f
        val annualScale = listOf(
            "91-100" to "A1",
            "81-90" to "A2",
            "71-80" to "B1",
            "61-70" to "B2",
            "51-60" to "C1",
            "41-50" to "C2",
            "33-40" to "D",
            "32&Below" to "E (Need improvement)"
        )
        annualScale.forEachIndexed { idx, (rng, grd) ->
            val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
            drawTableRow(canvas, rightX, ry, 12.4f, scaleCols, listOf(rng, grd), bg, COLOR_TEXT, regular, 6.8f, -1)
            ry += 12.4f
        }

        y = maxOf(ly, ry) + 12f

        // 7. FOOTER: Remarks, Promoted to Class, Date (dd/mm/yy), and 3 Signatures
        val remarksText = bundle.term2Info?.remarks?.ifBlank { bundle.term1Info?.remarks.orEmpty() }.orEmpty()
        val promotedTo = bundle.term2Info?.promotedTo.orEmpty()
        val dateText = bundle.term2Info?.reportDate?.ifBlank { bundle.term1Info?.reportDate.orEmpty() }.orEmpty()

        drawUnderlinedField(canvas, inL, y + 10f, inW, "Class Teacher Remarks : ", remarksText, pInfoBold, pInfoVal, lineP)
        y += 20f
        drawUnderlinedField(canvas, inL, y + 10f, inW * 0.60f, "Promoted to Class : ", promotedTo, pInfoBold, pInfoVal, lineP)
        drawUnderlinedField(canvas, inL + inW * 0.64f, y + 10f, inW * 0.36f, "Date : ", dateText, pInfoBold, pInfoVal, lineP)

        drawSignatureStrip(
            canvas = canvas,
            left = inL,
            baselineY = bottom - 15f,
            width = inW,
            bold = bold,
            textSize = 8.2f,
            labels = listOf("Guardian's Sign", "Class Teacher's Sign", "Principal's Sign"),
            lineHalfWidth = 60f
        )
    }

    // =========================================================================
    // SHARED VECTOR DRAWING HELPERS
    // =========================================================================
    private fun drawLogoOrPlaceholder(
        canvas: Canvas,
        rect: RectF,
        logo: Bitmap?,
        bold: Typeface,
        placeholderSize: Float,
        fillWhite: Boolean = false
    ) {
        if (fillWhite) {
            drawRectFillAndStroke(canvas, rect.left, rect.top, rect.right, rect.bottom, COLOR_WHITE, COLOR_BORDER, 0.7f)
        } else {
            drawRectStroke(canvas, rect.left, rect.top, rect.right, rect.bottom, COLOR_BORDER, 0.6f)
        }
        if (logo != null) {
            val pad = 2f
            canvas.drawBitmap(logo, null, RectF(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad), null)
        } else {
            canvas.drawText(
                "LOGO",
                rect.centerX(),
                rect.centerY() + placeholderSize / 3f,
                makePaint(bold, placeholderSize, COLOR_SUBTEXT, Paint.Align.CENTER)
            )
        }
    }

    private fun drawBand(canvas: Canvas, left: Float, top: Float, right: Float, height: Float, title: String, bold: Typeface, textSize: Float) {
        drawRectFill(canvas, left, top, right, top + height, COLOR_BAND)
        val p = makePaint(bold, textSize, COLOR_WHITE, Paint.Align.CENTER)
        val fm = p.fontMetrics
        val baseY = top + (height - (fm.descent + fm.ascent)) / 2f
        canvas.drawText(title, (left + right) / 2f, baseY, p)
    }

    private fun drawUnderlinedField(
        canvas: Canvas,
        x: Float,
        baselineY: Float,
        totalWidth: Float,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint,
        linePaint: Paint
    ) {
        canvas.drawText(label, x, baselineY, labelPaint)
        val lw = labelPaint.measureText(label)
        val valX = x + lw + 2f
        val availW = (totalWidth - lw - 4f).coerceAtLeast(16f)
        if (value.isNotBlank()) {
            canvas.drawText(ellipsize(value, valuePaint, availW), valX, baselineY, valuePaint)
        }
        canvas.drawLine(valX, baselineY + 2.2f, x + totalWidth, baselineY + 2.2f, linePaint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        maxLines: Int,
        paint: Paint,
        lineHeight: Float
    ) {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = ""
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = w
                if (lines.size >= maxLines - 1) break
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current)

        lines.take(maxLines).forEachIndexed { idx, line ->
            canvas.drawText(ellipsize(line, paint, maxWidth), x, startY + idx * lineHeight, paint)
        }
    }

    private fun drawSignatureStrip(
        canvas: Canvas,
        left: Float,
        baselineY: Float,
        width: Float,
        bold: Typeface,
        textSize: Float,
        labels: List<String>,
        lineHalfWidth: Float
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BAND
            strokeWidth = 0.7f
        }
        val labelPaint = makePaint(bold, textSize, COLOR_BAND, Paint.Align.CENTER)
        val slotW = width / labels.size.toFloat()
        labels.forEachIndexed { idx, label ->
            val cx = left + slotW * idx + slotW / 2f
            canvas.drawLine(cx - lineHalfWidth, baselineY - 11f, cx + lineHalfWidth, baselineY - 11f, linePaint)
            canvas.drawText(label, cx, baselineY, labelPaint)
        }
    }

    private fun drawTableRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        rowH: Float,
        colWidths: FloatArray,
        values: List<String>,
        bgColor: Int,
        textColor: Int,
        typeface: Typeface,
        textSize: Float,
        leftAlignCol: Int
    ) {
        val fillPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val textPaint = makePaint(typeface, textSize, textColor)
        val fm = textPaint.fontMetrics
        val baselineOffset = (rowH - (fm.descent + fm.ascent)) / 2f

        var cx = x
        colWidths.forEachIndexed { idx, w ->
            canvas.drawRect(cx, y, cx + w, y + rowH, fillPaint)
            canvas.drawRect(cx, y, cx + w, y + rowH, strokePaint)
            val raw = values.getOrElse(idx) { "" }
            if (raw.isNotEmpty()) {
                val safe = ellipsize(raw, textPaint, (w - 5f).coerceAtLeast(8f))
                if (idx == leftAlignCol) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(safe, cx + 3.5f, y + baselineOffset, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(safe, cx + w / 2f, y + baselineOffset, textPaint)
                }
            }
            cx += w
        }
    }

    private fun drawMultiLineTableRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        rowH: Float,
        colWidths: FloatArray,
        values: List<String>,
        bgColor: Int,
        textColor: Int,
        typeface: Typeface,
        textSize: Float,
        firstColLeftAlign: Boolean
    ) {
        val fillPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val textPaint = makePaint(typeface, textSize, textColor)
        var cx = x
        colWidths.forEachIndexed { idx, w ->
            canvas.drawRect(cx, y, cx + w, y + rowH, fillPaint)
            canvas.drawRect(cx, y, cx + w, y + rowH, strokePaint)
            val lines = values.getOrElse(idx) { "" }.split("\n")
            val lineH = textSize + 1.8f
            val totalH = lines.size * lineH
            var ly = y + (rowH - totalH) / 2f + textSize
            lines.forEach { line ->
                val safe = ellipsize(line, textPaint, (w - 4f).coerceAtLeast(6f))
                if (idx == 0 && firstColLeftAlign) {
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(safe, cx + 3.5f, ly, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(safe, cx + w / 2f, ly, textPaint)
                }
                ly += lineH
            }
            cx += w
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellW = paint.measureText(ellipsis)
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + ellW > maxWidth) end--
        return text.substring(0, end) + ellipsis
    }

    private fun makePaint(
        typeface: Typeface,
        size: Float,
        colorInt: Int,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        color = colorInt
        textAlign = align
    }

    private fun drawRectFill(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, colorInt: Int) {
        canvas.drawRect(l, t, r, b, Paint().apply { color = colorInt; style = Paint.Style.FILL })
    }

    private fun drawRectStroke(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, colorInt: Int, width: Float) {
        canvas.drawRect(l, t, r, b, Paint().apply { color = colorInt; style = Paint.Style.STROKE; strokeWidth = width })
    }

    private fun drawRectFillAndStroke(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, fill: Int, stroke: Int, width: Float) {
        drawRectFill(canvas, l, t, r, b, fill)
        drawRectStroke(canvas, l, t, r, b, stroke, width)
    }
}
