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

    // =========================================================================
    // PHASE 16A: ADS, SERVER TIME OFFSET & OFFLINE AD-FREE CACHE
    // =========================================================================
    fun getOrInitFirstInstallMs(fallbackNowMs: Long = System.currentTimeMillis()): Long {
        val existing = prefs.getLong(KEY_FIRST_INSTALL_MS, 0L)
        if (existing > 0L) return existing
        val safeNow = fallbackNowMs.coerceAtLeast(1L)
        prefs.edit().putLong(KEY_FIRST_INSTALL_MS, safeNow).apply()
        return safeNow
    }

    fun getLastKnownServerOffsetMs(): Long = prefs.getLong(KEY_SERVER_TIME_OFFSET_MS, 0L)

    fun setLastKnownServerOffsetMs(offsetMs: Long) {
        prefs.edit().putLong(KEY_SERVER_TIME_OFFSET_MS, offsetMs).apply()
    }

    fun loadCachedRewardedAdFreeState(uid: String, orgId: String): com.example.domain.RewardedAdFreeState {
        val raw = prefs.getString(keyRewardedAdFree(uid, orgId), null) ?: return com.example.domain.RewardedAdFreeState.EMPTY
        return parseRewardedAdFreeJson(raw)
    }

    fun saveCachedRewardedAdFreeState(uid: String, orgId: String, state: com.example.domain.RewardedAdFreeState) {
        val json = serializeRewardedAdFreeJson(state)
        prefs.edit()
            .putString(keyRewardedAdFree(uid, orgId), json)
            .putString(keyLatestRewardedAdFreeForUser(uid), json)
            .apply()
    }

    fun loadLatestRewardedAdFreeStateForUser(uid: String): com.example.domain.RewardedAdFreeState {
        val raw = prefs.getString(keyLatestRewardedAdFreeForUser(uid), null)
            ?: return com.example.domain.RewardedAdFreeState.EMPTY
        return parseRewardedAdFreeJson(raw)
    }

    fun getCachedOrgSubscription(orgId: String): com.example.domain.OrgSubscription {
        val statusWire = prefs.getString(keyOrgSubStatus(orgId), null)
        val plan = prefs.getString(keyOrgSubPlan(orgId), null).orEmpty()
        val exp = prefs.getLong(keyOrgSubExpiresAt(orgId), -1L).takeIf { it > 0L }
        val autoRenew = prefs.getBoolean("org_sub_autorenew_$orgId", false)
        val updatedAt = prefs.getLong("org_sub_updated_$orgId", -1L).takeIf { it > 0L }
        val tokenHash = prefs.getString("org_sub_token_hash_$orgId", null).orEmpty()
        return com.example.domain.OrgSubscription(
            status = com.example.domain.OrgSubscriptionStatus.fromWire(statusWire),
            plan = plan,
            expiresAtMs = exp,
            autoRenewing = autoRenew,
            updatedAtMs = updatedAt,
            purchaseTokenHash = tokenHash
        )
    }

    fun setCachedOrgSubscription(orgId: String, sub: com.example.domain.OrgSubscription) {
        if (orgId.isBlank()) return
        prefs.edit()
            .putString(keyOrgSubStatus(orgId), sub.status.wireValue)
            .putString(keyOrgSubPlan(orgId), sub.plan)
            .putLong(keyOrgSubExpiresAt(orgId), sub.expiresAtMs ?: -1L)
            .putBoolean("org_sub_autorenew_$orgId", sub.autoRenewing)
            .putLong("org_sub_updated_$orgId", sub.updatedAtMs ?: -1L)
            .putString("org_sub_token_hash_$orgId", sub.purchaseTokenHash)
            .apply()
    }

    fun hasAnyCachedAdFreeSubscription(orgIds: List<String>, serverNowMs: Long = 0L): Boolean =
        orgIds.any { id -> id.isNotBlank() && getCachedOrgSubscription(id).isEntitledAt(serverNowMs) }

    fun savePendingPurchaseVerification(item: com.example.domain.PendingPurchaseVerification) {
        val list = loadPendingPurchaseVerifications()
            .filterNot { it.purchaseToken == item.purchaseToken && it.orgId == item.orgId } + item
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("orgId", p.orgId)
                    put("purchaseToken", p.purchaseToken)
                    put("productId", p.productId)
                    put("basePlanId", p.basePlanId)
                    put("queuedAtMs", p.queuedAtMs)
                }
            )
        }
        prefs.edit().putString("pending_play_purchase_verifications", arr.toString()).apply()
    }

    fun loadPendingPurchaseVerifications(orgIdFilter: String? = null): List<com.example.domain.PendingPurchaseVerification> {
        val raw = prefs.getString("pending_play_purchase_verifications", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val orgId = obj.optString("orgId")
                    val token = obj.optString("purchaseToken")
                    if (orgId.isBlank() || token.isBlank()) continue
                    if (orgIdFilter != null && orgId != orgIdFilter) continue
                    add(
                        com.example.domain.PendingPurchaseVerification(
                            orgId = orgId,
                            purchaseToken = token,
                            productId = obj.optString("productId", com.example.domain.Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID),
                            basePlanId = obj.optString("basePlanId", ""),
                            queuedAtMs = obj.optLong("queuedAtMs", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removePendingPurchaseVerification(orgId: String, purchaseToken: String) {
        val remaining = loadPendingPurchaseVerifications()
            .filterNot { it.orgId == orgId && it.purchaseToken == purchaseToken }
        val arr = JSONArray()
        remaining.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("orgId", p.orgId)
                    put("purchaseToken", p.purchaseToken)
                    put("productId", p.productId)
                    put("basePlanId", p.basePlanId)
                    put("queuedAtMs", p.queuedAtMs)
                }
            )
        }
        prefs.edit().putString("pending_play_purchase_verifications", arr.toString()).apply()
    }

    fun getDismissedLapsedNoticeMarker(orgId: String): Long =
        prefs.getLong("dismissed_lapsed_sub_notice_$orgId", 0L)

    fun setDismissedLapsedNoticeMarker(orgId: String, markerMs: Long) {
        if (orgId.isBlank()) return
        prefs.edit().putLong("dismissed_lapsed_sub_notice_$orgId", markerMs).apply()
    }

    fun loadInterstitialFrequencyState(): com.example.domain.InterstitialFrequencyState {
        return com.example.domain.InterstitialFrequencyState(
            lastShownServerMs = prefs.getLong(KEY_INTERSTITIAL_LAST_SHOWN_MS, 0L),
            dailyDateKey = prefs.getString(KEY_INTERSTITIAL_DAILY_DATE, null).orEmpty(),
            dailyShownCount = prefs.getInt(KEY_INTERSTITIAL_DAILY_COUNT, 0).coerceAtLeast(0)
        )
    }

    fun saveInterstitialFrequencyState(state: com.example.domain.InterstitialFrequencyState) {
        prefs.edit()
            .putLong(KEY_INTERSTITIAL_LAST_SHOWN_MS, state.lastShownServerMs.coerceAtLeast(0L))
            .putString(KEY_INTERSTITIAL_DAILY_DATE, state.dailyDateKey)
            .putInt(KEY_INTERSTITIAL_DAILY_COUNT, state.dailyShownCount.coerceAtLeast(0))
            .apply()
    }

    fun getDebugSubscriptionSimulation(): com.example.domain.DebugSubscriptionSimulation =
        com.example.domain.DebugSubscriptionSimulation.fromWire(
            prefs.getString(KEY_DEBUG_SUB_SIMULATION, null)
        )

    fun setDebugSubscriptionSimulation(sim: com.example.domain.DebugSubscriptionSimulation) {
        prefs.edit().putString(KEY_DEBUG_SUB_SIMULATION, sim.wireValue).apply()
    }

    fun getDebugBypassInstallGrace(): Boolean =
        prefs.getBoolean(KEY_DEBUG_BYPASS_INSTALL_GRACE, false)

    fun setDebugBypassInstallGrace(bypass: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_BYPASS_INSTALL_GRACE, bypass).apply()
    }

    private fun serializeRewardedAdFreeJson(state: com.example.domain.RewardedAdFreeState): String =
        JSONObject().apply {
            put("adFreeUntilMs", state.adFreeUntilMs)
            put("windowStartAtMs", state.window.windowStartAtMs)
            put("countInWindow", state.window.countInWindow)
            put("dailyDateKey", state.window.dailyDateKey)
            put("dailyCount", state.window.dailyCount)
            put("totalCount", state.window.totalCount)
            put("lastRewardedAtMs", state.window.lastRewardedAtMs)
        }.toString()

    private fun parseRewardedAdFreeJson(raw: String): com.example.domain.RewardedAdFreeState = try {
        val obj = JSONObject(raw)
        com.example.domain.RewardedAdFreeState(
            adFreeUntilMs = obj.optLong("adFreeUntilMs", 0L).coerceAtLeast(0L),
            window = com.example.domain.RewardedAdsWindow(
                windowStartAtMs = obj.optLong("windowStartAtMs", 0L).coerceAtLeast(0L),
                countInWindow = obj.optInt("countInWindow", 0).coerceAtLeast(0),
                dailyDateKey = obj.optString("dailyDateKey", ""),
                dailyCount = obj.optInt("dailyCount", 0).coerceAtLeast(0),
                totalCount = obj.optLong("totalCount", 0L).coerceAtLeast(0L),
                lastRewardedAtMs = obj.optLong("lastRewardedAtMs", 0L).coerceAtLeast(0L)
            )
        )
    } catch (_: Exception) {
        com.example.domain.RewardedAdFreeState.EMPTY
    }

    fun clearAll() {
        val preservedInstallMs = prefs.getLong(KEY_FIRST_INSTALL_MS, 0L)
        prefs.edit().clear().apply()
        if (preservedInstallMs > 0L) {
            prefs.edit().putLong(KEY_FIRST_INSTALL_MS, preservedInstallMs).apply()
        }
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
    private fun keyRewardedAdFree(uid: String, orgId: String) =
        "p16a_ad_free_${uid.ifBlank { "default" }}_${orgId.ifBlank { "default" }}"
    private fun keyLatestRewardedAdFreeForUser(uid: String) =
        "p16a_ad_free_latest_${uid.ifBlank { "default" }}"
    private fun keyOrgSubStatus(orgId: String) = "p16a_org_sub_status_$orgId"
    private fun keyOrgSubPlan(orgId: String) = "p16a_org_sub_plan_$orgId"
    private fun keyOrgSubExpiresAt(orgId: String) = "p16a_org_sub_exp_$orgId"

    companion object {
        private const val PREFS_NAME = "school_report_offline_sync_prefs"
        private const val KEY_HINDI_LABELS_ENABLED = "p13_hindi_labels_enabled"
        private const val KEY_FIRST_INSTALL_MS = "p16a_first_install_ms"
        private const val KEY_SERVER_TIME_OFFSET_MS = "p16a_server_time_offset_ms"
        private const val KEY_INTERSTITIAL_LAST_SHOWN_MS = "p16a_interstitial_last_shown_ms"
        private const val KEY_INTERSTITIAL_DAILY_DATE = "p16a_interstitial_daily_date"
        private const val KEY_INTERSTITIAL_DAILY_COUNT = "p16a_interstitial_daily_count"
        private const val KEY_DEBUG_SUB_SIMULATION = "p16a_debug_sub_simulation"
        private const val KEY_DEBUG_BYPASS_INSTALL_GRACE = "p16a_debug_bypass_install_grace"
    }
}
