package com.example.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.data.StudentEntity
import com.example.domain.BirthdayOccurrenceItem
import com.example.domain.ClassDiaryEntryRecord
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.Phase18AccessEvaluator
import com.example.domain.Phase18DiaryAndBirthdayEngine
import com.example.domain.SchoolBirthdayConfig
import com.example.domain.WeeklyClassDiaryBundle
import com.example.util.BirthdayCardRenderBundle
import com.example.util.PdfExportHelper
import com.example.util.Phase18PdfGenerator
import com.example.util.WeeklyDiaryPdfRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =============================================================================
// PHASE 18 VIEWMODEL EXTENSIONS:
// CLASS DIARY (Author-Only Edit/Delete, Admin Override, History Kept, Weekly PDF)
// & BIRTHDAYS (Today & 7-Day Window, 29 Feb, WhatsApp Template, Wished Mark, Card)
// =============================================================================

private fun SchoolUiState.effectiveActorProfile(fallbackUid: String, schoolDocId: String): MemberAccessProfile {
    return accessProfile ?: MemberAccessProfile(
        uid = fallbackUid.ifBlank { "local_owner" },
        role = OrgRole.OWNER,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = listOf(schoolDocId),
        permissions = MemberPermissions.presetFor(OrgRole.OWNER)
    )
}

fun SchoolViewModel.availableDiarySubjectsForClassSection(
    schoolDocId: String,
    className: String,
    section: String
): List<String> {
    val state = uiState.value
    val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
    val phase3Subjects = state.subjectsForClass(className, schLongId)
    return Phase18DiaryAndBirthdayEngine.availableSubjectsForDiary(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolId = schoolDocId,
        className = className,
        section = section,
        classSubjects = phase3Subjects,
        classAssignments = state.classAssignments
    )
}

fun SchoolViewModel.canWriteDiaryForSubject(
    schoolDocId: String,
    className: String,
    section: String,
    subjectName: String
): Boolean {
    val state = uiState.value
    if (state.activeOrg == null) return true
    return Phase18AccessEvaluator.canWriteDiaryEntryFor(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolId = schoolDocId,
        className = className,
        section = section,
        subjectName = subjectName
    )
}

fun SchoolViewModel.canEditOrDeleteDiaryEntry(entry: ClassDiaryEntryRecord): Boolean {
    val state = uiState.value
    if (state.activeOrg == null) return true
    return Phase18AccessEvaluator.canEditOrDeleteDiaryEntry(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        entry = entry
    )
}

