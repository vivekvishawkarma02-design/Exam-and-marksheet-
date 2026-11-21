package com.example.util

import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.MarkEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.domain.MarkComponent
import com.example.domain.MarkValidationResult
import com.example.domain.ReportCalculations
import com.example.domain.SchoolClass
import org.json.JSONArray
import org.json.JSONObject

data class CsvImportResult(
    val parsedStudents: List<StudentEntity>,
    val skippedRows: Int
)

data class MarksCsvIssue(
    val rowNumber: Int,
    val summary: String
)

data class MarksCsvParseResult(
    val validMarks: List<MarkEntity>,
    val matchedStudentIds: Set<Long>,
    val processedRowCount: Int,
    val skippedEmptyCells: Int,
    val issues: List<MarksCsvIssue>
)

object BackupAndCsvManager {

    private const val BACKUP_FORMAT_VERSION = 2

    fun serializeBackupToJson(data: FullBackupData): String {
        val root = JSONObject()
        root.put("app", "CPI-CPS Report Card")
        root.put("version", BACKUP_FORMAT_VERSION)
        root.put("exportedAtMs", System.currentTimeMillis())

        val s = data.settings
        root.put(
            "settings",
            JSONObject().apply {
                put("cpsName", s.cpsName)
                put("cpsAddress", s.cpsAddress)
                put("cpsMobile", s.cpsMobile)
                put("cpsEmail", s.cpsEmail)
                put("cpsLogoBase64", s.cpsLogoBase64)
                put("cpsLogoCleared", s.cpsLogoCleared)
                put("cpiName", s.cpiName)
                put("cpiAddress", s.cpiAddress)
                put("cpiMobile", s.cpiMobile)
                put("cpiEmail", s.cpiEmail)
                put("cpiLogoBase64", s.cpiLogoBase64)
                put("cpiLogoCleared", s.cpiLogoCleared)
                put("useSameLogoForBoth", s.useSameLogoForBoth)
                put("classIdentityMapCsv", s.classIdentityMapCsv)
                put("currentSession", s.currentSession)
                put("sessionsCsv", s.sessionsCsv)
                put("passPercent", s.passPercent)
                put("showPhotoAndAdmOnAnnual", s.showPhotoAndAdmOnAnnual)
                put("showClassRank", s.showClassRank)
                put("lastBackupEpochMs", s.lastBackupEpochMs)
            }
        )

        root.put(
            "subjects",
            JSONArray().apply {
                data.subjects.forEach { sub ->
                    put(
                        JSONObject().apply {
                            put("classGroup", sub.classGroup)
                            put("name", sub.name)
                            put("sortOrder", sub.sortOrder)
                        }
                    )
                }
            }
        )

        root.put(
            "students",
            JSONArray().apply {
                data.students.forEach { st ->
                    put(
                        JSONObject().apply {
                            put("id", st.id)
                            put("session", st.session)
                            put("admNo", st.admNo)
                            put("name", st.name)
                            put("fatherName", st.fatherName)
                            put("motherName", st.motherName)
                            put("dob", st.dob)
                            put("mobile", st.mobile)
                            put("address", st.address)
                            put("photoBase64", st.photoBase64)
                            put("className", st.className)
                            put("section", st.section)
                            put("rollNo", st.rollNo)
                            put("isSample", st.isSample)
                        }
                    )
                }
            }
        )

        root.put(
            "marks",
            JSONArray().apply {
                data.marks.forEach { m ->
                    put(
                        JSONObject().apply {
                            put("studentId", m.studentId)
                            put("subjectName", m.subjectName)
                            put("componentKey", m.componentKey)
                            put("rawValue", m.rawValue)
                        }
                    )
                }
            }
        )

        root.put(
            "coScholastic",
            JSONArray().apply {
                data.coScholastic.forEach { c ->
                    put(
                        JSONObject().apply {
                            put("studentId", c.studentId)
                            put("termNo", c.termNo)
                            put("areaCode", c.areaCode)
                            put("grade", c.grade)
                        }
                    )
                }
            }
        )

        root.put(
            "termInfos",
            JSONArray().apply {
                data.termInfos.forEach { t ->
                    put(
                        JSONObject().apply {
                            put("studentId", t.studentId)
                            put("termNo", t.termNo)
                            put("daysPresent", t.daysPresent)
                            put("workingDays", t.workingDays)
                            put("remarks", t.remarks)
                            put("promotedTo", t.promotedTo)
                            put("reportDate", t.reportDate)
                        }
                    )
                }
            }
        )

        return root.toString(2)
    }

