package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AuthorDisplayHelper
import com.example.domain.BirthdayOccurrenceItem
import com.example.domain.BirthdayPersonType
import com.example.domain.ClassDiaryEntryRecord
import com.example.domain.Phase18AccessEvaluator
import com.example.domain.Phase18DiaryAndBirthdayEngine
import com.example.domain.Phase6DateUtils
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.util.BirthdayCardRenderBundle
import com.example.util.ImageUtils
import com.example.util.PdfExportHelper
import com.example.util.Phase18PdfGenerator

// =============================================================================
// PHASE 18: CLASS DIARY SCREEN
// =============================================================================

private enum class ClassDiaryTab(val en: String, val hi: String) {
    DAILY("Daily Diary", "दैनिक डायरी"),
    WEEKLY("Weekly & PDF", "साप्ताहिक व PDF"),
    NOT_WRITTEN("Not Written Today", "आज नहीं लिखी गई"),
    HISTORY("History", "इतिहास")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase18ClassDiaryScreen(
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hi = uiState.phase10HindiLabels

    if (!uiState.canViewClassDiary) {
        Scaffold { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "No Access",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = p10Label(hi, "कक्षा डायरी की अनुमति नहीं है", "No Access to Class Diary"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = p10Label(
                        hi,
                        "कृपया स्कूल एडमिन से 'Class Diary' अनुमति या अपनी कक्षा/विषय असाइनमेंट जाँचने के लिए कहें।",
                        "Please ask your school admin to grant Class Diary access or assign your class/subject."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("class_diary_no_access_back_btn")
                ) {
                    Text(p10Label(hi, "वापस जाएँ", "Go Back"))
                }
            }
        }
        return
    }

    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val todayIso = remember { Phase6DateUtils.todayIso() }

    val allClasses = remember(uiState.activeSchoolClassNames, uiState.ownTeacherScope, uiState.accessProfile, activeSchoolDocId) {
        val base = uiState.activeSchoolClassNames.ifEmpty { listOf("1st", "2nd", "3rd", "4th", "5th") }
        base
    }

    var selectedClass by remember(activeSchool.id, allClasses) {
        mutableStateOf(
            uiState.effectiveClassName.takeIf { it in allClasses }
                ?: allClasses.firstOrNull().orEmpty()
        )
    }

    val sectionsForClass = remember(activeSchool.id, selectedClass, uiState.allStudents, uiState.classAssignments) {
        uiState.sectionsForSchoolClass(activeSchool.id, selectedClass).ifEmpty { listOf("A") }
    }

    var selectedSection by remember(selectedClass, sectionsForClass) {
        mutableStateOf(
            uiState.filter.section.takeIf { it in sectionsForClass }
                ?: sectionsForClass.firstOrNull() ?: "A"
        )
    }

    var selectedDateIso by remember { mutableStateOf(todayIso) }

    val availableSubjects = remember(
        activeSchoolDocId,
        selectedClass,
        selectedSection,
        uiState.subjects,
        uiState.classAssignments,
        uiState.ownTeacherScope
    ) {
        viewModel.availableDiarySubjectsForClassSection(
            schoolDocId = activeSchoolDocId,
            className = selectedClass,
            section = selectedSection
        ).ifEmpty { listOf("Hindi", "English", "Mathematics", "EVS / Science", "Social Science") }
    }

    var selectedSubject by remember(selectedClass, selectedSection, availableSubjects) {
        mutableStateOf(availableSubjects.firstOrNull().orEmpty())
    }

    var classworkText by remember { mutableStateOf("") }
    var homeworkText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf("") }
    var editingEntryId by remember { mutableStateOf<String?>(null) }

    val entriesForClassDate = remember(
        uiState.classDiaryEntries,
        activeSchoolDocId,
        session,
        selectedDateIso,
        selectedClass,
        selectedSection
    ) {
        Phase18DiaryAndBirthdayEngine.entriesForClassAndDate(
            allEntries = uiState.classDiaryEntries,
            schoolId = activeSchoolDocId,
            session = session,
            dateIso = selectedDateIso,
            className = selectedClass,
            section = selectedSection
        )
    }

