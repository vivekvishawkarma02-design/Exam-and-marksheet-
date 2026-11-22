package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ReportCardRepository
import com.example.data.cloud.CloudOrgCodeLookup
import com.example.data.cloud.CloudUserAccount
import com.example.data.cloud.LocalMigrationSummary
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.OfflinePrefsStore
import com.example.data.cloud.OrgCloudRepository
import com.example.domain.MarkComponent
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgCodeValidation
import com.example.domain.OrgRole
import com.example.util.OperationType
import com.example.util.friendlyFirestoreErrorMessage
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OrgCodeCheckState {
    data object Idle : OrgCodeCheckState
    data class Checking(val normalized: String) : OrgCodeCheckState
    data class Available(val normalized: String) : OrgCodeCheckState
    data class Taken(val normalized: String) : OrgCodeCheckState
    data class Invalid(val reason: String) : OrgCodeCheckState
    data class Error(val message: String) : OrgCodeCheckState
}

sealed interface JoinOrgLookupState {
    data object Idle : JoinOrgLookupState
    data class Searching(val code: String) : JoinOrgLookupState
    data class Found(val org: CloudOrgCodeLookup) : JoinOrgLookupState
    data class NotFound(val code: String) : JoinOrgLookupState
    data class Invalid(val reason: String) : JoinOrgLookupState
    data class Error(val message: String) : JoinOrgLookupState
}

