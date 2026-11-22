package com.example.domain

/**
 * Built-in default school class-group template preserving the exact subject lists & order
 * (Nursery–UKG 7 subjects, 1st–5th 6 subjects, 6th–8th 8 subjects).
 */
data class DefaultGroupTemplate(
    val key: String,
    val name: String,
    val shortLabel: String,
    val defaultClasses: List<String>,
    val defaultSubjects: List<String>
)

object DefaultSubjects {
    // Exact names & order preserved from the original app specification
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

    fun forTemplateKey(key: String): List<String> = when (key) {
        DefaultSchoolTemplate.KEY_PRE_PRIMARY -> group1PrePrimary
        DefaultSchoolTemplate.KEY_PRIMARY -> group2Primary
        DefaultSchoolTemplate.KEY_MIDDLE -> group3Middle
        else -> emptyList()
    }

    fun forLegacyGroupId(legacyGroupId: Int): List<String> = when (legacyGroupId) {
        1 -> group1PrePrimary
        2 -> group2Primary
        3 -> group3Middle
        else -> group2Primary
    }
}

object DefaultSchoolTemplate {
    const val KEY_PRE_PRIMARY = "PRE_PRIMARY"
    const val KEY_PRIMARY = "PRIMARY"
    const val KEY_MIDDLE = "MIDDLE"

    val defaultSections: List<String> = listOf("A", "B", "C")

    val groups: List<DefaultGroupTemplate> = listOf(
        DefaultGroupTemplate(
            key = KEY_PRE_PRIMARY,
            name = "Nursery – UKG",
            shortLabel = "Nur–UKG",
            defaultClasses = listOf("Nursery", "LKG", "UKG"),
            defaultSubjects = DefaultSubjects.group1PrePrimary
        ),
        DefaultGroupTemplate(
            key = KEY_PRIMARY,
            name = "1st – 5th",
            shortLabel = "1st – 5th",
            defaultClasses = listOf("1st", "2nd", "3rd", "4th", "5th"),
            defaultSubjects = DefaultSubjects.group2Primary
        ),
        DefaultGroupTemplate(
            key = KEY_MIDDLE,
            name = "6th – 8th",
            shortLabel = "6th – 8th",
            defaultClasses = listOf("6th", "7th", "8th"),
            defaultSubjects = DefaultSubjects.group3Middle
        )
    )

    val allDefaultClasses: List<String> = groups.flatMap { it.defaultClasses }

    private val commonAliases: Map<String, String> = mapOf(
        "nur" to "Nursery",
        "nursery" to "Nursery",
        "l.k.g" to "LKG",
        "l.k.g." to "LKG",
        "lkg" to "LKG",
        "u.k.g" to "UKG",
        "u.k.g." to "UKG",
        "ukg" to "UKG",
        "i" to "1st",
        "1" to "1st",
        "1st" to "1st",
        "ii" to "2nd",
        "2" to "2nd",
        "2nd" to "2nd",
        "iii" to "3rd",
        "3" to "3rd",
        "3rd" to "3rd",
        "iv" to "4th",
        "4" to "4th",
        "4th" to "4th",
        "v" to "5th",
        "5" to "5th",
        "5th" to "5th",
        "vi" to "6th",
        "6" to "6th",
        "6th" to "6th",
        "vii" to "7th",
        "7" to "7th",
        "7th" to "7th",
        "viii" to "8th",
        "8" to "8th",
        "8th" to "8th",
        "ix" to "9th",
        "9" to "9th",
        "9th" to "9th",
        "x" to "10th",
        "10" to "10th",
        "10th" to "10th",
        "xi" to "11th",
        "11" to "11th",
        "11th" to "11th",
        "xii" to "12th",
        "12" to "12th",
        "12th" to "12th"
    )

    /**
     * Resolves a raw class name (e.g., from CSV or legacy backup) against a school's configured
     * class list first, falling back to common Roman/numeric aliases, while preserving arbitrary
     * custom class names like "9th", "Grade 10", "Prep", etc.
     */
    fun normalizeClassName(raw: String, availableClasses: List<String> = emptyList()): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return availableClasses.firstOrNull() ?: "1st"
        availableClasses.firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let { return it }
        val alias = commonAliases[trimmed.lowercase()]
        if (alias != null) {
            availableClasses.firstOrNull { it.equals(alias, ignoreCase = true) }?.let { return it }
            return alias
        }
        return trimmed
    }

    /**
     * Suggests the next class in the school's ordered class list, or increments numeric ordinals.
     */
    fun suggestNextClassName(currentClassName: String, orderedSchoolClasses: List<String>): String {
        val idx = orderedSchoolClasses.indexOfFirst { it.equals(currentClassName.trim(), ignoreCase = true) }
        if (idx >= 0 && idx + 1 < orderedSchoolClasses.size) {
            return orderedSchoolClasses[idx + 1]
        }
        val match = Regex("^(\\d+)(st|nd|rd|th)?$", RegexOption.IGNORE_CASE).matchEntire(currentClassName.trim())
        if (match != null) {
            val nextNum = (match.groupValues[1].toIntOrNull() ?: 0) + 1
            val suffix = when {
                nextNum % 100 in 11..13 -> "th"
                nextNum % 10 == 1 -> "st"
                nextNum % 10 == 2 -> "nd"
                nextNum % 10 == 3 -> "rd"
                else -> "th"
            }
            return "$nextNum$suffix"
        }
        return ""
    }

    fun inferDefaultGroupKeyForClass(className: String): String {
        val normalized = normalizeClassName(className)
        return groups.firstOrNull { grp ->
            grp.defaultClasses.any { it.equals(normalized, ignoreCase = true) }
        }?.key ?: KEY_MIDDLE
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
