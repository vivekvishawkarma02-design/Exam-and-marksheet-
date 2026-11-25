package com.example.data.cloud

import android.content.Context
import com.example.domain.AuthMethod
import com.example.domain.LocalPendingMarkEdit
import com.example.domain.LocalPendingPhase6Edit
import com.example.domain.LocalPendingTimetableEdit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight SharedPreferences store for Phase 3 offline session continuity:
 * 1) Persists the last selected orgId per user UID so reopening the app in airplane mode
 *    opens the cached organisation workspace seamlessly.
 * 2) Persists unacknowledged local mark edits across app restarts so latest-edit-wins
 *    conflict detection still notifies the user if their offline edit was replaced on reconnect.
 */
class OfflinePrefsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSelectedOrgId(uid: String): String? =
        prefs.getString(keyLastOrg(uid), null)?.takeIf { it.isNotBlank() }

    fun setLastSelectedOrgId(uid: String, orgId: String?) {
        val editor = prefs.edit()
        if (orgId.isNullOrBlank()) {
            editor.remove(keyLastOrg(uid))
        } else {
            editor.putString(keyLastOrg(uid), orgId)
        }
        editor.apply()
    }

    fun getLastSignInMethod(uid: String): AuthMethod? =
        AuthMethod.fromWire(prefs.getString(keyAuthMethod(uid), null))

    fun setLastSignInMethod(uid: String, method: AuthMethod) {
        prefs.edit().putString(keyAuthMethod(uid), method.wireValue).apply()
    }

    fun loadPendingMarkEdits(uid: String, orgId: String, schoolDocId: String): List<LocalPendingMarkEdit> {
        val raw = prefs.getString(keyPendingMarks(uid), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val oId = obj.optString("orgId")
                    val sId = obj.optString("schoolDocId")
                    if (oId == orgId && sId == schoolDocId) {
                        add(
                            LocalPendingMarkEdit(
                                orgId = oId,
                                schoolDocId = sId,
                                markDocId = obj.optString("markDocId"),
                                studentDocId = obj.optString("studentDocId"),
                                studentName = obj.optString("studentName"),
                                className = obj.optString("className"),
                                subjectName = obj.optString("subjectName"),
                                componentKey = obj.optString("componentKey"),
                                localValue = obj.optString("localValue"),
                                clientEditedAtMs = obj.optLong("clientEditedAtMs", 0L)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsertPendingMarkEdit(uid: String, edit: LocalPendingMarkEdit) {
        val all = loadAllPendingMarkEdits(uid)
            .filterNot { it.orgId == edit.orgId && it.schoolDocId == edit.schoolDocId && it.markDocId == edit.markDocId }
            .toMutableList()
        all.add(edit)
        // Bound stored list to 400 recent edits for fast serialization
        val bounded = if (all.size > 400) all.takeLast(400) else all
        saveAllPendingMarkEdits(uid, bounded)
    }

    fun replacePendingMarkEditsForSchool(
        uid: String,
        orgId: String,
        schoolDocId: String,
        remainingForSchool: List<LocalPendingMarkEdit>
    ) {
        val others = loadAllPendingMarkEdits(uid)
            .filterNot { it.orgId == orgId && it.schoolDocId == schoolDocId }
        saveAllPendingMarkEdits(uid, others + remainingForSchool)
    }

    fun clearPendingMarkEditsForOrg(uid: String, orgId: String) {
        val others = loadAllPendingMarkEdits(uid).filterNot { it.orgId == orgId }
        saveAllPendingMarkEdits(uid, others)
    }

    fun loadPendingTimetableEdits(uid: String, orgId: String): List<LocalPendingTimetableEdit> =
        loadAllPendingTimetableEdits(uid).filter { it.orgId == orgId }

    fun upsertPendingTimetableEdit(uid: String, edit: LocalPendingTimetableEdit) {
        val all = loadAllPendingTimetableEdits(uid)
            .filterNot { it.orgId == edit.orgId && it.cellId == edit.cellId }
            .toMutableList()
        all.add(edit)
        val bounded = if (all.size > 400) all.takeLast(400) else all
        saveAllPendingTimetableEdits(uid, bounded)
    }

    fun replacePendingTimetableEditsForOrg(
        uid: String,
        orgId: String,
        remainingForOrg: List<LocalPendingTimetableEdit>
    ) {
        val others = loadAllPendingTimetableEdits(uid).filterNot { it.orgId == orgId }
        saveAllPendingTimetableEdits(uid, others + remainingForOrg)
    }

    fun clearPendingTimetableEditsForOrg(uid: String, orgId: String) {
        val others = loadAllPendingTimetableEdits(uid).filterNot { it.orgId == orgId }
        saveAllPendingTimetableEdits(uid, others)
    }

    fun loadPendingPhase6Edits(uid: String, orgId: String): List<LocalPendingPhase6Edit> =
        loadAllPendingPhase6Edits(uid).filter { it.first == orgId }.map { it.second }

    fun upsertPendingPhase6Edit(uid: String, orgId: String, edit: LocalPendingPhase6Edit) {
        val all = loadAllPendingPhase6Edits(uid)
            .filterNot { it.first == orgId && it.second.kind == edit.kind && it.second.docId == edit.docId }
            .toMutableList()
        all.add(orgId to edit)
        val bounded = if (all.size > 400) all.takeLast(400) else all
        saveAllPendingPhase6Edits(uid, bounded)
    }

    fun replacePendingPhase6EditsForOrg(
        uid: String,
        orgId: String,
        remainingForOrg: List<LocalPendingPhase6Edit>
    ) {
        val others = loadAllPendingPhase6Edits(uid).filterNot { it.first == orgId }
        saveAllPendingPhase6Edits(uid, others + remainingForOrg.map { orgId to it })
    }

    fun clearPendingPhase6EditsForOrg(uid: String, orgId: String) {
        val others = loadAllPendingPhase6Edits(uid).filterNot { it.first == orgId }
        saveAllPendingPhase6Edits(uid, others)
    }

    fun getOrCreateDeviceNodeCode(uid: String): String {
        val key = "device_node_code_${uid.ifBlank { "default" }}"
        val existing = prefs.getString(key, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        var installationUuid = prefs.getString("device_installation_uuid", null)
        if (installationUuid.isNullOrBlank()) {
            installationUuid = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_installation_uuid", installationUuid).apply()
        }
        val code = com.example.domain.FeeCollectionEngine.deriveDeviceNodeCode(installationUuid, uid)
        prefs.edit().putString(key, code).apply()
        return code
    }

    fun getLastReceiptSequence(orgId: String, schoolId: String, session: String, deviceNodeCode: String): Int =
        prefs.getInt(keyReceiptSeq(orgId, schoolId, session, deviceNodeCode), 0).coerceAtLeast(0)

    fun setLastReceiptSequence(orgId: String, schoolId: String, session: String, deviceNodeCode: String, seq: Int) {
        prefs.edit().putInt(keyReceiptSeq(orgId, schoolId, session, deviceNodeCode), seq.coerceAtLeast(0)).apply()
    }

    fun getHindiLabelsEnabled(defaultValue: Boolean = true): Boolean =
        prefs.getBoolean(KEY_HINDI_LABELS_ENABLED, defaultValue)

    fun setHindiLabelsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HINDI_LABELS_ENABLED, enabled).apply()
    }

    fun isFirstTimeGuideSkipped(uid: String, orgId: String): Boolean =
        prefs.getBoolean(keyFirstTimeGuideSkipped(uid, orgId), false)

    fun setFirstTimeGuideSkipped(uid: String, orgId: String, skipped: Boolean) {
        prefs.edit().putBoolean(keyFirstTimeGuideSkipped(uid, orgId), skipped).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun loadAllPendingMarkEdits(uid: String): List<LocalPendingMarkEdit> {
        val raw = prefs.getString(keyPendingMarks(uid), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val markDocId = obj.optString("markDocId")
                    if (markDocId.isBlank()) continue
                    add(
                        LocalPendingMarkEdit(
                            orgId = obj.optString("orgId"),
                            schoolDocId = obj.optString("schoolDocId"),
                            markDocId = markDocId,
                            studentDocId = obj.optString("studentDocId"),
                            studentName = obj.optString("studentName"),
                            className = obj.optString("className"),
                            subjectName = obj.optString("subjectName"),
                            componentKey = obj.optString("componentKey"),
                            localValue = obj.optString("localValue"),
                            clientEditedAtMs = obj.optLong("clientEditedAtMs", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllPendingMarkEdits(uid: String, list: List<LocalPendingMarkEdit>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("orgId", e.orgId)
                    put("schoolDocId", e.schoolDocId)
                    put("markDocId", e.markDocId)
                    put("studentDocId", e.studentDocId)
                    put("studentName", e.studentName)
                    put("className", e.className)
                    put("subjectName", e.subjectName)
                    put("componentKey", e.componentKey)
                    put("localValue", e.localValue)
                    put("clientEditedAtMs", e.clientEditedAtMs)
                }
            )
        }
        prefs.edit().putString(keyPendingMarks(uid), arr.toString()).apply()
    }

    private fun loadAllPendingTimetableEdits(uid: String): List<LocalPendingTimetableEdit> {
        val raw = prefs.getString(keyPendingTimetable(uid), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val cellId = obj.optString("cellId")
                    if (cellId.isBlank()) continue
                    add(
                        LocalPendingTimetableEdit(
                            orgId = obj.optString("orgId"),
                            cellId = cellId,
                            schoolId = obj.optString("schoolId"),
                            session = obj.optString("session"),
                            className = obj.optString("className"),
                            section = obj.optString("section"),
                            dayWire = obj.optString("dayWire"),
                            slotId = obj.optString("slotId"),
                            localSubjectName = obj.optString("localSubjectName"),
                            localTeacherId = obj.optString("localTeacherId"),
                            localTeacherName = obj.optString("localTeacherName"),
                            localLocked = obj.optBoolean("localLocked", false),
                            wasCleared = obj.optBoolean("wasCleared", false),
                            clientEditedAtMs = obj.optLong("clientEditedAtMs", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllPendingTimetableEdits(uid: String, list: List<LocalPendingTimetableEdit>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("orgId", e.orgId)
                    put("cellId", e.cellId)
                    put("schoolId", e.schoolId)
                    put("session", e.session)
                    put("className", e.className)
                    put("section", e.section)
                    put("dayWire", e.dayWire)
                    put("slotId", e.slotId)
                    put("localSubjectName", e.localSubjectName)
                    put("localTeacherId", e.localTeacherId)
                    put("localTeacherName", e.localTeacherName)
                    put("localLocked", e.localLocked)
                    put("wasCleared", e.wasCleared)
                    put("clientEditedAtMs", e.clientEditedAtMs)
                }
            )
        }
        prefs.edit().putString(keyPendingTimetable(uid), arr.toString()).apply()
    }

    private fun loadAllPendingPhase6Edits(uid: String): List<Pair<String, LocalPendingPhase6Edit>> {
        val raw = prefs.getString(keyPendingPhase6(uid), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val docId = obj.optString("docId")
                    val orgId = obj.optString("orgId")
                    if (docId.isBlank() || orgId.isBlank()) continue
                    add(
                        orgId to LocalPendingPhase6Edit(
                            kind = obj.optString("kind", "LEAVE"),
                            docId = docId,
                            targetSummary = obj.optString("targetSummary"),
                            localValueSummary = obj.optString("localValueSummary"),
                            wasDeleted = obj.optBoolean("wasDeleted", false),
                            clientEditedAtMs = obj.optLong("clientEditedAtMs", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllPendingPhase6Edits(uid: String, list: List<Pair<String, LocalPendingPhase6Edit>>) {
        val arr = JSONArray()
        list.forEach { (orgId, e) ->
            arr.put(
                JSONObject().apply {
                    put("orgId", orgId)
                    put("kind", e.kind)
                    put("docId", e.docId)
                    put("targetSummary", e.targetSummary)
                    put("localValueSummary", e.localValueSummary)
                    put("wasDeleted", e.wasDeleted)
                    put("clientEditedAtMs", e.clientEditedAtMs)
                }
            )
        }
        prefs.edit().putString(keyPendingPhase6(uid), arr.toString()).apply()
    }

    private fun keyLastOrg(uid: String) = "last_org_$uid"
    private fun keyAuthMethod(uid: String) = "auth_method_$uid"
    private fun keyPendingMarks(uid: String) = "pending_marks_$uid"
    private fun keyPendingTimetable(uid: String) = "pending_timetable_$uid"
    private fun keyPendingPhase6(uid: String) = "pending_phase6_$uid"
    private fun keyReceiptSeq(orgId: String, schoolId: String, session: String, node: String) =
        "rcp_seq_${orgId}_${schoolId}_${session}_$node"
    private fun keyFirstTimeGuideSkipped(uid: String, orgId: String) =
        "p13_guide_skipped_${uid.ifBlank { "default" }}_${orgId.ifBlank { "default" }}"

    companion object {
        private const val PREFS_NAME = "school_report_offline_sync_prefs"
        private const val KEY_HINDI_LABELS_ENABLED = "p13_hindi_labels_enabled"
    }
}
