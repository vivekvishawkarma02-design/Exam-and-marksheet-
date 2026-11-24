package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.SchoolReportCardApp
import com.example.ui.theme.SchoolAppTheme
import com.example.util.StartupSafetyManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        StartupSafetyManager.installGlobalExceptionHandler(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SchoolAppTheme {
                SchoolReportCardApp()
            }
        }
    }
}