    // Auto-populate form when selecting a subject that already has an entry today (if user can edit it)
    LaunchedEffect(selectedClass, selectedSection, selectedDateIso, selectedSubject) {
        val existing = entriesForClassDate.firstOrNull {
            it.subjectName.equals(selectedSubject, ignoreCase = true)
        }
        if (existing != null && viewModel.canEditOrDeleteDiaryEntry(existing)) {
            editingEntryId = existing.id
            classworkText = existing.classwork
            homeworkText = existing.homework
            noteText = existing.note
            photoBase64 = existing.photoBase64
        } else if (editingEntryId != null) {
            editingEntryId = null
            classworkText = ""
            homeworkText = ""
            noteText = ""
            photoBase64 = ""
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val encoded = ImageUtils.encodeUriToBase64Under150Kb(context, uri)
            if (!encoded.isNullOrBlank()) {
                photoBase64 = encoded
                Toast.makeText(
                    context,
                    p10Label(hi, "ब्लैकबोर्ड/होमवर्क फोटो जुड़ गया", "Blackboard/homework photo attached"),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    p10Label(hi, "फोटो लोड नहीं हो सका", "Could not load photo"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    var photoZoomBase64 by remember { mutableStateOf<String?>(null) }
    var entryPendingDelete by remember { mutableStateOf<ClassDiaryEntryRecord?>(null) }

    val visibleTabs = remember(uiState.canViewDiaryNotWrittenToday) {
        if (uiState.canViewDiaryNotWrittenToday) {
            ClassDiaryTab.entries
        } else {
            listOf(ClassDiaryTab.DAILY, ClassDiaryTab.WEEKLY, ClassDiaryTab.HISTORY)
        }
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val currentTab = visibleTabs.getOrElse(selectedTabIndex) { ClassDiaryTab.DAILY }

    val shareTextForDay = remember(
        activeSchool.name,
        selectedClass,
        selectedSection,
        selectedDateIso,
        entriesForClassDate,
        hi
    ) {
        Phase18DiaryAndBirthdayEngine.buildShareMessageForClassDate(
            schoolName = activeSchool.name,
            className = selectedClass,
            section = selectedSection,
            dateIso = selectedDateIso,
            entries = entriesForClassDate,
            hindiLabels = hi
        )
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Back arrow + Title + Subtitle (two-row header so 320-360dp phones never squeeze title)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("class_diary_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = p10Label(hi, "वापस", "Back")
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(hi, "कक्षा डायरी (Class Diary)", "Class Diary"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeSchool.name} • $session • $selectedClass-$selectedSection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { viewModel.setPhase10HindiLabels(!hi) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("class_diary_lang_toggle_btn")
                        ) {
                            Text(if (hi) "EN" else "हिंदी", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Row 2: Equal-weight primary actions (Share as message & Weekly PDF)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${activeSchool.name} - Class $selectedClass-$selectedSection Diary ($selectedDateIso)")
                                    putExtra(Intent.EXTRA_TEXT, shareTextForDay)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        sendIntent,
                                        p10Label(hi, "डायरी संदेश साझा करें", "Share Class Diary Message")
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("diary_share_message_btn"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = p10Label(hi, "संदेश भेजें", "Share Message"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.previewWeeklyClassDiaryPdf(
                                    schoolDocId = activeSchoolDocId,
                                    session = session,
                                    className = selectedClass,
                                    section = selectedSection,
                                    anyDateInWeekIso = selectedDateIso
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("diary_weekly_pdf_btn"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = p10Label(hi, "साप्ताहिक PDF", "Weekly PDF"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Tabs
                    SecondaryScrollableTabRow(
                        selectedTabIndex = selectedTabIndex.coerceIn(0, visibleTabs.lastIndex),
                        edgePadding = 4.dp
                    ) {
                        visibleTabs.forEachIndexed { idx, tab ->
                            val missingCount = if (tab == ClassDiaryTab.NOT_WRITTEN) {
                                uiState.diaryNotWrittenTodayForActiveSchool(todayIso).size
                            } else 0
                            Tab(
                                selected = selectedTabIndex == idx,
                                onClick = { selectedTabIndex = idx },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("class_diary_tab_${tab.name.lowercase()}"),
                                text = {
                                    val baseLabel = p10Label(hi, tab.hi, tab.en)
                                    val badge = if (missingCount > 0) " ($missingCount)" else ""
                                    Text(
                                        text = baseLabel + badge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Class, Section, and Date Selector Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.activeSchools.size > 1) {
                            CompactDropdownSelector(
                                label = p10Label(hi, "स्कूल", "School"),
                                selectedValue = activeSchool.name,
                                options = uiState.activeSchools.map { it.name },
                                onSelect = { name ->
                                    uiState.activeSchools.firstOrNull { it.name == name }?.let {
                                        viewModel.selectSchool(it.id)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactDropdownSelector(
                                label = p10Label(hi, "कक्षा (Class)", "Class"),
                                selectedValue = selectedClass,
                                options = allClasses,
                                onSelect = { cls ->
                                    selectedClass = cls
                                    viewModel.selectClass(cls)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            CompactDropdownSelector(
                                label = p10Label(hi, "सेक्शन (Section)", "Section"),
                                selectedValue = selectedSection,
                                options = sectionsForClass,
                                onSelect = { sec ->
                                    selectedSection = sec
                                    viewModel.selectSection(sec)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Date Selector with quick -1 Day / Today / +1 Day buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedDateIso = Phase18DiaryAndBirthdayEngine.shiftDateIso(selectedDateIso, -1)
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("diary_prev_day_btn"),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("◀ -1d", maxLines = 1)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = selectedDateIso,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = Phase18DiaryAndBirthdayEngine.dayOfWeekShortLabel(selectedDateIso, hi),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (selectedDateIso != todayIso) {
                                TextButton(
                                    onClick = { selectedDateIso = todayIso },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("diary_today_btn")
                                ) {
                                    Text(p10Label(hi, "आज", "Today"), fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedDateIso = Phase18DiaryAndBirthdayEngine.shiftDateIso(selectedDateIso, 1)
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("diary_next_day_btn"),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("+1d ▶", maxLines = 1)
                            }
                        }
                    }
                }
            }

            when (currentTab) {
                ClassDiaryTab.DAILY -> {
                    val canWriteCurrentSubject = viewModel.canWriteDiaryForSubject(
                        schoolDocId = activeSchoolDocId,
                        className = selectedClass,
                        section = selectedSection,
                        subjectName = selectedSubject
                    )

                    // Quick 1-minute Diary Entry Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (editingEntryId != null) {
                                            p10Label(hi, "डायरी प्रविष्टि संपादित करें", "Edit Subject Diary Entry")
                                        } else {
                                            p10Label(hi, "विषय डायरी लिखें (< 1 मिनट)", "Write Subject Diary (< 1 min)")
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (editingEntryId != null) {
                                        TextButton(
                                            onClick = {
                                                editingEntryId = null
                                                classworkText = ""
                                                homeworkText = ""
                                                noteText = ""
                                                photoBase64 = ""
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text(p10Label(hi, "नया लिखें", "Clear / New"))
                                        }
                                    }
                                }

                                // Wrapping Subject FilterChips so teacher taps subject in 1 click
                                Text(
                                    text = p10Label(hi, "विषय चुनें (Subject):", "Pick Subject:"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    availableSubjects.forEach { subj ->
                                        val hasEntryForSubj = entriesForClassDate.any {
                                            it.subjectName.equals(subj, ignoreCase = true)
                                        }
                                        val isSelected = selectedSubject.equals(subj, ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedSubject = subj },
                                            label = {
                                                Text(
                                                    text = if (hasEntryForSubj) "$subj ✓" else subj,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("diary_subject_chip_${subj.lowercase().replace(" ", "_")}")
                                        )
                                    }
                                }

                                if (!canWriteCurrentSubject) {
                                    Surface(
                                        color = AbsentAmberContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "आप केवल अपनी आवंटित कक्षा और विषय ($selectedClass-$selectedSection • $selectedSubject) के लिए डायरी लिख सकते हैं।",
                                                "You have view-only access for $selectedClass-$selectedSection ($selectedSubject). Assigned teachers or admins can write entries."
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AbsentAmber,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = classworkText,
                                        onValueChange = { classworkText = it },
                                        label = {
                                            Text(
                                                p10Label(
                                                    hi,
                                                    "आज क्या पढ़ाया गया (Classwork)",
                                                    "What was taught today (Classwork)"
                                                )
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                p10Label(
                                                    hi,
                                                    "उदा. पाठ 4 समझाया और प्रश्न 1-3 कराए",
                                                    "e.g. Explained Ch 4 fractions & solved Ex 4.1 Q1-Q3"
                                                )
                                            )
                                        },
                                        minLines = 2,
                                        maxLines = 4,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("diary_classwork_input")
                                    )

                                    OutlinedTextField(
                                        value = homeworkText,
                                        onValueChange = { homeworkText = it },
                                        label = {
                                            Text(
                                                p10Label(
                                                    hi,
                                                    "गृहकार्य (Homework)",
                                                    "Homework"
                                                )
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                p10Label(
                                                    hi,
                                                    "उदा. प्रश्न 4-6 कॉपी में पूरे करें",
                                                    "e.g. Complete Ex 4.1 Q4-Q6 in fair notebook"
                                                )
                                            )
                                        },
                                        minLines = 2,
                                        maxLines = 4,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("diary_homework_input")
                                    )

                                    OutlinedTextField(
                                        value = noteText,
                                        onValueChange = { noteText = it },
                                        label = {
                                            Text(
                                                p10Label(
                                                    hi,
                                                    "विशेष नोट (Optional Note)",
                                                    "Optional Note"
                                                )
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                p10Label(
                                                    hi,
                                                    "उदा. कल टेस्ट होगा / ग्राफ पेपर लाएँ",
                                                    "e.g. Bring geometry box tomorrow"
                                                )
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("diary_note_input")
                                    )

                                    // Optional Blackboard / Homework page photo attachment
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("diary_attach_photo_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (photoBase64.isBlank()) {
                                                    p10Label(hi, "ब्लैकबोर्ड/होमवर्क फोटो (वैकल्पिक)", "Blackboard / Homework Photo")
                                                } else {
                                                    p10Label(hi, "फोटो बदलें", "Change Photo")
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (photoBase64.isNotBlank()) {
                                            val thumbBmp = remember(photoBase64) {
                                                ImageUtils.decodeBase64ToBitmap(photoBase64)
                                            }
                                            if (thumbBmp != null) {
                                                Image(
                                                    bitmap = thumbBmp.asImageBitmap(),
                                                    contentDescription = "Attached blackboard photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { photoZoomBase64 = photoBase64 }
                                                )
                                            }
                                            IconButton(
                                                onClick = { photoBase64 = "" },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("diary_remove_photo_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove photo",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.saveClassDiaryEntry(
                                                schoolDocId = activeSchoolDocId,
                                                session = session,
                                                dateIso = selectedDateIso,
                                                className = selectedClass,
                                                section = selectedSection,
                                                subjectName = selectedSubject,
                                                classwork = classworkText,
                                                homework = homeworkText,
                                                note = noteText,
                                                photoBase64 = photoBase64,
                                                existingEntryId = editingEntryId
                                            ) { ok, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                if (ok) {
                                                    val nextSubj = availableSubjects.firstOrNull { cand ->
                                                        !cand.equals(selectedSubject, ignoreCase = true) &&
                                                            entriesForClassDate.none { it.subjectName.equals(cand, ignoreCase = true) }
                                                    }
                                                    if (nextSubj != null && editingEntryId == null) {
                                                        selectedSubject = nextSubj
                                                        classworkText = ""
                                                        homeworkText = ""
                                                        noteText = ""
                                                        photoBase64 = ""
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("diary_save_entry_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (editingEntryId != null) {
                                                p10Label(hi, "$selectedSubject डायरी अपडेट करें", "Update $selectedSubject Entry")
                                            } else {
                                                p10Label(hi, "$selectedSubject डायरी सेव करें", "Save $selectedSubject Entry")
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // One-Tap Share / Copy Class Message Preview Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "व्हाट्सएप समूह संदेश ($selectedClass-$selectedSection • ${entriesForClassDate.size} विषय)",
                                            "Class WhatsApp Message ($selectedClass-$selectedSection • ${entriesForClassDate.size} subject(s))"
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = shareTextForDay,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(shareTextForDay))
                                            Toast.makeText(
                                                context,
                                                p10Label(hi, "डायरी संदेश कॉपी किया गया", "Class diary message copied"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("diary_copy_message_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = p10Label(hi, "कॉपी करें", "Copy Message"),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareTextForDay)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    sendIntent,
                                                    p10Label(hi, "व्हाट्सएप/संदेश पर साझा करें", "Share as Message")
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("diary_share_card_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = p10Label(hi, "शेयर करें", "Share as Message"),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Entries List for Selected Class & Date
                    item {
                        Text(
                            text = p10Label(
                                hi,
                                "$selectedDateIso की डायरी प्रविष्टियाँ (${entriesForClassDate.size})",
                                "Diary Entries for $selectedDateIso (${entriesForClassDate.size})"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entriesForClassDate.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = p10Label(
                                        hi,
                                        "इस तारीख के लिए अभी तक कोई डायरी नहीं लिखी गई है। ऊपर विषय चुनकर लिखें।",
                                        "No diary entries written for this class and date yet."
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(entriesForClassDate, key = { it.id }) { entry ->
                            val canModify = viewModel.canEditOrDeleteDiaryEntry(entry)
                            val authorDisplay = AuthorDisplayHelper.resolveAuthorName(
                                authorUid = entry.authorUid,
                                storedName = entry.authorName,
                                deletedUserUids = uiState.deletedUserUids
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("diary_entry_card_${entry.subjectName.lowercase().replace(" ", "_")}"),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = entry.subjectName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (entry.editHistory.isNotEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = p10Label(
                                                        hi,
                                                        "${entry.editHistory.size} बार संशोधित",
                                                        "Edited ${entry.editHistory.size}x"
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (entry.classwork.isNotBlank()) {
                                        Text(
                                            text = "${p10Label(hi, "कक्षा कार्य (CW)", "Classwork")}: ${entry.classwork}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (entry.homework.isNotBlank()) {
                                        Text(
                                            text = "${p10Label(hi, "गृहकार्य (HW)", "Homework")}: ${entry.homework}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (entry.note.isNotBlank()) {
                                        Text(
                                            text = "${p10Label(hi, "नोट", "Note")}: ${entry.note}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }

                                    if (entry.photoBase64.isNotBlank()) {
                                        val entryBmp = remember(entry.photoBase64) {
                                            ImageUtils.decodeBase64ToBitmap(entry.photoBase64)
                                        }
                                        if (entryBmp != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { photoZoomBase64 = entry.photoBase64 }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Image(
                                                    bitmap = entryBmp.asImageBitmap(),
                                                    contentDescription = "Blackboard or homework photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Text(
                                                    text = p10Label(
                                                        hi,
                                                        "📷 ब्लैकबोर्ड/होमवर्क फोटो बड़ा देखने के लिए टैप करें",
                                                        "📷 Tap to view blackboard/homework photo"
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "लेखक: $authorDisplay",
                                                "By: $authorDisplay"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (canModify) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        selectedSubject = entry.subjectName
                                                        editingEntryId = entry.id
                                                        classworkText = entry.classwork
                                                        homeworkText = entry.homework
                                                        noteText = entry.note
                                                        photoBase64 = entry.photoBase64
                                                    },
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("diary_edit_btn_${entry.subjectName.lowercase().replace(" ", "_")}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(p10Label(hi, "बदलें", "Edit"))
                                                }
                                                TextButton(
                                                    onClick = { entryPendingDelete = entry },
                                                    colors = ButtonDefaults.textButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error
                                                    ),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("diary_delete_btn_${entry.subjectName.lowercase().replace(" ", "_")}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(p10Label(hi, "हटाएँ", "Delete"))
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = p10Label(hi, "केवल लेखक/एडमिन बदल सकते हैं", "Author/Admin only"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ClassDiaryTab.WEEKLY -> {
                    val weeklyBundle = viewModel.buildWeeklyDiaryBundle(
                        schoolDocId = activeSchoolDocId,
                        session = session,
                        className = selectedClass,
                        section = selectedSection,
                        anyDateInWeekIso = selectedDateIso
                    )

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedDateIso = Phase18DiaryAndBirthdayEngine.shiftDateIso(weeklyBundle.weekStartIso, -7)
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(hi, "◀ पिछला सप्ताह", "◀ Prev Week"))
                                    }
                                    Text(
                                        text = "${weeklyBundle.weekStartIso} – ${weeklyBundle.weekEndIso}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            selectedDateIso = Phase18DiaryAndBirthdayEngine.shiftDateIso(weeklyBundle.weekStartIso, 7)
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(hi, "अगला सप्ताह ▶", "Next Week ▶"))
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.previewWeeklyClassDiaryPdf(
                                            schoolDocId = activeSchoolDocId,
                                            session = session,
                                            className = selectedClass,
                                            section = selectedSection,
                                            anyDateInWeekIso = selectedDateIso
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("diary_weekly_tab_pdf_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "साप्ताहिक डायरी A4 PDF प्रीव्यू व प्रिंट (${weeklyBundle.totalEntries} प्रविष्टियाँ)",
                                            "Preview & Print Weekly Diary A4 PDF (${weeklyBundle.totalEntries} entries)"
                                        ),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    items(weeklyBundle.daysByDate.entries.toList(), key = { it.key }) { (dayIso, dayEntries) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (dayIso == todayIso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$dayIso (${Phase18DiaryAndBirthdayEngine.dayOfWeekShortLabel(dayIso, hi)})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = {
                                            selectedDateIso = dayIso
                                            selectedTabIndex = 0
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(hi, "इस दिन खोलें", "Open Day"))
                                    }
                                }
                                if (dayEntries.isEmpty()) {
                                    Text(
                                        text = p10Label(hi, "कोई डायरी प्रविष्टि नहीं", "No diary entries"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    dayEntries.forEach { e ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "${e.subjectName} • ${e.authorName}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (e.classwork.isNotBlank()) {
                                                Text(
                                                    text = "CW: ${e.classwork}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            if (e.homework.isNotBlank()) {
                                                Text(
                                                    text = "HW: ${e.homework}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (e.note.isNotBlank()) {
                                                Text(
                                                    text = "Note: ${e.note}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ClassDiaryTab.NOT_WRITTEN -> {
                    val missingSections = uiState.diaryNotWrittenTodayForActiveSchool(todayIso)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (missingSections.isEmpty()) PassGreenContainer else AbsentAmberContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (missingSections.isEmpty()) {
                                        p10Label(hi, "✓ आज सभी कक्षाओं की डायरी लिखी जा चुकी है!", "✓ All classes have diary entries written today ($todayIso)!")
                                    } else {
                                        p10Label(
                                            hi,
                                            "आज ($todayIso) ${missingSections.size} कक्षा/सेक्शन में डायरी नहीं लिखी गई है",
                                            "Diary not written today ($todayIso) in ${missingSections.size} class-section(s)"
                                        )
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (missingSections.isEmpty()) PassGreen else AbsentAmber
                                )
                                Text(
                                    text = p10Label(
                                        hi,
                                        "केवल स्कूल मालिक और एडमिन को यह सूची दिखाई देती है। किसी कक्षा पर टैप करके तुरंत डायरी खोलें।",
                                        "Visible to school Owner and Admins. Tap any class below to open its diary for today."
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    items(missingSections, key = { "${it.className}_${it.section}" }) { missing ->
                        val ctName = missing.classTeacherName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diary_missing_card_${missing.className}_${missing.section}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${missing.className}-${missing.section}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = buildString {
                                            append(
                                                p10Label(
                                                    hi,
                                                    "${missing.activeStudentCount} सक्रिय छात्र",
                                                    "${missing.activeStudentCount} active students"
                                                )
                                            )
                                            if (ctName.isNotBlank()) {
                                                append(" • CT: $ctName")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        selectedClass = missing.className
                                        selectedSection = missing.section
                                        selectedDateIso = todayIso
                                        selectedTabIndex = 0
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(p10Label(hi, "डायरी लिखें", "Write Diary"))
                                }
                            }
                        }
                    }
                }

                ClassDiaryTab.HISTORY -> {
                    val historyForClass = uiState.classDiaryHistory.filter {
                        it.schoolId == activeSchoolDocId &&
                            it.className.equals(selectedClass, ignoreCase = true) &&
                            it.section.equals(selectedSection, ignoreCase = true)
                    }.sortedByDescending { it.timestampMs }

                    item {
                        Text(
                            text = p10Label(
                                hi,
                                "$selectedClass-$selectedSection का डायरी संशोधन इतिहास (${historyForClass.size})",
                                "Diary Revision & Deletion History for $selectedClass-$selectedSection (${historyForClass.size})"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (historyForClass.isEmpty()) {
                        item {
                            Text(
                                text = p10Label(
                                    hi,
                                    "इस कक्षा के लिए अभी तक कोई डायरी इतिहास रिकॉर्ड नहीं है।",
                                    "No diary history records for this class-section yet."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(historyForClass, key = { it.id }) { h ->
                            val actorDisplay = AuthorDisplayHelper.resolveAuthorName(
                                authorUid = h.actorUid,
                                storedName = h.actorName,
                                deletedUserUids = uiState.deletedUserUids
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${h.dateIso} • ${h.subjectName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = when (h.action) {
                                                "DELETED" -> FailRedContainer
                                                "EDITED" -> AbsentAmberContainer
                                                else -> PassGreenContainer
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = h.action,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when (h.action) {
                                                    "DELETED" -> FailRed
                                                    "EDITED" -> AbsentAmber
                                                    else -> PassGreen
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (h.classwork.isNotBlank()) {
                                        Text("CW: ${h.classwork}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (h.homework.isNotBlank()) {
                                        Text("HW: ${h.homework}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (h.previousClasswork.isNotBlank() || h.previousHomework.isNotBlank()) {
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "पहले: CW='${h.previousClasswork}' | HW='${h.previousHomework}'",
                                                "Previous: CW='${h.previousClasswork}' | HW='${h.previousHomework}'"
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = p10Label(hi, "द्वारा: $actorDisplay", "By: $actorDisplay"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    entryPendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = {
                Text(p10Label(hi, "डायरी प्रविष्टि हटाएँ?", "Delete Diary Entry?"))
            },
            text = {
                Text(
                    p10Label(
                        hi,
                        "क्या आप ${entry.dateIso} (${entry.className}-${entry.section}) की '${entry.subjectName}' डायरी प्रविष्टि हटाना चाहते हैं? इसका रिकॉर्ड इतिहास (History) में सुरक्षित रहेगा।",
                        "Delete '${entry.subjectName}' diary entry for ${entry.className}-${entry.section} (${entry.dateIso})? Its audit history will still be kept."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClassDiaryEntry(entry) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        entryPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("diary_confirm_delete_btn")
                ) {
                    Text(p10Label(hi, "हटाएँ", "Delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { entryPendingDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "रद्द करें", "Cancel"))
                }
            }
        )
    }

    // Photo Zoom Dialog
    photoZoomBase64?.let { b64 ->
        val fullBmp = remember(b64) { ImageUtils.decodeBase64ToBitmap(b64) }
        AlertDialog(
            onDismissRequest = { photoZoomBase64 = null },
            title = {
                Text(p10Label(hi, "ब्लैकबोर्ड / होमवर्क फोटो", "Blackboard / Homework Photo"))
            },
            text = {
                if (fullBmp != null) {
                    Image(
                        bitmap = fullBmp.asImageBitmap(),
                        contentDescription = "Zoomed blackboard photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { photoZoomBase64 = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "बंद करें", "Close"))
                }
            }
        )
    }

    // Shared PDF Preview Dialog
    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }
}

// =============================================================================
// PHASE 18: BIRTHDAYS SCREEN
// =============================================================================

private enum class BirthdaysTab(val en: String, val hi: String) {
    UPCOMING("Today & Next 7 Days", "आज और अगले 7 दिन"),
    SETTINGS("Settings & Exclusions", "सेटिंग्स और छूट")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase18BirthdaysScreen(
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hi = uiState.phase10HindiLabels

    if (!uiState.canViewBirthdays) {
        Scaffold { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "No Access",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = p10Label(hi, "जन्मदिन सूची देखने की अनुमति नहीं है", "No Access to Birthdays"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = p10Label(
                        hi,
                        "जन्मदिन देखने के लिए छात्र रजिस्टर (Student Register/Students) या शिक्षक (Teachers) मॉड्यूल की अनुमति आवश्यक है।",
                        "Birthdays are visible to anyone with access to the Student Register or Teachers module."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("birthdays_no_access_back_btn")
                ) {
                    Text(p10Label(hi, "वापस जाएँ", "Go Back"))
                }
            }
        }
        return
    }

    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val todayIso = remember { Phase6DateUtils.todayIso() }
    val bdayConfig = uiState.activeSchoolBirthdayConfig
    val summary = remember(
        uiState.allStudents,
        uiState.effectivePhase12Teachers,
        uiState.schoolBirthdayConfigs,
        uiState.birthdayWishes,
        activeSchoolDocId,
        todayIso,
        hi
    ) {
        uiState.birthdaySummaryForActiveSchool(todayIso, activeSchoolDocId)
    }

    val visibleTabs = remember(uiState.canManageBirthdaySettings) {
        if (uiState.canManageBirthdaySettings) {
            BirthdaysTab.entries
        } else {
            listOf(BirthdaysTab.UPCOMING)
        }
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val currentTab = visibleTabs.getOrElse(selectedTabIndex) { BirthdaysTab.UPCOMING }

    var cardDialogItem by remember { mutableStateOf<BirthdayOccurrenceItem?>(null) }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("birthdays_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = p10Label(hi, "वापस", "Back")
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(hi, "जन्मदिन व शुभकामनाएँ (Birthdays)", "Birthdays & Wishes"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeSchool.name} • ${p10Label(hi, "आज: ${summary.todayItems.size} | अगले 7 दिन: ${summary.upcoming7DaysItems.size}", "Today: ${summary.todayItems.size} | Next 7 Days: ${summary.upcoming7DaysItems.size}")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { viewModel.setPhase10HindiLabels(!hi) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("birthdays_lang_toggle_btn")
                        ) {
                            Text(if (hi) "EN" else "हिंदी", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (visibleTabs.size > 1) {
                        SecondaryScrollableTabRow(
                            selectedTabIndex = selectedTabIndex.coerceIn(0, visibleTabs.lastIndex),
                            edgePadding = 4.dp
                        ) {
                            visibleTabs.forEachIndexed { idx, tab ->
                                Tab(
                                    selected = selectedTabIndex == idx,
                                    onClick = { selectedTabIndex = idx },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("birthdays_tab_${tab.name.lowercase()}"),
                                    text = {
                                        Text(
                                            text = p10Label(hi, tab.hi, tab.en),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.activeSchools.size > 1) {
                item {
                    CompactDropdownSelector(
                        label = p10Label(hi, "स्कूल चुनें", "Select School"),
                        selectedValue = activeSchool.name,
                        options = uiState.activeSchools.map { it.name },
                        onSelect = { name ->
                            uiState.activeSchools.firstOrNull { it.name == name }?.let {
                                viewModel.selectSchool(it.id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when (currentTab) {
                BirthdaysTab.UPCOMING -> {
                    if (!bdayConfig.birthdayWishesEnabled) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AbsentAmberContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "इस स्कूल के लिए जन्मदिन शुभकामनाएँ बंद हैं",
                                            "Birthday wishes are turned off for this school"
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AbsentAmber
                                    )
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "यदि जन्मतिथि (DOB) डेटा अधूरा है तो स्कूल इसे बंद रख सकता है।",
                                            "Schools where DOB data is incomplete can keep birthday wishes turned off."
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (uiState.canManageBirthdaySettings) {
                                        Button(
                                            onClick = {
                                                viewModel.saveSchoolBirthdayConfig(
                                                    bdayConfig.copy(birthdayWishesEnabled = true)
                                                )
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("birthdays_enable_school_btn")
                                        ) {
                                            Text(p10Label(hi, "जन्मदिन शुभकामनाएँ चालू करें", "Turn On Birthday Wishes"))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Summary Banner
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Cake,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "आज के जन्मदिन: ${summary.todayItems.size} • अगले 7 दिन: ${summary.upcoming7DaysItems.size}",
                                                "Today's Birthdays: ${summary.todayItems.size} • Next 7 Days: ${summary.upcoming7DaysItems.size}"
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "केवल सक्रिय छात्र और स्टाफ दिखाए जाते हैं (29 फरवरी गैर-लीप वर्ष में 28 फरवरी को मनाया जाता है)।",
                                                "Active students & staff only (29 Feb birthdays celebrated on 28 Feb in non-leap years)."
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (summary.todayItems.isEmpty() && summary.upcoming7DaysItems.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "आज या अगले 7 दिनों में किसी सक्रिय छात्र या स्टाफ का जन्मदिन नहीं है।",
                                            "No birthdays today or in the next 7 days for active students or staff with recorded DOBs."
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            // Render grouped by Class (plus Staff / Teachers)
                            summary.groupedByClass.forEach { (classGroup, itemsInClass) ->
                                item(key = "header_$classGroup") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (classGroup == "Staff / Teachers") {
                                                    p10Label(hi, "स्टाफ / शिक्षक (Staff / Teachers)", "Staff / Teachers")
                                                } else {
                                                    "${p10Label(hi, "कक्षा", "Class")} $classGroup"
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${itemsInClass.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                items(itemsInClass, key = { "${it.personType}_${it.personId}" }) { item ->
                                    BirthdayPersonCard(
                                        item = item,
                                        hi = hi,
                                        canManageExclusions = uiState.canManageBirthdaySettings,
                                        onSendWhatsApp = {
                                            val url = Phase18DiaryAndBirthdayEngine.buildWhatsAppWishUrl(
                                                rawOrNormalizedMobile = item.mobile,
                                                message = item.formattedWishMessage
                                            )
                                            if (url != null) {
                                                viewModel.markBirthdayPersonWished(item, "WHATSAPP")
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                runCatching { context.startActivity(intent) }.onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        p10Label(hi, "व्हाट्सएप नहीं खुल सका", "Could not open WhatsApp link"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } else {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, item.formattedWishMessage)
                                                }
                                                viewModel.markBirthdayPersonWished(item, "SHARE")
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        sendIntent,
                                                        p10Label(hi, "शुभकामना संदेश भेजें", "Send Birthday Wishes")
                                                    )
                                                )
                                            }
                                        },
                                        onCopyMessage = {
                                            clipboard.setText(AnnotatedString(item.formattedWishMessage))
                                            Toast.makeText(
                                                context,
                                                p10Label(hi, "शुभकामना संदेश कॉपी किया गया", "Birthday wish message copied"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onOpenCardDialog = { cardDialogItem = item },
                                        onToggleWished = {
                                            if (item.isWished) {
                                                viewModel.unmarkBirthdayPersonWished(item)
                                            } else {
                                                viewModel.markBirthdayPersonWished(item, "MANUAL")
                                            }
                                        },
                                        onExclude = {
                                            if (item.personType == BirthdayPersonType.STUDENT) {
                                                val stu = uiState.sessionStudents.firstOrNull {
                                                    it.id.toString() == item.personId || it.admNo == item.admissionNo
                                                }
                                                if (stu != null) {
                                                    viewModel.toggleStudentBirthdayExclusion(stu, exclude = true)
                                                }
                                            } else {
                                                viewModel.toggleTeacherBirthdayExclusion(item.personId, exclude = true)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                BirthdaysTab.SETTINGS -> {
                    item {
                        BirthdaySettingsAndExclusionsSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            hi = hi
                        )
                    }
                }
            }
        }
    }

    // Birthday Card Image / PDF Chooser Dialog
    cardDialogItem?.let { item ->
        val cardBundle = remember(activeSchool, item, hi) {
            BirthdayCardRenderBundle(
                school = activeSchool,
                item = item,
                wishMessage = item.formattedWishMessage,
                hindiLabels = hi
            )
        }
        val previewBmp = remember(cardBundle) {
            Phase18PdfGenerator.renderBirthdayCardBitmap(cardBundle)
        }
        AlertDialog(
            onDismissRequest = { cardDialogItem = null },
            title = {
                Text(p10Label(hi, "जन्मदिन कार्ड: ${item.name}", "Birthday Card: ${item.name}"))
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Birthday Card Preview for ${item.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 300.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Text(
                        text = p10Label(
                            hi,
                            "आप इस कार्ड को इमेज (PNG) या A4 PDF के रूप में साझा या प्रिंट कर सकते हैं।",
                            "Share this birthday card as an Image (PNG) to WhatsApp or preview/print as an A4 PDF."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val target = item
                            cardDialogItem = null
                            viewModel.previewBirthdayCardPdf(target, target.formattedWishMessage)
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("birthday_card_pdf_btn")
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }
                    Button(
                        onClick = {
                            val target = item
                            cardDialogItem = null
                            viewModel.markBirthdayPersonWished(target, "CARD")
                            viewModel.shareBirthdayCardPng(context, target, target.formattedWishMessage)
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("birthday_card_share_png_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(hi, "इमेज शेयर करें", "Share Image"))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { cardDialogItem = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "बंद करें", "Close"))
                }
            }
        )
    }

    // Shared PDF Preview Dialog
    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BirthdayPersonCard(
    item: BirthdayOccurrenceItem,
    hi: Boolean,
    canManageExclusions: Boolean,
    onSendWhatsApp: () -> Unit,
    onCopyMessage: () -> Unit,
    onOpenCardDialog: () -> Unit,
    onToggleWished: () -> Unit,
    onExclude: () -> Unit
) {
    val isToday = item.daysUntil == 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("birthday_item_card_${item.personId}"),
        border = BorderStroke(
            width = if (isToday) 1.5.dp else 1.dp,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isToday) {
                                    p10Label(hi, "🎂 आज!", "🎂 TODAY!")
                                } else {
                                    p10Label(hi, "${item.daysUntil} दिन बाद (${item.celebrationDateIso})", "In ${item.daysUntil}d (${item.celebrationDateIso})")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = buildString {
                            append(item.classGroupLabel)
                            if (item.admissionNo.isNotBlank()) append(" • Adm: ${item.admissionNo}")
                            if (item.guardianName.isNotBlank()) append(" • C/o ${item.guardianName}")
                            append(" • DOB: ${item.dobIso}")
                            val age = item.turningAge ?: 0
                            if (age > 0) append(" ($age yrs)")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.isFeb29CelebratedOnFeb28) {
                        Text(
                            text = p10Label(
                                hi,
                                "★ 29 फरवरी जन्मतिथि (गैर-लीप वर्ष में 28 फरवरी को मनाया गया)",
                                "★ 29 Feb Birthday (celebrated on 28 Feb in non-leap year)"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Wished status badge / toggle button
                FilterChip(
                    selected = item.isWished,
                    onClick = onToggleWished,
                    label = {
                        Text(
                            text = if (item.isWished) {
                                p10Label(hi, "✓ बधाई दी गई", "✓ Wished")
                            } else {
                                p10Label(hi, "बधाई चिह्नित करें", "Mark Wished")
                            },
                            maxLines = 1
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("birthday_wished_btn_${item.personId}")
                )
            }

            val wishRec = item.wishedRecord
            if (item.isWished && wishRec != null) {
                Surface(
                    color = PassGreenContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = p10Label(
                            hi,
                            "✓ ${wishRec.wishedByName} द्वारा शुभकामना भेजी गई (${wishRec.channel})",
                            "✓ Wished by ${wishRec.wishedByName} (${wishRec.channel})"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = PassGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Action Buttons (FlowRow so 320-360dp phones never clip text)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onSendWhatsApp,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("birthday_whatsapp_btn_${item.personId}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (item.normalizedMobile.isNotBlank()) {
                            p10Label(hi, "व्हाट्सएप बधाई (${item.normalizedMobile})", "Send Wishes on WhatsApp")
                        } else {
                            p10Label(hi, "बधाई संदेश भेजें", "Share Wishes")
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onCopyMessage,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("birthday_copy_btn_${item.personId}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = p10Label(hi, "संदेश कॉपी करें", "Copy Message"),
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onOpenCardDialog,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("birthday_card_btn_${item.personId}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cake,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = p10Label(hi, "जन्मदिन कार्ड", "Birthday Card"),
                        maxLines = 1
                    )
                }

                if (canManageExclusions) {
                    TextButton(
                        onClick = onExclude,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("birthday_exclude_btn_${item.personId}")
                    ) {
                        Text(p10Label(hi, "सूची से हटाएँ", "Exclude"))
                    }
                }
            }
        }
    }
}

@Composable
private fun BirthdaySettingsAndExclusionsSection(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    hi: Boolean
) {
    val context = LocalContext.current
    val config = uiState.activeSchoolBirthdayConfig
    var enabled by remember(config.schoolId, config.birthdayWishesEnabled) {
        mutableStateOf(config.birthdayWishesEnabled)
    }
    var templateEn by remember(config.schoolId, config.wishTemplateEn) {
        mutableStateOf(config.wishTemplateEn)
    }
    var templateHi by remember(config.schoolId, config.wishTemplateHi) {
        mutableStateOf(config.wishTemplateHi)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p10Label(hi, "इस स्कूल के लिए जन्मदिन शुभकामनाएँ", "Birthday Wishes for This School"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = p10Label(
                                hi,
                                "यदि जन्मतिथि (DOB) डेटा तैयार नहीं है तो इसे बंद रखें।",
                                "Turn off for schools where DOB data is not ready."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            viewModel.saveSchoolBirthdayConfig(config.copy(birthdayWishesEnabled = it))
                        },
                        modifier = Modifier.testTag("birthday_settings_enable_switch")
                    )
                }

                HorizontalDivider()

                Text(
                    text = p10Label(
                        hi,
                        "शुभकामना संदेश टेम्पलेट ({name}, {school}, {class}, {age} का उपयोग करें):",
                        "Editable Wish Template (use {name}, {school}, {class}, {age}):"
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = templateEn,
                    onValueChange = { templateEn = it },
                    label = { Text("English Wish Template") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("birthday_template_en_input")
                )

                OutlinedTextField(
                    value = templateHi,
                    onValueChange = { templateHi = it },
                    label = { Text("हिंदी शुभकामना टेम्पलेट (Hindi Template)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("birthday_template_hi_input")
                )

                val samplePreview = remember(templateEn, templateHi, hi, uiState.activeSchool.name) {
                    Phase18DiaryAndBirthdayEngine.formatBirthdayWishMessage(
                        template = if (hi) templateHi else templateEn,
                        personName = "Aarav Sharma",
                        schoolName = uiState.activeSchool.name,
                        className = "5th-A",
                        turningAge = 10,
                        isHindi = hi
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${p10Label(hi, "उदाहरण (Preview)", "Live Preview")}: $samplePreview",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Button(
                    onClick = {
                        viewModel.saveSchoolBirthdayConfig(
                            config.copy(
                                birthdayWishesEnabled = enabled,
                                templateEn = templateEn.ifBlank { Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_EN },
                                templateHi = templateHi.ifBlank { Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_HI }
                            )
                        ) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("birthday_save_settings_btn")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(p10Label(hi, "टेम्पलेट व सेटिंग्स सेव करें", "Save Birthday Settings"), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Staff DOBs & Exclusions Management Card
        val schoolTeachers = remember(uiState.effectivePhase12Teachers, uiState.activeSchoolDocId) {
            uiState.effectivePhase12Teachers.filter {
                it.active && (it.schoolIds.isEmpty() || uiState.activeSchoolDocId in it.schoolIds)
            }
        }
        if (schoolTeachers.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = p10Label(
                            hi,
                            "शिक्षक / स्टाफ जन्मतिथि (DOB) व छूट",
                            "Staff / Teachers DOB & Birthday Inclusion"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    schoolTeachers.forEach { t ->
                        val currentDob = t.dob.ifBlank { config.staffDobByTeacherId[t.id].orEmpty() }
                        var dobDraft by remember(t.id, currentDob) { mutableStateOf(currentDob) }
                        val isExcluded = t.id in config.excludedTeacherIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = dobDraft,
                                    onValueChange = { dobDraft = it },
                                    label = { Text("DOB (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.setTeacherBirthdayDob(t.id, dobDraft)
                                    Toast.makeText(context, "Saved DOB for ${t.name}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(p10Label(hi, "सेव", "Save"))
                            }
                            FilterChip(
                                selected = !isExcluded,
                                onClick = {
                                    viewModel.toggleTeacherBirthdayExclusion(t.id, exclude = !isExcluded)
                                },
                                label = {
                                    Text(if (isExcluded) p10Label(hi, "छूट", "Excluded") else p10Label(hi, "शामिल", "Included"))
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }
        }

        // Excluded Students List (with 1-tap Restore)
        val excludedStudents = remember(uiState.sessionStudents, config.excludedStudentKeys) {
            uiState.sessionStudents.filter { st ->
                Phase18DiaryAndBirthdayEngine.isStudentExcluded(st, config)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = p10Label(
                        hi,
                        "जन्मदिन सूची से हटाए गए छात्र (${excludedStudents.size})",
                        "Excluded Students (${excludedStudents.size})"
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (excludedStudents.isEmpty()) {
                    Text(
                        text = p10Label(
                            hi,
                            "कोई छात्र हटाया नहीं गया है। किसी छात्र की जन्मतिथि अनिश्चित होने पर जन्मदिन कार्ड पर 'सूची से हटाएँ' दबाएँ।",
                            "No students are excluded. You can exclude any student with missing or unsure DOB from the birthday card or below."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    excludedStudents.forEach { st ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${st.name} (${st.className}-${st.section} • Adm: ${st.admNo})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    viewModel.toggleStudentBirthdayExclusion(st, exclude = false)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(p10Label(hi, "वापस शामिल करें", "Re-include"))
                            }
                        }
                    }
                }
            }
        }
    }
}
