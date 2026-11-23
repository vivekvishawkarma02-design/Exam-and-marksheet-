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
import com.example.domain.DayOfWeekCode
import com.example.domain.PeriodSlot
import com.example.domain.Phase4Validator
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.domain.SubjectWeeklyProgress
import com.example.domain.TeacherTimetableSummary
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class TimetablePrintOrientation(val label: String) {
    LANDSCAPE("Landscape A4"),
    PORTRAIT("Portrait A4")
}

data class ClassTimetablePrintBundle(
    val school: SchoolEntity,
    val session: String,
    val className: String,
    val section: String,
    val classTeacherName: String,
    val periodStructure: SchoolPeriodStructure,
    val cells: List<TimetableCell>,
    val subjectProgress: List<SubjectWeeklyProgress>
)

data class TeacherTimetablePrintBundle(
    val primarySchool: SchoolEntity,
    val session: String,
    val summary: TeacherTimetableSummary,
    val periodStructureForRows: SchoolPeriodStructure
)

data class MasterClassSectionRow(
    val classGroupName: String,
    val className: String,
    val section: String,
    val classTeacherShort: String,
    val cellsByDayAndSlot: Map<Pair<DayOfWeekCode, String>, TimetableCell>
) {
    val label: String get() = "$className-$section"
}

data class MasterTimetablePrintBundle(
    val school: SchoolEntity,
    val session: String,
    val periodStructure: SchoolPeriodStructure,
    val rows: List<MasterClassSectionRow>
)

data class MasterPagePlan(
    val groupTitle: String,
    val pageIndexInGroup: Int,
    val totalPagesInGroup: Int,
    val rows: List<MasterClassSectionRow>
)

/**
 * Native Android A4 Vector PDF & Batch ZIP Generator for Phase 5 Timetables.
 *
 * Matches the Report Card aesthetic (dark navy-slate header bands with white bold text,
 * crisp thin borders, light shading for assembly/breaks/lunch, high B&W print contrast,
 * 4x vector text shaping so glyphs never overlap or clip). Works 100% offline.
 */
object TimetablePdfGenerator {

    // A4 Portrait = 595 x 842 pt, Landscape = 842 x 595 pt
    const val PORTRAIT_W = 595
    const val PORTRAIT_H = 842
    const val LANDSCAPE_W = 842
    const val LANDSCAPE_H = 595

    private const val MARGIN = 20f
    private const val TEXT_SCALE = 4f
    private const val LETTER_SPACING_EM = 0.03f

    // Exact same print-safe dark-slate/navy palette as PdfReportGenerator
    private val COLOR_BAND = Color.parseColor("#243447")
    private val COLOR_SUB_BAND = Color.parseColor("#3B4D61")
    private val COLOR_TINT = Color.parseColor("#E6ECF2")
    private val COLOR_BREAK_FILL = Color.parseColor("#EDF1F5")
    private val COLOR_ZEBRA = Color.parseColor("#F7F9FB")
    private val COLOR_BORDER = Color.parseColor("#526171")
    private val COLOR_TEXT = Color.parseColor("#111111")
    private val COLOR_SUBTEXT = Color.parseColor("#333F4D")
    private val COLOR_WHITE = Color.WHITE

    private val TYPEFACE_REGULAR: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val TYPEFACE_BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    // Fixed readable Master table geometry (A4 Landscape: 842 x 595 pt)
    internal const val MASTER_FIXED_ROW_HEIGHT = 24f
    internal const val MASTER_HEADER_BLOCK_HEIGHT = 112f // School header + Day + Period headers
    internal const val MASTER_FOOTER_HEIGHT = 18f

    // =========================================================================
    // 1. PUBLIC FILE GENERATORS (Single PDF, Combined Batch PDF, Batch ZIP)
    // =========================================================================

    fun generateClassTimetablePdf(
        context: Context,
        bundles: List<ClassTimetablePrintBundle>,
        orientation: TimetablePrintOrientation = TimetablePrintOrientation.LANDSCAPE,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File {
        val dir = File(context.cacheDir, "timetables").apply { mkdirs() }
        val fileName = buildClassPdfFileName(bundles)
        val outFile = File(dir, fileName)
        writeClassTimetablePdfToFile(bundles, orientation, outFile, onProgress)
        return outFile
    }

    fun generateBatchClassesZip(
        context: Context,
        bundles: List<ClassTimetablePrintBundle>,
        orientation: TimetablePrintOrientation = TimetablePrintOrientation.LANDSCAPE,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File {
        val dir = File(context.cacheDir, "timetables").apply { mkdirs() }
        val first = bundles.firstOrNull()
        val schoolSlug = safeSlug(first?.school?.name.orEmpty(), "School")
        val sessionSlug = safeSlug(first?.session.orEmpty(), "Session")
        val zipFile = File(dir, "${schoolSlug}_All_Class_Timetables_${sessionSlug}.zip")
        val tmpDir = File(dir, "zip_cls_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                bundles.forEachIndexed { idx, bundle ->
                    val entryName = "${safeSlug(bundle.school.name, "School")}_Class_${safeSlug(bundle.className, "Class")}_${safeSlug(bundle.section, "A")}_${safeSlug(bundle.session, "Session")}.pdf"
                    val single = File(tmpDir, entryName)
                    writeClassTimetablePdfToFile(listOf(bundle), orientation, single)
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(single).use { it.copyTo(zos) }
                    zos.closeEntry()
                    single.delete()
                    onProgress(idx + 1, bundles.size)
                }
            }
        } finally {
            tmpDir.deleteRecursively()
        }
        return zipFile
    }

