package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.cloud.CloudMember
import com.example.data.cloud.CloudMembershipEntry
import com.example.data.cloud.CloudOrganisation
import com.example.data.cloud.CloudSchoolRecord
import com.example.domain.AuthMethod
import com.example.domain.AuthorDisplayHelper
import com.example.domain.LegalConfig
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgMembershipRef
import com.example.domain.OrgRole
import com.example.domain.SyncStatus
import com.example.domain.SyncUiState
import com.example.ui.DashboardScreen
import com.example.ui.DeleteAccountDialog
import com.example.ui.FilterSelection
import com.example.ui.LegalDocType
import com.example.ui.PrivacyAndTermsScreen
import com.example.ui.SchoolUiState
import com.example.ui.SettingsAboutAndLegalCard
import com.example.ui.theme.SchoolAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Phase15DeletionAndReadinessTest {

    @get:Rule
    val composeRule = createComposeRule()

    // =========================================================================
    // 1. PURE UNIT TESTS: AUTHOR ATTRIBUTION & SOLE-OWNER GUARD
    // =========================================================================

    @Test
    fun authorDisplayHelper_returnsDeletedUserOnlyForDeletedUids() {
        val deletedUids = setOf("uid_gone_1", "uid_gone_2")
        assertEquals(
            AuthorDisplayHelper.DELETED_USER_LABEL,
            AuthorDisplayHelper.resolveAuthorName("uid_gone_1", "Mrs. Sharma", deletedUids)
        )
        assertEquals(
            "Mrs. Verma",
            AuthorDisplayHelper.resolveAuthorName("uid_active", " Mrs. Verma ", deletedUids)
        )
        assertEquals(
            "Staff",
            AuthorDisplayHelper.resolveAuthorName("uid_active", "", deletedUids)
        )
        assertEquals(
            "Admin",
            AuthorDisplayHelper.resolveAuthorName("", "", deletedUids, fallback = "Admin")
        )
    }

    @Test
    fun soleOwnerDeletionValidation_requiresExactOrgCodeOrMultiOrgPhrase() {
        val singleOwned = listOf(
            OrgMembershipRef("org1", "STS Primary", "sts-ghazipur", OrgRole.OWNER, MemberStatus.ACTIVE),
            OrgMembershipRef("org2", "Guest Org", "guest-org", OrgRole.TEACHER, MemberStatus.ACTIVE)
        )
        val nonOwnerOnly = listOf(
            OrgMembershipRef("org2", "Guest Org", "guest-org", OrgRole.TEACHER, MemberStatus.ACTIVE)
        )
        val multiOwned = listOf(
            OrgMembershipRef("org1", "STS Primary", "sts-ghazipur", OrgRole.OWNER, MemberStatus.ACTIVE),
            OrgMembershipRef("org3", "STS Inter", "sts-inter", OrgRole.OWNER, MemberStatus.ACTIVE)
        )

        // Normal member allowed directly
        assertNull(OrgAccessEvaluator.canDeleteOwnAccount(nonOwnerOnly))
        assertTrue(OrgAccessEvaluator.ownedActiveOrganisations(nonOwnerOnly).isEmpty())
        assertEquals(1, OrgAccessEvaluator.nonOwnedMemberships(nonOwnerOnly).size)

        // Sole Owner blocked unless org deletion/transfer is handled
        assertNotNull(OrgAccessEvaluator.canDeleteOwnAccount(singleOwned))
        assertEquals(1, OrgAccessEvaluator.ownedActiveOrganisations(singleOwned).size)
        assertEquals(1, OrgAccessEvaluator.nonOwnedMemberships(singleOwned).size)

        // Single owned org requires exact normalised org code
        assertNotNull(OrgAccessEvaluator.validateOwnerAccountAndOrgDeletion(singleOwned, ""))
        assertNotNull(OrgAccessEvaluator.validateOwnerAccountAndOrgDeletion(singleOwned, "wrong-code"))
        assertNull(OrgAccessEvaluator.validateOwnerAccountAndOrgDeletion(singleOwned, "  STS-GHAZIPUR "))

        // Multiple owned orgs require exact phrase
        assertNotNull(OrgAccessEvaluator.validateOwnerAccountAndOrgDeletion(multiOwned, "sts-ghazipur"))
        assertNull(OrgAccessEvaluator.validateOwnerAccountAndOrgDeletion(multiOwned, "DELETE ALL ORGANISATIONS"))
    }

    @Test
    fun legalConfig_placeholderCheckDetectsUnconfiguredExampleLinks() {
        assertFalse(LegalConfig.isConfiguredUrl(LegalConfig.PRIVACY_POLICY_URL))
        assertFalse(LegalConfig.isConfiguredUrl("http://insecure.example.com/privacy.html"))
        assertTrue(LegalConfig.isConfiguredUrl("https://school.example.org/privacy.html"))
    }

    // =========================================================================
    // 2. UI TESTS: DELETE ACCOUNT DIALOG (NORMAL MEMBER vs SOLE OWNER)
    // =========================================================================

    @Test
    fun deleteAccountDialog_normalMember_showsWhatWillAndWillNotBeDeleted_andRequiresPassword() {
        var submittedPassword: String? = null
        var submittedDeleteOwnedOrgs: Boolean? = null

        composeRule.setContent {
            SchoolAppTheme {
                DeleteAccountDialog(
                    memberships = listOf(
                        CloudMembershipEntry(
                            orgId = "org_1",
                            orgName = "Sunrise Group",
                            orgCode = "sunrise-01",
                            role = OrgRole.TEACHER.name,
                            status = MemberStatus.ACTIVE.wireValue
                        )
                    ),
                    reauthMethod = AuthMethod.EMAIL_PASSWORD,
                    userEmail = "teacher@sunrise.edu",
                    isDeleting = false,
                    errorMessage = null,
                    onConfirmDeleteWithPassword = { pwd, delOrg ->
                        submittedPassword = pwd
                        submittedDeleteOwnedOrgs = delOrg
                    },
                    onConfirmDeleteWithGoogle = { _ -> },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("What WILL be deleted immediately:").assertIsDisplayed()
        composeRule.onNodeWithText("What will NOT be deleted (unless an Owner deletes the organisation):").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_account_password_input").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_account_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("delete_account_password_input").performTextInput("MySecret123")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm_delete_account_button")
            .assertIsEnabled()
            .performClick()

        assertEquals("MySecret123", submittedPassword)
        assertEquals(false, submittedDeleteOwnedOrgs)
    }

    @Test
    fun deleteAccountDialog_soleOwner_requiresTransferOrExplicitWholeOrgConfirmationWithOrgCode() {
        var transferClicked = false
        var googleDeleteCalled = false
        var googleDeleteOrgFlag = false

        composeRule.setContent {
            SchoolAppTheme {
                DeleteAccountDialog(
                    memberships = listOf(
                        CloudMembershipEntry(
                            orgId = "org_1",
                            orgName = "Sunrise Group",
                            orgCode = "sunrise-01",
                            role = OrgRole.OWNER.name,
                            status = MemberStatus.ACTIVE.wireValue
                        )
                    ),
                    reauthMethod = AuthMethod.GOOGLE,
                    userEmail = "owner@sunrise.edu",
                    isDeleting = false,
                    errorMessage = null,
                    onConfirmDeleteWithPassword = { _, _ -> },
                    onConfirmDeleteWithGoogle = { delOrg ->
                        googleDeleteCalled = true
                        googleDeleteOrgFlag = delOrg
                    },
                    onTransferOwnershipClick = { transferClicked = true },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Step 1: Owner sees Transfer Ownership option and whole-org checkbox; destructive confirm button is hidden initially
        composeRule.onNodeWithTag("delete_account_transfer_ownership_button").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_account_whole_org_checkbox_row").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_account_button").assertDoesNotExist()

        // Check the whole-org option -> warning + org code input + Google verify button appear (disabled until code matches)
        composeRule.onNodeWithTag("delete_account_whole_org_checkbox_row")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_account_org_confirm_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_account_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("delete_account_org_confirm_input").performTextInput("SUNRISE-01")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm_delete_account_button")
            .assertIsEnabled()
            .performClick()

        assertTrue(googleDeleteCalled)
        assertTrue(googleDeleteOrgFlag)
        assertFalse(transferClicked)

        // Also test clicking Transfer Ownership button in isolation
        composeRule.onNodeWithTag("delete_account_transfer_ownership_button")
            .performScrollTo()
            .performClick()
        assertTrue(transferClicked)
    }

    // =========================================================================
    // 3. UI TESTS: SETTINGS ABOUT CARD & LEGAL DOCUMENTS
    // =========================================================================

    @Test
    fun settingsAboutAndLegalCard_displaysVersionAndOpensAllFourDocs() {
        val openedDocs = mutableListOf<LegalDocType>()

        composeRule.setContent {
            SchoolAppTheme {
                SettingsAboutAndLegalCard(
                    versionName = "1.2.3",
                    versionCode = 42,
                    onOpenLegalDoc = { openedDocs += it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_about_version_text").assertIsDisplayed()
        composeRule.onNodeWithText("Version 1.2.3 (Build 42)").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_about_privacy_button").performClick()
        composeRule.onNodeWithTag("settings_about_support_button").performClick()
        composeRule.onNodeWithTag("settings_about_terms_button").performClick()
        composeRule.onNodeWithTag("settings_about_delete_account_button").performClick()

        assertEquals(
            listOf(
                LegalDocType.PRIVACY_POLICY,
                LegalDocType.SUPPORT,
                LegalDocType.TERMS_OF_SERVICE,
                LegalDocType.DELETE_ACCOUNT
            ),
            openedDocs
        )
    }

    @Test
    fun privacyAndTermsScreen_deleteAccountDoc_rendersInAppAndEmailInstructions() {
        composeRule.setContent {
            SchoolAppTheme {
                PrivacyAndTermsScreen(
                    docType = LegalDocType.DELETE_ACCOUNT,
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Delete Account & Data").assertIsDisplayed()
        composeRule.onNodeWithText("Normal Member vs Organisation Owner").assertIsDisplayed()
        composeRule.onNodeWithText("Request by Email (If App Uninstalled)").assertIsDisplayed()
    }

    // =========================================================================
    // 4. NARROW PHONE (320dp) + LARGE SYSTEM FONT SCALE (1.3x / 1.5x) TESTS
    // =========================================================================

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
    fun settingsAboutCard_at320dpWithFontScale1_5_buttonsStayInsideWidthAnd48dpTall() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.5f)
            ) {
                SchoolAppTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        SettingsAboutAndLegalCard(
                            versionName = "1.0.0",
                            versionCode = 1,
                            onOpenLegalDoc = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        listOf(
            "settings_about_privacy_button",
            "settings_about_support_button",
            "settings_about_terms_button",
            "settings_about_delete_account_button"
        ).forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Expected $tag height >= 48.dp, got ${bounds.height}", bounds.height >= 47.5.dp)
            assertTrue("Expected $tag width > 40.dp, got ${bounds.width}", bounds.width > 40.dp)
            assertTrue("Expected $tag right (${bounds.right}) <= 320.5.dp", bounds.right <= 320.5.dp)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
    fun dashboardTodayHome_at320dpWithFontScale1_3_rendersWithoutOverflowOrCrash() {
        val activeOrg = CloudOrganisation(id = "org_1", name = "Sunrise Group", ownerUid = "u_owner")
        val ownerMember = CloudMember(
            id = "u_owner",
            uid = "u_owner",
            name = "Principal Verma",
            role = OrgRole.OWNER.name,
            status = MemberStatus.ACTIVE.wireValue,
            allSchools = true,
            permissions = MemberPermissions.presetFor(OrgRole.OWNER).toFirestoreMap()
        )
        val school = SchoolEntity(id = 1L, name = "Sunrise Public School", address = "Lucknow", logoBase64 = "")
        val schoolRec = CloudSchoolRecord(
            docId = "sch_1",
            stableId = 1L,
            name = "Sunrise Public School",
            address = "Lucknow",
            mobile = "9876500000",
            email = "info@sunrise.edu",
            logoBase64 = "",
            active = true,
            classGroups = emptyList(),
            classes = emptyList()
        )
        val class5 = SchoolClassEntity(id = 1L, schoolId = 1L, classGroupId = 1L, name = "5", sortOrder = 1)
        val state = SchoolUiState(
            activeOrg = activeOrg,
            currentMember = ownerMember,
            schools = listOf(school),
            schoolRecordsByDocId = mapOf("sch_1" to schoolRec),
            schoolDocIdByLongId = mapOf(1L to "sch_1"),
            schoolClasses = listOf(class5),
            filter = FilterSelection(schoolId = 1L, session = "2026-2027", className = "5", section = "A"),
            syncUiState = SyncUiState(status = SyncStatus.ONLINE),
            phase10HindiLabels = true
        )

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.3f)
            ) {
                SchoolAppTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        DashboardScreen(
                            uiState = state,
                            onSessionSelected = {},
                            onClassSelected = {},
                            onSectionSelected = {},
                            onStudentSelected = {},
                            onDeleteLegacyMarks = {},
                            onNavigateToSchools = {},
                            onNavigateToStudents = {},
                            onNavigateToMarks = {},
                            onNavigateToCoScholastic = {},
                            onNavigateToReports = {},
                            onNavigateToSettings = {},
                            onNavigateToBackup = {},
                            onOpenStudentMarks = {},
                            onOpenStudentCoScholastic = {},
                            onOpenStudentReport = {},
                            todayIsoOverride = "2026-07-15"
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("today_hero_header_card").assertIsDisplayed()
        composeRule.onNodeWithTag("today_sync_status_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("phase13_language_toggle").assertIsDisplayed()
        val langBounds = composeRule.onNodeWithTag("phase13_language_toggle").getUnclippedBoundsInRoot()
        assertTrue("Expected lang toggle width > 40.dp, got ${langBounds.width}", langBounds.width > 40.dp)
        assertTrue("Expected lang toggle right (${langBounds.right}) <= 320.5.dp", langBounds.right <= 320.5.dp)
    }
}
