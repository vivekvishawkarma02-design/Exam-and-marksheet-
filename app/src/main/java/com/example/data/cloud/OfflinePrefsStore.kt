package com.example.data.cloud

import android.content.Context
import com.example.domain.AuthMethod
import com.example.domain.LocalPendingMarkEdit
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

    private fun keyLastOrg(uid: String) = "last_org_$uid"
    private fun keyAuthMethod(uid: String) = "auth_method_$uid"
    private fun keyPendingMarks(uid: String) = "pending_marks_$uid"

    companion object {
        private const val PREFS_NAME = "school_report_offline_sync_prefs"
    }
}