    fun generateTeacherTimetablePdf(
        context: Context,
        bundles: List<TeacherTimetablePrintBundle>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File {
        val dir = File(context.cacheDir, "timetables").apply { mkdirs() }
        val fileName = buildTeacherPdfFileName(bundles)
        val outFile = File(dir, fileName)
        writeTeacherTimetablePdfToFile(bundles, outFile, onProgress)
        return outFile
    }

    fun generateBatchTeachersZip(
        context: Context,
        bundles: List<TeacherTimetablePrintBundle>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File {
        val dir = File(context.cacheDir, "timetables").apply { mkdirs() }
        val first = bundles.firstOrNull()
        val schoolSlug = safeSlug(first?.primarySchool?.name.orEmpty(), "Org")
        val sessionSlug = safeSlug(first?.session.orEmpty(), "Session")
        val zipFile = File(dir, "${schoolSlug}_All_Teacher_Timetables_${sessionSlug}.zip")
        val tmpDir = File(dir, "zip_tchr_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                bundles.forEachIndexed { idx, bundle ->
                    val entryName = "Teacher_${safeSlug(bundle.summary.teacherName, "Teacher")}_${safeSlug(bundle.session, "Session")}.pdf"
                    val single = File(tmpDir, entryName)
                    writeTeacherTimetablePdfToFile(listOf(bundle), single)
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(single).use { it.copyTo(zos) }
                    zos.closeEntry()
                    single.delete()
                    onProgress(idx + 1, bundles.size)
                }
            }
        } finally {
            tmpDir.deleteRecursively()
        }
        return zipFile
    }

    fun generateMasterTimetablePdf(
        context: Context,
        bundle: MasterTimetablePrintBundle
    ): File {
        val dir = File(context.cacheDir, "timetables").apply { mkdirs() }
        val fileName = "${safeSlug(bundle.school.name, "School")}_Master_Timetable_${safeSlug(bundle.session, "Session")}.pdf"
        val outFile = File(dir, fileName)
        writeMasterTimetablePdfToFile(bundle, outFile)
        return outFile
    }

    // =========================================================================
    // 2. FILE NAME & PAGINATION PLANNERS (Testable in pure JVM & Robolectric)
    // =========================================================================

    fun buildClassPdfFileName(bundles: List<ClassTimetablePrintBundle>): String {
        val first = bundles.firstOrNull() ?: return "Class_Timetable.pdf"
        val sch = safeSlug(first.school.name, "School")
        val ses = safeSlug(first.session, "Session")
        return if (bundles.size == 1) {
            "${sch}_Timetable_${safeSlug(first.className, "Class")}_${safeSlug(first.section, "A")}_${ses}.pdf"
        } else {
            "${sch}_All_Class_Timetables_${ses}.pdf"
        }
    }

    fun buildTeacherPdfFileName(bundles: List<TeacherTimetablePrintBundle>): String {
        val first = bundles.firstOrNull() ?: return "Teacher_Timetable.pdf"
        val ses = safeSlug(first.session, "Session")
        return if (bundles.size == 1) {
            "Teacher_Timetable_${safeSlug(first.summary.teacherName, "Teacher")}_${ses}.pdf"
        } else {
            "${safeSlug(first.primarySchool.name, "Org")}_All_Teacher_Timetables_${ses}.pdf"
        }
    }

    /**
     * Plans Master Timetable pagination on A4 Landscape:
     * - If ALL rows fit cleanly on 1 page at fixed row height (MASTER_FIXED_ROW_HEIGHT = 24 pt),
     *   outputs 1 single school page.
     * - Otherwise splits cleanly by Class Group onto separate pages (and further chunks any
     *   extra-large class group) with the school + column header repeated on every page.
     */
    fun planMasterPages(rows: List<MasterClassSectionRow>): List<MasterPagePlan> {
        if (rows.isEmpty()) {
            return listOf(MasterPagePlan("All Classes", 1, 1, emptyList()))
        }
        val availableTableHeight = LANDSCAPE_H - (MARGIN * 2f) - MASTER_HEADER_BLOCK_HEIGHT - MASTER_FOOTER_HEIGHT
        val maxRowsPerPage = (availableTableHeight / MASTER_FIXED_ROW_HEIGHT).toInt().coerceAtLeast(6)

        if (rows.size <= maxRowsPerPage) {
            return listOf(
                MasterPagePlan(
                    groupTitle = "All Classes",
                    pageIndexInGroup = 1,
                    totalPagesInGroup = 1,
                    rows = rows
                )
            )
        }

        val grouped = linkedMapOf<String, MutableList<MasterClassSectionRow>>()
        rows.forEach { r ->
            val key = r.classGroupName.ifBlank { "General" }
            grouped.getOrPut(key) { mutableListOf() }.add(r)
        }

        val plans = mutableListOf<MasterPagePlan>()
        grouped.forEach { (groupName, groupRows) ->
            val chunks = groupRows.chunked(maxRowsPerPage)
            chunks.forEachIndexed { idx, chunk ->
                plans.add(
                    MasterPagePlan(
                        groupTitle = groupName,
                        pageIndexInGroup = idx + 1,
                        totalPagesInGroup = chunks.size,
                        rows = chunk
                    )
                )
            }
        }
        return plans
    }

    // =========================================================================
    // 3. BITMAP VERIFICATION RENDERERS (For Robolectric tests without native PdfDocument stub issues)
    // =========================================================================

