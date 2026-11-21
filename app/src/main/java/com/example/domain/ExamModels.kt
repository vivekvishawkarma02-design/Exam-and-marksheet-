package com.example.domain

enum class SchoolIdentityCode(val code: String, val defaultName: String) {
    CPS("CPS", "C.P.S. SCHOOL"),
    CPI("CPI", "C.P.I. SCHOOL");

    companion object {
        fun fromCode(code: String?): SchoolIdentityCode =
            if (code.equals("CPI", ignoreCase = true)) CPI else CPS
    }
}

data class ResolvedSchoolIdentity(
    val code: SchoolIdentityCode,
    val schoolName: String,
    val address: String,
    val mobile: String,
    val email: String,
    val logoBase64: String,
    val logoCleared: Boolean
)

enum class ClassGroup(val id: Int, val label: String, val shortLabel: String) {
    PRE_PRIMARY(1, "Nursery – UKG (7 Subjects)", "Nur–UKG"),
    PRIMARY(2, "1st – 5th (6 Subjects)", "1st – 5th"),
    MIDDLE(3, "6th – 8th (8 Subjects)", "6th – 8th");

    companion object {
        fun fromId(id: Int): ClassGroup = entries.firstOrNull { it.id == id } ?: PRIMARY
    }
}

enum class SchoolClass(
    val displayName: String,
    val group: ClassGroup,
    val defaultIdentity: SchoolIdentityCode,
    val order: Int,
    private val aliases: List<String> = emptyList()
) {
    NURSERY("Nursery", ClassGroup.PRE_PRIMARY, SchoolIdentityCode.CPS, 1, listOf("nur")),
    LKG("LKG", ClassGroup.PRE_PRIMARY, SchoolIdentityCode.CPS, 2, listOf("l.k.g", "l.k.g.")),
    UKG("UKG", ClassGroup.PRE_PRIMARY, SchoolIdentityCode.CPS, 3, listOf("u.k.g", "u.k.g.")),
    FIRST("1st", ClassGroup.PRIMARY, SchoolIdentityCode.CPS, 4, listOf("I", "1")),
    SECOND("2nd", ClassGroup.PRIMARY, SchoolIdentityCode.CPS, 5, listOf("II", "2")),
    THIRD("3rd", ClassGroup.PRIMARY, SchoolIdentityCode.CPS, 6, listOf("III", "3")),
    FOURTH("4th", ClassGroup.PRIMARY, SchoolIdentityCode.CPS, 7, listOf("IV", "4")),
    FIFTH("5th", ClassGroup.PRIMARY, SchoolIdentityCode.CPS, 8, listOf("V", "5")),
    SIXTH("6th", ClassGroup.MIDDLE, SchoolIdentityCode.CPI, 9, listOf("VI", "6")),
    SEVENTH("7th", ClassGroup.MIDDLE, SchoolIdentityCode.CPI, 10, listOf("VII", "7")),
    EIGHTH("8th", ClassGroup.MIDDLE, SchoolIdentityCode.CPI, 11, listOf("VIII", "8"));

    fun nextClassName(): String {
        val next = entries.firstOrNull { it.order == order + 1 }
        return next?.displayName ?: "9th"
    }

    companion object {
        fun fromName(name: String): SchoolClass {
            val trimmed = name.trim()
            return entries.firstOrNull { sc ->
                sc.displayName.equals(trimmed, ignoreCase = true) ||
                    sc.name.equals(trimmed, ignoreCase = true) ||
                    sc.aliases.any { it.equals(trimmed, ignoreCase = true) }
            } ?: FIRST
        }

        fun defaultClassIdentityMap(): Map<String, SchoolIdentityCode> =
            entries.associate { it.displayName to it.defaultIdentity }

        fun serializeIdentityMap(map: Map<String, SchoolIdentityCode>): String =
            entries.joinToString(",") { sc ->
                val chosen = map[sc.displayName] ?: sc.defaultIdentity
                "${sc.displayName}=${chosen.code}"
            }

        fun parseIdentityMap(csv: String?): Map<String, SchoolIdentityCode> {
            val result = defaultClassIdentityMap().toMutableMap()
            if (csv.isNullOrBlank()) return result
            csv.split(",").forEach { token ->
                val parts = token.split("=")
                if (parts.size == 2) {
                    val cls = fromName(parts[0]).displayName
                    result[cls] = SchoolIdentityCode.fromCode(parts[1].trim())
                }
            }
            return result
        }

        val defaultSections: List<String> = listOf("A", "B", "C")
    }
}

enum class ExamType(val label: String, val shortLabel: String) {
    UT1("UT1", "UT1"),
    UT2("UT2", "UT2"),
    HALF_YEARLY("Half-Yearly", "H.Y."),
    ANNUAL("Annual", "Annual")
}