    fun deserializeBackupJson(jsonText: String): Result<FullBackupData> {
        return try {
            val root = JSONObject(jsonText)
            val sObj = root.optJSONObject("settings")
            val defaultMap = SchoolClass.serializeIdentityMap(SchoolClass.defaultClassIdentityMap())
            val settings = if (sObj != null) {
                val legacyName = sObj.optString("schoolName", "C.P.I. SCHOOL")
                val legacyAddress = sObj.optString("address", "Harikaranpur Bijaura Ghazipur (U.P.)")
                val legacyMobile = sObj.optString("mobile", "")
                val legacyLogo = sObj.optString("logoBase64", "")
                SchoolSettingsEntity(
                    id = 1,
                    cpsName = sObj.optString("cpsName", "C.P.S. SCHOOL"),
                    cpsAddress = sObj.optString("cpsAddress", legacyAddress),
                    cpsMobile = sObj.optString("cpsMobile", legacyMobile),
                    cpsEmail = sObj.optString("cpsEmail", ""),
                    cpsLogoBase64 = sObj.optString("cpsLogoBase64", ""),
                    cpsLogoCleared = sObj.optBoolean("cpsLogoCleared", false),
                    cpiName = sObj.optString("cpiName", legacyName),
                    cpiAddress = sObj.optString("cpiAddress", legacyAddress),
                    cpiMobile = sObj.optString("cpiMobile", legacyMobile),
                    cpiEmail = sObj.optString("cpiEmail", ""),
                    cpiLogoBase64 = sObj.optString("cpiLogoBase64", legacyLogo),
                    cpiLogoCleared = sObj.optBoolean("cpiLogoCleared", false),
                    useSameLogoForBoth = sObj.optBoolean("useSameLogoForBoth", false),
                    classIdentityMapCsv = sObj.optString("classIdentityMapCsv", defaultMap),
                    currentSession = sObj.optString("currentSession", "2026-2027"),
                    sessionsCsv = sObj.optString("sessionsCsv", "2026-2027"),
                    passPercent = sObj.optInt("passPercent", 33).coerceIn(1, 100),
                    showPhotoAndAdmOnAnnual = sObj.optBoolean("showPhotoAndAdmOnAnnual", false),
                    showClassRank = sObj.optBoolean("showClassRank", true),
                    lastBackupEpochMs = sObj.optLong("lastBackupEpochMs", 0L)
                )
            } else {
                SchoolSettingsEntity()
            }

            val subjects = mutableListOf<SubjectEntity>()
            val subArr = root.optJSONArray("subjects") ?: JSONArray()
            for (i in 0 until subArr.length()) {
                val o = subArr.getJSONObject(i)
                val name = o.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    subjects += SubjectEntity(
                        classGroup = o.optInt("classGroup", 1),
                        name = name,
                        sortOrder = o.optInt("sortOrder", i)
                    )
                }
            }

            val students = mutableListOf<StudentEntity>()
            val stArr = root.optJSONArray("students") ?: JSONArray()
            for (i in 0 until stArr.length()) {
                val o = stArr.getJSONObject(i)
                val admNo = o.optString("admNo", "").trim()
                val name = o.optString("name", "").trim()
                if (admNo.isNotEmpty() && name.isNotEmpty()) {
                    val normalizedClass = SchoolClass.fromName(o.optString("className", "1st")).displayName
                    students += StudentEntity(
                        id = o.optLong("id", (i + 1).toLong()),
                        session = o.optString("session", settings.currentSession),
                        admNo = admNo,
                        name = name,
                        fatherName = o.optString("fatherName", ""),
                        motherName = o.optString("motherName", ""),
                        dob = o.optString("dob", ""),
                        mobile = o.optString("mobile", ""),
                        address = o.optString("address", ""),
                        photoBase64 = o.optString("photoBase64", ""),
                        className = normalizedClass,
                        section = o.optString("section", "A"),
                        rollNo = o.optInt("rollNo", i + 1),
                        isSample = o.optBoolean("isSample", false)
                    )
                }
            }

            val marks = mutableListOf<MarkEntity>()
            val mArr = root.optJSONArray("marks") ?: JSONArray()
            for (i in 0 until mArr.length()) {
                val o = mArr.getJSONObject(i)
                val rawKey = o.optString("componentKey", "")
                val normalizedKey = when (rawKey) {
                    "T1_A_IA" -> "T1_IA"
                    "T1_A_UT1" -> "T1_UT"
                    "T1_A_HY" -> "T1_EXAM"
                    else -> rawKey
                }
                marks += MarkEntity(
                    studentId = o.optLong("studentId", -1L),
                    subjectName = o.optString("subjectName", ""),
                    componentKey = normalizedKey,
                    rawValue = o.optString("rawValue", "")
                )
            }

            val coList = mutableListOf<CoScholasticEntity>()
            val coArr = root.optJSONArray("coScholastic") ?: JSONArray()
            for (i in 0 until coArr.length()) {
                val o = coArr.getJSONObject(i)
                coList += CoScholasticEntity(
                    studentId = o.optLong("studentId", -1L),
                    termNo = o.optInt("termNo", 1),
                    areaCode = o.optString("areaCode", ""),
                    grade = o.optString("grade", "")
                )
            }

            val termInfos = mutableListOf<TermInfoEntity>()
            val tiArr = root.optJSONArray("termInfos") ?: JSONArray()
            for (i in 0 until tiArr.length()) {
                val o = tiArr.getJSONObject(i)
                termInfos += TermInfoEntity(
                    studentId = o.optLong("studentId", -1L),
                    termNo = o.optInt("termNo", 1),
                    daysPresent = o.optInt("daysPresent", 0),
                    workingDays = o.optInt("workingDays", 0),
                    remarks = o.optString("remarks", ""),
                    promotedTo = o.optString("promotedTo", ""),
                    reportDate = o.optString("reportDate", "")
                )
            }

            Result.success(
                FullBackupData(
                    settings = settings,
                    subjects = subjects,
                    students = students,
                    marks = marks,
                    coScholastic = coList,
                    termInfos = termInfos
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid backup JSON file: ${e.message}"))
        }
    }

    fun parseStudentsCsv(
        csvText: String,
        defaultSession: String,
        defaultClass: String,
        defaultSection: String,
        startingRollNo: Int
    ): CsvImportResult {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return CsvImportResult(emptyList(), 0)

        val firstCols = splitCsvLine(lines.first()).map { it.lowercase().replace(" ", "").replace("_", "") }
        val hasHeader = firstCols.any { it.contains("adm") || it == "name" || it.contains("student") }

        val dataLines = if (hasHeader) lines.drop(1) else lines
        val headerMap = if (hasHeader) {
            firstCols.withIndex().associate { (idx, name) -> name to idx }
        } else {
            emptyMap()
        }

        fun colIndex(vararg names: String, fallback: Int): Int {
            if (!hasHeader) return fallback
            for (n in names) {
                val match = headerMap.entries.firstOrNull { it.key.contains(n) }?.value
                if (match != null) return match
            }
            return -1
        }

        val idxAdm = colIndex("adm", fallback = 0)
        val idxName = colIndex("studentname", "name", fallback = 1)
        val idxFather = colIndex("father", fallback = 2)
        val idxMother = colIndex("mother", fallback = 3)
        val idxDob = colIndex("dob", "birth", fallback = 4)
        val idxMobile = colIndex("mobile", "phone", "mob", fallback = 5)
        val idxAddress = colIndex("address", "addr", fallback = 6)
        val idxClass = colIndex("class", fallback = 7)
        val idxSection = colIndex("sec", fallback = 8)
        val idxRoll = colIndex("roll", fallback = 9)
        val idxSession = colIndex("session", fallback = 10)

        val parsed = mutableListOf<StudentEntity>()
        var skipped = 0
        var nextRoll = startingRollNo

        dataLines.forEach { line ->
            val cols = splitCsvLine(line)
            fun getCol(index: Int): String = if (index in cols.indices) cols[index].trim() else ""

            val admNo = getCol(idxAdm)
            val name = getCol(idxName)
            if (admNo.isEmpty() || name.isEmpty()) {
                skipped++
                return@forEach
            }
            val rawClass = getCol(idxClass).ifEmpty { defaultClass }
            val normalizedClass = SchoolClass.fromName(rawClass).displayName
            val section = getCol(idxSection).uppercase().ifEmpty { defaultSection }
            val roll = getCol(idxRoll).toIntOrNull() ?: nextRoll++
            val session = getCol(idxSession).ifEmpty { defaultSession }

            parsed += StudentEntity(
                session = session,
                admNo = admNo,
                name = name,
                fatherName = getCol(idxFather),
                motherName = getCol(idxMother),
                dob = getCol(idxDob),
                mobile = getCol(idxMobile),
                address = getCol(idxAddress),
                className = normalizedClass,
                section = section,
                rollNo = roll,
                isSample = false
            )
        }
        return CsvImportResult(parsed, skipped)
    }

    const val MARKS_CSV_HEADER =
        "session,class,section,rollNo,admNo,studentName,subject,T1_IA,T1_UT,T1_EXAM,T2_IA,T2_UT,T2_EXAM"

    /**
     * Generates a standardized marks CSV template pre-populated with students & subjects for the
     * selected class/section (including any already-saved marks) so teachers can quickly fill
     * marks in Excel or Google Sheets for a new academic session.
     */
    fun generateMarksCsvTemplate(
        session: String,
        className: String,
        section: String,
        students: List<StudentEntity>,
        subjects: List<String>,
        existingMarksByStudent: Map<Long, Map<Pair<String, String>, String>> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.appendLine(MARKS_CSV_HEADER)
        val effectiveSubjects = subjects.ifEmpty { listOf("ENGLISH", "HINDI", "MATH") }

        if (students.isEmpty()) {
            effectiveSubjects.forEach { sub ->
                sb.appendLine(
                    listOf(
                        session,
                        className,
                        section,
                        "1",
                        "ADM-001",
                        "Sample Student",
                        sub,
                        "", "", "", "", "", ""
                    ).joinToString(",") { escapeCsvCell(it) }
                )
            }
            return sb.toString()
        }

        students.sortedBy { it.rollNo }.forEach { st ->
            val stMarks = existingMarksByStudent[st.id].orEmpty()
            effectiveSubjects.forEach { sub ->
                sb.appendLine(
                    listOf(
                        st.session,
                        st.className,
                        st.section,
                        st.rollNo.toString(),
                        st.admNo,
                        st.name,
                        sub,
                        stMarks[sub to MarkComponent.T1_IA.key].orEmpty(),
                        stMarks[sub to MarkComponent.T1_UT.key].orEmpty(),
                        stMarks[sub to MarkComponent.T1_EXAM.key].orEmpty(),
                        stMarks[sub to MarkComponent.T2_IA.key].orEmpty(),
                        stMarks[sub to MarkComponent.T2_UT.key].orEmpty(),
                        stMarks[sub to MarkComponent.T2_EXAM.key].orEmpty()
                    ).joinToString(",") { escapeCsvCell(it) }
                )
            }
        }
        return sb.toString()
    }

    /**
     * Parses and validates a standardized marks CSV against existing students and class subjects.
     * Supports matching by (session, admNo) first and (session, class, section, rollNo) as fallback.
     * Blank mark cells are safely skipped; invalid marks (e.g., > 10/30/60) are reported as row issues.
     */
    fun parseMarksCsv(
        csvText: String,
        students: List<StudentEntity>,
        subjectsByGroup: Map<Int, List<String>>,
        defaultSession: String,
        defaultClass: String,
        defaultSection: String
    ): MarksCsvParseResult {
        val cleanedText = csvText.removePrefix("\uFEFF")
        val rawLines = cleanedText.lines()
            .mapIndexed { idx, line -> (idx + 1) to line.trim() }
            .filter { it.second.isNotEmpty() }

        if (rawLines.isEmpty()) {
            return MarksCsvParseResult(
                validMarks = emptyList(),
                matchedStudentIds = emptySet(),
                processedRowCount = 0,
                skippedEmptyCells = 0,
                issues = listOf(MarksCsvIssue(1, "CSV file is empty."))
            )
        }

        val firstCols = splitCsvLine(rawLines.first().second).map(::normalizeHeaderKey)
        val hasHeader = firstCols.any {
            it == "subject" || it == "subjectname" || it.contains("t1ia") || it.contains("t1ut") ||
                it.contains("t1exam") || it.contains("t2ia") || it.contains("adm") || it.contains("roll")
        }

        val dataLines = if (hasHeader) rawLines.drop(1) else rawLines
        val headerMap = if (hasHeader) {
            firstCols.withIndex().associate { (idx, name) -> name to idx }
        } else {
            emptyMap()
        }

        fun colIndex(aliases: List<String>, fallback: Int): Int {
            if (!hasHeader) return fallback
            for (alias in aliases) {
                headerMap[alias]?.let { return it }
            }
            for (alias in aliases) {
                headerMap.entries.firstOrNull { it.key.contains(alias) }?.value?.let { return it }
            }
            return -1
        }

        val idxSession = colIndex(listOf("session", "academicsession", "year"), fallback = 0)
        val idxClass = colIndex(listOf("class", "classname", "grade", "std"), fallback = 1)
        val idxSection = colIndex(listOf("section", "sec"), fallback = 2)
        val idxRoll = colIndex(listOf("rollno", "roll", "rn"), fallback = 3)
        val idxAdm = colIndex(listOf("admno", "admissionno", "admission", "adm"), fallback = 4)
        val idxName = colIndex(listOf("studentname", "name"), fallback = 5)
        val idxSubject = colIndex(listOf("subjectname", "subject", "sub"), fallback = 6)

        val compColumns: List<Pair<MarkComponent, Int>> = listOf(
            MarkComponent.T1_IA to colIndex(listOf("t1ia", "term1ia", "t1intass", "ia1", "intass1", "internal1"), fallback = 7),
            MarkComponent.T1_UT to colIndex(listOf("t1ut", "t1ut1", "term1ut", "ut1", "unittest1"), fallback = 8),
            MarkComponent.T1_EXAM to colIndex(listOf("t1exam", "t1hy", "term1exam", "hy", "hyexam", "halfyearly"), fallback = 9),
            MarkComponent.T2_IA to colIndex(listOf("t2ia", "term2ia", "t2intass", "ia2", "intass2", "internal2"), fallback = 10),
            MarkComponent.T2_UT to colIndex(listOf("t2ut", "t2ut2", "term2ut", "ut2", "unittest2"), fallback = 11),
            MarkComponent.T2_EXAM to colIndex(listOf("t2exam", "t2yearly", "term2exam", "yearly", "annual", "finalexam"), fallback = 12)
        )

        if (idxSubject < 0) {
            return MarksCsvParseResult(
                validMarks = emptyList(),
                matchedStudentIds = emptySet(),
                processedRowCount = 0,
                skippedEmptyCells = 0,
                issues = listOf(MarksCsvIssue(1, "Missing required 'subject' column in CSV header."))
            )
        }
        if (compColumns.all { it.second < 0 }) {
            return MarksCsvParseResult(
                validMarks = emptyList(),
                matchedStudentIds = emptySet(),
                processedRowCount = 0,
                skippedEmptyCells = 0,
                issues = listOf(
                    MarksCsvIssue(
                        1,
                        "No mark columns found. Expected columns such as T1_IA, T1_UT, T1_EXAM, T2_IA, T2_UT, T2_EXAM."
                    )
                )
            )
        }

        // Fast lookup indices for matching students
        val bySessionAndAdm = students.associateBy {
            it.session.trim().lowercase() to it.admNo.trim().lowercase()
        }
        val bySessionClassSecRoll = students.associateBy {
            listOf(
                it.session.trim().lowercase(),
                SchoolClass.fromName(it.className).displayName.lowercase(),
                it.section.trim().uppercase(),
                it.rollNo.toString()
            )
        }

        val dedupedMarks = LinkedHashMap<Triple<Long, String, String>, MarkEntity>()
        val matchedStudents = LinkedHashSet<Long>()
        val issues = mutableListOf<MarksCsvIssue>()
        var skippedEmpty = 0

        dataLines.forEach { (rowNum, line) ->
            val cols = splitCsvLine(line)
            fun getCol(index: Int): String = if (index in cols.indices) cols[index].trim() else ""

            val session = getCol(idxSession).ifEmpty { defaultSession }.trim()
            val rawClass = getCol(idxClass).ifEmpty { defaultClass }.trim()
            val normalizedClass = SchoolClass.fromName(rawClass).displayName
            val section = getCol(idxSection).uppercase().ifEmpty { defaultSection.uppercase() }
            val admNo = getCol(idxAdm)
            val rollNo = getCol(idxRoll).toIntOrNull()
            val csvName = getCol(idxName)
            val rawSubject = getCol(idxSubject)

            if (rawSubject.isEmpty()) {
                issues += MarksCsvIssue(rowNum, "Row $rowNum: Missing subject name.")
                return@forEach
            }

            val student = when {
                admNo.isNotEmpty() -> bySessionAndAdm[session.lowercase() to admNo.lowercase()]
                    ?: rollNo?.let {
                        bySessionClassSecRoll[listOf(session.lowercase(), normalizedClass.lowercase(), section, it.toString())]
                    }
                rollNo != null -> bySessionClassSecRoll[listOf(session.lowercase(), normalizedClass.lowercase(), section, rollNo.toString())]
                else -> null
            }

            if (student == null) {
                val idLabel = when {
                    admNo.isNotEmpty() -> "Adm '$admNo'"
                    rollNo != null -> "Class $normalizedClass-$section Roll $rollNo"
                    csvName.isNotEmpty() -> "'$csvName'"
                    else -> "Unknown student"
                }
                issues += MarksCsvIssue(rowNum, "Row $rowNum: Student $idLabel not found in session $session.")
                return@forEach
            }

            // Resolve subject name case-insensitively against configured subjects for the student's class group
            val group = SchoolClass.fromName(student.className).group.id
            val configuredSubjects = subjectsByGroup[group].orEmpty()
            val resolvedSubject = configuredSubjects.firstOrNull { it.equals(rawSubject, ignoreCase = true) }
                ?: rawSubject.uppercase()

            var rowMatchedAny = false
            compColumns.forEach { (component, colIdx) ->
                if (colIdx < 0) return@forEach
                val cellVal = getCol(colIdx)
                if (cellVal.isEmpty()) {
                    skippedEmpty++
                    return@forEach
                }
                when (val validation = ReportCalculations.validateMarkInput(cellVal, component.maxMarks)) {
                    is MarkValidationResult.Valid -> {
                        if (validation.normalized.isNotEmpty()) {
                            val key = Triple(student.id, resolvedSubject, component.key)
                            dedupedMarks[key] = MarkEntity(
                                studentId = student.id,
                                subjectName = resolvedSubject,
                                componentKey = component.key,
                                rawValue = validation.normalized
                            )
                            rowMatchedAny = true
                        }
                    }
                    is MarkValidationResult.Invalid -> {
                        issues += MarksCsvIssue(
                            rowNum,
                            "Row $rowNum (${student.name}, $resolvedSubject, ${component.key}): ${validation.message}"
                        )
                    }
                }
            }
            if (rowMatchedAny) {
                matchedStudents += student.id
            }
        }

        return MarksCsvParseResult(
            validMarks = dedupedMarks.values.toList(),
            matchedStudentIds = matchedStudents,
            processedRowCount = dataLines.size,
            skippedEmptyCells = skippedEmpty,
            issues = issues
        )
    }

    private fun normalizeHeaderKey(raw: String): String =
        raw.removePrefix("\uFEFF")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")

    private fun escapeCsvCell(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
