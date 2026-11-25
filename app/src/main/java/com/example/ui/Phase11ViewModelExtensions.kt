package com.example.ui

import androidx.lifecycle.viewModelScope
import com.example.data.StudentEntity
import com.example.domain.ExamCategoryType
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamRoomRecord
import com.example.domain.ExamScheduleEngine
import com.example.domain.ExamScheduleEntry
import com.example.domain.ExamScheduleRecord
import com.example.domain.ExamSeatingRecord
import com.example.domain.FeeCollectionEngine
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.Phase11AccessEvaluator
import com.example.domain.Phase6DateUtils
import com.example.domain.SeatingAllocationMode
import com.example.domain.StockEngine
import com.example.domain.StockTransactionRecord
import com.example.domain.StockTransactionType
import com.example.domain.TeacherProfile
import com.example.domain.TransportEngine
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportRouteSummary
import com.example.domain.TransportStop
import com.example.util.AdmitCardStudentItem
import com.example.util.AdmitCardsPdfBundle
import com.example.util.DutyListPdfBundle
import com.example.util.ExamTimetablePdfBundle
import com.example.util.Phase11PdfGenerator
import com.example.util.RoomSeatingPdfBundle
import com.example.util.StockReportPdfBundle
import com.example.util.TransportRoutePdfBundle
import com.example.util.friendlyFirestoreErrorMessage
import kotlinx.coroutines.launch
import java.util.UUID

// =============================================================================
// PHASE 11 VIEWMODEL EXTENSIONS: EXAMS & ADMIT CARDS, STOCK, TRANSPORT ROUTES
// =============================================================================

// =============================================================================
// 1. EXAM SCHEDULE, ROOMS, SEATING, INVIGILATOR DUTIES & ADMIT CARDS
// =============================================================================

fun SchoolViewModel.createOrUpdateExamSchedule(
    existing: ExamScheduleRecord?,
    examType: ExamCategoryType,
    customTitle: String,
    session: String = uiState.value.filter.session,
    withholdAboveDuesRs: Int = existing?.withholdAdmitCardAboveDuesRs ?: 0,
    instructions: String = existing?.admitCardInstructions ?: ExamScheduleRecord.DEFAULT_ADMIT_INSTRUCTIONS,
    onResult: (Boolean, String?, String?) -> Unit = { _, _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.", null)
        return
    }
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, schoolDocId) && !state.canEditExams) {
        onResult(false, "View-only access: Exams Edit permission required.", null)
        return
    }
    val title = customTitle.trim().ifBlank { examType.englishLabel }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val record = (existing ?: ExamScheduleRecord(
        id = "",
        schoolId = schoolDocId,
        session = session.trim().ifBlank { state.filter.session },
        examType = examType,
        title = title
    )).copy(
        examType = examType,
        title = title,
        session = session.trim().ifBlank { state.filter.session },
        withholdAdmitCardAboveDuesRs = withholdAboveDuesRs.coerceAtLeast(0),
        admitCardInstructions = instructions.trim().ifBlank { ExamScheduleRecord.DEFAULT_ADMIT_INSTRUCTIONS }
    )
    viewModelScope.launch {
        phase11Repo.saveExamSchedule(orgId, record, actorName)
            .onSuccess { docId ->
                emitMessage("Saved exam '${record.title}'")
                onResult(true, null, docId)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save exam schedule."), null)
            }
    }
}

fun SchoolViewModel.addOrUpdateExamPaperEntry(
    exam: ExamScheduleRecord,
    className: String,
    subjectName: String,
    dateIso: String,
    startTime: String,
    endTime: String,
    maxMarks: Int = 100,
    editingEntryId: String? = null,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, exam.schoolId) && !state.canEditExams) {
        onResult(false, "Exams Edit permission required.")
        return
    }
    val entry = ExamScheduleEntry(
        entryId = editingEntryId?.takeIf { it.isNotBlank() } ?: "paper_${UUID.randomUUID().toString().replace("-", "").take(8)}",
        className = className.trim(),
        subjectName = subjectName.trim(),
        dateIso = dateIso.trim(),
        startTime = startTime.trim(),
        endTime = endTime.trim(),
        maxMarks = maxMarks.coerceIn(1, 1000)
    )
    val err = ExamScheduleEngine.validateEntry(entry, exam.session, exam.entries)
    if (err != null) {
        onResult(false, err)
        return
    }
    val updatedEntries = (exam.entries.filterNot { it.entryId == entry.entryId } + entry)
        .sortedWith(compareBy({ it.className }, { it.dateIso }, { it.startTime }))
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    viewModelScope.launch {
        phase11Repo.saveExamSchedule(orgId, exam.copy(entries = updatedEntries), actorName)
            .onSuccess {
                emitMessage("Added ${entry.subjectName} (${entry.className}) on ${entry.dateIso}")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to update exam schedule."))
            }
    }
}

