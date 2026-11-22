package com.example

import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgCodeValidation
import com.example.domain.OrgMembershipRef
import com.example.domain.OrgRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgAccessEvaluatorTest {

    private fun owner(uid: String = "u-owner") = MemberAccessProfile(
        uid = uid,
        role = OrgRole.OWNER,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = emptyList(),
        permissions = MemberPermissions.presetFor(OrgRole.OWNER)
    )

    private fun admin(uid: String = "u-admin") = MemberAccessProfile(
        uid = uid,
        role = OrgRole.ADMIN,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = emptyList(),
        permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
    )

    private fun officeOperator(
        uid: String = "u-office",
        allSchools: Boolean = false,
        allowedSchoolIds: List<String> = listOf("sch_1"),
        permissions: MemberPermissions = MemberPermissions.presetFor(OrgRole.OFFICE_OPERATOR)
    ) = MemberAccessProfile(
        uid = uid,
        role = OrgRole.OFFICE_OPERATOR,
        status = MemberStatus.ACTIVE,
        allSchools = allSchools,
        allowedSchoolIds = allowedSchoolIds,
        permissions = permissions
    )

    private fun teacher(
        uid: String = "u-teacher",
        allSchools: Boolean = false,
        allowedSchoolIds: List<String> = listOf("sch_1"),
        teacherProfileCompleted: Boolean = false,
        assignedClassSubjects: List<String> = emptyList(),
        permissions: MemberPermissions = MemberPermissions.presetFor(OrgRole.TEACHER)
    ) = MemberAccessProfile(
        uid = uid,
        role = OrgRole.TEACHER,
        status = MemberStatus.ACTIVE,
        allSchools = allSchools,
        allowedSchoolIds = allowedSchoolIds,
        permissions = permissions,
        teacherProfileCompleted = teacherProfileCompleted,
        assignedClassSubjects = assignedClassSubjects
    )

    @Test
    fun orgCodeValidation_acceptsValidAndRejectsInvalidCodes() {
        val valid = OrgAccessEvaluator.validateOrgCode("  CPS-Group-01 ")
        assertTrue(valid is OrgCodeValidation.Valid)
        assertEquals("cps-group-01", (valid as OrgCodeValidation.Valid).normalized)

        assertTrue(OrgAccessEvaluator.validateOrgCode("abc") is OrgCodeValidation.Invalid) // too short (<4)
        assertTrue(OrgAccessEvaluator.validateOrgCode("a".repeat(31)) is OrgCodeValidation.Invalid) // too long (>30)
        assertTrue(OrgAccessEvaluator.validateOrgCode("-school") is OrgCodeValidation.Invalid) // leading hyphen
        assertTrue(OrgAccessEvaluator.validateOrgCode("school-") is OrgCodeValidation.Invalid) // trailing hyphen
        assertTrue(OrgAccessEvaluator.validateOrgCode("my--school") is OrgCodeValidation.Invalid) // consecutive hyphens
        assertTrue(OrgAccessEvaluator.validateOrgCode("my_school!") is OrgCodeValidation.Invalid) // invalid chars
    }

    @Test
    fun rolePresets_matchPhase2Specification() {
        val ownerPerms = MemberPermissions.presetFor(OrgRole.OWNER)
        val adminPerms = MemberPermissions.presetFor(OrgRole.ADMIN)
        AppModule.entries.forEach { module ->
            assertEquals(AccessLevel.EDIT, ownerPerms.levelFor(module))
            assertEquals(AccessLevel.EDIT, adminPerms.levelFor(module))
        }

        val officePerms = MemberPermissions.presetFor(OrgRole.OFFICE_OPERATOR)
        assertEquals(AccessLevel.EDIT, officePerms.students)
        assertEquals(AccessLevel.EDIT, officePerms.marks)
        assertEquals(AccessLevel.EDIT, officePerms.coScholastic)
        assertEquals(AccessLevel.EDIT, officePerms.reports)
        assertEquals(AccessLevel.VIEW, officePerms.timetable)
        assertEquals(AccessLevel.NONE, officePerms.teachers)
        assertEquals(AccessLevel.NONE, officePerms.settings)
        assertEquals(AccessLevel.NONE, officePerms.members)

        val teacherPerms = MemberPermissions.presetFor(OrgRole.TEACHER)
        assertEquals(AccessLevel.VIEW, teacherPerms.students)
        assertEquals(AccessLevel.EDIT, teacherPerms.marks)
        assertEquals(AccessLevel.EDIT, teacherPerms.coScholastic)
        assertEquals(AccessLevel.VIEW, teacherPerms.reports)
        assertEquals(AccessLevel.VIEW, teacherPerms.timetable)
        assertEquals(AccessLevel.NONE, teacherPerms.teachers)
        assertEquals(AccessLevel.NONE, teacherPerms.settings)
        assertEquals(AccessLevel.NONE, teacherPerms.members)

        val otherPerms = MemberPermissions.presetFor(OrgRole.OTHER)
        assertEquals(AccessLevel.NONE, otherPerms.students)
        assertEquals(AccessLevel.NONE, otherPerms.marks)
        assertEquals(AccessLevel.VIEW, otherPerms.timetable)
        assertEquals(AccessLevel.NONE, otherPerms.members)
    }

    @Test
    fun moduleAccess_enforcesViewEditAndNoneAndBlocksRemovedMembers() {
        val t = teacher()
        assertTrue(OrgAccessEvaluator.canViewModule(t, AppModule.STUDENTS))
        assertFalse(OrgAccessEvaluator.canEditModule(t, AppModule.STUDENTS))
        assertTrue(OrgAccessEvaluator.canViewModule(t, AppModule.MARKS))
        assertTrue(OrgAccessEvaluator.canEditModule(t, AppModule.MARKS))
        assertFalse(OrgAccessEvaluator.canViewModule(t, AppModule.SETTINGS))
        assertFalse(OrgAccessEvaluator.canViewModule(t, AppModule.MEMBERS))

        // Revoked / removed member loses ALL access immediately
        val removed = t.copy(status = MemberStatus.REMOVED)
        assertFalse(OrgAccessEvaluator.isActive(removed))
        AppModule.entries.forEach { mod ->
            assertFalse(OrgAccessEvaluator.canViewModule(removed, mod))
            assertFalse(OrgAccessEvaluator.canEditModule(removed, mod))
        }
        assertFalse(OrgAccessEvaluator.canAccessSchool(removed, "sch_1"))
    }

    @Test
    fun perSchoolIsolation_enforcesAllowedSchoolIdsVsAllSchools() {
        val restrictedOffice = officeOperator(allSchools = false, allowedSchoolIds = listOf("sch_1"))
        assertTrue(OrgAccessEvaluator.canAccessSchool(restrictedOffice, "sch_1"))
        assertFalse(OrgAccessEvaluator.canAccessSchool(restrictedOffice, "sch_2"))

        val multiSchoolOffice = officeOperator(allSchools = true, allowedSchoolIds = emptyList())
        assertTrue(OrgAccessEvaluator.canAccessSchool(multiSchoolOffice, "sch_1"))
        assertTrue(OrgAccessEvaluator.canAccessSchool(multiSchoolOffice, "sch_2"))

        val orgOwner = owner()
        assertTrue(OrgAccessEvaluator.canAccessSchool(orgOwner, "sch_any"))
    }

    @Test
    fun teacherMarkScope_restrictsEditingToAssignedClassSubjectsWhenConfigured() {
        val scopedTeacher = teacher(
            allowedSchoolIds = listOf("sch_1"),
            assignedClassSubjects = listOf(
                OrgAccessEvaluator.makeTeacherScopeKey("sch_1", "6th", "Math"),
                OrgAccessEvaluator.makeTeacherScopeKey("sch_1", "7th", "Science")
            )
        )

        assertTrue(OrgAccessEvaluator.canEditMarksFor(scopedTeacher, "sch_1", "6th", "Math"))
        assertTrue(OrgAccessEvaluator.canEditMarksFor(scopedTeacher, "sch_1", "6th", "math")) // case-insensitive
        assertFalse(OrgAccessEvaluator.canEditMarksFor(scopedTeacher, "sch_1", "6th", "English"))
        assertFalse(OrgAccessEvaluator.canEditMarksFor(scopedTeacher, "sch_2", "6th", "Math"))

        // Teacher with empty assignedClassSubjects can edit within allowed school
        val openTeacher = teacher(allowedSchoolIds = listOf("sch_1"), assignedClassSubjects = emptyList())
        assertTrue(OrgAccessEvaluator.canEditMarksFor(openTeacher, "sch_1", "6th", "English"))
        assertFalse(OrgAccessEvaluator.canEditMarksFor(openTeacher, "sch_2", "6th", "English"))

        // Teacher whose MARKS permission was lowered to VIEW cannot edit any mark
        val viewOnlyMarksTeacher = teacher(
            allowedSchoolIds = listOf("sch_1"),
            permissions = MemberPermissions.presetFor(OrgRole.TEACHER).withModule(AppModule.MARKS, AccessLevel.VIEW)
        )
        assertFalse(OrgAccessEvaluator.canEditMarksFor(viewOnlyMarksTeacher, "sch_1", "6th", "Math"))
    }

    @Test
    fun teacherOnboarding_onlyTriggersForActiveTeachersWithIncompleteProfile() {
        assertTrue(OrgAccessEvaluator.needsTeacherProfileOnboarding(teacher(teacherProfileCompleted = false)))
        assertFalse(OrgAccessEvaluator.needsTeacherProfileOnboarding(teacher(teacherProfileCompleted = true)))
        assertFalse(OrgAccessEvaluator.needsTeacherProfileOnboarding(officeOperator()))
        assertFalse(OrgAccessEvaluator.needsTeacherProfileOnboarding(owner()))
    }

    @Test
    fun safetyGuards_protectOwnerAndLastAdminAndRequireBackupToDeleteOrg() {
        val own = owner("u-owner")
        val adm = admin("u-admin")
        val tch = teacher("u-teacher")
        val allMembers = listOf(own, adm, tch)

        // Owner can never be removed or edited directly
        assertNotNull(OrgAccessEvaluator.canRemoveMember(adm, own, allMembers))
        assertFalse(OrgAccessEvaluator.canEditTargetMember(adm, own))
        assertNotNull(OrgAccessEvaluator.canDemoteRole(adm, own, OrgRole.ADMIN, allMembers))

        // Admin can remove a teacher, and owner can remove a second admin
        assertNull(OrgAccessEvaluator.canRemoveMember(adm, tch, allMembers))
        assertNull(OrgAccessEvaluator.canRemoveMember(own, adm, allMembers))

        // Last-admin protection when only 1 admin exists
        val singleAdminList = listOf(adm, tch)
        assertNotNull(OrgAccessEvaluator.canDemoteRole(adm, adm, OrgRole.TEACHER, singleAdminList))
        assertNotNull(OrgAccessEvaluator.canLeaveOrganisation(adm, singleAdminList))

        // Owner cannot leave without transfer/delete
        assertNotNull(OrgAccessEvaluator.canLeaveOrganisation(own, allMembers))
        // Non-owner teacher can leave freely
        assertNull(OrgAccessEvaluator.canLeaveOrganisation(tch, allMembers))

        // Ownership transfer only by Owner to another active member
        assertNotNull(OrgAccessEvaluator.canTransferOwnership(adm, tch))
        assertNotNull(OrgAccessEvaluator.canTransferOwnership(own, own))
        assertNull(OrgAccessEvaluator.canTransferOwnership(own, adm))

        // Delete org requires Owner + backupExported + exact code match
        assertNotNull(OrgAccessEvaluator.canDeleteOrganisation(adm, "my-org", "my-org", backupExported = true))
        assertNotNull(OrgAccessEvaluator.canDeleteOrganisation(own, "my-org", "my-org", backupExported = false))
        assertNotNull(OrgAccessEvaluator.canDeleteOrganisation(own, "wrong-org", "my-org", backupExported = true))
        assertNull(OrgAccessEvaluator.canDeleteOrganisation(own, "MY-ORG", "my-org", backupExported = true))
    }

    @Test
    fun deleteOwnAccount_blockedIfOwnerOfAnyOrganisation() {
        val ownedList = listOf(
            OrgMembershipRef("org_1", "Sample Group", "sample-group", OrgRole.OWNER, MemberStatus.ACTIVE),
            OrgMembershipRef("org_2", "Other School", "other-school", OrgRole.TEACHER, MemberStatus.ACTIVE)
        )
        assertNotNull(OrgAccessEvaluator.canDeleteOwnAccount(ownedList))

        val nonOwnedList = listOf(
            OrgMembershipRef("org_2", "Other School", "other-school", OrgRole.TEACHER, MemberStatus.ACTIVE),
            OrgMembershipRef("org_3", "Third School", "third-school", OrgRole.ADMIN, MemberStatus.ACTIVE)
        )
        assertNull(OrgAccessEvaluator.canDeleteOwnAccount(nonOwnedList))
        assertNull(OrgAccessEvaluator.canDeleteOwnAccount(emptyList()))
    }
}
