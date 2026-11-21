package com.example.util

import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.MarkEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.domain.SchoolClass
import org.json.JSONArray
import org.json.JSONObject

data class CsvImportResult(
    val parsedStudents: List<StudentEntity>,
    val skippedRows: Int
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
