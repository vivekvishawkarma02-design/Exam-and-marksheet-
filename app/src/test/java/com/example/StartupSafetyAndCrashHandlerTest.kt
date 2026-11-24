package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.SchoolReportCardApp
import com.example.ui.theme.SchoolAppTheme
import com.example.util.StartupSafetyManager
import com.google.firebase.FirebaseApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupSafetyAndCrashHandlerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        StartupSafetyManager.clearLastCrashStackTrace(context)
        FirebaseApp.clearInstancesForTest()
    }

    @After
    fun tearDown() {
        StartupSafetyManager.clearLastCrashStackTrace(context)
        FirebaseApp.clearInstancesForTest()
    }

    @Test
    fun whenFirebaseIsNotConfigured_showsFallbackScreenWithoutCrashing() {
        assertTrue(FirebaseApp.getApps(context).isEmpty())
        assertFalse(StartupSafetyManager.isFirebaseConfigured(context))

        composeTestRule.setContent {
            SchoolAppTheme {
                SchoolReportCardApp()
            }
        }

        composeTestRule
            .onNodeWithTag("firebase_not_configured_screen")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Firebase is not configured in this build. Install the build made with google-services.json.")
            .assertIsDisplayed()
    }

    @Test
    fun whenCrashWasSavedOnPreviousLaunch_showsCrashScreenWithCopyAndShareAndCanDismiss() {
        val fakeError = IllegalStateException("Simulated startup crash in test")
        StartupSafetyManager.saveCrashStackTrace(
            context = context,
            threadName = "main",
            throwable = fakeError,
            timestampMs = 1700000000000L
        )

        val stored = StartupSafetyManager.readLastCrashStackTrace(context)
        assertNotNull(stored)
        assertTrue(stored!!.contains("IllegalStateException: Simulated startup crash in test"))

        composeTestRule.setContent {
            SchoolAppTheme {
                SchoolReportCardApp()
            }
        }

        composeTestRule.onNodeWithTag("last_crash_report_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crash_copy_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crash_share_button").assertIsDisplayed()

        // Test Copy button copies stack trace to clipboard
        composeTestRule.onNodeWithTag("crash_copy_button").performClick()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedText.contains("Simulated startup crash in test"))

        // Test Dismiss clears crash trace and proceeds to Firebase check screen
        composeTestRule.onNodeWithTag("crash_dismiss_button").performClick()
        composeTestRule.waitForIdle()

        assertNull(StartupSafetyManager.readLastCrashStackTrace(context))
        composeTestRule.onNodeWithTag("firebase_not_configured_screen").assertIsDisplayed()
    }
}
