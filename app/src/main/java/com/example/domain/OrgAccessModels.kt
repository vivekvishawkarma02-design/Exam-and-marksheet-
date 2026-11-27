package com.example.domain

/**
 * Phase 2 Multi-Tenant Organisation Roles, Per-Module Access Control, and Safety Rules.
 * Pure Kotlin domain logic with zero Android/Firebase dependencies for direct JUnit testing.
 */
enum class OrgRole(val label: String, val shortLabel: String) {
    OWNER("Owner", "Owner"),
    ADMIN("Admin", "Admin"),
    OFFICE_OPERATOR("Office Operator", "Office"),
    TEACHER("Teacher", "Teacher"),
    OTHER("Other Staff", "Staff");

    companion object {
        fun fromRaw(raw: String?): OrgRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER

        val assignablePresets: List<OrgRole> = listOf(ADMIN, OFFICE_OPERATOR, TEACHER, OTHER)
        val joinRequestRoles: List<OrgRole> = listOf(TEACHER, OFFICE_OPERATOR, OTHER)
    }
}

enum class MemberStatus(val wireValue: String) {
    ACTIVE("active"),
    REMOVED("removed");

    companion object {
        fun fromRaw(raw: String?): MemberStatus =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: REMOVED
    }
}

enum class AppModule(val key: String, val label: String, val description: String) {
    STUDENTS("students", "Students", "Student profiles, admission records, and class lists"),
    MARKS("marks", "Marks", "Exam marks entry, validation, and CSV marks import"),
    CO_SCHOLASTIC("coScholastic", "Co-scholastic & Attendance", "Co-scholastic grades, attendance, and remarks"),
    REPORTS("reports", "Reports", "UT slips, Half-Yearly slips, Annual report cards, and batch PDFs"),
    TIMETABLE("timetable", "Timetable", "Class and teacher timetables (full scheduling in later phases)"),
    TEACHERS("teachers", "Teachers and assignments", "Teachers, period structure, class-subject assignments, and subject rules"),
    ATTENDANCE("attendance", "Attendance", "Daily student attendance, school calendar, and attendance reports"),
    FEES("fees", "Fees", "Fee structure, fee collection, receipts, dues, reports, and send queue"),
    STUDENT_REGISTER("studentRegister", "Student Register", "Extended student register, transport, sibling links, left status, and ID cards"),
    CERTIFICATES("certificates", "Certificates", "Transfer, Bonafide & Character certificates and register"),
    CASH_BOOK("cashBook", "Cash Book", "Daily expenses, fee income, day closing, and financial reports"),
    ENQUIRY("enquiry", "Enquiry", "Admission enquiries, follow-ups, WhatsApp reminders, and admission conversion"),
    EXAMS("exams", "Exams", "Exam schedules, rooms, seating, invigilator duties, and admit cards"),
    STOCK("stock", "Stock", "School item inventory, purchases, fee-receipt stock out, and low-stock alerts"),
    TRANSPORT("transport", "Transport", "Bus/van routes, stops, drivers, student assignments, and route lists"),
    SALARY("salary", "Salary", "Staff salary setup, monthly sheets, attendance deductions, advances, slips, and month locking"),
    SESSION_PROMOTION("sessionPromotion", "Session and Promotion", "Start new session, student promotion, class order, alumni history, and promotion register"),
    RESULT_ANALYSIS("resultAnalysis", "Result Analysis", "View-only class analytics, toppers, subject stats, weak students & student progress"),
    CLASS_DIARY("classDiary", "Class Diary", "Daily classwork, homework, notes, blackboard photos, weekly PDF, and WhatsApp class summary"),
    STUDENT_REMARKS("studentRemarks", "Student Remarks", "Sensitive student remarks, follow-ups, PTM parent meetings, and A4 remark summary"),
    GATE("gate", "Gate", "Early leave gate passes, A5 pass slips, daily gate register, and visitor register"),
    SETTINGS("settings", "Settings", "Organisation settings, sessions, schools, and backups"),
    MEMBERS("members", "Members", "Member directory, join requests, roles, and access control");

    companion object {
        fun fromKey(key: String?): AppModule? = entries.firstOrNull { it.key == key }
    }
}