fun SchoolViewModel.removeExamPaperEntry(exam: ExamScheduleRecord, entryId: String) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, exam.schoolId) && !state.canEditExams) {
        emitMessage("Exams Edit permission required.")
        return
    }
    val updated = exam.copy(entries = exam.entries.filterNot { it.entryId == entryId })
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    viewModelScope.launch {
        phase11Repo.saveExamSchedule(orgId, updated, actorName)
            .onSuccess { emitMessage("Removed exam paper") }
            .onFailure { e -> emitMessage(friendlyFirestoreErrorMessage(e, "Unable to remove paper.")) }
    }
}

fun SchoolViewModel.deleteExamScheduleRecord(exam: ExamScheduleRecord) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, exam.schoolId) && !state.canEditExams) {
        emitMessage("Exams Edit permission required.")
        return
    }
    viewModelScope.launch {
        phase11Repo.deleteExamSchedule(orgId, exam.schoolId, exam.id, exam.title)
            .onSuccess { emitMessage("Deleted exam '${exam.title}'") }
            .onFailure { e -> emitMessage(friendlyFirestoreErrorMessage(e, "Unable to delete exam.")) }
    }
}

fun SchoolViewModel.toggleAdmitCardDuesOverride(
    exam: ExamScheduleRecord,
    studentDocId: String
) {
    val state = uiState.value
    if (!state.isAdmin) {
        emitMessage("Only an Admin or Owner can override fee dues withholding.")
        return
    }
    val current = exam.admitCardOverrideStudentIds.toMutableSet()
    val added = if (studentDocId in current) {
        current.remove(studentDocId)
        false
    } else {
        current.add(studentDocId)
        true
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Admin"
    viewModelScope.launch {
        phase11Repo.saveExamSchedule(orgId, exam.copy(admitCardOverrideStudentIds = current.toList()), actorName)
            .onSuccess {
                emitMessage(if (added) "Admin override enabled for admit card" else "Admin override removed")
            }
            .onFailure { e ->
                emitMessage(friendlyFirestoreErrorMessage(e, "Unable to update override."))
            }
    }
}

fun SchoolViewModel.updateExamDuesWithholdThreshold(
    exam: ExamScheduleRecord,
    thresholdRs: Int
) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, exam.schoolId) && !state.canEditExams) {
        emitMessage("Exams Edit permission required.")
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    viewModelScope.launch {
        phase11Repo.saveExamSchedule(orgId, exam.copy(withholdAdmitCardAboveDuesRs = thresholdRs.coerceAtLeast(0)), actorName)
            .onSuccess {
                emitMessage(if (thresholdRs > 0) "Withhold warning set for dues > ₹$thresholdRs" else "Admit card dues warning disabled")
            }
            .onFailure { e ->
                emitMessage(friendlyFirestoreErrorMessage(e, "Unable to save threshold."))
            }
    }
}

fun SchoolViewModel.saveExamRoomRecord(
    existing: ExamRoomRecord?,
    roomName: String,
    capacity: Int,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, schoolDocId) && !state.canEditExams) {
        onResult(false, "Exams Edit permission required.")
        return
    }
    if (roomName.trim().isEmpty()) {
        onResult(false, "Please enter room name.")
        return
    }
    if (capacity <= 0) {
        onResult(false, "Room capacity must be at least 1.")
        return
    }
    val record = ExamRoomRecord(
        id = existing?.id.orEmpty(),
        schoolId = schoolDocId,
        roomName = roomName.trim(),
        capacity = capacity.coerceIn(1, 500),
        active = true
    )
    viewModelScope.launch {
        phase11Repo.saveExamRoom(orgId, record)
            .onSuccess {
                emitMessage("Saved room '${record.roomName}' (Cap ${record.capacity})")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save room."))
            }
    }
}