    fun renderClassPagesToBitmapsForVerification(
        bundles: List<ClassTimetablePrintBundle>,
        orientation: TimetablePrintOrientation = TimetablePrintOrientation.LANDSCAPE
    ): List<Bitmap> {
        val w = if (orientation == TimetablePrintOrientation.LANDSCAPE) LANDSCAPE_W else PORTRAIT_W
        val h = if (orientation == TimetablePrintOrientation.LANDSCAPE) LANDSCAPE_H else PORTRAIT_H
        return bundles.map { bundle ->
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            drawClassTimetablePage(canvas, w.toFloat(), h.toFloat(), bundle)
            bmp
        }
    }

    fun renderTeacherPagesToBitmapsForVerification(
        bundles: List<TeacherTimetablePrintBundle>
    ): List<Bitmap> = bundles.map { bundle ->
        val bmp = Bitmap.createBitmap(LANDSCAPE_W, LANDSCAPE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        drawTeacherTimetablePage(canvas, LANDSCAPE_W.toFloat(), LANDSCAPE_H.toFloat(), bundle)
        bmp
    }

    fun renderMasterPagesToBitmapsForVerification(
        bundle: MasterTimetablePrintBundle
    ): List<Bitmap> {
        val plans = planMasterPages(bundle.rows)
        return plans.mapIndexed { idx, plan ->
            val bmp = Bitmap.createBitmap(LANDSCAPE_W, LANDSCAPE_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            drawMasterTimetablePage(
                canvas = canvas,
                pageW = LANDSCAPE_W.toFloat(),
                pageH = LANDSCAPE_H.toFloat(),
                bundle = bundle,
                plan = plan,
                pageNumber = idx + 1,
                totalPages = plans.size
            )
            bmp
        }
    }

    // =========================================================================
    // 4. INTERNAL PDF WRITERS
    // =========================================================================

    private fun writeClassTimetablePdfToFile(
        bundles: List<ClassTimetablePrintBundle>,
        orientation: TimetablePrintOrientation,
        outFile: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        val w = if (orientation == TimetablePrintOrientation.LANDSCAPE) LANDSCAPE_W else PORTRAIT_W
        val h = if (orientation == TimetablePrintOrientation.LANDSCAPE) LANDSCAPE_H else PORTRAIT_H
        val doc = PdfDocument()
        try {
            bundles.forEachIndexed { idx, bundle ->
                val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                drawClassTimetablePage(page.canvas, w.toFloat(), h.toFloat(), bundle)
                doc.finishPage(page)
                onProgress(idx + 1, bundles.size)
            }
            FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun writeTeacherTimetablePdfToFile(
        bundles: List<TeacherTimetablePrintBundle>,
        outFile: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        val doc = PdfDocument()
        try {
            bundles.forEachIndexed { idx, bundle ->
                val pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_W, LANDSCAPE_H, idx + 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                drawTeacherTimetablePage(page.canvas, LANDSCAPE_W.toFloat(), LANDSCAPE_H.toFloat(), bundle)
                doc.finishPage(page)
                onProgress(idx + 1, bundles.size)
            }
            FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun writeMasterTimetablePdfToFile(
        bundle: MasterTimetablePrintBundle,
        outFile: File
    ) {
        val plans = planMasterPages(bundle.rows)
        val doc = PdfDocument()
        try {
            plans.forEachIndexed { idx, plan ->
                val pageInfo = PdfDocument.PageInfo.Builder(LANDSCAPE_W, LANDSCAPE_H, idx + 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                drawMasterTimetablePage(
                    canvas = page.canvas,
                    pageW = LANDSCAPE_W.toFloat(),
                    pageH = LANDSCAPE_H.toFloat(),
                    bundle = bundle,
                    plan = plan,
                    pageNumber = idx + 1,
                    totalPages = plans.size
                )
                doc.finishPage(page)
            }
            FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    // =========================================================================
    // 5. PAGE RENDERERS (Class, Teacher, Master)
    // =========================================================================

    private fun drawClassTimetablePage(
        canvas: Canvas,
        pageW: Float,
        pageH: Float,
        bundle: ClassTimetablePrintBundle
    ) {
        val left = MARGIN
        val right = pageW - MARGIN
        val contentW = right - left
        var y = MARGIN

        val subtitle = buildString {
            append("CLASS TIMETABLE  •  CLASS ${bundle.className}-${bundle.section}  •  SESSION ${bundle.session}")
            if (bundle.classTeacherName.isNotBlank()) {
                append("  •  CLASS TEACHER: ${bundle.classTeacherName}")
            }
        }
        y = drawSchoolHeader(canvas, left, y, contentW, bundle.school, subtitle)
        y += 8f

        val days = bundle.periodStructure.workingDays.sortedBy { it.sortOrder }.ifEmpty { DayOfWeekCode.defaultWorkingDays }
        val gridRows = TimetableEngine.buildGridRows(bundle.periodStructure)
        val cellLookup = bundle.cells.associateBy { it.day to it.slotId }

        val footerReserve = if (bundle.subjectProgress.isNotEmpty()) 44f else 18f
        val tableBottom = pageH - MARGIN - footerReserve
        val headerRowH = 24f
        val availableRowsH = (tableBottom - y - headerRowH).coerceAtLeast(120f)

        val timeColW = if (pageW > 700f) 96f else 80f
        val dayColW = (contentW - timeColW) / days.size.coerceAtLeast(1)

        // Compute row heights: break/assembly rows slightly slimmer so teaching periods stay generous
        val breakWeight = 0.62f
        val totalWeight = gridRows.sumOf { if (it.isEditablePeriodRow) 1.0 else breakWeight.toDouble() }.toFloat().coerceAtLeast(1f)
        val unitRowH = (availableRowsH / totalWeight).coerceIn(18f, 44f)

        // Table Header Band (Dark Navy)
        drawFilledRect(canvas, left, y, right, y + headerRowH, COLOR_BAND)
        drawCenteredText(canvas, "PERIOD / TIME", left + timeColW / 2f, y + headerRowH / 2f, 8f, COLOR_WHITE, bold = true, maxWidth = timeColW - 6f)
        days.forEachIndexed { dIdx, day ->
            val x0 = left + timeColW + dIdx * dayColW
            drawVerticalLine(canvas, x0, y, y + headerRowH, COLOR_WHITE, 0.5f)
            drawCenteredText(canvas, day.fullLabel.uppercase(), x0 + dayColW / 2f, y + headerRowH / 2f, 8.2f, COLOR_WHITE, bold = true, maxWidth = dayColW - 6f)
        }
        drawBorderRect(canvas, left, y, right, y + headerRowH, COLOR_BORDER, 0.8f)
        y += headerRowH

        // Grid Rows
        gridRows.forEachIndexed { rIdx, row ->
            val rowH = if (row.isEditablePeriodRow) unitRowH else (unitRowH * breakWeight).coerceAtLeast(16f)
            if (y + rowH > tableBottom + 2f) return@forEachIndexed

            if (!row.isEditablePeriodRow) {
                // Shaded non-editable assembly/break/lunch band
                drawFilledRect(canvas, left, y, right, y + rowH, COLOR_BREAK_FILL)
                drawCenteredText(
                    canvas,
                    "${row.label.uppercase()}  (${row.timeLabel})",
                    left + contentW / 2f,
                    y + rowH / 2f,
                    7.8f,
                    COLOR_SUBTEXT,
                    bold = true,
                    maxWidth = contentW - 12f
                )
                drawBorderRect(canvas, left, y, right, y + rowH, COLOR_BORDER, 0.6f)
            } else {
                val fill = if (rIdx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
                drawFilledRect(canvas, left, y, right, y + rowH, fill)
                drawFilledRect(canvas, left, y, left + timeColW, y + rowH, COLOR_TINT)

                // Sticky Time/Period Column
                drawTwoLineCenteredText(
                    canvas = canvas,
                    line1 = row.label,
                    line2 = row.timeLabel,
                    cx = left + timeColW / 2f,
                    cy = y + rowH / 2f,
                    size1 = 7.6f,
                    size2 = 6.7f,
                    color1 = COLOR_TEXT,
                    color2 = COLOR_SUBTEXT,
                    maxWidth = timeColW - 6f
                )

                days.forEachIndexed { dIdx, day ->
                    val x0 = left + timeColW + dIdx * dayColW
                    drawVerticalLine(canvas, x0, y, y + rowH, COLOR_BORDER, 0.5f)
                    val daySlot = row.slotByDay[day]
                    if (daySlot == null) {
                        drawFilledRect(canvas, x0 + 0.5f, y + 0.5f, x0 + dayColW - 0.5f, y + rowH - 0.5f, COLOR_BREAK_FILL)
                        drawCenteredText(canvas, "—", x0 + dayColW / 2f, y + rowH / 2f, 7.5f, COLOR_SUBTEXT, bold = false, maxWidth = dayColW - 6f)
                    } else {
                        val cell = cellLookup[day to daySlot.id]
                        if (cell != null) {
                            drawTwoLineCenteredText(
                                canvas = canvas,
                                line1 = cell.subjectName,
                                line2 = cell.teacherName.ifBlank { TimetableEngine.shortTeacherName(cell.teacherId) },
                                cx = x0 + dayColW / 2f,
                                cy = y + rowH / 2f,
                                size1 = if (pageW > 700f) 8.2f else 7.2f,
                                size2 = if (pageW > 700f) 7.0f else 6.4f,
                                color1 = COLOR_TEXT,
                                color2 = COLOR_SUBTEXT,
                                maxWidth = dayColW - 8f
                            )
                        }
                    }
                }
                drawBorderRect(canvas, left, y, right, y + rowH, COLOR_BORDER, 0.6f)
            }
            y += rowH
        }

        // Weekly Subject Period Summary Footer
        if (bundle.subjectProgress.isNotEmpty()) {
            val summaryY = (y + 8f).coerceAtMost(pageH - MARGIN - 30f)
            val summaryText = "Weekly Periods: " + bundle.subjectProgress.joinToString("  •  ") { it.label }
            drawFilledRect(canvas, left, summaryY, right, summaryY + 20f, COLOR_TINT)
            drawBorderRect(canvas, left, summaryY, right, summaryY + 20f, COLOR_BORDER, 0.5f)
            drawLeftText(canvas, summaryText, left + 8f, summaryY + 10f, 7.2f, COLOR_TEXT, bold = false, maxWidth = contentW - 16f)
        }

        drawPageFooter(canvas, left, right, pageH - MARGIN + 4f, "Session ${bundle.session}  •  Class ${bundle.className}-${bundle.section}")
    }

    private fun drawTeacherTimetablePage(
        canvas: Canvas,
        pageW: Float,
        pageH: Float,
        bundle: TeacherTimetablePrintBundle
    ) {
        val left = MARGIN
        val right = pageW - MARGIN
        val contentW = right - left
        var y = MARGIN

        val summary = bundle.summary
        val subtitle = "TEACHER TIMETABLE  •  ${summary.teacherName.uppercase()}  •  SESSION ${bundle.session}  •  " +
            "LOAD: ${summary.totalPeriodsPerWeek}/${summary.weeklyLimit} PERIODS/WK  •  FREE: ${summary.freePeriodsPerWeek}"
        y = drawSchoolHeader(canvas, left, y, contentW, bundle.primarySchool, subtitle)
        y += 8f

        val days = DayOfWeekCode.defaultWorkingDays
        val gridRows = TimetableEngine.buildGridRows(bundle.periodStructureForRows)
        val tableBottom = pageH - MARGIN - 20f
        val headerRowH = 24f
        val availableH = (tableBottom - y - headerRowH).coerceAtLeast(120f)
        val timeColW = 96f
        val dayColW = (contentW - timeColW) / days.size

        // Check if teacher has periods at timings outside primary template (e.g. second school shift)
        val extraSchoolItems = summary.itemsByDay.values.flatten()
        val hasMultiSchool = extraSchoolItems.map { it.schoolId }.distinct().size > 1

        val breakWeight = 0.58f
        val totalWeight = gridRows.sumOf { if (it.isEditablePeriodRow) 1.0 else breakWeight.toDouble() }.toFloat().coerceAtLeast(1f)
        val unitRowH = (availableH / totalWeight).coerceIn(22f, 44f)

        // Header
        drawFilledRect(canvas, left, y, right, y + headerRowH, COLOR_BAND)
        drawCenteredText(canvas, "PERIOD / TIME", left + timeColW / 2f, y + headerRowH / 2f, 8f, COLOR_WHITE, bold = true, maxWidth = timeColW - 6f)
        days.forEachIndexed { dIdx, day ->
            val x0 = left + timeColW + dIdx * dayColW
            drawVerticalLine(canvas, x0, y, y + headerRowH, COLOR_WHITE, 0.5f)
            drawCenteredText(canvas, day.fullLabel.uppercase(), x0 + dayColW / 2f, y + headerRowH / 2f, 8.2f, COLOR_WHITE, bold = true, maxWidth = dayColW - 6f)
        }
        drawBorderRect(canvas, left, y, right, y + headerRowH, COLOR_BORDER, 0.8f)
        y += headerRowH

        val usedCellIds = mutableSetOf<String>()

        gridRows.forEachIndexed { rIdx, row ->
            val rowH = if (row.isEditablePeriodRow) unitRowH else (unitRowH * breakWeight).coerceAtLeast(16f)
            if (y + rowH > tableBottom + 2f) return@forEachIndexed

            if (!row.isEditablePeriodRow) {
                drawFilledRect(canvas, left, y, right, y + rowH, COLOR_BREAK_FILL)
                drawCenteredText(
                    canvas,
                    "${row.label.uppercase()}  (${row.timeLabel})",
                    left + contentW / 2f,
                    y + rowH / 2f,
                    7.6f,
                    COLOR_SUBTEXT,
                    bold = true,
                    maxWidth = contentW - 12f
                )
                drawBorderRect(canvas, left, y, right, y + rowH, COLOR_BORDER, 0.6f)
            } else {
                val fill = if (rIdx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
                drawFilledRect(canvas, left, y, right, y + rowH, fill)
                drawFilledRect(canvas, left, y, left + timeColW, y + rowH, COLOR_TINT)
                drawTwoLineCenteredText(
                    canvas = canvas,
                    line1 = row.label,
                    line2 = row.timeLabel,
                    cx = left + timeColW / 2f,
                    cy = y + rowH / 2f,
                    size1 = 7.6f,
                    size2 = 6.7f,
                    color1 = COLOR_TEXT,
                    color2 = COLOR_SUBTEXT,
                    maxWidth = timeColW - 6f
                )
                days.forEachIndexed { dIdx, day ->
                    val x0 = left + timeColW + dIdx * dayColW
                    drawVerticalLine(canvas, x0, y, y + rowH, COLOR_BORDER, 0.5f)
                    val daySlot = row.slotByDay[day]
                    val dayItems = summary.itemsByDay[day].orEmpty()
                    val matched = dayItems.firstOrNull { item ->
                        item.cell.id !in usedCellIds && (
                            (daySlot != null && item.cell.slotId == daySlot.id) ||
                                (daySlot != null && TimetableEngine.slotsOverlap(item.startMinutes, item.endMinutes, daySlot.startMinutes, daySlot.endMinutes))
                            )
                    }
                    if (matched != null) {
                        usedCellIds.add(matched.cell.id)
                        val line1 = "${matched.className}-${matched.section} • ${TimetableEngine.shortSubjectName(matched.subjectName)}"
                        val line2 = if (hasMultiSchool) {
                            "${matched.schoolName} (${Phase4Validator.formatMinutes(matched.startMinutes)}–${Phase4Validator.formatMinutes(matched.endMinutes)})"
                        } else {
                            matched.schoolName
                        }
                        drawTwoLineCenteredText(
                            canvas = canvas,
                            line1 = line1,
                            line2 = line2,
                            cx = x0 + dayColW / 2f,
                            cy = y + rowH / 2f,
                            size1 = 7.6f,
                            size2 = 6.4f,
                            color1 = COLOR_TEXT,
                            color2 = COLOR_SUBTEXT,
                            maxWidth = dayColW - 6f
                        )
                    } else if (daySlot == null) {
                        drawFilledRect(canvas, x0 + 0.5f, y + 0.5f, x0 + dayColW - 0.5f, y + rowH - 0.5f, COLOR_BREAK_FILL)
                        drawCenteredText(canvas, "—", x0 + dayColW / 2f, y + rowH / 2f, 7.2f, COLOR_SUBTEXT, bold = false, maxWidth = dayColW - 6f)
                    } else {
                        drawCenteredText(canvas, "Free", x0 + dayColW / 2f, y + rowH / 2f, 6.8f, Color.parseColor("#7A8694"), bold = false, maxWidth = dayColW - 6f)
                    }
                }
                drawBorderRect(canvas, left, y, right, y + rowH, COLOR_BORDER, 0.6f)
            }
            y += rowH
        }

        // If teacher also teaches in another school with non-overlapping shift times outside primary rows, list below
        val remainingShiftItems = summary.itemsByDay.values.flatten().filter { it.cell.id !in usedCellIds }
        if (remainingShiftItems.isNotEmpty() && y + 18f <= pageH - MARGIN) {
            val extraLine = "Additional School Shift(s): " + remainingShiftItems.joinToString("  |  ") {
                "${it.day.shortLabel} ${Phase4Validator.formatMinutes(it.startMinutes)}–${Phase4Validator.formatMinutes(it.endMinutes)} ${it.className}-${it.section} ${it.subjectName} (${it.schoolName})"
            }
            drawFilledRect(canvas, left, y + 4f, right, y + 20f, COLOR_TINT)
            drawLeftText(canvas, extraLine, left + 6f, y + 12f, 6.6f, COLOR_TEXT, bold = false, maxWidth = contentW - 12f)
        }

        drawPageFooter(canvas, left, right, pageH - MARGIN + 4f, "Teacher: ${summary.teacherName}  •  Total: ${summary.totalPeriodsPerWeek} periods/week  •  Free: ${summary.freePeriodsPerWeek}")
    }

    private fun drawMasterTimetablePage(
        canvas: Canvas,
        pageW: Float,
        pageH: Float,
        bundle: MasterTimetablePrintBundle,
        plan: MasterPagePlan,
        pageNumber: Int,
        totalPages: Int
    ) {
        val left = MARGIN
        val right = pageW - MARGIN
        val contentW = right - left
        var y = MARGIN

        val groupSuffix = if (totalPages > 1) {
            val part = if (plan.totalPagesInGroup > 1) " (${plan.pageIndexInGroup}/${plan.totalPagesInGroup})" else ""
            "  •  ${plan.groupTitle}$part"
        } else {
            ""
        }
        val subtitle = "MASTER TIMETABLE  •  SESSION ${bundle.session}$groupSuffix  •  PAGE $pageNumber OF $totalPages"
        y = drawSchoolHeader(canvas, left, y, contentW, bundle.school, subtitle, compact = true)
        y += 6f

        // Build ordered (day, teachingSlot) columns across working days
        val days = bundle.periodStructure.workingDays.sortedBy { it.sortOrder }.ifEmpty { DayOfWeekCode.defaultWorkingDays }
        val dayColumns = days.map { day ->
            val periods = bundle.periodStructure.slotsFor(day).filter { it.type.countsAsTeachingPeriod }
            day to periods
        }.filter { it.second.isNotEmpty() }

        val flatCols = dayColumns.flatMap { (day, periods) ->
            periods.mapIndexed { idx, slot -> Triple(day, idx + 1, slot) }
        }
        if (flatCols.isEmpty()) {
            drawCenteredText(canvas, "No teaching periods configured.", left + contentW / 2f, y + 40f, 10f, COLOR_SUBTEXT, bold = false, maxWidth = contentW)
            return
        }

        val classColW = 52f
        val colW = (contentW - classColW) / flatCols.size
        val dayHeaderH = 16f
        val periodHeaderH = 14f

        // Row 1: Day Group Headers (Dark Navy)
        drawFilledRect(canvas, left, y, right, y + dayHeaderH, COLOR_BAND)
        drawCenteredText(canvas, "CLASS", left + classColW / 2f, y + (dayHeaderH + periodHeaderH) / 2f, 7.2f, COLOR_WHITE, bold = true, maxWidth = classColW - 4f)
        var xCursor = left + classColW
        dayColumns.forEach { (day, periods) ->
            val spanW = periods.size * colW
            drawVerticalLine(canvas, xCursor, y, y + dayHeaderH + periodHeaderH, COLOR_WHITE, 0.9f)
            drawCenteredText(canvas, day.shortLabel.uppercase(), xCursor + spanW / 2f, y + dayHeaderH / 2f, 7.2f, COLOR_WHITE, bold = true, maxWidth = spanW - 2f)
            xCursor += spanW
        }
        drawBorderRect(canvas, left, y, right, y + dayHeaderH, COLOR_BORDER, 0.7f)
        y += dayHeaderH

        // Row 2: Period Number Headers (Secondary Slate)
        drawFilledRect(canvas, left + classColW, y, right, y + periodHeaderH, COLOR_SUB_BAND)
        xCursor = left + classColW
        flatCols.forEachIndexed { idx, (_, pNum, _) ->
            val isDayBoundary = idx > 0 && flatCols[idx - 1].first != flatCols[idx].first
            drawVerticalLine(canvas, xCursor, y, y + periodHeaderH, COLOR_WHITE, if (isDayBoundary) 0.9f else 0.4f)
            drawCenteredText(canvas, "P$pNum", xCursor + colW / 2f, y + periodHeaderH / 2f, 6.2f, COLOR_WHITE, bold = true, maxWidth = colW - 1f)
            xCursor += colW
        }
        drawBorderRect(canvas, left, y - dayHeaderH, right, y + periodHeaderH, COLOR_BORDER, 0.8f)
        y += periodHeaderH

        // Fixed-height Class-Section Rows (readable fonts, zero clipping)
        val rowH = MASTER_FIXED_ROW_HEIGHT
        val subjFontSize = if (colW >= 18f) 5.8f else 5.0f
        val tchrFontSize = if (colW >= 18f) 5.1f else 4.5f

        plan.rows.forEachIndexed { rIdx, row ->
            val fill = if (rIdx % 2 == 1) COLOR_ZEBRA else COLOR_WHITE
            drawFilledRect(canvas, left, y, right, y + rowH, fill)
            drawFilledRect(canvas, left, y, left + classColW, y + rowH, COLOR_TINT)
            drawTwoLineCenteredText(
                canvas = canvas,
                line1 = row.label,
                line2 = row.classTeacherShort,
                cx = left + classColW / 2f,
                cy = y + rowH / 2f,
                size1 = 6.8f,
                size2 = 5.4f,
                color1 = COLOR_TEXT,
                color2 = COLOR_SUBTEXT,
                maxWidth = classColW - 4f
            )

            xCursor = left + classColW
            flatCols.forEachIndexed { cIdx, (day, _, slot) ->
                val isDayBoundary = cIdx > 0 && flatCols[cIdx - 1].first != day
                drawVerticalLine(canvas, xCursor, y, y + rowH, COLOR_BORDER, if (isDayBoundary) 0.9f else 0.35f)
                val cell = row.cellsByDayAndSlot[day to slot.id]
                if (cell != null) {
                    val shortSub = abbreviateForMasterCell(cell.subjectName, colW)
                    val shortTchr = abbreviateTeacherForMasterCell(cell.teacherName.ifBlank { cell.teacherId }, colW)
                    drawTwoLineCenteredText(
                        canvas = canvas,
                        line1 = shortSub,
                        line2 = shortTchr,
                        cx = xCursor + colW / 2f,
                        cy = y + rowH / 2f,
                        size1 = subjFontSize,
                        size2 = tchrFontSize,
                        color1 = COLOR_TEXT,
                        color2 = COLOR_SUBTEXT,
                        maxWidth = colW - 2f
                    )
                }
                xCursor += colW
            }
            drawBorderRect(canvas, left, y, right, y + rowH, COLOR_BORDER, 0.55f)
            y += rowH
        }

        drawPageFooter(
            canvas,
            left,
            right,
            pageH - MARGIN + 4f,
            "${bundle.school.name}  •  Master Timetable (${plan.groupTitle})  •  Page $pageNumber/$totalPages"
        )
    }

    // =========================================================================
    // 6. SHARED HEADER, FOOTER & HIGH-DPI TEXT PRIMITIVES
    // =========================================================================

    private fun drawSchoolHeader(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        school: SchoolEntity,
        subtitle: String,
        compact: Boolean = false
    ): Float {
        val headerH = if (compact) 52f else 62f
        val bandH = 18f

        // Outer box
        drawFilledRect(canvas, left, top, left + width, top + headerH, COLOR_WHITE)
        drawBorderRect(canvas, left, top, left + width, top + headerH + bandH, COLOR_BAND, 1.0f)

        // Logo on left
        val logoSize = if (compact) 38f else 46f
        val logoRect = RectF(left + 8f, top + (headerH - logoSize) / 2f, left + 8f + logoSize, top + (headerH + logoSize) / 2f)
        val logoBmp = ImageUtils.resolveSchoolLogoBitmap(school.logoBase64)
        if (logoBmp != null) {
            canvas.drawBitmap(logoBmp, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            logoBmp.recycle()
        } else {
            drawFilledRect(canvas, logoRect.left, logoRect.top, logoRect.right, logoRect.bottom, COLOR_TINT)
            drawBorderRect(canvas, logoRect.left, logoRect.top, logoRect.right, logoRect.bottom, COLOR_BORDER, 0.6f)
            val initials = school.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "SC" }
            drawCenteredText(canvas, initials, logoRect.centerX(), logoRect.centerY(), 11f, COLOR_BAND, bold = true, maxWidth = logoSize - 4f)
        }

        // School Name & Address centered
        val textLeft = logoRect.right + 8f
        val textMaxW = width - (logoSize + 24f)
        val cx = left + width / 2f
        drawCenteredText(
            canvas = canvas,
            text = school.name.uppercase(),
            cx = cx.coerceAtLeast(textLeft + textMaxW / 2f - 10f),
            cy = top + (if (compact) 17f else 21f),
            sizePt = if (compact) 12.5f else 14f,
            color = COLOR_BAND,
            bold = true,
            maxWidth = textMaxW
        )
        val addrLine = buildString {
            if (school.address.isNotBlank()) append(school.address)
            if (school.mobile.isNotBlank()) {
                if (isNotEmpty()) append("  •  Ph: ")
                append(school.mobile)
            }
        }
        if (addrLine.isNotBlank()) {
            drawCenteredText(
                canvas = canvas,
                text = addrLine,
                cx = cx.coerceAtLeast(textLeft + textMaxW / 2f - 10f),
                cy = top + (if (compact) 34f else 42f),
                sizePt = 7.8f,
                color = COLOR_SUBTEXT,
                bold = false,
                maxWidth = textMaxW
            )
        }

        // Subtitle Dark Band
        val bandTop = top + headerH
        drawFilledRect(canvas, left, bandTop, left + width, bandTop + bandH, COLOR_BAND)
        drawCenteredText(canvas, subtitle, left + width / 2f, bandTop + bandH / 2f, 8.2f, COLOR_WHITE, bold = true, maxWidth = width - 14f)

        return bandTop + bandH
    }

    private fun drawPageFooter(canvas: Canvas, left: Float, right: Float, baselineY: Float, text: String) {
        drawLeftText(canvas, text, left, baselineY, 6.8f, COLOR_SUBTEXT, bold = false, maxWidth = right - left)
    }

    private fun abbreviateForMasterCell(subject: String, colW: Float): String {
        val clean = subject.trim()
        if (colW >= 24f) return TimetableEngine.shortSubjectName(clean).take(6)
        val upper = clean.uppercase()
        return when {
            upper.startsWith("MATH") -> "MTH"
            upper.startsWith("ENGL") -> "ENG"
            upper.startsWith("HIND") -> "HIN"
            upper.startsWith("SCIE") -> "SCI"
            upper.startsWith("SOCI") -> "SST"
            upper.startsWith("COMP") -> "CMP"
            upper.startsWith("SANS") -> "SNK"
            upper.startsWith("PHYS") -> "PHY"
            upper.startsWith("CHEM") -> "CHM"
            upper.startsWith("BIOL") -> "BIO"
            upper.startsWith("HIST") -> "HIS"
            upper.startsWith("GEOG") -> "GEO"
            upper.startsWith("ECON") -> "ECO"
            upper.startsWith("ART") -> "ART"
            upper.startsWith("G.K") || upper.startsWith("GENE") -> "GK"
            upper.startsWith("E.V") || upper.startsWith("ENVI") -> "EVS"
            else -> clean.take(4).uppercase()
        }
    }

    private fun abbreviateTeacherForMasterCell(teacherName: String, colW: Float): String {
        val clean = teacherName.trim()
        if (clean.isEmpty()) return ""
        if (colW >= 24f) return TimetableEngine.shortTeacherName(clean).take(7)
        val honorifics = setOf("mr", "mr.", "mrs", "mrs.", "ms", "ms.", "dr", "dr.")
        val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() && it.lowercase() !in honorifics }
        if (parts.isEmpty()) return clean.take(3).uppercase()
        return if (parts.size >= 2) {
            "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
        } else {
            parts.first().take(3)
        }
    }

    private fun makeTextPaint(sizePt: Float, color: Int, bold: Boolean, align: Paint.Align): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            this.textSize = sizePt * TEXT_SCALE
            this.typeface = if (bold) TYPEFACE_BOLD else TYPEFACE_REGULAR
            this.textAlign = align
            this.letterSpacing = LETTER_SPACING_EM
            this.hinting = Paint.HINTING_OFF
        }

    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidthPt: Float): String {
        val maxPx = (maxWidthPt * TEXT_SCALE).coerceAtLeast(10f)
        if (paint.measureText(text) <= maxPx) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxPx) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        sizePt: Float,
        color: Int,
        bold: Boolean,
        maxWidth: Float
    ) {
        if (text.isBlank()) return
        val paint = makeTextPaint(sizePt, color, bold, Paint.Align.CENTER)
        val fitted = ellipsizeToWidth(text, paint, maxWidth)
        val fm = paint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(fitted, cx * TEXT_SCALE, cy * TEXT_SCALE + baselineOffset, paint)
        canvas.restore()
    }

    private fun drawLeftText(
        canvas: Canvas,
        text: String,
        x: Float,
        cy: Float,
        sizePt: Float,
        color: Int,
        bold: Boolean,
        maxWidth: Float
    ) {
        if (text.isBlank()) return
        val paint = makeTextPaint(sizePt, color, bold, Paint.Align.LEFT)
        val fitted = ellipsizeToWidth(text, paint, maxWidth)
        val fm = paint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f
        canvas.save()
        canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
        canvas.drawText(fitted, x * TEXT_SCALE, cy * TEXT_SCALE + baselineOffset, paint)
        canvas.restore()
    }

    private fun drawTwoLineCenteredText(
        canvas: Canvas,
        line1: String,
        line2: String,
        cx: Float,
        cy: Float,
        size1: Float,
        size2: Float,
        color1: Int,
        color2: Int,
        maxWidth: Float
    ) {
        if (line2.isBlank()) {
            drawCenteredText(canvas, line1, cx, cy, size1, color1, bold = true, maxWidth = maxWidth)
            return
        }
        val dy = ((size1 + size2) * 0.38f).coerceAtLeast(4.5f)
        drawCenteredText(canvas, line1, cx, cy - dy, size1, color1, bold = true, maxWidth = maxWidth)
        drawCenteredText(canvas, line2, cx, cy + dy, size2, color2, bold = false, maxWidth = maxWidth)
    }

    private fun drawFilledRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawRect(l, t, r, b, p)
    }

    private fun drawBorderRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, strokeW: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = strokeW
        }
        canvas.drawRect(l, t, r, b, p)
    }

    private fun drawVerticalLine(canvas: Canvas, x: Float, y0: Float, y1: Float, color: Int, strokeW: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = strokeW
        }
        canvas.drawLine(x, y0, x, y1, p)
    }

    private fun safeSlug(raw: String, fallback: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(28).ifEmpty { fallback }
}
