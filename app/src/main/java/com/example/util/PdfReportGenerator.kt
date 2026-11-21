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
 *
 * Typography & zero-overlap guarantees:
 * - Uses standard system sans-serif font family (native Regular + native Bold weights, no faux bold,
 *   no subset TTF metric mismatch) with built-in Unicode / Devanagari fallback.
 * - Shapes text at 4x high-precision vector scale with LINEAR_TEXT_FLAG, SUBPIXEL_TEXT_FLAG,
 *   and HINTING_OFF to eliminate Android 72-DPI Skia PDF character-width quantization bugs.
 * - Applies explicit positive letter spacing (+0.04em, ~0.3pt) and 1.45x line-height across all
 *   headings, labels, profile fields, and table cells with dynamic minimum row heights.
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

    // High-DPI vector text shaping constants (eliminates 72-DPI Android PdfDocument letter overlap)
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.04f       // ~0.3pt positive tracking between every glyph
    private const val LINE_HEIGHT_MULTIPLIER = 1.45f  // 1.45x vertical line-height across all text

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
        val (baseTypeface, boldTypeface) = loadReportTypefaces()
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
        val (baseTypeface, boldTypeface) = loadReportTypefaces()
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

    /**
     * Uses Android's standard system sans-serif family (true static Regular + true static Bold weights
     * with native Devanagari fallback) to prevent custom subset TTF / faux-bold glyph width collisions
     * in Skia PDF and PDFium renderers.
     */
    private fun loadReportTypefaces(): Pair<Typeface, Typeface> {
        val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        return regular to bold
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
        val pad = 8f
        val inL = left + pad
        val inR = right - pad
        val inW = inR - inL // 380 pt
        val st = bundle.student
        val id = bundle.schoolIdentity
        val isHalfYearly = slipReportType == ReportType.HALF_YEARLY
        val isUt2 = slipReportType == ReportType.UT2

        // Crisp outer frame
        drawRectStroke(canvas, left, top, right, bottom, COLOR_BAND, 1.3f)

        var y = top + 6f

        // 1. SINGLE CLEAN SCHOOL HEADER (generous 1.45x vertical spacing between every line)
        val headerH = 58f
        val logoSize = 44f
        val logoRect = RectF(inL, y + 7f, inL + logoSize, y + 7f + logoSize)
        drawLogoOrPlaceholder(canvas, logoRect, logo, bold, 7.2f)

        val textAreaLeft = inL + logoSize + 8f
        val textAreaW = inR - textAreaLeft
        val textAreaCenter = textAreaLeft + textAreaW / 2f

        val mobPaint = makePaint(bold, 6.2f, COLOR_SUBTEXT, Paint.Align.RIGHT)
        val titlePaint = makePaint(bold, 12.0f, COLOR_BAND, Paint.Align.CENTER)
        val addrPaint = makePaint(regular, 7.0f, COLOR_TEXT, Paint.Align.CENTER)
        val sessPaint = makePaint(bold, 7.4f, COLOR_SUB_BAND, Paint.Align.CENTER)

        if (id.mobile.isNotBlank()) {
            drawTextPt(canvas, ellipsize("Mob. ${id.mobile}", mobPaint, textAreaW), inR, y + 8f, mobPaint)
        }
        drawTextPt(canvas, ellipsize(id.schoolName.uppercase(), titlePaint, textAreaW), textAreaCenter, y + 25.5f, titlePaint)
        drawTextPt(canvas, ellipsize(id.address, addrPaint, textAreaW), textAreaCenter, y + 39f, addrPaint)
        val formattedSession = st.session.replace("-", " - ")
        drawTextPt(canvas, "SESSION : $formattedSession", textAreaCenter, y + 52f, sessPaint)
        y += headerH + 6f

        // 2. STUDENT'S PROFILE (Header band strictly above bordered details box with natural row expansion)
        y += drawBand(canvas, inL, y, inR, 16.5f, "STUDENT'S PROFILE", bold, 7.6f)

        val pLbl = makePaint(bold, 7.1f, COLOR_BAND)
        val pVal = makePaint(regular, 7.2f, COLOR_TEXT)
        val pPadX = 6f
        val pPadTop = 5f
        val pPadBottom = 5f
        val pRowH = maxOf(15.8f, lineHeightPt(pVal) + 4.5f)
        val pBoxH = pPadTop + (pRowH * 4f) + pPadBottom

        drawRectFillAndStroke(canvas, inL, y, inR, y + pBoxH, COLOR_WHITE, COLOR_BORDER, 0.65f)

        val fieldL = inL + pPadX
        val fieldW = inW - (pPadX * 2f)
        var py = y + pPadTop

        // Row 1: NAME
        drawPlainField(canvas, fieldL, centerBaselineY(py, pRowH, pVal), fieldW, "NAME : ", st.name, pLbl, pVal)
        py += pRowH
        // Row 2: CLASS, SEC, R.N. (explicit 8pt column gutters)
        val colGapSmall = 8f
        val wClass = (fieldW - colGapSmall * 2f) * 0.38f
        val wSec = (fieldW - colGapSmall * 2f) * 0.28f
        val wRoll = (fieldW - colGapSmall * 2f) * 0.34f
        val row2Base = centerBaselineY(py, pRowH, pVal)
        drawPlainField(canvas, fieldL, row2Base, wClass, "CLASS : ", st.className, pLbl, pVal)
        drawPlainField(canvas, fieldL + wClass + colGapSmall, row2Base, wSec, "SEC : ", st.section, pLbl, pVal)
        drawPlainField(canvas, fieldL + wClass + wSec + colGapSmall * 2f, row2Base, wRoll, "R.N. : ", st.rollNo.toString(), pLbl, pVal)
        py += pRowH
        // Row 3: FATHER'S NAME
        drawPlainField(canvas, fieldL, centerBaselineY(py, pRowH, pVal), fieldW, "FATHER'S NAME : ", st.fatherName, pLbl, pVal)
        py += pRowH
        // Row 4: ADDRESS
        drawPlainField(canvas, fieldL, centerBaselineY(py, pRowH, pVal), fieldW, "ADDRESS : ", st.address, pLbl, pVal)

        y += pBoxH + 6f

        // 3. ACADEMIC PERFORMANCE BAND
        val perfBandTitle = when (slipReportType) {
            ReportType.HALF_YEARLY -> "ACADEMIC PERFORMANCE (TERM - I)"
            ReportType.UT1 -> "ACADEMIC PERFORMANCE (UNIT TEST 1)"
            ReportType.UT2 -> "ACADEMIC PERFORMANCE (UNIT TEST 2)"
            ReportType.ANNUAL -> "ACADEMIC PERFORMANCE"
        }
        y += drawBand(canvas, inL, y, inR, 16f, perfBandTitle, bold, 7.6f)

        // 4. MARKS TABLE (Dynamic minimum row heights, balanced columns, 1.45x line spacing)
        val subjectCount = bundle.subjects.size.coerceAtLeast(1)
        val minRowH = when {
            subjectCount >= 8 -> 15.8f
            subjectCount == 7 -> 17.2f
            else -> 18.6f
        }

        val percentageDisplay: String
        val attendanceDisplay: String

        if (isHalfYearly) {
            val cols = floatArrayOf(28f, 108f, 49f, 49f, 52f, 52f, inW - 338f)
            val headers = listOf("S.NO", "SUBJECT", "INT. ASS.\n(10)", "U.T. 1ST\n(30)", "H.Y. EXAM\n(60)", "TOTAL\n(100)", "GRADE")
            y += drawMultiLineTableRow(canvas, inL, y, 24.5f, cols, headers, COLOR_TINT, COLOR_BAND, bold, 6.1f, false)

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
                y += drawTableRow(canvas, inL, y, minRowH, cols, vals, bg, COLOR_TEXT, regular, 6.9f, leftAlignCol = 1)
            }

            // Spanned TOTAL row (Result spans the 3 component columns cleanly)
            val totalCols = floatArrayOf(cols[0] + cols[1], cols[2] + cols[3] + cols[4], cols[5], cols[6])
            val totalVals = listOf(
                "TOTAL (MAX ${bundle.performance.term1Max})",
                "RESULT : ${bundle.performance.term1Result.label}",
                ReportCalculations.formatNumber(bundle.performance.term1Total),
                bundle.performance.term1Grade
            )
            y += drawTableRow(canvas, inL, y, 18.5f, totalCols, totalVals, COLOR_TINT, COLOR_BAND, bold, 6.9f, leftAlignCol = 0)

            percentageDisplay = "${ReportCalculations.formatPercent(bundle.performance.term1Percentage)}%"
            attendanceDisplay = bundle.attendance.formatTerm1()
        } else {
            val examType = if (isUt2) ExamType.UT2 else ExamType.UT1
            val utReport = ReportCalculations.calculateSingleExamReport(
                examType = examType,
                subjects = bundle.subjects,
                marksBySubjectAndKey = bundle.marksBySubjectAndKey,
                passPercent = settings.passPercent
            )
            val cols = floatArrayOf(28f, 166f, 72f, 58f, inW - 324f)
            val headers = listOf("S.NO", "SUBJECT", "MARKS\nOBTAINED", "MAX\n(30)", "GRADE")
            y += drawMultiLineTableRow(canvas, inL, y, 24.5f, cols, headers, COLOR_TINT, COLOR_BAND, bold, 6.5f, false)

            utReport.rows.forEachIndexed { idx, row ->
                val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
                val vals = listOf(
                    (idx + 1).toString(),
                    row.subjectName.uppercase(),
                    ReportCalculations.displayMarkValue(row.rawValue),
                    row.maxMarks.toString(),
                    row.grade
                )
                y += drawTableRow(canvas, inL, y, minRowH, cols, vals, bg, COLOR_TEXT, regular, 7.1f, leftAlignCol = 1)
            }

            val totalCols = floatArrayOf(cols[0] + cols[1], cols[2], cols[3], cols[4])
            val totalVals = listOf(
                "TOTAL  (${utReport.resultStatus.label})",
                ReportCalculations.formatNumber(utReport.totalObtained),
                utReport.totalMax.toString(),
                utReport.grade
            )
            y += drawTableRow(canvas, inL, y, 18.5f, totalCols, totalVals, COLOR_TINT, COLOR_BAND, bold, 7.0f, leftAlignCol = 0)

            percentageDisplay = "${ReportCalculations.formatPercent(utReport.percentage)}%"
            attendanceDisplay = if (isUt2) bundle.attendance.formatTerm2() else bundle.attendance.formatTerm1()
        }

        // Keep bottom section consistently spaced regardless of 6, 7 or 8 rows
        y = maxOf(y + 6f, top + 358f)

        // 5. PERCENTAGE & ATTENDANCE ROW
        val pctCols = floatArrayOf(inW * 0.50f, inW * 0.50f)
        y += drawTableRow(
            canvas,
            inL,
            y,
            18.5f,
            pctCols,
            listOf("PERCENTAGE :  $percentageDisplay", "ATTENDANCE :  $attendanceDisplay"),
            COLOR_ZEBRA,
            COLOR_BAND,
            bold,
            7.1f,
            leftAlignCol = -1
        ) + 6f

        // 6. TWO ALIGNED COLUMNS: Left = GRADING SCALE | Right = ART/DISCIPLINE (H.Y. only) + REMARKS + DATE
        val colGap = 10f
        val leftW = inW * 0.45f
        val rightX = inL + leftW + colGap
        val rightW = inW - leftW - colGap

        // Left: GRADING SCALE table
        var gy = y
        gy += drawBand(canvas, inL, gy, inL + leftW, 14.5f, "GRADING SCALE", bold, 6.6f)
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
            gy += drawTableRow(canvas, inL, gy, 12.8f, gCols, listOf(gr, range), bg, COLOR_TEXT, regular, 6.2f, leftAlignCol = -1)
        }

        // Right column aligned precisely from y down to gy
        var ry = y
        val termInfo = if (isUt2) bundle.term2Info else bundle.term1Info
        if (isHalfYearly) {
            val artVal = bundle.coScholasticT1[CoScholasticArea.ART_EDUCATION.code].orEmpty().ifEmpty { "-" }
            val discVal = bundle.coScholasticT1[CoScholasticArea.DISCIPLINE.code].orEmpty().ifEmpty { "-" }
            val adCols = floatArrayOf(rightW * 0.58f, rightW * 0.42f)
            ry += drawTableRow(canvas, rightX, ry, 16.5f, adCols, listOf("ART", artVal), COLOR_WHITE, COLOR_TEXT, bold, 6.9f, leftAlignCol = 0)
            ry += drawTableRow(canvas, rightX, ry, 16.5f, adCols, listOf("DISCIPLINE", discVal), COLOR_ZEBRA, COLOR_TEXT, bold, 6.9f, leftAlignCol = 0) + 5.5f
        }

        // REMARKS box (leaves clean 19pt band at bottom for DATE aligned with gy)
        val remBoxBottom = maxOf(ry + 36f, gy - 19.5f)
        drawRectFillAndStroke(canvas, rightX, ry, rightX + rightW, remBoxBottom, COLOR_WHITE, COLOR_BORDER, 0.65f)
        val remLblPaint = makePaint(bold, 6.7f, COLOR_BAND)
        val remBodyPaint = makePaint(regular, 6.8f, COLOR_TEXT)
        drawTextPt(canvas, "REMARKS :", rightX + 6f, ry + 11f, remLblPaint)
        val remarksText = termInfo?.remarks.orEmpty().trim()
        if (remarksText.isNotEmpty()) {
            drawWrappedText(
                canvas = canvas,
                text = remarksText,
                x = rightX + 6f,
                startY = ry + 22.5f,
                maxWidth = rightW - 12f,
                maxLines = if (isHalfYearly) 3 else 5,
                paint = remBodyPaint,
                lineHeight = 6.8f * LINE_HEIGHT_MULTIPLIER
            )
        }

        // DATE (plain printed text, no blank underline/dashes)
        val dateText = termInfo?.reportDate.orEmpty().trim()
        drawPlainField(
            canvas = canvas,
            x = rightX,
            baselineY = gy - 4f,
            totalWidth = rightW,
            label = "DATE : ",
            value = dateText,
            labelPaint = makePaint(bold, 7.0f, COLOR_BAND),
            valuePaint = makePaint(regular, 7.0f, COLOR_TEXT)
        )

        // 7. THREE EQUALLY SPACED SIGNATURE BLOCKS AT BOTTOM
        drawSignatureStrip(
            canvas = canvas,
            left = inL,
            baselineY = bottom - 10f,
            width = inW,
            bold = bold,
            textSize = 6.4f,
            labels = listOf("GUARDIAN'S SIGN", "CLASS TEACHER'S SIGN", "PRINCIPAL'S SIGN"),
            lineHalfWidth = 46f
        )
    }

    // =========================================================================
    // 9B. ANNUAL (YEARLY) REPORT CARD — A4 PORTRAIT (595 x 842 pt, 8 mm margin)
    // Guaranteed single-page fit for 6, 7, and 8 subjects with zero letter overlap
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
        val inL = left + 9f
        val inR = right - 9f
        val inW = inR - inL // ~531.6 pt
        val centerX = PORTRAIT_W / 2f
        val st = bundle.student
        val id = bundle.schoolIdentity
        val perf = bundle.performance

        // Outer crisp double border frame
        drawRectStroke(canvas, left, top, right, bottom, COLOR_BAND, 1.8f)
        drawRectStroke(canvas, left + 3f, top + 3f, right - 3f, bottom - 3f, COLOR_BORDER, 0.6f)

        var y = top + 8f

        // 1. SINGLE CLEAN SCHOOL HEADER BANNER (78 pt — generous 1.45x spacing between every line)
        val bannerH = 78f
        drawRectFill(canvas, inL, y, inR, y + bannerH, COLOR_BAND)

        val logoBoxSize = 60f
        val logoRect = RectF(inL + 8f, y + 9f, inL + 8f + logoBoxSize, y + 9f + logoBoxSize)
        drawLogoOrPlaceholder(canvas, logoRect, logo, bold, 8.2f, fillWhite = true)

        val bannerTextLeft = inL + 8f + logoBoxSize + 10f
        val bannerTextW = inR - 8f - bannerTextLeft
        val bannerTextCenterX = bannerTextLeft + bannerTextW / 2f

        val contactPaint = makePaint(regular, 6.6f, Color.parseColor("#DCE5F0"), Paint.Align.RIGHT)
        val schoolTitlePaint = makePaint(bold, 16.2f, COLOR_WHITE, Paint.Align.CENTER)
        val schoolAddrPaint = makePaint(regular, 8.4f, Color.parseColor("#E6ECF2"), Paint.Align.CENTER)
        val annualSubPaint = makePaint(bold, 8.2f, Color.parseColor("#D5E4F5"), Paint.Align.CENTER)

        val contactParts = buildList {
            if (id.mobile.isNotBlank()) add("MOB : ${id.mobile}")
            if (id.email.isNotBlank()) add("E-Mail : ${id.email}")
        }
        if (contactParts.isNotEmpty()) {
            drawTextPt(
                canvas,
                ellipsize(contactParts.joinToString("   |   "), contactPaint, bannerTextW),
                inR - 8f,
                y + 13f,
                contactPaint
            )
        }

        drawTextPt(canvas, ellipsize(id.schoolName.uppercase(), schoolTitlePaint, bannerTextW), bannerTextCenterX, y + 37.5f, schoolTitlePaint)
        drawTextPt(canvas, ellipsize(id.address, schoolAddrPaint, bannerTextW), bannerTextCenterX, y + 54.5f, schoolAddrPaint)
        drawTextPt(canvas, "ANNUAL REPORT CARD   •   SESSION : ${st.session}", bannerTextCenterX, y + 69.5f, annualSubPaint)
        y += bannerH + 8f

        // 2. ONE CLEAN STUDENT'S PROFILE SECTION (Heading band strictly above bordered details box)
        y += drawBand(canvas, inL, y, inR, 17f, "STUDENT'S PROFILE", bold, 8.3f)

        val pInfoBold = makePaint(bold, 7.9f, COLOR_BAND)
        val pInfoVal = makePaint(regular, 8.0f, COLOR_TEXT)

        val showPhotoAndAdm = settings.showPhotoAndAdmOnAnnual
        val profPadX = 8f
        val profPadTop = 5f
        val profPadBottom = 5f
        val pRowH = maxOf(16.2f, lineHeightPt(pInfoVal) + 4.5f)
        val profBoxH = profPadTop + (pRowH * 6f) + profPadBottom

        drawRectFillAndStroke(canvas, inL, y, inR, y + profBoxH, COLOR_WHITE, COLOR_BORDER, 0.7f)

        val fieldL = inL + profPadX
        val photoBoxW = 68f
        val photoBoxH = 82f
        val profileWidth = if (showPhotoAndAdm) (inW - profPadX * 2f - photoBoxW - 10f) else (inW - profPadX * 2f)
        var py = y + profPadTop

        // Row 1: Student's Name (+ Adm. No. only when toggle enabled)
        val r1Base = centerBaselineY(py, pRowH, pInfoVal)
        if (showPhotoAndAdm) {
            val colGap = 10f
            val wName = (profileWidth - colGap) * 0.64f
            val wAdm = (profileWidth - colGap) * 0.36f
            drawPlainField(canvas, fieldL, r1Base, wName, "STUDENT'S NAME : ", st.name, pInfoBold, pInfoVal)
            drawPlainField(canvas, fieldL + wName + colGap, r1Base, wAdm, "ADM. NO. : ", st.admNo, pInfoBold, pInfoVal)
        } else {
            drawPlainField(canvas, fieldL, r1Base, profileWidth, "STUDENT'S NAME : ", st.name, pInfoBold, pInfoVal)
        }
        py += pRowH

        // Row 2: Class, Sec, Roll No
        val triGap = 12f
        val wCls = (profileWidth - triGap * 2f) * 0.36f
        val wSec = (profileWidth - triGap * 2f) * 0.28f
        val wRoll = (profileWidth - triGap * 2f) * 0.36f
        val r2Base = centerBaselineY(py, pRowH, pInfoVal)
        drawPlainField(canvas, fieldL, r2Base, wCls, "CLASS : ", st.className, pInfoBold, pInfoVal)
        drawPlainField(canvas, fieldL + wCls + triGap, r2Base, wSec, "SEC : ", st.section, pInfoBold, pInfoVal)
        drawPlainField(canvas, fieldL + wCls + wSec + triGap * 2f, r2Base, wRoll, "ROLL NO. : ", st.rollNo.toString(), pInfoBold, pInfoVal)
        py += pRowH

        // Row 3: Date of Birth & Mobile No
        val duoGap = 12f
        val wDob = (profileWidth - duoGap) * 0.52f
        val wMob = (profileWidth - duoGap) * 0.48f
        val r3Base = centerBaselineY(py, pRowH, pInfoVal)
        drawPlainField(canvas, fieldL, r3Base, wDob, "DATE OF BIRTH : ", st.dob, pInfoBold, pInfoVal)
        drawPlainField(canvas, fieldL + wDob + duoGap, r3Base, wMob, "MOB. NO. : ", st.mobile, pInfoBold, pInfoVal)
        py += pRowH

        // Row 4: Mother's Name
        drawPlainField(canvas, fieldL, centerBaselineY(py, pRowH, pInfoVal), profileWidth, "MOTHER'S NAME : ", st.motherName, pInfoBold, pInfoVal)
        py += pRowH

        // Row 5: Father's Name
        drawPlainField(canvas, fieldL, centerBaselineY(py, pRowH, pInfoVal), profileWidth, "FATHER'S NAME : ", st.fatherName, pInfoBold, pInfoVal)
        py += pRowH

        // Row 6: Address
        drawPlainField(canvas, fieldL, centerBaselineY(py, pRowH, pInfoVal), profileWidth, "ADDRESS : ", st.address, pInfoBold, pInfoVal)

        if (showPhotoAndAdm) {
            val photoTop = y + (profBoxH - photoBoxH) / 2f
            val photoRect = RectF(inR - profPadX - photoBoxW, photoTop, inR - profPadX, photoTop + photoBoxH)
            drawRectFillAndStroke(canvas, photoRect.left, photoRect.top, photoRect.right, photoRect.bottom, COLOR_WHITE, COLOR_BAND, 0.9f)
            val studentBmp = ImageUtils.decodeBase64ToBitmap(st.photoBase64)
            if (studentBmp != null) {
                canvas.drawBitmap(studentBmp, null, RectF(photoRect.left + 2f, photoRect.top + 2f, photoRect.right - 2f, photoRect.bottom - 2f), null)
                studentBmp.recycle()
            } else {
                val phPaint = makePaint(bold, 7.8f, COLOR_SUBTEXT, Paint.Align.CENTER)
                drawTextPt(canvas, "PHOTO", photoRect.centerX(), centerBaselineY(photoRect.top, photoRect.height(), phPaint), phPaint)
            }
        }

        y += profBoxH + 8f

        // 3. ATTENDANCE ROW (Cleanly aligned label left + compact 3-column table right)
        val attTableW = 246f
        val attTableX = inR - attTableW
        val attCols = floatArrayOf(attTableW / 3f, attTableW / 3f, attTableW / 3f)
        val attLabelMaxW = attTableX - inL - 8f
        val attLabelPaint = makePaint(bold, 7.4f, COLOR_BAND)
        val attH1 = drawTableRow(canvas, attTableX, y, 15.5f, attCols, listOf("Term-I", "Term-II", "Grand Total"), COLOR_TINT, COLOR_BAND, bold, 7.0f, -1)
        val attH2 = drawTableRow(
            canvas,
            attTableX,
            y + attH1,
            17.0f,
            attCols,
            listOf(bundle.attendance.formatTerm1(), bundle.attendance.formatTerm2(), bundle.attendance.formatGrandTotal()),
            COLOR_WHITE,
            COLOR_TEXT,
            bold,
            7.4f,
            -1
        )
        val totalAttH = attH1 + attH2
        drawTextPt(
            canvas,
            ellipsize("Attendance (Days Present / Working Days) :", attLabelPaint, attLabelMaxW),
            inL,
            centerBaselineY(y, totalAttH, attLabelPaint),
            attLabelPaint
        )
        y += totalAttH + 7f

        // 4. SCHOLASTIC AREAS TABLE (Unified 10/30/60/100 for Term-I & Term-II + Grand Total 200)
        y += drawBand(canvas, inL, y, inR, 17f, "ACADEMIC PERFORMANCE : SCHOLASTIC AREAS (8-POINT SCALE)", bold, 8.1f)

        val wIA = 42f
        val wUT = 40f
        val wEx = 48f
        val wFull = 51f // Term block = 181 pt
        val termW = wIA + wUT + wEx + wFull
        val wGrand = 68f
        val wSub = inW - (termW * 2f) - wGrand // ~101.6 pt
        val cols = floatArrayOf(wSub, wIA, wUT, wEx, wFull, wIA, wUT, wEx, wFull, wGrand)

        // Super header row
        val superCols = floatArrayOf(wSub, termW, termW, wGrand)
        y += drawTableRow(canvas, inL, y, 16.5f, superCols, listOf("SUBJECT", "TERM - I (100)", "TERM - II (100)", "OVERALL"), COLOR_SUB_BAND, COLOR_WHITE, bold, 7.4f, -1)

        // Sub header row (dynamic min-height with 1.45x line spacing)
        val subHeaders = listOf(
            "Subject",
            "Int. Ass.\n(10)", "U.T. 1\n(30)", "H.Y. Exam\n(60)", "Full Marks\n(100)",
            "Int. Ass.\n(10)", "U.T. 2\n(30)", "Yearly\n(60)", "Full Marks\n(100)",
            "Grand Total\n(200)"
        )
        y += drawMultiLineTableRow(canvas, inL, y, 26f, cols, subHeaders, COLOR_TINT, COLOR_BAND, bold, 6.2f, false)

        // Subject rows (dynamic min-height, fits 6, 7, and 8 subjects cleanly on one A4 page)
        val minSubRowH = when {
            perf.subjects.size >= 8 -> 18.8f
            perf.subjects.size == 7 -> 20.8f
            else -> 22.4f
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
            y += drawTableRow(canvas, inL, y, minSubRowH, cols, vals, bg, COLOR_TEXT, regular, 7.2f, leftAlignCol = 0)
        }

        // Term Totals row (spanned cleanly across Term-I & Term-II blocks so totals never truncate)
        val totalVals = listOf(
            "TOTAL",
            "Term-I :  ${ReportCalculations.formatNumber(perf.term1Total)} / ${perf.term1Max}",
            "Term-II :  ${ReportCalculations.formatNumber(perf.term2Total)} / ${perf.term2Max}",
            "${ReportCalculations.formatNumber(perf.overallTotal)} / ${perf.overallMax}"
        )
        y += drawTableRow(canvas, inL, y, 19.5f, superCols, totalVals, COLOR_TINT, COLOR_BAND, bold, 6.9f, leftAlignCol = 0) + 5f

        // Overall Summary Bar
        val sumH = 23.5f
        drawRectFillAndStroke(canvas, inL, y, inR, y + sumH, COLOR_ZEBRA, COLOR_BORDER, 0.7f)
        val overallSummary = buildString {
            append("Overall Total : ${ReportCalculations.formatNumber(perf.overallTotal)} / ${perf.overallMax}")
            append("   |   Percentage : ${ReportCalculations.formatPercent(perf.overallPercentage)}%")
            append("   |   Grade : ${perf.overallGrade}")
            append("   |   Result : ${perf.overallResult.label}")
            if (settings.showClassRank) {
                val rStr = perf.classRank?.let { "#$it" } ?: "-"
                append("   |   Rank : $rStr")
            }
        }
        val summaryPaint = makePaint(bold, 7.7f, COLOR_BAND, Paint.Align.CENTER)
        drawTextPt(canvas, ellipsize(overallSummary, summaryPaint, inW - 12f), centerX, centerBaselineY(y, sumH, summaryPaint), summaryPaint)
        y += sumH + 8f

        // 5. CO-SCHOLASTIC AREAS + BORDERED MARKS RANGE / GRADE TABLE SIDE BY SIDE (Exact equal height!)
        y += drawBand(canvas, inL, y, inR, 16.5f, "CO-SCHOLASTIC AREAS & GRADING SCALE", bold, 7.9f) + 3f

        val gap = 10f
        val leftW = inW * 0.58f
        val rightX = inL + leftW + gap
        val rightW = inW - leftW - gap

        // Compute scale row height first so Co-Scholastic 5 rows match 8 scale rows to the exact point
        val scalePaintSample = makePaint(regular, 6.5f, COLOR_TEXT)
        val scaleRowH = maxOf(13.2f, lineHeightPt(scalePaintSample) + 3.6f)
        val coRowH = (scaleRowH * 8f) / 5f
        val tableHeaderH = 16.5f

        // Left: Co-Scholastic 5 areas
        var ly = y
        val coCols = floatArrayOf(leftW * 0.68f, leftW * 0.16f, leftW * 0.16f)
        val headerHLeft = drawTableRow(canvas, inL, ly, tableHeaderH, coCols, listOf("Activity / Area", "Term-I", "Term-II"), COLOR_TINT, COLOR_BAND, bold, 7.0f, leftAlignCol = 0)
        ly += headerHLeft
        CoScholasticArea.entries.forEachIndexed { idx, area ->
            val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
            val g1 = bundle.coScholasticT1[area.code].orEmpty().ifEmpty { "-" }
            val g2 = bundle.coScholasticT2[area.code].orEmpty().ifEmpty { "-" }
            ly += drawTableRow(canvas, inL, ly, coRowH, coCols, listOf(area.label, g1, g2), bg, COLOR_TEXT, regular, 6.8f, leftAlignCol = 0)
        }

        // Right: Bordered Marks Range / Grade table
        var ry = y
        val scaleCols = floatArrayOf(rightW * 0.37f, rightW * 0.63f)
        ry += drawTableRow(canvas, rightX, ry, headerHLeft, scaleCols, listOf("Marks Range", "Grade"), COLOR_TINT, COLOR_BAND, bold, 7.0f, -1)
        val annualScale = listOf(
            "91 - 100" to "A1",
            "81 - 90" to "A2",
            "71 - 80" to "B1",
            "61 - 70" to "B2",
            "51 - 60" to "C1",
            "41 - 50" to "C2",
            "33 - 40" to "D",
            "32 & Below" to "E (Need improvement)"
        )
        annualScale.forEachIndexed { idx, (rng, grd) ->
            val bg = if (idx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
            ry += drawTableRow(canvas, rightX, ry, scaleRowH, scaleCols, listOf(rng, grd), bg, COLOR_TEXT, regular, 6.5f, -1)
        }

        y = maxOf(ly, ry) + 11f

        // 6. FOOTER: Remarks, Promoted to Class, Date (plain printed text, no blank lines) & 3 Equal Signatures
        val remarksText = bundle.term2Info?.remarks?.ifBlank { bundle.term1Info?.remarks.orEmpty() }.orEmpty().trim()
        val promotedTo = bundle.term2Info?.promotedTo.orEmpty().trim()
        val dateText = bundle.term2Info?.reportDate?.ifBlank { bundle.term1Info?.reportDate.orEmpty() }.orEmpty().trim()

        val footerRowH = maxOf(18f, lineHeightPt(pInfoVal) + 5f)
        drawPlainField(canvas, inL, centerBaselineY(y, footerRowH, pInfoVal), inW, "CLASS TEACHER'S REMARKS : ", remarksText, pInfoBold, pInfoVal)
        y += footerRowH + 2f
        val footerGap = 14f
        val wProm = (inW - footerGap) * 0.62f
        val wDate = (inW - footerGap) * 0.38f
        val f2Base = centerBaselineY(y, footerRowH, pInfoVal)
        drawPlainField(canvas, inL, f2Base, wProm, "PROMOTED TO CLASS : ", promotedTo, pInfoBold, pInfoVal)
        drawPlainField(canvas, inL + wProm + footerGap, f2Base, wDate, "DATE : ", dateText, pInfoBold, pInfoVal)

        drawSignatureStrip(
            canvas = canvas,
            left = inL,
            baselineY = bottom - 13f,
            width = inW,
            bold = bold,
            textSize = 7.7f,
            labels = listOf("GUARDIAN'S SIGN", "CLASS TEACHER'S SIGN", "PRINCIPAL'S SIGN"),
            lineHalfWidth = 58f
        )
    }

    // =========================================================================
    // SHARED HIGH-PRECISION VECTOR DRAWING HELPERS
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
            val p = makePaint(bold, placeholderSize, COLOR_SUBTEXT, Paint.Align.CENTER)
            drawTextPt(canvas, "LOGO", rect.centerX(), centerBaselineY(rect.top, rect.height(), p), p)
        }
    }

    private fun drawBand(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        minHeight: Float,
        title: String,
        bold: Typeface,
        textSize: Float
    ): Float {
        val p = makePaint(bold, textSize, COLOR_WHITE, Paint.Align.CENTER)
        val height = maxOf(minHeight, lineHeightPt(p) + 4f)
        drawRectFill(canvas, left, top, right, top + height, COLOR_BAND)
        drawTextPt(canvas, ellipsize(title, p, (right - left - 12f).coerceAtLeast(16f)), (left + right) / 2f, centerBaselineY(top, height, p), p)
        return height
    }

    private fun drawPlainField(
        canvas: Canvas,
        x: Float,
        baselineY: Float,
        totalWidth: Float,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        drawTextPt(canvas, label, x, baselineY, labelPaint)
        val lw = measureTextPt(label, labelPaint)
        val valX = (x + lw + 3f).coerceAtMost(x + totalWidth - 4f)
        val availW = (x + totalWidth - valX - 2f).coerceAtLeast(0f)
        if (value.isNotBlank() && availW > 4f) {
            drawTextPt(canvas, ellipsize(value, valuePaint, availW), valX, baselineY, valuePaint)
        }
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
            if (measureTextPt(candidate, paint) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = w
                if (lines.size >= maxLines - 1) break
            }
        }
        if (current.isNotEmpty() && lines.size < maxLines) lines.add(current)

        lines.take(maxLines).forEachIndexed { idx, line ->
            drawTextPt(canvas, ellipsize(line, paint, maxWidth), x, startY + idx * lineHeight, paint)
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
            strokeWidth = 0.75f
        }
        val labelPaint = makePaint(bold, textSize, COLOR_BAND, Paint.Align.CENTER)
        val slotW = width / labels.size.toFloat()
        labels.forEachIndexed { idx, label ->
            val cx = left + slotW * idx + slotW / 2f
            canvas.drawLine(cx - lineHalfWidth, baselineY - 12f, cx + lineHalfWidth, baselineY - 12f, linePaint)
            drawTextPt(canvas, ellipsize(label, labelPaint, slotW - 6f), cx, baselineY, labelPaint)
        }
    }

    private fun drawTableRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        minRowH: Float,
        colWidths: FloatArray,
        values: List<String>,
        bgColor: Int,
        textColor: Int,
        typeface: Typeface,
        textSize: Float,
        leftAlignCol: Int
    ): Float {
        val fillPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val textPaint = makePaint(typeface, textSize, textColor)
        val rowH = maxOf(minRowH, lineHeightPt(textPaint) + 3.4f)
        val baselineY = centerBaselineY(y, rowH, textPaint)
        val cellPadX = 5f

        var cx = x
        colWidths.forEachIndexed { idx, w ->
            canvas.drawRect(cx, y, cx + w, y + rowH, fillPaint)
            canvas.drawRect(cx, y, cx + w, y + rowH, strokePaint)
            val raw = values.getOrElse(idx) { "" }
            if (raw.isNotEmpty()) {
                val safe = ellipsize(raw, textPaint, (w - cellPadX * 2f).coerceAtLeast(8f))
                if (idx == leftAlignCol) {
                    textPaint.textAlign = Paint.Align.LEFT
                    drawTextPt(canvas, safe, cx + cellPadX, baselineY, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.CENTER
                    drawTextPt(canvas, safe, cx + w / 2f, baselineY, textPaint)
                }
            }
            cx += w
        }
        return rowH
    }

    private fun drawMultiLineTableRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        minRowH: Float,
        colWidths: FloatArray,
        values: List<String>,
        bgColor: Int,
        textColor: Int,
        typeface: Typeface,
        textSize: Float,
        firstColLeftAlign: Boolean
    ): Float {
        val fillPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = COLOR_BORDER; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val textPaint = makePaint(typeface, textSize, textColor)
        val asc = ascentPt(textPaint)
        val desc = descentPt(textPaint)
        val singleLineH = textSize * LINE_HEIGHT_MULTIPLIER
        val cellPadX = 3.2f

        val maxLineCount = values.maxOfOrNull { it.count { ch -> ch == '\n' } + 1 } ?: 1
        val maxBlockH = (maxLineCount - 1) * singleLineH + (desc - asc)
        val rowH = maxOf(minRowH, maxBlockH + 6f)

        var cx = x
        colWidths.forEachIndexed { idx, w ->
            canvas.drawRect(cx, y, cx + w, y + rowH, fillPaint)
            canvas.drawRect(cx, y, cx + w, y + rowH, strokePaint)
            val lines = values.getOrElse(idx) { "" }.split("\n")
            val blockH = (lines.size - 1) * singleLineH + (desc - asc)
            var ly = y + (rowH - blockH) / 2f - asc
            lines.forEach { line ->
                val safe = ellipsize(line, textPaint, (w - cellPadX * 2f).coerceAtLeast(6f))
                if (idx == 0 && firstColLeftAlign) {
                    textPaint.textAlign = Paint.Align.LEFT
                    drawTextPt(canvas, safe, cx + cellPadX, ly, textPaint)
                } else {
                    textPaint.textAlign = Paint.Align.CENTER
                    drawTextPt(canvas, safe, cx + w / 2f, ly, textPaint)
                }
                ly += singleLineH
            }
            cx += w
        }
        return rowH
    }

    private fun drawTextPt(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        if (text.isEmpty()) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(text, 0f, 0f, paint)
        canvas.restore()
    }

    private fun measureTextPt(text: String, paint: Paint): Float =
        paint.measureText(text) / TEXT_SCALE

    private fun ascentPt(paint: Paint): Float = paint.fontMetrics.ascent / TEXT_SCALE

    private fun descentPt(paint: Paint): Float = paint.fontMetrics.descent / TEXT_SCALE

    private fun lineHeightPt(paint: Paint): Float {
        val fontSizePt = paint.textSize / TEXT_SCALE
        return maxOf(fontSizePt * LINE_HEIGHT_MULTIPLIER, descentPt(paint) - ascentPt(paint))
    }

    private fun centerBaselineY(topY: Float, boxHeight: Float, paint: Paint): Float =
        topY + (boxHeight - (descentPt(paint) + ascentPt(paint))) / 2f

    private fun ellipsize(text: String, paint: Paint, maxWidthPt: Float): String {
        if (maxWidthPt <= 0f) return ""
        if (measureTextPt(text, paint) <= maxWidthPt) return text
        val ellipsis = "…"
        val ellW = measureTextPt(ellipsis, paint)
        var end = text.length
        while (end > 1 && measureTextPt(text.substring(0, end), paint) + ellW > maxWidthPt) end--
        return text.substring(0, end) + ellipsis
    }

    private fun makePaint(
        typeface: Typeface,
        sizePt: Float,
        colorInt: Int,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
    ).apply {
        this.typeface = typeface
        textSize = sizePt * TEXT_SCALE
        letterSpacing = LETTER_SPACING_EM
        hinting = Paint.HINTING_OFF
        isFakeBoldText = false
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
