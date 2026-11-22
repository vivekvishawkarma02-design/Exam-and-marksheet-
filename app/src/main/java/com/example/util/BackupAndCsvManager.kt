package com.example.util

import com.example.data.ClassGroupEntity
import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.LegacyBackupPayload
import com.example.data.LegacyBackupStudent
import com.example.data.LegacyBackupSubject
import com.example.data.LegacySuggestedSchool
import com.example.data.MarkEntity
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.MarkComponent
import com.example.domain.MarkValidationResult
import com.example.domain.ReportCalculations
import org.json.JSONArray
import org.json.JSONObject

sealed class ParsedBackupResult {
    data class ModernBackup(val data: FullBackupData) : ParsedBackupResult()
    data class LegacyBackup(val payload: LegacyBackupPayload) : ParsedBackupResult()
}

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

    private const val BACKUP_FORMAT_VERSION = 3

    fun serializeBackupToJson(data: FullBackupData): String {
        val root = JSONObject()
        root.put("app", "School Report Card")
        root.put("version", BACKUP_FORMAT_VERSION)
        root.put("exportedAtMs", System.currentTimeMillis())

        val s = data.settings
        root.put(
            "settings",
            JSONObject().apply {
                put("currentSession", s.currentSession)
                put("sessionsCsv", s.sessionsCsv)
                put("passPercent", s.passPercent)
                put("showPhotoAndAdmOnAnnual", s.showPhotoAndAdmOnAnnual)
                put("showClassRank", s.showClassRank)
                put("lastBackupEpochMs", s.lastBackupEpochMs)
            }
        )

        root.put(
            "schools",
            JSONArray().apply {
                data.schools.forEach { school ->
                    put(
                        JSONObject().apply {
                            put("id", school.id)
                            put("name", school.name)
                            put("address", school.address)
                            put("mobile", school.mobile)
                            put("email", school.email)
                            put("logoBase64", school.logoBase64)
                            put("active", school.active)
                        }
                    )
                }
            }
        )

        root.put(
            "classGroups",
            JSONArray().apply {
                data.classGroups.forEach { grp ->
                    put(
                        JSONObject().apply {
                            put("id", grp.id)
                            put("schoolId", grp.schoolId)
                            put("name", grp.name)
                            put("shortLabel", grp.shortLabel)
                            put("sortOrder", grp.sortOrder)
                            put("templateKey", grp.templateKey)
                        }
                    )
                }
            }
        )

        root.put(
            "schoolClasses",
            JSONArray().apply {
                data.schoolClasses.forEach { cls ->
                    put(
                        JSONObject().apply {
                            put("id", cls.id)
                            put("schoolId", cls.schoolId)
                            put("classGroupId", cls.classGroupId)
                            put("name", cls.name)
                            put("sortOrder", cls.sortOrder)
                        }
                    )
                }
            }
        )

        root.put(
            "subjects",
            JSONArray().apply {
                data.subjects.forEach { sub ->
                    put(
                        JSONObject().apply {
                            put("id", sub.id)
                            put("schoolId", sub.schoolId)
                            put("classGroupId", sub.classGroupId)
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
                            put("schoolId", st.schoolId)
                            put("classId", st.classId)
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

    /**
     * Parses both modern v3 multi-school backups and legacy pre-Phase-1 (v1/v2) backups.
     * Legacy backups return [ParsedBackupResult.LegacyBackup] so the UI can ask the user
     * which school the old students belong to before attaching them.
     */
    fun deserializeBackupJson(jsonText: String): Result<ParsedBackupResult> {
        return try {
            val root = JSONObject(jsonText)
            val version = root.optInt("version", 1)
            val schoolsArr = root.optJSONArray("schools")
            val isModern = version >= 3 && schoolsArr != null

            if (!isModern) {
                return Result.success(ParsedBackupResult.LegacyBackup(parseLegacyBackup(root)))
            }

            val sObj = root.optJSONObject("settings")
            val settings = if (sObj != null) {
                SchoolSettingsEntity(
                    id = 1,
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

            val schools = mutableListOf<SchoolEntity>()
            for (i in 0 until schoolsArr.length()) {
                val o = schoolsArr.getJSONObject(i)
                val name = o.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    schools += SchoolEntity(
                        id = o.optLong("id", (i + 1).toLong()),
                        name = name,
                        address = o.optString("address", ""),
                        mobile = o.optString("mobile", ""),
                        email = o.optString("email", ""),
                        logoBase64 = o.optString("logoBase64", ""),
                        active = o.optBoolean("active", true)
                    )
                }
            }

            val classGroups = mutableListOf<ClassGroupEntity>()
            val cgArr = root.optJSONArray("classGroups") ?: JSONArray()
            for (i in 0 until cgArr.length()) {
                val o = cgArr.getJSONObject(i)
                val name = o.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    classGroups += ClassGroupEntity(
                        id = o.optLong("id", (i + 1).toLong()),
                        schoolId = o.optLong("schoolId", schools.firstOrNull()?.id ?: 1L),
                        name = name,
                        shortLabel = o.optString("shortLabel", name.take(12)),
                        sortOrder = o.optInt("sortOrder", i),
                        templateKey = o.optString("templateKey", "")
                    )
                }
            }

            val schoolClasses = mutableListOf<SchoolClassEntity>()
            val scArr = root.optJSONArray("schoolClasses") ?: JSONArray()
            for (i in 0 until scArr.length()) {
                val o = scArr.getJSONObject(i)
                val name = o.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    schoolClasses += SchoolClassEntity(
                        id = o.optLong("id", (i + 1).toLong()),
                        schoolId = o.optLong("schoolId", schools.firstOrNull()?.id ?: 1L),
                        classGroupId = o.optLong("classGroupId", classGroups.firstOrNull()?.id ?: 1L),
                        name = name,
                        sortOrder = o.optInt("sortOrder", i)
                    )
                }
            }

            val subjects = mutableListOf<SubjectEntity>()
            val subArr = root.optJSONArray("subjects") ?: JSONArray()
            for (i in 0 until subArr.length()) {
                val o = subArr.getJSONObject(i)
                val name = o.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    subjects += SubjectEntity(
                        id = o.optLong("id", (i + 1).toLong()),
                        schoolId = o.optLong("schoolId", schools.firstOrNull()?.id ?: 1L),
                        classGroupId = o.optLong("classGroupId", classGroups.firstOrNull()?.id ?: 1L),
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
                    students += StudentEntity(
                        id = o.optLong("id", (i + 1).toLong()),
                        schoolId = o.optLong("schoolId", schools.firstOrNull()?.id ?: 1L),
                        classId = o.optLong("classId", 0L),
                        session = o.optString("session", settings.currentSession),
                        admNo = admNo,
                        name = name,
                        fatherName = o.optString("fatherName", ""),
                        motherName = o.optString("motherName", ""),
                        dob = o.optString("dob", ""),
                        mobile = o.optString("mobile", ""),
                        address = o.optString("address", ""),
                        photoBase64 = o.optString("photoBase64", ""),
                        className = o.optString("className", "1st").trim(),
                        section = o.optString("section", "A"),
                        rollNo = o.optInt("rollNo", i + 1),
                        isSample = o.optBoolean("isSample", false)
                    )
                }
            }

            val marks = parseMarksArray(root.optJSONArray("marks"))
            val coList = parseCoScholasticArray(root.optJSONArray("coScholastic"))
            val termInfos = parseTermInfosArray(root.optJSONArray("termInfos"))

            Result.success(
                ParsedBackupResult.ModernBackup(
                    FullBackupData(
                        settings = settings,
                        schools = schools,
                        classGroups = classGroups,
                        schoolClasses = schoolClasses,
                        subjects = subjects,
                        students = students,
                        marks = marks,
                        coScholastic = coList,
                        termInfos = termInfos
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid backup JSON file: ${e.message}"))
        }
    }

    /**
     * Old-backup migration parser (pre-Phase-1 v1/v2 JSON backups).
     * Extracts suggested school info from legacy settings keys and preserves all students,
     * subjects, marks, co-scholastic grades, and term info without data loss.
     */
    private fun parseLegacyBackup(root: JSONObject): LegacyBackupPayload {
        val sObj = root.optJSONObject("settings")
        val settings = if (sObj != null) {
            SchoolSettingsEntity(
                id = 1,
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

        val suggestedSchools = mutableListOf<LegacySuggestedSchool>()
        if (sObj != null) {
            val useSameLogo = sObj.optBoolean("useSameLogoForBoth", false)
            val legacyPrimaryName = sObj.optString("cpsName", "").trim()
            val legacySecondaryName = sObj.optString("cpiName", "").trim()
            val legacySingleName = sObj.optString("schoolName", "").trim()

            if (legacyPrimaryName.isNotEmpty()) {
                suggestedSchools += LegacySuggestedSchool(
                    suggestedName = legacyPrimaryName,
                    address = sObj.optString("cpsAddress", sObj.optString("address", "")),
                    mobile = sObj.optString("cpsMobile", sObj.optString("mobile", "")),
                    email = sObj.optString("cpsEmail", ""),
                    logoBase64 = sObj.optString("cpsLogoBase64", "")
                )
            }
            if (legacySecondaryName.isNotEmpty()) {
                val secondaryLogo = if (useSameLogo) {
                    sObj.optString("cpsLogoBase64", "")
                } else {
                    sObj.optString("cpiLogoBase64", sObj.optString("logoBase64", ""))
                }
                suggestedSchools += LegacySuggestedSchool(
                    suggestedName = legacySecondaryName,
                    address = sObj.optString("cpiAddress", sObj.optString("address", "")),
                    mobile = sObj.optString("cpiMobile", sObj.optString("mobile", "")),
                    email = sObj.optString("cpiEmail", ""),
                    logoBase64 = secondaryLogo
                )
            }
            if (suggestedSchools.isEmpty() && legacySingleName.isNotEmpty()) {
                suggestedSchools += LegacySuggestedSchool(
                    suggestedName = legacySingleName,
                    address = sObj.optString("address", ""),
                    mobile = sObj.optString("mobile", ""),
                    email = sObj.optString("email", ""),
                    logoBase64 = sObj.optString("logoBase64", "")
                )
            }
        }
        if (suggestedSchools.isEmpty()) {
            suggestedSchools += LegacySuggestedSchool(
                suggestedName = "Imported School",
                address = "",
                mobile = "",
                email = "",
                logoBase64 = ""
            )
        }

        val subjects = mutableListOf<LegacyBackupSubject>()
        val subArr = root.optJSONArray("subjects") ?: JSONArray()
        for (i in 0 until subArr.length()) {
            val o = subArr.getJSONObject(i)
            val name = o.optString("name", "").trim()
            if (name.isNotEmpty()) {
                subjects += LegacyBackupSubject(
                    legacyGroupId = o.optInt("classGroup", 2),
                    name = name,
                    sortOrder = o.optInt("sortOrder", i)
                )
            }
        }

        val students = mutableListOf<LegacyBackupStudent>()
        val stArr = root.optJSONArray("students") ?: JSONArray()
        for (i in 0 until stArr.length()) {
            val o = stArr.getJSONObject(i)
            val admNo = o.optString("admNo", "").trim()
            val name = o.optString("name", "").trim()
            if (admNo.isNotEmpty() && name.isNotEmpty()) {
                students += LegacyBackupStudent(
                    oldId = o.optLong("id", (i + 1).toLong()),
                    session = o.optString("session", settings.currentSession),
                    admNo = admNo,
                    name = name,
                    fatherName = o.optString("fatherName", ""),
                    motherName = o.optString("motherName", ""),
                    dob = o.optString("dob", ""),
                    mobile = o.optString("mobile", ""),
                    address = o.optString("address", ""),
                    photoBase64 = o.optString("photoBase64", ""),
                    className = DefaultSchoolTemplate.normalizeClassName(o.optString("className", "1st")),
                    section = o.optString("section", "A"),
                    rollNo = o.optInt("rollNo", i + 1),
                    isSample = o.optBoolean("isSample", false)
                )
            }
        }

        return LegacyBackupPayload(
            settings = settings,
            suggestedSchools = suggestedSchools,
            subjects = subjects,
            students = students,
            marks = parseMarksArray(root.optJSONArray("marks")),
            coScholastic = parseCoScholasticArray(root.optJSONArray("coScholastic")),
            termInfos = parseTermInfosArray(root.optJSONArray("termInfos"))
        )
    }

    private fun parseMarksArray(mArr: JSONArray?): List<MarkEntity> {
        val marks = mutableListOf<MarkEntity>()
        if (mArr == null) return marks
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
        return marks
    }

    private fun parseCoScholasticArray(coArr: JSONArray?): List<CoScholasticEntity> {
        val list = mutableListOf<CoScholasticEntity>()
        if (coArr == null) return list
        for (i in 0 until coArr.length()) {
            val o = coArr.getJSONObject(i)
            list += CoScholasticEntity(
                studentId = o.optLong("studentId", -1L),
                termNo = o.optInt("termNo", 1),
                areaCode = o.optString("areaCode", ""),
                grade = o.optString("grade", "")
            )
        }
        return list
    }

    private fun parseTermInfosArray(tiArr: JSONArray?): List<TermInfoEntity> {
        val list = mutableListOf<TermInfoEntity>()
        if (tiArr == null) return list
        for (i in 0 until tiArr.length()) {
            val o = tiArr.getJSONObject(i)
            list += TermInfoEntity(
                studentId = o.optLong("studentId", -1L),
                termNo = o.optInt("termNo", 1),
                daysPresent = o.optInt("daysPresent", 0),
                workingDays = o.optInt("workingDays", 0),
                remarks = o.optString("remarks", ""),
                promotedTo = o.optString("promotedTo", ""),
                reportDate = o.optString("reportDate", "")
            )
        }
        return list
    }

    // -------------------------------------------------------------------------
    // Students CSV Import (scoped to active school)
    // -------------------------------------------------------------------------
    fun parseStudentsCsv(
        csvText: String,
        schoolId: Long,
        schoolClasses: List<SchoolClassEntity>,
        defaultSession: String,
        defaultClass: String,
        defaultSection: String,
        startingRollNo: Int
    ): CsvImportResult {
        val lines = csvText.removePrefix("\uFEFF").lines().map { it.trim() }.filter { it.isNotEmpty() }
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

        val availableNames = schoolClasses.map { it.name }
        val classIdByLower = schoolClasses.associate { it.name.lowercase() to it.id }

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
            val normalizedClass = DefaultSchoolTemplate.normalizeClassName(rawClass, availableNames)
            val matchedClassId = classIdByLower[normalizedClass.lowercase()] ?: 0L
            val section = getCol(idxSection).uppercase().ifEmpty { defaultSection }
            val roll = getCol(idxRoll).toIntOrNull() ?: nextRoll++
            val session = getCol(idxSession).ifEmpty { defaultSession }

            parsed += StudentEntity(
                schoolId = schoolId,
                classId = matchedClassId,
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

    // -------------------------------------------------------------------------
    // Standardized Marks CSV Template & Parser (scoped to active school)
    // -------------------------------------------------------------------------
    const val MARKS_CSV_HEADER =
        "session,class,section,rollNo,admNo,studentName,subject,T1_IA,T1_UT,T1_EXAM,T2_IA,T2_UT,T2_EXAM"

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

    fun parseMarksCsv(
        csvText: String,
        students: List<StudentEntity>,
        schoolClasses: List<SchoolClassEntity>,
        subjectsByGroupId: Map<Long, List<String>>,
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

        val availableClassNames = schoolClasses.map { it.name }
        val classById = schoolClasses.associateBy { it.id }
        val classByLower = schoolClasses.associateBy { it.name.lowercase() }

        val bySessionAndAdm = students.associateBy {
            it.session.trim().lowercase() to it.admNo.trim().lowercase()
        }
        val bySessionClassSecRoll = students.associateBy {
            listOf(
                it.session.trim().lowercase(),
                DefaultSchoolTemplate.normalizeClassName(it.className, availableClassNames).lowercase(),
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
            val normalizedClass = DefaultSchoolTemplate.normalizeClassName(rawClass, availableClassNames)
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

            val matchedSchoolClass = classById[student.classId] ?: classByLower[student.className.lowercase()]
            val configuredSubjects = matchedSchoolClass?.let { subjectsByGroupId[it.classGroupId] }.orEmpty()
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
