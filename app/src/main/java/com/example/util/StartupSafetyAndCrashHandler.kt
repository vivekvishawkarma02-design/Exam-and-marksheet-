package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StartupSafetyManager {
    const val FIREBASE_NOT_CONFIGURED_MESSAGE =
        "Firebase is not configured in this build. Install the build made with google-services.json."

    private const val CRASH_FILE_NAME = "last_crash_stacktrace.txt"

    @Volatile
    private var handlerInstalled = false

    fun isFirebaseConfigured(context: Context): Boolean {
        return try {
            !FirebaseApp.getApps(context).isEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    fun installGlobalExceptionHandler(context: Context) {
        if (handlerInstalled) return
        synchronized(this) {
            if (handlerInstalled) return
            val appContext = context.applicationContext ?: context
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (previousHandler is AppUncaughtExceptionHandler) {
                handlerInstalled = true
                return
            }
            Thread.setDefaultUncaughtExceptionHandler(
                AppUncaughtExceptionHandler(appContext, previousHandler)
            )
            handlerInstalled = true
        }
    }

    private class AppUncaughtExceptionHandler(
        private val appContext: Context,
        private val previousHandler: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                saveCrashStackTrace(appContext, thread.name, throwable)
            } catch (_: Throwable) {
                // Ignore secondary failures while writing crash report
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun formatStackTrace(threadName: String, throwable: Throwable, timestampMs: Long = System.currentTimeMillis()): String {
        val dateStr = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(timestampMs))
        } catch (_: Throwable) {
            timestampMs.toString()
        }
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("Timestamp: $dateStr")
        pw.println("Thread: $threadName")
        pw.println("Exception: ${throwable.javaClass.name}: ${throwable.message ?: ""}")
        pw.println()
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    fun saveCrashStackTrace(
        context: Context,
        threadName: String,
        throwable: Throwable,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val text = formatStackTrace(threadName, throwable, timestampMs)
        val file = File(context.filesDir, CRASH_FILE_NAME)
        file.writeText(text, Charsets.UTF_8)
    }

    fun readLastCrashStackTrace(context: Context): String? {
        return try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists() && file.isFile) {
                val content = file.readText(Charsets.UTF_8).trim()
                content.ifEmpty { null }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun clearLastCrashStackTrace(context: Context) {
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Throwable) {
            // Ignore
        }
    }

    fun copyToClipboard(context: Context, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Crash Stack Trace", text))
            clipboard != null
        } catch (_: Throwable) {
            false
        }
    }

    fun shareStackTrace(context: Context, text: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "School Report Card - Crash Report")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Crash Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {
            // Ignore if no activity can handle share intent
        }
    }
}

@Composable
fun FirebaseNotConfiguredScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("firebase_not_configured_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Firebase not configured",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = StartupSafetyManager.FIREBASE_NOT_CONFIGURED_MESSAGE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("firebase_not_configured_text")
                    )
                }
            }
        }
    }
}

@Composable
fun LastCrashReportScreen(
    stackTrace: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("last_crash_report_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Crash report",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = "Last Crash Stack Trace",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "An unexpected error occurred on the previous launch. Copy or share this report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        StartupSafetyManager.copyToClipboard(context, stackTrace)
                        copiedNotice = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crash_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy crash report",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (copiedNotice) "Copied" else "Copy")
                }

                Button(
                    onClick = {
                        StartupSafetyManager.shareStackTrace(context, stackTrace)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crash_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share crash report",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("crash_dismiss_button")
                ) {
                    Text("Continue")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stackTrace,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("crash_stacktrace_text")
                    )
                }
            }
        }
    }
}