/**
 * Unified 10 / 30 / 60 exam structure for BOTH Term-I and Term-II (100 marks per term per subject).
 * No Note Book, no Subject Enrich, no Scheme A/B toggle.
 */
enum class MarkComponent(
    val key: String,
    val label: String,
    val shortLabel: String,
    val maxMarks: Int,
    val termNo: Int
) {
    // Term-I (total = 100)
    T1_IA("T1_IA", "Internal Assessment", "Int. Ass.", 10, 1),
    T1_UT("T1_UT", "U.T. 1st", "U.T. 1", 30, 1),
    T1_EXAM("T1_EXAM", "H.Y. Exam", "H.Y. Exam", 60, 1),

    // Term-II (total = 100)
    T2_IA("T2_IA", "Internal Assessment", "Int. Ass.", 10, 2),
    T2_UT("T2_UT", "U.T. 2nd", "U.T. 2", 30, 2),
    T2_EXAM("T2_EXAM", "Yearly Exam", "Yearly", 60, 2);

    companion object {
        val validKeys: Set<String> = entries.map { it.key }.toSet()

        val legacyKeysToDelete: Set<String> = setOf(
            "T1_B_NB", "T1_B_SE", "T1_B_PT", "T1_B_HY",
            "T2_B_NB", "T2_B_SE", "T2_B_PT", "T2_B_ANNUAL"
        )

        fun term1Components(): List<MarkComponent> = listOf(T1_IA, T1_UT, T1_EXAM)

        fun term2Components(): List<MarkComponent> = listOf(T2_IA, T2_UT, T2_EXAM)

        fun componentsForExam(exam: ExamType): List<MarkComponent> = when (exam) {
            ExamType.UT1 -> listOf(T1_UT)
            ExamType.UT2 -> listOf(T2_UT)
            ExamType.HALF_YEARLY -> term1Components()
            ExamType.ANNUAL -> term2Components()
        }

        fun fromKey(key: String): MarkComponent? = entries.firstOrNull { it.key == key }
    }
}

enum class CoScholasticArea(val code: String, val label: String, val shortLabel: String) {
    SKILL("SKILL", "Skill", "Skill"),
    WORK_EDUCATION("WORK_ED", "Work Education ( Or Pre - Vocational Education)", "Work Ed."),
    ART_EDUCATION("ART_ED", "Art Education", "Art"),
    HEALTH_PE("HEALTH_PE", "Health and Physical Education", "Health & PE"),
    DISCIPLINE("DISCIPLINE", "Discipline", "Discipline");

    companion object {
        // Accepts 8-point & 3-point grades (real printed cards often use A1 and A2 here)
        val validGrades = listOf("A1", "A2", "B1", "B2", "C1", "C2", "D", "E", "A", "B", "C")
    }
}

enum class ReportType(val label: String, val subtitle: String, val isLandscape: Boolean) {
    UT1("UT1 Slip", "A4 Landscape (2 A5 slips side by side)", true),
    UT2("UT2 Slip", "A4 Landscape (2 A5 slips side by side)", true),
    HALF_YEARLY("Half-Yearly Slip", "A4 Landscape (2/page, 1/page, or Pair UT+H.Y.)", true),
    ANNUAL("Annual Report Card", "A4 Portrait full-page consolidated Term-I & Term-II card", false)
}

enum class SlipLayoutMode(val label: String, val description: String) {
    TWO_PER_PAGE("2 per page", "Left then right in roll-number order (default)"),
    ONE_PER_PAGE("1 per page", "Left half only on A4 Landscape"),
    PAIR_UT_HY("Pair (UT + H.Y.)", "UT1 slip on left, Half-Yearly slip on right for same student")
}

object DefaultSubjects {
    // Exact names & order required by specification
    val group1PrePrimary: List<String> = listOf(
        "HINDI",
        "HINDI (ORAL)",
        "ENGLISH",
        "ENGLISH (ORAL)",
        "MATHS",
        "MATHS( ORAL)",
        "RHYNES"
    )

    val group2Primary: List<String> = listOf(
        "ENGLISH",
        "HINDI",
        "MATH",
        "SCIENCE",
        "COMPUTER",
        "G.K."
    )

    val group3Middle: List<String> = listOf(
        "ENGLISH",
        "HINDI",
        "MATHS",
        "SCIENCE / EVS",
        "COMPUTER",
        "S.ST.",
        "G.K.",
        "SANSKRIT"
    )

    fun forGroup(group: ClassGroup): List<String> = when (group) {
        ClassGroup.PRE_PRIMARY -> group1PrePrimary
        ClassGroup.PRIMARY -> group2Primary
        ClassGroup.MIDDLE -> group3Middle
    }
}