fun SchoolViewModel.deleteExamRoomRecord(room: ExamRoomRecord) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, room.schoolId) && !state.canEditExams) {
        emitMessage("Exams Edit permission required.")
        return
    }
    viewModelScope.launch {
        phase11Repo.deleteExamRoom(orgId, room.id)
            .onSuccess { emitMessage("Deleted room '${room.roomName}'") }
            .onFailure { e -> emitMessage(friendlyFirestoreErrorMessage(e, "Unable to delete room.")) }
    }
}

fun SchoolViewModel.autoAllocateExamSeating(
    exam: ExamScheduleRecord,
    selectedClasses: List<String>,
    mode: SeatingAllocationMode,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, exam.schoolId) && !state.canEditExams) {
        onResult(false, "Exams Edit permission required.")
        return
    }
    val rooms = state.examRooms.filter { it.schoolId == exam.schoolId && it.active }
    if (rooms.isEmpty()) {
        onResult(false, "Please add at least one exam room first.")
        return
    }
    val schoolLongId = state.schoolRecordsByDocId[exam.schoolId]?.stableId ?: state.activeSchool.id
    val classSet = selectedClasses.map { it.trim().lowercase() }.toSet()
    val candidates = state.allStudents
        .filter { st ->
            st.schoolId == schoolLongId &&
                st.isActiveStatus &&
                (classSet.isEmpty() || st.className.trim().lowercase() in classSet)
        }
        .map { st -> state.studentDocIdFor(st.id).ifBlank { st.admNo } to st }

    if (candidates.isEmpty()) {
        onResult(false, "No active students found for the selected classes.")
        return
    }
    val plan = ExamScheduleEngine.allocateSeating(
        schoolId = exam.schoolId,
        session = exam.session,
        examId = exam.id,
        studentsWithDocIds = candidates,
        rooms = rooms,
        mode = mode
    )
    viewModelScope.launch {
        phase11Repo.saveExamSeating(orgId, plan)
            .onSuccess {
                val msg = if (plan.unallocatedStudentIds.isEmpty()) {
                    "Allocated ${plan.allocations.size} students across ${plan.roomIds.size} rooms (${mode.englishLabel})"
                } else {
                    "Allocated ${plan.allocations.size} students (${plan.unallocatedStudentIds.size} unallocated — add more room seats)"
                }
                emitMessage(msg)
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save seating plan."))
            }
    }
}

fun SchoolViewModel.moveStudentExamSeatRoom(
    seating: ExamSeatingRecord,
    studentDocId: String,
    targetRoom: ExamRoomRecord
) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, seating.schoolId) && !state.canEditExams) {
        emitMessage("Exams Edit permission required.")
        return
    }
    val updated = ExamScheduleEngine.moveStudentToRoom(seating, studentDocId, targetRoom)
    viewModelScope.launch {
        phase11Repo.saveExamSeating(orgId, updated)
            .onSuccess { emitMessage("Moved student to ${targetRoom.roomName}") }
            .onFailure { e -> emitMessage(friendlyFirestoreErrorMessage(e, "Unable to move student seat.")) }
    }
}

