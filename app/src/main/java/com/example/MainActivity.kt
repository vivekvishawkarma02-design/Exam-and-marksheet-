package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.SchoolReportCardApp
import com.example.ui.theme.AppearancePrefsStore
import com.example.ui.theme.LocalAppearanceController
import com.example.ui.theme.SchoolAppTheme
import com.example.util.StartupSafetyManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        StartupSafetyManager.installGlobalExceptionHandler(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearanceStore = remember { AppearancePrefsStore(applicationContext) }
            val appearanceSettings by appearanceStore.settings.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppearanceController provides appearanceStore) {
                SchoolAppTheme(
                    themeMode = appearanceSettings.themeMode,
                    simpleLook = appearanceSettings.simpleLook
                ) {
                    SchoolReportCardApp()
                }
            }
        }
    }
}