enum class AccessLevel(val wireValue: String, val label: String) {
    NONE("none", "No access"),
    VIEW("view", "View only"),
    EDIT("edit", "View & edit");

    companion object {
        fun fromRaw(raw: String?): AccessLevel =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class SyncStatus(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    SYNCING("Syncing"),
    ERROR("Sync error")
}

data class MemberPermissions(
    val students: AccessLevel = AccessLevel.NONE,
    val marks: AccessLevel = AccessLevel.NONE,
    val coScholastic: AccessLevel = AccessLevel.NONE,
    val reports: AccessLevel = AccessLevel.NONE,
    val timetable: AccessLevel = AccessLevel.NONE,
    val teachers: AccessLevel = AccessLevel.NONE,
    val attendance: AccessLevel = AccessLevel.NONE,
    val fees: AccessLevel = AccessLevel.NONE,
    val studentRegister: AccessLevel = AccessLevel.NONE,
    val certificates: AccessLevel = AccessLevel.NONE,
    val cashBook: AccessLevel = AccessLevel.NONE,
    val enquiry: AccessLevel = AccessLevel.NONE,
    val exams: AccessLevel = AccessLevel.NONE,
    val stock: AccessLevel = AccessLevel.NONE,
    val transport: AccessLevel = AccessLevel.NONE,
    val salary: AccessLevel = AccessLevel.NONE,
    val sessionPromotion: AccessLevel = AccessLevel.NONE,
    val resultAnalysis: AccessLevel = AccessLevel.NONE,
    val classDiary: AccessLevel = AccessLevel.NONE,
    val studentRemarks: AccessLevel = AccessLevel.NONE,
    val gate: AccessLevel = AccessLevel.NONE,
    val settings: AccessLevel = AccessLevel.NONE,
    val members: AccessLevel = AccessLevel.NONE
) {
    fun levelFor(module: AppModule): AccessLevel = when (module) {
        AppModule.STUDENTS -> students
        AppModule.MARKS -> marks
        AppModule.CO_SCHOLASTIC -> coScholastic
        AppModule.REPORTS -> reports
        AppModule.TIMETABLE -> timetable
        AppModule.TEACHERS -> teachers
        AppModule.ATTENDANCE -> attendance
        AppModule.FEES -> fees
        AppModule.STUDENT_REGISTER -> studentRegister
        AppModule.CERTIFICATES -> certificates
        AppModule.CASH_BOOK -> cashBook
        AppModule.ENQUIRY -> enquiry
        AppModule.EXAMS -> exams
        AppModule.STOCK -> stock
        AppModule.TRANSPORT -> transport
        AppModule.SALARY -> salary
        AppModule.SESSION_PROMOTION -> sessionPromotion
        AppModule.RESULT_ANALYSIS -> resultAnalysis
        AppModule.CLASS_DIARY -> classDiary
        AppModule.STUDENT_REMARKS -> studentRemarks
        AppModule.GATE -> gate
        AppModule.SETTINGS -> settings
        AppModule.MEMBERS -> members
    }

    fun withModule(module: AppModule, level: AccessLevel): MemberPermissions = when (module) {
        AppModule.STUDENTS -> copy(students = level)
        AppModule.MARKS -> copy(marks = level)
        AppModule.CO_SCHOLASTIC -> copy(coScholastic = level)
        AppModule.REPORTS -> copy(reports = level)
        AppModule.TIMETABLE -> copy(timetable = level)
        AppModule.TEACHERS -> copy(teachers = level)
        AppModule.ATTENDANCE -> copy(attendance = level)
        AppModule.FEES -> copy(fees = level)
        AppModule.STUDENT_REGISTER -> copy(studentRegister = level)
        AppModule.CERTIFICATES -> copy(certificates = level)
        AppModule.CASH_BOOK -> copy(cashBook = level)
        AppModule.ENQUIRY -> copy(enquiry = level)
        AppModule.EXAMS -> copy(exams = level)
        AppModule.STOCK -> copy(stock = level)
        AppModule.TRANSPORT -> copy(transport = level)
        AppModule.SALARY -> copy(salary = level)
        AppModule.SESSION_PROMOTION -> copy(sessionPromotion = level)
        AppModule.RESULT_ANALYSIS -> copy(resultAnalysis = level)
        AppModule.CLASS_DIARY -> copy(classDiary = level)
        AppModule.STUDENT_REMARKS -> copy(studentRemarks = level)
        AppModule.GATE -> copy(gate = level)
        AppModule.SETTINGS -> copy(settings = level)
        AppModule.MEMBERS -> copy(members = level)
    }

    fun toFirestoreMap(): Map<String, String> = mapOf(
        AppModule.STUDENTS.key to students.wireValue,
        AppModule.MARKS.key to marks.wireValue,
        AppModule.CO_SCHOLASTIC.key to coScholastic.wireValue,
        AppModule.REPORTS.key to reports.wireValue,
        AppModule.TIMETABLE.key to timetable.wireValue,
        AppModule.TEACHERS.key to teachers.wireValue,
        AppModule.ATTENDANCE.key to attendance.wireValue,
        AppModule.FEES.key to fees.wireValue,
        AppModule.STUDENT_REGISTER.key to studentRegister.wireValue,
        AppModule.CERTIFICATES.key to certificates.wireValue,
        AppModule.CASH_BOOK.key to cashBook.wireValue,
        AppModule.ENQUIRY.key to enquiry.wireValue,
        AppModule.EXAMS.key to exams.wireValue,
        AppModule.STOCK.key to stock.wireValue,
        AppModule.TRANSPORT.key to transport.wireValue,
        AppModule.SALARY.key to salary.wireValue,
        AppModule.SESSION_PROMOTION.key to sessionPromotion.wireValue,
        AppModule.RESULT_ANALYSIS.key to resultAnalysis.wireValue,
        AppModule.CLASS_DIARY.key to classDiary.wireValue,
        AppModule.STUDENT_REMARKS.key to studentRemarks.wireValue,
        AppModule.GATE.key to gate.wireValue,
        AppModule.SETTINGS.key to settings.wireValue,
        AppModule.MEMBERS.key to members.wireValue
    )

    companion object {
        fun presetFor(role: OrgRole): MemberPermissions = when (role) {
            OrgRole.OWNER -> MemberPermissions(
                students = AccessLevel.EDIT,
                marks = AccessLevel.EDIT,
                coScholastic = AccessLevel.EDIT,
                reports = AccessLevel.EDIT,
                timetable = AccessLevel.EDIT,
                teachers = AccessLevel.EDIT,
                attendance = AccessLevel.EDIT,
                fees = AccessLevel.EDIT,
                studentRegister = AccessLevel.EDIT,
                certificates = AccessLevel.EDIT,
                cashBook = AccessLevel.EDIT,
                enquiry = AccessLevel.EDIT,
                exams = AccessLevel.EDIT,
                stock = AccessLevel.EDIT,
                transport = AccessLevel.EDIT,
                salary = AccessLevel.EDIT,
                sessionPromotion = AccessLevel.EDIT,
                resultAnalysis = AccessLevel.VIEW,
                classDiary = AccessLevel.EDIT,
                studentRemarks = AccessLevel.EDIT,
                gate = AccessLevel.EDIT,
                settings = AccessLevel.EDIT,
                members = AccessLevel.EDIT
            )
            OrgRole.ADMIN -> MemberPermissions(
                students = AccessLevel.EDIT,
                marks = AccessLevel.EDIT,
                coScholastic = AccessLevel.EDIT,
                reports = AccessLevel.EDIT,
                timetable = AccessLevel.EDIT,
                teachers = AccessLevel.EDIT,
                attendance = AccessLevel.EDIT,
                fees = AccessLevel.EDIT,
                studentRegister = AccessLevel.EDIT,
                certificates = AccessLevel.EDIT,
                cashBook = AccessLevel.EDIT,
                enquiry = AccessLevel.EDIT,
                exams = AccessLevel.EDIT,
                stock = AccessLevel.EDIT,
                transport = AccessLevel.EDIT,
                salary = AccessLevel.NONE,
                sessionPromotion = AccessLevel.EDIT,
                resultAnalysis = AccessLevel.VIEW,
                classDiary = AccessLevel.EDIT,
                studentRemarks = AccessLevel.EDIT,
                gate = AccessLevel.EDIT,
                settings = AccessLevel.EDIT,
                members = AccessLevel.EDIT
            )
            OrgRole.OFFICE_OPERATOR -> MemberPermissions(
                students = AccessLevel.EDIT,
                marks = AccessLevel.EDIT,
                coScholastic = AccessLevel.EDIT,
                reports = AccessLevel.EDIT,
                timetable = AccessLevel.VIEW,
                teachers = AccessLevel.NONE,
                attendance = AccessLevel.EDIT,
                fees = AccessLevel.EDIT,
                studentRegister = AccessLevel.EDIT,
                certificates = AccessLevel.EDIT,
                cashBook = AccessLevel.EDIT,
                enquiry = AccessLevel.EDIT,
                exams = AccessLevel.EDIT,
                stock = AccessLevel.EDIT,
                transport = AccessLevel.EDIT,
                salary = AccessLevel.NONE,
                sessionPromotion = AccessLevel.NONE,
                resultAnalysis = AccessLevel.NONE,
                classDiary = AccessLevel.NONE,
                studentRemarks = AccessLevel.NONE,
                gate = AccessLevel.NONE,
                settings = AccessLevel.NONE,
                members = AccessLevel.NONE
            )
            OrgRole.TEACHER -> MemberPermissions(
                students = AccessLevel.VIEW,
                marks = AccessLevel.EDIT,
                coScholastic = AccessLevel.EDIT,
                reports = AccessLevel.VIEW,
                timetable = AccessLevel.VIEW,
                teachers = AccessLevel.NONE,
                attendance = AccessLevel.NONE,
                fees = AccessLevel.NONE,
                studentRegister = AccessLevel.NONE,
                certificates = AccessLevel.NONE,
                cashBook = AccessLevel.NONE,
                enquiry = AccessLevel.NONE,
                exams = AccessLevel.NONE,
                stock = AccessLevel.NONE,
                transport = AccessLevel.NONE,
                salary = AccessLevel.NONE,
                sessionPromotion = AccessLevel.NONE,
                resultAnalysis = AccessLevel.NONE,
                classDiary = AccessLevel.NONE,
                studentRemarks = AccessLevel.NONE,
                gate = AccessLevel.NONE,
                settings = AccessLevel.NONE,
                members = AccessLevel.NONE
            )
            OrgRole.OTHER -> MemberPermissions(
                students = AccessLevel.NONE,
                marks = AccessLevel.NONE,
                coScholastic = AccessLevel.NONE,
                reports = AccessLevel.NONE,
                timetable = AccessLevel.VIEW,
                teachers = AccessLevel.NONE,
                attendance = AccessLevel.NONE,
                fees = AccessLevel.NONE,
                studentRegister = AccessLevel.NONE,
                certificates = AccessLevel.NONE,
                cashBook = AccessLevel.NONE,
                enquiry = AccessLevel.NONE,
                exams = AccessLevel.NONE,
                stock = AccessLevel.NONE,
                transport = AccessLevel.NONE,
                salary = AccessLevel.NONE,
                sessionPromotion = AccessLevel.NONE,
                resultAnalysis = AccessLevel.NONE,
                classDiary = AccessLevel.NONE,
                studentRemarks = AccessLevel.NONE,
                gate = AccessLevel.NONE,
                settings = AccessLevel.NONE,
                members = AccessLevel.NONE
            )
        }

        fun fromMap(raw: Map<*, *>?): MemberPermissions {
            if (raw == null) return MemberPermissions()
            return MemberPermissions(
                students = AccessLevel.fromRaw(raw[AppModule.STUDENTS.key] as? String),
                marks = AccessLevel.fromRaw(raw[AppModule.MARKS.key] as? String),
                coScholastic = AccessLevel.fromRaw(raw[AppModule.CO_SCHOLASTIC.key] as? String),
                reports = AccessLevel.fromRaw(raw[AppModule.REPORTS.key] as? String),
                timetable = AccessLevel.fromRaw(raw[AppModule.TIMETABLE.key] as? String),
                teachers = AccessLevel.fromRaw(raw[AppModule.TEACHERS.key] as? String),
                attendance = AccessLevel.fromRaw(raw[AppModule.ATTENDANCE.key] as? String),
                fees = AccessLevel.fromRaw(raw[AppModule.FEES.key] as? String),
                studentRegister = AccessLevel.fromRaw(raw[AppModule.STUDENT_REGISTER.key] as? String),
                certificates = AccessLevel.fromRaw(raw[AppModule.CERTIFICATES.key] as? String),
                cashBook = AccessLevel.fromRaw(raw[AppModule.CASH_BOOK.key] as? String),
                enquiry = AccessLevel.fromRaw(raw[AppModule.ENQUIRY.key] as? String),
                exams = AccessLevel.fromRaw(raw[AppModule.EXAMS.key] as? String),
                stock = AccessLevel.fromRaw(raw[AppModule.STOCK.key] as? String),
                transport = AccessLevel.fromRaw(raw[AppModule.TRANSPORT.key] as? String),
                salary = AccessLevel.fromRaw(raw[AppModule.SALARY.key] as? String),
                sessionPromotion = AccessLevel.fromRaw(raw[AppModule.SESSION_PROMOTION.key] as? String),
                resultAnalysis = AccessLevel.fromRaw(raw[AppModule.RESULT_ANALYSIS.key] as? String),
                classDiary = AccessLevel.fromRaw(raw[AppModule.CLASS_DIARY.key] as? String),
                studentRemarks = AccessLevel.fromRaw(raw[AppModule.STUDENT_REMARKS.key] as? String),
                gate = AccessLevel.fromRaw(raw[AppModule.GATE.key] as? String),
                settings = AccessLevel.fromRaw(raw[AppModule.SETTINGS.key] as? String),
                members = AccessLevel.fromRaw(raw[AppModule.MEMBERS.key] as? String)
            )
        }
    }
}

data class MemberAccessProfile(
    val uid: String,
    val role: OrgRole,
    val status: MemberStatus,
    val allSchools: Boolean,
    val allowedSchoolIds: List<String>,
    val permissions: MemberPermissions,
    val teacherProfileCompleted: Boolean = false,
    val assignedClassSubjects: List<String> = emptyList()
)

data class OrgMembershipRef(
    val orgId: String,
    val orgName: String,
    val orgCode: String,
    val role: OrgRole,
    val status: MemberStatus = MemberStatus.ACTIVE
)

sealed interface OrgCodeValidation {
    data class Valid(val normalized: String) : OrgCodeValidation
    data class Invalid(val reason: String) : OrgCodeValidation
}

object OrgAccessEvaluator {
    private val ORG_CODE_REGEX = Regex("^[a-z0-9][a-z0-9-]{2,28}[a-z0-9]$")

    fun normalizeOrgCode(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), "-")

    fun validateOrgCode(raw: String): OrgCodeValidation {
        val code = normalizeOrgCode(raw)
        if (code.length < 4 || code.length > 30) {
            return OrgCodeValidation.Invalid("Organisation code must be 4 to 30 characters.")
        }
        if (code.startsWith("-") || code.endsWith("-")) {
            return OrgCodeValidation.Invalid("Code cannot start or end with a hyphen.")
        }
        if (code.contains("--")) {
            return OrgCodeValidation.Invalid("Code cannot contain consecutive hyphens.")
        }
        if (!ORG_CODE_REGEX.matches(code)) {
            return OrgCodeValidation.Invalid("Use only lowercase letters (a-z), digits (0-9), and hyphens (-).")
        }
        return OrgCodeValidation.Valid(code)
    }

    fun isActive(member: MemberAccessProfile?): Boolean =
        member != null && member.status == MemberStatus.ACTIVE

    fun isOwner(member: MemberAccessProfile?): Boolean =
        isActive(member) && member!!.role == OrgRole.OWNER

    fun isAdmin(member: MemberAccessProfile?): Boolean =
        isActive(member) && (member!!.role == OrgRole.OWNER || member.role == OrgRole.ADMIN)

    fun canGrantSalaryAccess(actor: MemberAccessProfile?): Boolean =
        isOwner(actor)

    fun canViewModule(member: MemberAccessProfile?, module: AppModule): Boolean {
        if (!isActive(member)) return false
        val m = member!!
        if (m.role == OrgRole.OWNER) return true
        if (module != AppModule.SALARY && m.role == OrgRole.ADMIN) return true
        val lvl = m.permissions.levelFor(module)
        return lvl == AccessLevel.VIEW || lvl == AccessLevel.EDIT
    }

    fun canEditModule(member: MemberAccessProfile?, module: AppModule): Boolean {
        if (!isActive(member)) return false
        val m = member!!
        if (m.role == OrgRole.OWNER) return true
        if (module != AppModule.SALARY && m.role == OrgRole.ADMIN) return true
        return m.permissions.levelFor(module) == AccessLevel.EDIT
    }

    fun canAccessSchool(member: MemberAccessProfile?, schoolDocId: String): Boolean {
        if (!isActive(member) || schoolDocId.isBlank()) return false
        val m = member!!
        if (m.role == OrgRole.OWNER || m.role == OrgRole.ADMIN || m.allSchools) return true
        return schoolDocId in m.allowedSchoolIds
    }

    fun canEditMarksFor(
        member: MemberAccessProfile?,
        schoolDocId: String,
        className: String,
        subjectName: String
    ): Boolean {
        if (!canAccessSchool(member, schoolDocId)) return false
        if (!canEditModule(member, AppModule.MARKS)) return false
        val m = member!!
        if (m.role == OrgRole.OWNER || m.role == OrgRole.ADMIN || m.role != OrgRole.TEACHER) {
            return true
        }
        if (m.assignedClassSubjects.isEmpty()) return true
        val scopeKey = makeTeacherScopeKey(schoolDocId, className, subjectName)
        return m.assignedClassSubjects.any { it.equals(scopeKey, ignoreCase = true) }
    }

    fun makeTeacherScopeKey(schoolDocId: String, className: String, subjectName: String): String =
        "${schoolDocId.trim()}|${className.trim()}|${subjectName.trim()}"

    fun needsTeacherProfileOnboarding(member: MemberAccessProfile?): Boolean =
        isActive(member) && member!!.role == OrgRole.TEACHER && !member.teacherProfileCompleted

    fun canManageMembers(member: MemberAccessProfile?): Boolean =
        isAdmin(member) || canEditModule(member, AppModule.MEMBERS)

    fun canEditTargetMember(actor: MemberAccessProfile?, target: MemberAccessProfile): Boolean {
        if (!canManageMembers(actor)) return false
        if (target.role == OrgRole.OWNER) return false
        return true
    }

    fun canRemoveMember(
        actor: MemberAccessProfile?,
        target: MemberAccessProfile,
        allActiveMembers: List<MemberAccessProfile>
    ): String? {
        if (!isAdmin(actor)) return "Only Owners and Admins can remove members."
        if (target.role == OrgRole.OWNER) return "The Owner cannot be removed."
        if (target.uid == actor!!.uid) return "Use 'Leave Organisation' to leave yourself."
        val activeAdmins = allActiveMembers.count {
            it.status == MemberStatus.ACTIVE && (it.role == OrgRole.OWNER || it.role == OrgRole.ADMIN)
        }
        if (target.role == OrgRole.ADMIN && activeAdmins <= 1) {
            return "An organisation must keep at least one admin."
        }
        return null
    }

    fun canDemoteRole(
        actor: MemberAccessProfile?,
        target: MemberAccessProfile,
        newRole: OrgRole,
        allActiveMembers: List<MemberAccessProfile>
    ): String? {
        if (!canManageMembers(actor)) return "You do not have permission to change roles."
        if (target.role == OrgRole.OWNER) return "Cannot change the Owner's role directly. Use Transfer Ownership."
        if (newRole == OrgRole.OWNER) return "Use Transfer Ownership to assign a new Owner."
        val wasAdmin = target.role == OrgRole.ADMIN
        val willBeAdmin = newRole == OrgRole.ADMIN
        if (wasAdmin && !willBeAdmin) {
            val activeAdmins = allActiveMembers.count {
                it.status == MemberStatus.ACTIVE && (it.role == OrgRole.OWNER || it.role == OrgRole.ADMIN)
            }
            if (activeAdmins <= 1) {
                return "An organisation must keep at least one admin."
            }
        }
        return null
    }

    fun canLeaveOrganisation(
        member: MemberAccessProfile?,
        allActiveMembers: List<MemberAccessProfile>
    ): String? {
        if (!isActive(member)) return "You are not an active member of this organisation."
        val m = member!!
        if (m.role == OrgRole.OWNER) {
            return "Owners must transfer ownership or delete the organisation before leaving."
        }
        if (m.role == OrgRole.ADMIN) {
            val activeAdmins = allActiveMembers.count {
                it.status == MemberStatus.ACTIVE && (it.role == OrgRole.OWNER || it.role == OrgRole.ADMIN)
            }
            if (activeAdmins <= 1) {
                return "An organisation must keep at least one admin."
            }
        }
        return null
    }

    fun canTransferOwnership(actor: MemberAccessProfile?, target: MemberAccessProfile): String? {
        if (!isOwner(actor)) return "Only the Owner can transfer ownership."
        if (target.uid == actor!!.uid) return "You are already the Owner."
        if (target.status != MemberStatus.ACTIVE) return "Target member must be active."
        return null
    }

    fun canDeleteOrganisation(
        actor: MemberAccessProfile?,
        typedCode: String,
        expectedOrgCode: String,
        backupExported: Boolean
    ): String? {
        if (!isOwner(actor)) return "Only the Owner can delete the organisation."
        if (!backupExported) return "Please export a JSON backup first before deleting the organisation."
        if (normalizeOrgCode(typedCode) != normalizeOrgCode(expectedOrgCode)) {
            return "Typed organisation code does not match '$expectedOrgCode'."
        }
        return null
    }

    fun canDeleteOwnAccount(memberships: List<OrgMembershipRef>): String? {
        val ownedOrgs = ownedActiveOrganisations(memberships)
        if (ownedOrgs.isNotEmpty()) {
            val names = ownedOrgs.joinToString(", ") { it.orgName }
            return "Transfer ownership or delete your owned organisation(s) first: $names."
        }
        return null
    }

    fun ownedActiveOrganisations(memberships: List<OrgMembershipRef>): List<OrgMembershipRef> =
        memberships.filter { it.status == MemberStatus.ACTIVE && it.role == OrgRole.OWNER }

    fun nonOwnedMemberships(memberships: List<OrgMembershipRef>): List<OrgMembershipRef> =
        memberships.filterNot { it.status == MemberStatus.ACTIVE && it.role == OrgRole.OWNER }

    fun validateOwnerAccountAndOrgDeletion(
        memberships: List<OrgMembershipRef>,
        typedConfirmation: String
    ): String? {
        val ownedOrgs = ownedActiveOrganisations(memberships)
        if (ownedOrgs.isEmpty()) return null
        if (ownedOrgs.size == 1) {
            val expectedCode = normalizeOrgCode(ownedOrgs.first().orgCode)
            val typedNormalized = normalizeOrgCode(typedConfirmation)
            if (expectedCode.isNotBlank() && typedNormalized != expectedCode) {
                return "Type the exact organisation code '${ownedOrgs.first().orgCode}' to confirm deleting the whole organisation."
            }
            if (expectedCode.isBlank() && typedConfirmation.trim() != "DELETE ORGANISATION") {
                return "Type DELETE ORGANISATION to confirm."
            }
            return null
        }
        if (typedConfirmation.trim() != "DELETE ALL ORGANISATIONS") {
            return "You own ${ownedOrgs.size} organisations. Type DELETE ALL ORGANISATIONS to confirm permanent deletion of all of them."
        }
        return null
    }
}

/**
 * Pure Kotlin helper for rendering historical author names on school records.
 * When a user deletes their own account, school records remain intact for continuity,
 * and author names resolve cleanly to [DELETED_USER_LABEL].
 */
object AuthorDisplayHelper {
    const val DELETED_USER_LABEL = "deleted user"

    fun resolveAuthorName(
        authorUid: String,
        storedName: String,
        deletedUserUids: Set<String>,
        fallback: String = "Staff"
    ): String {
        if (authorUid.isNotBlank() && authorUid in deletedUserUids) {
            return DELETED_USER_LABEL
        }
        return storedName.trim().ifBlank { fallback }
    }
}