fun SchoolViewModel.autoAssignExamInvigilators(
    exam: ExamScheduleRecord,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, exam.schoolId) && !state.canEditExams) {
        onResult(false, "Exams Edit permission required.")
        return
    }
    if (exam.entries.isEmpty()) {
        onResult(false, "Add exam schedule papers before assigning invigilators.")
        return
    }
    val seating = state.examSeatings.firstOrNull { it.examId == exam.id }
    val allSchoolRooms = state.examRooms.filter { it.schoolId == exam.schoolId && it.active }
    val roomsUsed = if (seating != null && seating.allocations.isNotEmpty()) {
        val usedIds = seating.allocations.map { it.roomId }.toSet()
        allSchoolRooms.filter { it.id in usedIds }.ifEmpty { allSchoolRooms }
    } else {
        allSchoolRooms
    }
    if (roomsUsed.isEmpty()) {
        onResult(false, "Please add at least one exam room first.")
        return
    }
    val teachers = state.effectivePhase11Teachers
    if (teachers.none { it.active }) {
        onResult(false, "No active teachers found in the organisation.")
        return
    }
    val existingForExam = state.examDuties.filter { it.examId == exam.id }
    val assignment = ExamScheduleEngine.autoAssignInvigilators(
        exam = exam,
        rooms = roomsUsed,
        teachers = teachers,
        leaves = state.effectivePhase11TeacherLeaves,
        existingManualDuties = existingForExam
    )
    viewModelScope.launch {
        phase11Repo.replaceExamDuties(orgId, exam.schoolId, exam.id, existingForExam, assignment.duties)
            .onSuccess {
                val msg = if (assignment.unfilledSlots.isEmpty()) {
                    "Assigned ${assignment.duties.size} invigilator duties (avoiding approved leaves)"
                } else {
                    "Assigned ${assignment.duties.size} duties (${assignment.unfilledSlots.size} slots could not be filled)"
                }
                emitMessage(msg)
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save invigilator duties."))
            }
    }
}

fun SchoolViewModel.changeInvigilatorTeacherManually(
    duty: ExamInvigilatorDutyRecord,
    newTeacher: TeacherProfile
) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditExams(state.accessProfile, duty.schoolId) && !state.canEditExams) {
        emitMessage("Exams Edit permission required.")
        return
    }
    val onLeave = ExamScheduleEngine.isTeacherOnApprovedLeave(
        newTeacher.id,
        duty.dateIso,
        state.effectivePhase11TeacherLeaves
    )
    val updated = duty.copy(
        teacherId = newTeacher.id,
        teacherUid = newTeacher.linkedMemberUid,
        teacherName = newTeacher.name,
        assignedManually = true
    )
    viewModelScope.launch {
        phase11Repo.saveSingleExamDuty(orgId, updated)
            .onSuccess {
                emitMessage(
                    if (onLeave) "Assigned ${newTeacher.name} (Warning: on approved leave on ${duty.dateIso})"
                    else "Updated invigilator to ${newTeacher.name}"
                )
            }
            .onFailure { e ->
                emitMessage(friendlyFirestoreErrorMessage(e, "Unable to update duty."))
            }
    }
}

fun SchoolViewModel.computeStudentFeeDuesMapForSchool(
    schoolDocId: String,
    session: String
): Map<String, Int> {
    val state = uiState.value
    val structure = state.feeStructureForPhase11(schoolDocId, session)
    val schoolLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
    val overridesByStudent = state.effectivePhase11FeeOverrides
        .filter { it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true) }
        .associateBy { it.studentId }
    val receipts = state.effectivePhase11FeeReceipts
        .filter { it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true) }
    val today = Phase6DateUtils.todayIso()
    val result = mutableMapOf<String, Int>()
    state.allStudents
        .filter { it.schoolId == schoolLongId && it.isActiveStatus }
        .forEach { st ->
            val docId = state.studentDocIdFor(st.id).ifBlank { st.admNo }
            val ledger = FeeCollectionEngine.computeStudentFeeAccount(
                structure = structure,
                student = st,
                studentDocId = docId,
                override = overridesByStudent[docId],
                receiptsForStudent = receipts
            )
            result[docId] = ledger.totalRemainingDue.coerceAtLeast(0)
        }
    return result
}

