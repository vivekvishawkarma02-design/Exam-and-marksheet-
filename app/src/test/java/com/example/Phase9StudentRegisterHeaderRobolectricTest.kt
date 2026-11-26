package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.ui.StudentRegisterHeader
import com.example.ui.theme.SchoolAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class Phase9StudentRegisterHeaderRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun studentRegisterHeader_at360dp_titleIsNotSqueezedAndBothButtonsAreVisible() {
        composeRule.setContent {
            SchoolAppTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    StudentRegisterHeader(
                        schoolName = "Sunrise International Public School",
                        session = "2026-2027",
                        canEditRegister = true,
                        idCardSelectionMode = false,
                        selectedIdCardsCount = 0,
                        onBack = {},
                        onPrintIdCards = {},
                        onAddStudent = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val titleNode = composeRule.onNodeWithTag("register_header_title").assertIsDisplayed()
        val subtitleNode = composeRule.onNodeWithTag("register_header_subtitle").assertIsDisplayed()
        val idCardsNode = composeRule.onNodeWithTag("register_print_id_cards_button").assertIsDisplayed()
        val addStudentNode = composeRule.onNodeWithTag("register_add_student_button").assertIsDisplayed()

        val titleBounds = titleNode.getUnclippedBoundsInRoot()
        val subtitleBounds = subtitleNode.getUnclippedBoundsInRoot()
        val idCardsBounds = idCardsNode.getUnclippedBoundsInRoot()
        val addStudentBounds = addStudentNode.getUnclippedBoundsInRoot()

        // Title must not wrap into one letter per line: width clearly exceeds height and stays single-line
        assertTrue(
            "Expected title width (${titleBounds.width}) > height (${titleBounds.height}) at 360dp",
            titleBounds.width > titleBounds.height
        )
        assertTrue(
            "Expected single-line title height < 48.dp, got ${titleBounds.height}",
            titleBounds.height < 48.dp
        )
        assertTrue(
            "Expected subtitle width (${subtitleBounds.width}) > height (${subtitleBounds.height})",
            subtitleBounds.width > subtitleBounds.height
        )

        // Row 2 buttons must sit below Row 1, be >= 48dp tall, equal width, and non-overlapping
        assertTrue(
            "Expected ID Cards button top (${idCardsBounds.top}) >= subtitle bottom (${subtitleBounds.bottom})",
            idCardsBounds.top >= subtitleBounds.bottom - 1.dp
        )
        assertTrue(
            "Expected ID Cards button height >= 48.dp, got ${idCardsBounds.height}",
            idCardsBounds.height >= 47.5.dp
        )
        assertTrue(
            "Expected Add Student button height >= 48.dp, got ${addStudentBounds.height}",
            addStudentBounds.height >= 47.5.dp
        )
        assertTrue(
            "Expected equal button widths, got idCards=${idCardsBounds.width}, addStudent=${addStudentBounds.width}",
            abs(idCardsBounds.width.value - addStudentBounds.width.value) <= 2f
        )
        assertTrue(
            "Expected buttons side-by-side without overlap",
            addStudentBounds.left >= idCardsBounds.right
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
    fun studentRegisterHeader_at320dpWithFontScale1_3_titleIsNotSqueezedAndBothButtonsAreVisible() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.3f)
            ) {
                SchoolAppTheme {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        StudentRegisterHeader(
                            schoolName = "Sunrise International Public School",
                            session = "2026-2027",
                            canEditRegister = true,
                            idCardSelectionMode = false,
                            selectedIdCardsCount = 0,
                            onBack = {},
                            onPrintIdCards = {},
                            onAddStudent = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val titleNode = composeRule.onNodeWithTag("register_header_title").assertIsDisplayed()
        val subtitleNode = composeRule.onNodeWithTag("register_header_subtitle").assertIsDisplayed()
        val idCardsNode = composeRule.onNodeWithTag("register_print_id_cards_button").assertIsDisplayed()
        val addStudentNode = composeRule.onNodeWithTag("register_add_student_button").assertIsDisplayed()

        val titleBounds = titleNode.getUnclippedBoundsInRoot()
        val subtitleBounds = subtitleNode.getUnclippedBoundsInRoot()
        val idCardsBounds = idCardsNode.getUnclippedBoundsInRoot()
        val addStudentBounds = addStudentNode.getUnclippedBoundsInRoot()

        assertTrue(
            "Expected title width (${titleBounds.width}) > height (${titleBounds.height}) at 320dp + 1.3x font scale",
            titleBounds.width > titleBounds.height
        )
        assertTrue(
            "Expected wide title >= 140.dp at 320dp + 1.3x font scale, got ${titleBounds.width}",
            titleBounds.width >= 140.dp
        )
        assertTrue(
            "Expected subtitle width (${subtitleBounds.width}) > height (${subtitleBounds.height})",
            subtitleBounds.width > subtitleBounds.height
        )
        assertTrue(
            "Expected ID Cards button below Row 1",
            idCardsBounds.top >= subtitleBounds.bottom - 1.dp
        )
        assertTrue(
            "Expected ID Cards button height >= 48.dp, got ${idCardsBounds.height}",
            idCardsBounds.height >= 47.5.dp
        )
        assertTrue(
            "Expected Add Student button height >= 48.dp, got ${addStudentBounds.height}",
            addStudentBounds.height >= 47.5.dp
        )
        assertTrue(
            "Expected equal button widths at 320dp + 1.3x font scale, got idCards=${idCardsBounds.width}, addStudent=${addStudentBounds.width}",
            abs(idCardsBounds.width.value - addStudentBounds.width.value) <= 2f
        )
        assertTrue(
            "Expected Add Student button right (${addStudentBounds.right}) <= 320.dp",
            addStudentBounds.right <= 320.5.dp
        )
    }
}