data class OrgSessionUiState(
    val isLoadingAccount: Boolean = true,
    val isSyncingMemberships: Boolean = false,
    val userAccount: CloudUserAccount? = null,
    val isOnline: Boolean = true,
    val selectedOrgId: String? = null,
    val promptLocalMigrationForOrgId: String? = null,
    val localMigrationSummary: LocalMigrationSummary = LocalMigrationSummary(),
    val codeCheckState: OrgCodeCheckState = OrgCodeCheckState.Idle,
    val joinLookupState: JoinOrgLookupState = JoinOrgLookupState.Idle,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class OrgSessionViewModel(
    private val currentUserId: String,
    private val orgRepo: OrgCloudRepository,
    private val localRepo: ReportCardRepository,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val offlinePrefs: OfflinePrefsStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        OrgSessionUiState(
            isOnline = connectivityMonitor.isCurrentlyOnline(),
            selectedOrgId = offlinePrefs.getLastSelectedOrgId(currentUserId)
        )
    )
    val state: StateFlow<OrgSessionUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var codeCheckJob: Job? = null
    private var wasOffline = !connectivityMonitor.isCurrentlyOnline()

    init {
        observeConnectivity()
        observeUserAccount()
        bootstrapAndSyncAccount()
        inspectLocalPhoneData()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online ->
                _state.update { it.copy(isOnline = online) }
                if (online && wasOffline) {
                    wasOffline = false
                    refreshMemberships(silent = true)
                } else if (!online) {
                    wasOffline = true
                }
            }
        }
    }

    private fun observeUserAccount() {
        viewModelScope.launch {
            orgRepo.observeUserAccount(currentUserId)
                .catch { e ->
                    val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                    if (cause != null) {
                        handleFirestoreError(cause, OperationType.GET, "users/$currentUserId")
                        _state.update {
                            it.copy(
                                isLoadingAccount = false,
                                errorMessage = if (it.isOnline) {
                                    friendlyFirestoreErrorMessage(cause, "Could not load account.")
                                } else null
                            )
                        }
                        return@catch
                    }
                    if (e is CancellationException) throw e
                    _state.update {
                        it.copy(
                            isLoadingAccount = false,
                            errorMessage = if (it.isOnline) e.localizedMessage ?: "Could not load account." else null
                        )
                    }
                }
                .collect { account ->
                    _state.update { prev ->
                        val currentSelected = prev.selectedOrgId
                        val hasMemberships = account.memberships.isNotEmpty()
                        val stillMember = currentSelected != null && (
                            account.memberships.any { it.orgId == currentSelected } ||
                                (!prev.isOnline && !hasMemberships)
                            )
                        val nextSelected = if (stillMember) currentSelected else null
                        if (currentSelected != null && nextSelected == null && prev.isOnline && hasMemberships) {
                            offlinePrefs.setLastSelectedOrgId(currentUserId, null)
                        }
                        prev.copy(
                            isLoadingAccount = false,
                            userAccount = account,
                            selectedOrgId = nextSelected
                        )
                    }
                }
        }
    }

    private fun bootstrapAndSyncAccount() {
        viewModelScope.launch {
            if (!connectivityMonitor.isCurrentlyOnline()) {
                // Offline launch: rely cleanly on Firestore local cache + saved lastSelectedOrgId
                val cached = orgRepo.ensureUserDocumentExists().getOrNull()
                _state.update { prev ->
                    prev.copy(
                        isLoadingAccount = false,
                        isSyncingMemberships = false,
                        userAccount = cached ?: prev.userAccount
                    )
                }
                return@launch
            }
            _state.update { it.copy(isSyncingMemberships = true) }
            val result = orgRepo.syncUserMembershipsAndPendingStatus()
            result.onSuccess { synced ->
                _state.update {
                    it.copy(
                        isLoadingAccount = false,
                        isSyncingMemberships = false,
                        userAccount = synced
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isLoadingAccount = false,
                        isSyncingMemberships = false,
                        errorMessage = if (it.isOnline) friendlyFirestoreErrorMessage(err, "Unable to sync organisations.") else null
                    )
                }
            }
        }
    }

    fun refreshMemberships(silent: Boolean = false) {
        if (!connectivityMonitor.isCurrentlyOnline()) {
            if (!silent) {
                _snackbar.tryEmit("Offline — showing organisations saved on this phone")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSyncingMemberships = true, errorMessage = null) }
            val result = orgRepo.syncUserMembershipsAndPendingStatus()
            result.onSuccess { synced ->
                _state.update { it.copy(isSyncingMemberships = false, userAccount = synced) }
                if (!silent) _snackbar.tryEmit("Refreshed organisation status")
            }.onFailure { err ->
                _state.update { it.copy(isSyncingMemberships = false) }
                if (!silent) _snackbar.tryEmit(friendlyFirestoreErrorMessage(err, "Refresh failed"))
            }
        }
    }

    private fun inspectLocalPhoneData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = localRepo.getFullBackupSnapshot()
                val validMarks = snapshot.marks.count {
                    it.componentKey in MarkComponent.validKeys && it.rawValue.isNotBlank()
                }
                val hasData = snapshot.schools.isNotEmpty() || snapshot.students.isNotEmpty()
                _state.update {
                    it.copy(
                        localMigrationSummary = LocalMigrationSummary(
                            schoolCount = snapshot.schools.size,
                            studentCount = snapshot.students.size,
                            markCount = validMarks,
                            hasDataToUpload = hasData
                        )
                    )
                }
            } catch (_: Exception) {
                // Local Room DB empty or unavailable; no migration needed
            }
        }
    }

    fun selectOrganisation(orgId: String) {
        offlinePrefs.setLastSelectedOrgId(currentUserId, orgId)
        _state.update { it.copy(selectedOrgId = orgId, errorMessage = null) }
    }

    fun exitOrganisationToPicker(notice: String? = null) {
        offlinePrefs.setLastSelectedOrgId(currentUserId, null)
        _state.update { it.copy(selectedOrgId = null) }
        if (!notice.isNullOrBlank()) {
            _snackbar.tryEmit(notice)
        }
        refreshMemberships(silent = true)
    }

    fun dismissLocalMigrationPrompt() {
        _state.update { it.copy(promptLocalMigrationForOrgId = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun checkOrgCodeAvailability(rawCode: String) {
        codeCheckJob?.cancel()
        if (rawCode.isBlank()) {
            _state.update { it.copy(codeCheckState = OrgCodeCheckState.Idle) }
            return
        }
        val validation = OrgAccessEvaluator.validateOrgCode(rawCode)
        if (validation is OrgCodeValidation.Invalid) {
            _state.update { it.copy(codeCheckState = OrgCodeCheckState.Invalid(validation.reason)) }
            return
        }
        val normalized = (validation as OrgCodeValidation.Valid).normalized
        _state.update { it.copy(codeCheckState = OrgCodeCheckState.Checking(normalized)) }
        codeCheckJob = viewModelScope.launch {
            delay(350)
            val res = orgRepo.lookupOrgCode(normalized)
            res.onSuccess { existing ->
                _state.update {
                    it.copy(
                        codeCheckState = if (existing == null) {
                            OrgCodeCheckState.Available(normalized)
                        } else {
                            OrgCodeCheckState.Taken(normalized)
                        }
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        codeCheckState = OrgCodeCheckState.Error(
                            friendlyFirestoreErrorMessage(err, "Could not verify code availability.")
                        )
                    )
                }
            }
        }
    }

    fun resetOrgCodeCheck() {
        codeCheckJob?.cancel()
        _state.update { it.copy(codeCheckState = OrgCodeCheckState.Idle) }
    }

    fun createOrganisation(
        orgName: String,
        rawCode: String,
        contactMobile: String,
        contactEmail: String,
        firstSchoolName: String,
        firstSchoolAddress: String,
        firstSchoolMobile: String,
        firstSchoolEmail: String,
        firstSchoolLogoBase64: String,
        onCreated: () -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = orgRepo.createOrganisation(
                orgName = orgName,
                rawCode = rawCode,
                contactMobile = contactMobile,
                contactEmail = contactEmail,
                firstSchoolName = firstSchoolName,
                firstSchoolAddress = firstSchoolAddress,
                firstSchoolMobile = firstSchoolMobile,
                firstSchoolEmail = firstSchoolEmail,
                firstSchoolLogoBase64 = firstSchoolLogoBase64
            )
            result.onSuccess { membership ->
                offlinePrefs.setLastSelectedOrgId(currentUserId, membership.orgId)
                val shouldPromptUpload = _state.value.localMigrationSummary.hasDataToUpload
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        selectedOrgId = membership.orgId,
                        promptLocalMigrationForOrgId = if (shouldPromptUpload) membership.orgId else null,
                        codeCheckState = OrgCodeCheckState.Idle
                    )
                }
                _snackbar.tryEmit("Created '${membership.orgName}' (${membership.orgCode})")
                onCreated()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = friendlyFirestoreErrorMessage(err, "Could not create organisation.")
                    )
                }
            }
        }
    }

    fun lookupOrgCodeForJoin(rawCode: String) {
        val validation = OrgAccessEvaluator.validateOrgCode(rawCode)
        if (validation is OrgCodeValidation.Invalid) {
            _state.update { it.copy(joinLookupState = JoinOrgLookupState.Invalid(validation.reason)) }
            return
        }
        val code = (validation as OrgCodeValidation.Valid).normalized
        viewModelScope.launch {
            _state.update { it.copy(joinLookupState = JoinOrgLookupState.Searching(code), errorMessage = null) }
            val result = orgRepo.lookupOrgCode(code)
            result.onSuccess { found ->
                _state.update {
                    it.copy(
                        joinLookupState = if (found != null) {
                            JoinOrgLookupState.Found(found)
                        } else {
                            JoinOrgLookupState.NotFound(code)
                        }
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        joinLookupState = JoinOrgLookupState.Error(
                            friendlyFirestoreErrorMessage(err, "Could not look up organisation code.")
                        )
                    )
                }
            }
        }
    }

    fun resetJoinLookup() {
        _state.update { it.copy(joinLookupState = JoinOrgLookupState.Idle, errorMessage = null) }
    }

    fun submitJoinRequest(
        orgLookup: CloudOrgCodeLookup,
        displayName: String,
        message: String,
        requestedRole: OrgRole,
        onSubmitted: () -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = orgRepo.submitJoinRequest(orgLookup, displayName, message, requestedRole)
            result.onSuccess {
                _state.update { it.copy(isSubmitting = false, joinLookupState = JoinOrgLookupState.Idle) }
                _snackbar.tryEmit("Join request sent to ${orgLookup.name}")
                onSubmitted()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = friendlyFirestoreErrorMessage(err, "Could not send join request.")
                    )
                }
            }
        }
    }

    fun cancelJoinRequest(orgId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = orgRepo.cancelJoinRequest(orgId)
            result.onSuccess {
                _state.update { it.copy(isSubmitting = false) }
                _snackbar.tryEmit("Join request cancelled")
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = friendlyFirestoreErrorMessage(err, "Could not cancel request.")
                    )
                }
            }
        }
    }

    fun redeemInviteCode(rawOrgCode: String, rawInviteCode: String, onJoined: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = orgRepo.redeemInviteCode(rawOrgCode, rawInviteCode)
            result.onSuccess { membership ->
                offlinePrefs.setLastSelectedOrgId(currentUserId, membership.orgId)
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        selectedOrgId = membership.orgId,
                        joinLookupState = JoinOrgLookupState.Idle
                    )
                }
                _snackbar.tryEmit("Joined '${membership.orgName}' as ${OrgRole.fromRaw(membership.role).label}")
                onJoined()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = friendlyFirestoreErrorMessage(err, "Could not redeem invite code.")
                    )
                }
            }
        }
    }

    fun deleteOwnAccount(
        reauthenticate: suspend () -> Result<Unit>,
        onResult: (ok: Boolean, error: String?) -> Unit
    ) {
        val memberships = _state.value.userAccount?.memberships.orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            // 1. Re-authenticate FIRST so we never delete Firestore docs if verification fails
            val reauthRes = reauthenticate()
            if (reauthRes.isFailure) {
                val msg = reauthRes.exceptionOrNull()?.localizedMessage
                    ?: "Re-authentication failed. Please try again."
                _state.update { it.copy(isSubmitting = false) }
                onResult(false, msg)
                return@launch
            }
            // 2. Delete memberships, private user profile, and Firebase Auth account
            val res = withContext(Dispatchers.IO) {
                orgRepo.deleteOwnAccount(memberships)
            }
            res.onSuccess {
                _state.update { it.copy(isSubmitting = false, selectedOrgId = null) }
                onResult(true, null)
            }.onFailure { err ->
                val msg = friendlyFirestoreErrorMessage(err, "Could not delete account.")
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = msg
                    )
                }
                onResult(false, msg)
            }
        }
    }

    companion object {
        fun provideFactory(
            currentUserId: String,
            orgRepo: OrgCloudRepository,
            localRepo: ReportCardRepository,
            connectivityMonitor: NetworkConnectivityMonitor,
            offlinePrefs: OfflinePrefsStore
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OrgSessionViewModel(
                    currentUserId = currentUserId,
                    orgRepo = orgRepo,
                    localRepo = localRepo,
                    connectivityMonitor = connectivityMonitor,
                    offlinePrefs = offlinePrefs
                )
            }
        }
    }
}