fun SchoolViewModel.generateExamTimetablePdf(exam: ExamScheduleRecord, className: String) {
    val state = uiState.value
    val schoolLongId = state.schoolRecordsByDocId[exam.schoolId]?.stableId ?: state.activeSchool.id
    val school = state.schoolById(schoolLongId) ?: state.activeSchool
    val entries = exam.entries.filter { it.className.equals(className, ignoreCase = true) }
    val bundle = ExamTimetablePdfBundle(school = school, exam = exam, className = className, entries = entries)
    launchPhase9PdfJob(title = "Timetable • ${exam.title} (Class $className)", sizeLabel = "A4 Portrait") {
        Phase11PdfGenerator.generateClassTimetablePdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateClassAdmitCardsPdf(
    exam: ExamScheduleRecord,
    className: String,
    printableStudents: List<AdmitCardStudentItem>
) {
    if (printableStudents.isEmpty()) {
        emitMessage("No eligible students to print admit cards.")
        return
    }
    val state = uiState.value
    val schoolLongId = state.schoolRecordsByDocId[exam.schoolId]?.stableId ?: state.activeSchool.id
    val school = state.schoolById(schoolLongId) ?: state.activeSchool
    val bundle = AdmitCardsPdfBundle(school = school, exam = exam, className = className, items = printableStudents)
    launchPhase9PdfJob(
        title = "Admit Cards • ${exam.title} (Class $className, ${printableStudents.size} students)",
        sizeLabel = "A4 Portrait (2/page)"
    ) {
        Phase11PdfGenerator.generateAdmitCardsPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateRoomSeatingPdf(exam: ExamScheduleRecord, room: ExamRoomRecord) {
    val state = uiState.value
    val seating = state.examSeatings.firstOrNull { it.examId == exam.id }
    val allocs = seating?.allocations?.filter { it.roomId == room.id }.orEmpty()
    val schoolLongId = state.schoolRecordsByDocId[exam.schoolId]?.stableId ?: state.activeSchool.id
    val school = state.schoolById(schoolLongId) ?: state.activeSchool
    val bundle = RoomSeatingPdfBundle(school = school, exam = exam, room = room, allocations = allocs)
    launchPhase9PdfJob(title = "Seating • ${room.roomName} (${exam.title})", sizeLabel = "A4 Portrait") {
        Phase11PdfGenerator.generateSeatingListPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateExamDutyListPdf(exam: ExamScheduleRecord) {
    val state = uiState.value
    val duties = state.examDuties.filter { it.examId == exam.id }
    val schoolLongId = state.schoolRecordsByDocId[exam.schoolId]?.stableId ?: state.activeSchool.id
    val school = state.schoolById(schoolLongId) ?: state.activeSchool
    val bundle = DutyListPdfBundle(school = school, exam = exam, duties = duties)
    launchPhase9PdfJob(title = "Duty Roster • ${exam.title}", sizeLabel = "A4 Portrait") {
        Phase11PdfGenerator.generateDutyListPdf(appContext, bundle)
    }
}

// =============================================================================
// 2. STOCK ACTIONS (Unified with Phase 9 `optionalCatalogue`)
// =============================================================================

fun SchoolViewModel.saveStockCatalogueItem(
    existingItem: OptionalFeeCatalogueItem?,
    name: String,
    unit: String,
    sellingPriceRs: Int,
    openingStock: Int,
    lowStockLevel: Int,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val session = state.filter.session
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase11AccessEvaluator.canEditStock(state.accessProfile, schoolDocId) && !state.canEditStock) {
        onResult(false, "View-only access: Stock Edit permission required.")
        return
    }
    val cleanName = name.trim()
    if (cleanName.isEmpty()) {
        onResult(false, "Please enter item name.")
        return
    }
    val existingStructure = state.effectivePhase11FeeStructures.firstOrNull {
        it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
    }
    val currentCatalogue = (existingStructure ?: state.feeStructureForPhase11(schoolDocId, session)).optionalCatalogue
    val newItem = OptionalFeeCatalogueItem(
        id = existingItem?.id?.takeIf { it.isNotBlank() }
            ?: "opt_${UUID.randomUUID().toString().replace("-", "").take(8)}",
        name = cleanName,
        price = sellingPriceRs.coerceAtLeast(0),
        hidden = false,
        unit = unit.trim().ifBlank { "pcs" },
        openingStock = openingStock.coerceAtLeast(0),
        lowStockLevel = lowStockLevel.coerceAtLeast(0)
    )
    val updatedList = if (existingItem != null) {
        currentCatalogue.map { if (it.id == existingItem.id) newItem else it }
    } else {
        currentCatalogue + newItem
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    viewModelScope.launch {
        phase11Repo.saveUnifiedStockCatalogue(
            orgId = orgId,
            schoolId = schoolDocId,
            session = session,
            existingStructure = existingStructure,
            updatedCatalogue = updatedList,
            editorName = actorName
        ).onSuccess {
            emitMessage("Saved item '${newItem.name}' (also synced with Fee Counter)")
            onResult(true, null)
        }.onFailure { e ->
            onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save stock item."))
        }
    }
}

fun SchoolViewModel.recordStockInOrAdjustment(
    item: OptionalFeeCatalogueItem,
    txType: StockTransactionType,
    dateIso: String,
    quantityDelta: Int,
    unitCostRs: Int = 0,
    supplier: String = "",
    reason: String = "",
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase11AccessEvaluator.canEditStock(state.accessProfile, schoolDocId) && !state.canEditStock) {
        onResult(false, "Stock Edit permission required.")
        return
    }
    val err = StockEngine.validateStockTransaction(txType, quantityDelta, reason)
    if (err != null) {
        onResult(false, err)
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val cleanDate = dateIso.trim().takeIf { Phase6DateUtils.isValidIsoDate(it) } ?: Phase6DateUtils.todayIso()
    val totalCost = if (txType == StockTransactionType.STOCK_IN) {
        (quantityDelta.coerceAtLeast(0) * unitCostRs.coerceAtLeast(0))
    } else 0
    val rec = StockTransactionRecord(
        id = "",
        schoolId = schoolDocId,
        session = state.filter.session,
        itemId = item.id,
        itemName = item.name,
        txType = txType,
        dateIso = cleanDate,
        quantityDelta = quantityDelta,
        unitCostRs = unitCostRs.coerceAtLeast(0),
        totalCostRs = totalCost,
        supplier = supplier.trim(),
        reason = reason.trim(),
        createdByName = actorName
    )
    viewModelScope.launch {
        phase11Repo.recordStockTransaction(orgId, rec)
            .onSuccess {
                val sign = if (quantityDelta > 0) "+$quantityDelta" else "$quantityDelta"
                emitMessage("${txType.englishLabel}: ${item.name} ($sign ${item.unit})")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to record stock entry."))
            }
    }
}

fun SchoolViewModel.generateStockSummaryReportPdf() {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val session = state.filter.session
    val summaries = state.stockSummariesForSchool(schoolDocId, session)
    val bundle = StockReportPdfBundle(
        school = state.activeSchool,
        session = session,
        generatedDateIso = Phase6DateUtils.todayIso(),
        summaries = summaries
    )
    launchPhase9PdfJob(title = "Stock Report • $session", sizeLabel = "A4 Portrait") {
        Phase11PdfGenerator.generateStockReportPdf(appContext, bundle)
    }
}

// =============================================================================
// 3. TRANSPORT ROUTES ACTIONS
// =============================================================================

fun SchoolViewModel.saveTransportRouteRecord(
    existing: TransportRouteRecord?,
    routeName: String,
    vehicleNumber: String,
    vehicleType: String,
    seatCapacity: Int,
    driverName: String,
    driverPhone: String,
    monthlyFeeRs: Int,
    stops: List<TransportStop>,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase11AccessEvaluator.canEditTransport(state.accessProfile, schoolDocId) && !state.canEditTransport) {
        onResult(false, "View-only access: Transport Edit permission required.")
        return
    }
    if (routeName.trim().isEmpty()) {
        onResult(false, "Please enter a route name.")
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val rec = TransportRouteRecord(
        id = existing?.id.orEmpty(),
        schoolId = schoolDocId,
        routeName = routeName.trim(),
        vehicleNumber = vehicleNumber.trim(),
        vehicleType = vehicleType.trim().ifBlank { "Bus" },
        seatCapacity = seatCapacity.coerceIn(1, 200),
        driverName = driverName.trim(),
        driverPhone = driverPhone.trim(),
        monthlyFeeRs = monthlyFeeRs.coerceAtLeast(0),
        stops = stops.filter { it.stopName.isNotBlank() },
        active = true
    )
    viewModelScope.launch {
        phase11Repo.saveTransportRoute(orgId, rec, actorName)
            .onSuccess {
                emitMessage("Saved route '${rec.routeName}'")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save transport route."))
            }
    }
}

fun SchoolViewModel.deleteTransportRouteRecord(route: TransportRouteRecord) {
    val state = uiState.value
    if (!Phase11AccessEvaluator.canEditTransport(state.accessProfile, route.schoolId) && !state.canEditTransport) {
        emitMessage("Transport Edit permission required.")
        return
    }
    viewModelScope.launch {
        phase11Repo.deleteTransportRoute(orgId, route.schoolId, route.id, route.routeName)
            .onSuccess { emitMessage("Deleted route '${route.routeName}'") }
            .onFailure { e -> emitMessage(friendlyFirestoreErrorMessage(e, "Unable to delete route.")) }
    }
}

fun SchoolViewModel.assignStudentToTransportRoute(
    student: StudentEntity,
    route: TransportRouteRecord,
    stopName: String,
    preserveExistingAmountIfPositive: Boolean = true,
    explicitMonthlyFeeOverride: Int? = null,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.schoolDocIdByLongId[student.schoolId] ?: state.activeSchoolDocId
    if (!Phase11AccessEvaluator.canEditTransport(state.accessProfile, schoolDocId) && !state.canEditTransport && !state.canEditStudentRegister) {
        onResult(false, "Transport or Student Register Edit permission required.")
        return
    }
    val studentDocId = state.studentDocIdFor(student.id)
    if (studentDocId.isBlank()) {
        onResult(false, "Student cloud record not found.")
        return
    }
    val updated = TransportEngine.assignStudentToRoute(
        student = student,
        route = route,
        stopName = stopName,
        preserveExistingAmountIfPositive = preserveExistingAmountIfPositive,
        explicitMonthlyFeeOverride = explicitMonthlyFeeOverride
    )
    viewModelScope.launch {
        academicRepo.updateStudentTransportAssignment(
            orgId = orgId,
            schoolDocId = schoolDocId,
            studentDocId = studentDocId,
            studentName = student.name,
            usesTransport = true,
            transportRouteId = updated.transportRouteId,
            transportRouteName = updated.transportRouteName,
            transportStopName = updated.transportStopName,
            transportMonthlyAmount = updated.transportMonthlyAmount
        ).onSuccess {
            emitMessage("Assigned ${student.name} to ${updated.transportRouteName} (₹${updated.transportMonthlyAmount}/mo)")
            onResult(true, null)
        }.onFailure { e ->
            onResult(false, friendlyFirestoreErrorMessage(e, "Unable to assign student to route."))
        }
    }
}

fun SchoolViewModel.removeStudentFromTransportRoute(
    student: StudentEntity,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.schoolDocIdByLongId[student.schoolId] ?: state.activeSchoolDocId
    if (!Phase11AccessEvaluator.canEditTransport(state.accessProfile, schoolDocId) && !state.canEditTransport && !state.canEditStudentRegister) {
        onResult(false, "Transport Edit permission required.")
        return
    }
    val studentDocId = state.studentDocIdFor(student.id)
    if (studentDocId.isBlank()) {
        onResult(false, "Student cloud record not found.")
        return
    }
    viewModelScope.launch {
        academicRepo.updateStudentTransportAssignment(
            orgId = orgId,
            schoolDocId = schoolDocId,
            studentDocId = studentDocId,
            studentName = student.name,
            usesTransport = false,
            transportRouteId = "",
            transportRouteName = "",
            transportStopName = "",
            transportMonthlyAmount = 0
        ).onSuccess {
            emitMessage("Removed ${student.name} from transport")
            onResult(true, null)
        }.onFailure { e ->
            onResult(false, friendlyFirestoreErrorMessage(e, "Unable to update student transport."))
        }
    }
}

fun SchoolViewModel.generateTransportRouteListPdf(summary: TransportRouteSummary) {
    val state = uiState.value
    val bundle = TransportRoutePdfBundle(
        school = state.activeSchool,
        session = state.filter.session,
        generatedDateIso = Phase6DateUtils.todayIso(),
        summary = summary
    )
    launchPhase9PdfJob(title = "Route List • ${summary.route.routeName}", sizeLabel = "A4 Portrait") {
        Phase11PdfGenerator.generateTransportRouteListPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateTransportDriverListPdf(summary: TransportRouteSummary) {
    val state = uiState.value
    val bundle = TransportRoutePdfBundle(
        school = state.activeSchool,
        session = state.filter.session,
        generatedDateIso = Phase6DateUtils.todayIso(),
        summary = summary
    )
    launchPhase9PdfJob(title = "Driver List • ${summary.route.routeName}", sizeLabel = "A4 Portrait") {
        Phase11PdfGenerator.generateTransportDriverListPdf(appContext, bundle)
    }
}
