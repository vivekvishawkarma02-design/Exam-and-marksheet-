package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportStop
import com.example.domain.TransportTripType
import com.example.ui.StudentRegisterTransportSection
import com.example.ui.TransportSummaryBar
import com.example.ui.TransportTopHeader
import com.example.ui.theme.SchoolAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
class Phase11TransportLayoutRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    private fun verifyTransportHeaderSummaryAndTabsAt320dp(fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = fontScale)) {
                SchoolAppTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TransportTopHeader(
                                schoolName = "Sunrise International Public School",
                                session = "2026-2027",
                                activeRouteCount = 4,
                                isHindi = false,
                                onBack = {},
                                onToggleHindi = {},
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            TransportSummaryBar(
                                totalTransportStudents = 128,
                                seatsUsed = 128,
                                totalCapacity = 140,
                                expectedMonthlyIncomeRs = 89600,
                                monthCode = "AUG",
                                duesThisMonthRs = 14200,
                                isHindi = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            PrimaryScrollableTabRow(
                                selectedTabIndex = 0,
                                edgePadding = 12.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("transport_tab_bar")
                            ) {
                                listOf("Routes", "Students", "Dues", "Vehicles & Expenses").forEachIndexed { idx, label ->
                                    Tab(
                                        selected = idx == 0,
                                        onClick = {},
                                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("transport_tab_$idx")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val titleBounds = composeRule.onNodeWithTag("transport_header_title").assertIsDisplayed().getUnclippedBoundsInRoot()
        val langBounds = composeRule.onNodeWithTag("transport_lang_toggle").assertIsDisplayed().getUnclippedBoundsInRoot()
        val summaryBounds = composeRule.onNodeWithTag("transport_summary_bar").assertIsDisplayed().getUnclippedBoundsInRoot()
        val backBounds = composeRule.onNodeWithTag("transport_back_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val tab0Bounds = composeRule.onNodeWithTag("transport_tab_0").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue(
            "Title must not wrap vertically at $fontScale (width=${titleBounds.width}, height=${titleBounds.height})",
            titleBounds.width > titleBounds.height && titleBounds.width >= 140.dp
        )
        assertTrue("Back button >= 48dp touch target, got ${backBounds.height}", backBounds.height >= 47.5.dp)
        assertTrue("Toggle right (${langBounds.right}) <= 320dp at $fontScale", langBounds.right <= 320.5.dp)
        assertTrue(
            "Language toggle (${langBounds.top}) below title/header row (${titleBounds.bottom}) at $fontScale",
            langBounds.top >= titleBounds.bottom - 1.dp
        )
        assertTrue("Summary bar right (${summaryBounds.right}) <= 320dp at $fontScale", summaryBounds.right <= 320.5.dp)
        assertTrue("Tab height >= 48dp at $fontScale", tab0Bounds.height >= 47.5.dp)
    }

    @Test
    fun transportHeaderSummaryTabs_320dp_font1_3_noClip() {
        verifyTransportHeaderSummaryAndTabsAt320dp(1.3f)
    }

    @Test
    fun transportHeaderSummaryTabs_320dp_font1_5_noClip() {
        verifyTransportHeaderSummaryAndTabsAt320dp(1.5f)
    }

    private fun verifyStudentRegisterTransportSectionAt320dp(fontScale: Float) {
        val sampleRoute = TransportRouteRecord(
            id = "rt_1",
            schoolId = "sch_1",
            routeName = "North Bus Express",
            seatCapacity = 30,
            monthlyFeeRs = 800,
            pickupOnlyFeeRs = 550,
            dropOnlyFeeRs = 500,
            stops = listOf(TransportStop("s1", "Station Road", "07:30 AM", 850))
        )
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = fontScale)) {
                SchoolAppTheme {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        StudentRegisterTransportSection(
                            isHindi = false,
                            usesTransport = true,
                            onUsesTransportChange = {},
                            routes = listOf(sampleRoute),
                            allSchoolStudents = emptyList(),
                            existingStudentId = null,
                            selectedRouteId = "rt_1",
                            selectedStopId = "s1",
                            selectedStopName = "Station Road",
                            routeDisplayFallback = "North Bus Express - Station Road",
                            onRouteDisplayFallbackChange = {},
                            selectedTripType = TransportTripType.PICKUP_ONLY,
                            selectedStartMonth = "AUG",
                            selectedEndMonth = "",
                            chargedMonths = listOf("APR", "MAY", "JUN", "JUL", "AUG"),
                            amountText = "550",
                            onAmountTextChange = {},
                            feeOverridden = true,
                            overrideRemark = "Sibling discount",
                            onOverrideRemarkChange = {},
                            overCapacityAcknowledged = false,
                            onOverCapacityAcknowledgedChange = {},
                            onSelectRoute = {},
                            onSelectStop = { _, _ -> },
                            onSelectTripType = {},
                            onSelectStartMonth = {},
                            onSelectEndMonth = {},
                            onToggleOverride = {},
                            onResetToRouteFee = {},
                            canNavigateToTransport = true,
                            onCreateRouteShortcut = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val sectionBounds = composeRule.onNodeWithTag("register_transport_section").assertIsDisplayed().getUnclippedBoundsInRoot()
        val resetBtnBounds = composeRule.onNodeWithTag("register_transport_reset_fee_btn").assertIsDisplayed().getUnclippedBoundsInRoot()
        val tripBounds = composeRule.onNodeWithTag("register_trip_PICKUP_ONLY").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue("Transport section right (${sectionBounds.right}) <= 320dp at $fontScale", sectionBounds.right <= 320.5.dp)
        assertTrue("Reset button >= 48dp touch target, got ${resetBtnBounds.height}", resetBtnBounds.height >= 47.5.dp)
        assertTrue("Reset button right (${resetBtnBounds.right}) <= 320dp at $fontScale", resetBtnBounds.right <= 320.5.dp)
        assertTrue("Trip chip >= 48dp touch target, got ${tripBounds.height}", tripBounds.height >= 47.5.dp)
    }

    @Test
    fun studentRegisterTransportSection_320dp_font1_3_noClip() {
        verifyStudentRegisterTransportSectionAt320dp(1.3f)
    }

    @Test
    fun studentRegisterTransportSection_320dp_font1_5_noClip() {
        verifyStudentRegisterTransportSectionAt320dp(1.5f)
    }
}