fun SchoolViewModel.saveClassDiaryEntry(
    schoolDocId: String,
    session: String,
    dateIso: String,
    className: String,
    section: String,
    subjectName: String,
    classwork: String,
    homework: String,
    note: String,
    photoBase64: String,
    existingEntryId: String? = null,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (schoolDocId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Teacher" } ?: "Teacher"
    val actorProfile = state.effectiveActorProfile(actorUid, schoolDocId)

    val targetId = existingEntryId?.takeIf { it.isNotBlank() }
        ?: ClassDiaryEntryRecord.buildId(
            schoolId = schoolDocId,
            session = session,
            dateIso = dateIso,
            className = className,
            section = section,
            subjectName = subjectName
        )

    val existing = state.classDiaryEntries.firstOrNull { it.id == targetId }

    phase18Repo.saveDiaryEntry(
        orgId = orgId,
        schoolId = schoolDocId,
        session = session,
        dateIso = dateIso,
        className = className,
        section = section,
        subjectName = subjectName,
        classwork = classwork,
        homework = homework,
        note = note,
        photoBase64 = photoBase64,
        existingEntry = existing,
        actor = actorProfile,
        actorName = actorName,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess { entry ->
        val feedback = if (existing == null) {
            "Saved diary entry for ${entry.subjectName} (${entry.className}-${entry.section})."
        } else {
            "Updated diary entry for ${entry.subjectName} (${entry.className}-${entry.section})."
        }
        emitMessage(feedback)
        onResult(true, feedback)
    }.onFailure { err ->
        val msg = err.message ?: "Could not save diary entry."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.deleteClassDiaryEntry(
    entry: ClassDiaryEntryRecord,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Teacher" } ?: "Teacher"
    val actorProfile = state.effectiveActorProfile(actorUid, entry.schoolId)

    phase18Repo.deleteDiaryEntry(
        orgId = orgId,
        existingEntry = entry,
        actor = actorProfile,
        actorName = actorName,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess {
        val msg = "Deleted ${entry.subjectName} diary entry (history preserved)."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "You can only delete diary entries written by you."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.buildWeeklyDiaryBundle(
    schoolDocId: String,
    session: String,
    className: String,
    section: String,
    anyDateInWeekIso: String
): WeeklyClassDiaryBundle {
    val state = uiState.value
    val schName = state.schoolRecordsByDocId[schoolDocId]?.name ?: state.activeSchool.name
    val subjOrder = availableDiarySubjectsForClassSection(schoolDocId, className, section)
    return Phase18DiaryAndBirthdayEngine.buildWeeklyDiaryBundle(
        schoolId = schoolDocId,
        schoolName = schName,
        session = session,
        className = className,
        section = section,
        anchorDateIso = anyDateInWeekIso,
        allDiaryEntries = state.classDiaryEntries,
        subjectOrder = subjOrder
    )
}

fun SchoolViewModel.previewWeeklyClassDiaryPdf(
    schoolDocId: String,
    session: String,
    className: String,
    section: String,
    anyDateInWeekIso: String
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val bundle = buildWeeklyDiaryBundle(schoolDocId, session, className, section, anyDateInWeekIso)
    val actorName = state.currentMember?.name.orEmpty()
    val request = WeeklyDiaryPdfRequest(
        school = school,
        weeklyBundle = bundle,
        generatedByName = actorName
    )
    val title = "Weekly Diary • $className-${section.trim().uppercase().ifBlank { "A" }} (${bundle.weekStartIso} to ${bundle.weekEndIso})"
    _timetablePdfState.value = TimetablePdfPreviewState(
        isGenerating = true,
        title = title,
        statusLabel = "Generating A4 Portrait Weekly Diary PDF…",
        isLandscape = false
    )
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = Phase18PdfGenerator.generateWeeklyDiaryPdf(
                    context = appContext,
                    request = request
                )
                val rendered = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                val preview = rendered.ifEmpty {
                    Phase18PdfGenerator.renderWeeklyDiaryPreviewBitmaps(request)
                }
                val totalPages = PdfExportHelper.countPdfPages(file).takeIf { it > 0 } ?: preview.size.coerceAtLeast(1)
                Triple(file, totalPages, preview)
            }
        }.onSuccess { (file, totalPages, preview) ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                statusLabel = "Ready ($totalPages page(s), A4 Portrait)",
                generatedFile = file,
                totalPages = totalPages,
                previewPages = preview
            )
        }.onFailure { err ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                error = err.message ?: "Failed to generate Weekly Diary PDF"
            )
        }
    }
}

// =============================================================================
// BIRTHDAYS ACTIONS
// =============================================================================

fun SchoolViewModel.saveSchoolBirthdayConfig(
    config: SchoolBirthdayConfig,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (config.schoolId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }
    val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
    val actorProfile = state.effectiveActorProfile(actorUid, config.schoolId)

    phase18Repo.saveSchoolBirthdayConfig(
        orgId = orgId,
        config = config,
        actor = actorProfile,
        actorName = actorName
    ).onSuccess {
        val msg = "Birthday settings saved."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not save birthday settings."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.toggleStudentBirthdayExclusion(
    student: StudentEntity,
    exclude: Boolean,
    schoolDocId: String = uiState.value.activeSchoolDocId
) {
    val state = uiState.value
    val current = state.birthdayConfigForSchool(schoolDocId)
    val key = Phase18DiaryAndBirthdayEngine.studentExclusionKey(student)
    val altKey = if (student.id > 0L) "id:${student.id}" else key
    val updatedSet = current.excludedStudentKeys.toMutableSet()
    if (exclude) {
        updatedSet.add(key)
        updatedSet.add(altKey)
    } else {
        updatedSet.remove(key)
        updatedSet.remove(altKey)
        if (student.admNo.isNotBlank()) {
            updatedSet.remove("adm:${student.admNo.trim().uppercase()}")
            updatedSet.remove(student.admNo.trim())
        }
    }
    saveSchoolBirthdayConfig(current.copy(excludedStudentKeys = updatedSet.toList().sorted()))
}

fun SchoolViewModel.toggleTeacherBirthdayExclusion(
    teacherId: String,
    exclude: Boolean,
    schoolDocId: String = uiState.value.activeSchoolDocId
) {
    val state = uiState.value
    val current = state.birthdayConfigForSchool(schoolDocId)
    val updatedSet = current.excludedTeacherIds.toMutableSet()
    if (exclude) {
        updatedSet.add(teacherId)
    } else {
        updatedSet.remove(teacherId)
    }
    saveSchoolBirthdayConfig(current.copy(excludedTeacherIds = updatedSet.toList().sorted()))
}

fun SchoolViewModel.setTeacherBirthdayDob(
    teacherId: String,
    dobIso: String,
    schoolDocId: String = uiState.value.activeSchoolDocId
) {
    val state = uiState.value
    val current = state.birthdayConfigForSchool(schoolDocId)
    val updatedMap = current.staffDobByTeacherId.toMutableMap()
    if (dobIso.isBlank()) {
        updatedMap.remove(teacherId)
    } else {
        updatedMap[teacherId] = dobIso.trim()
    }
    saveSchoolBirthdayConfig(current.copy(staffDobByTeacherId = updatedMap))
}

fun SchoolViewModel.markBirthdayPersonWished(
    item: BirthdayOccurrenceItem,
    channel: String,
    schoolDocId: String = uiState.value.activeSchoolDocId,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (schoolDocId.isBlank()) {
        onResult(false, "No school selected")
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Staff" } ?: "Staff"
    val actorProfile = state.effectiveActorProfile(actorUid, schoolDocId)

    phase18Repo.markPersonWished(
        orgId = orgId,
        schoolId = schoolDocId,
        item = item,
        channel = channel,
        actor = actorProfile,
        actorName = actorName,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess {
        val msg = "Marked ${item.name} as wished."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not mark as wished."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.unmarkBirthdayPersonWished(
    item: BirthdayOccurrenceItem,
    schoolDocId: String = uiState.value.activeSchoolDocId
) {
    val state = uiState.value
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorProfile = state.effectiveActorProfile(actorUid, schoolDocId)
    val wishId = item.wishRecord?.id ?: Phase18DiaryAndBirthdayEngine.makeBirthdayWishDocId(
        schoolId = schoolDocId,
        personType = item.personType,
        personId = item.personId,
        celebrationDateIso = item.celebrationDateIso
    )
    phase18Repo.unmarkPersonWished(
        orgId = orgId,
        schoolId = schoolDocId,
        wishId = wishId,
        actor = actorProfile,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess {
        emitMessage("Cleared wished mark for ${item.name}.")
    }.onFailure { err ->
        emitMessage(err.message ?: "Could not clear wished mark.")
    }
}

fun SchoolViewModel.previewBirthdayCardPdf(
    item: BirthdayOccurrenceItem,
    wishMessage: String,
    schoolDocId: String = uiState.value.activeSchoolDocId
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val bundle = BirthdayCardRenderBundle(
        school = school,
        item = item,
        wishMessage = wishMessage,
        hindiLabels = state.phase10HindiLabels
    )
    val title = "Birthday Card • ${item.name} (${item.classGroupLabel})"
    _timetablePdfState.value = TimetablePdfPreviewState(
        isGenerating = true,
        title = title,
        statusLabel = "Generating Birthday Card A4 PDF…",
        isLandscape = false
    )
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = Phase18PdfGenerator.generateBirthdayCardPdf(appContext, bundle)
                val rendered = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 2)
                val preview = rendered.ifEmpty {
                    listOf(Phase18PdfGenerator.renderBirthdayCardBitmap(bundle))
                }
                val totalPages = PdfExportHelper.countPdfPages(file).takeIf { it > 0 } ?: 1
                Triple(file, totalPages, preview)
            }
        }.onSuccess { (file, totalPages, preview) ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                statusLabel = "Ready ($totalPages page, A4 Portrait)",
                generatedFile = file,
                totalPages = totalPages,
                previewPages = preview
            )
        }.onFailure { err ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                error = err.message ?: "Failed to generate Birthday Card PDF"
            )
        }
    }
}

fun SchoolViewModel.shareBirthdayCardPng(
    context: Context,
    item: BirthdayOccurrenceItem,
    wishMessage: String,
    schoolDocId: String = uiState.value.activeSchoolDocId
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val bundle = BirthdayCardRenderBundle(
        school = school,
        item = item,
        wishMessage = wishMessage,
        hindiLabels = state.phase10HindiLabels
    )
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                Phase18PdfGenerator.generateBirthdayCardPng(appContext, bundle)
            }
        }.onSuccess { pngFile ->
            PdfExportHelper.shareFile(
                context = context,
                file = pngFile,
                mimeType = "image/png",
                chooserTitle = "Share Birthday Card for ${item.name}"
            )
        }.onFailure { err ->
            emitMessage(err.message ?: "Could not generate Birthday Card image.")
        }
    }
}
