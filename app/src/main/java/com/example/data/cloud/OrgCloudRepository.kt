package com.example.data.cloud

import com.example.domain.AuthorDisplayHelper
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.DefaultSubjects
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgCodeValidation
import com.example.domain.OrgRole
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date
import java.util.UUID

data class MemberSnapshotWithMeta(
    val member: CloudMember?,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean
)

class OrgCloudRepository(
    private val dbProvider: () -> FirebaseFirestore,
    private val isOnlineProvider: () -> Boolean = { true },
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(
        db: FirebaseFirestore,
        isOnlineProvider: () -> Boolean = { true },
        auth: FirebaseAuth = Firebase.auth
    ) : this({ db }, isOnlineProvider, auth)

    val db: FirebaseFirestore get() = dbProvider()

    fun isCurrentlyOnline(): Boolean = isOnlineProvider()

    private fun requireOnline(actionLabel: String) {
        if (!isOnlineProvider()) {
            throw IllegalStateException("Internet connection is required to $actionLabel. Please reconnect and try again.")
        }
    }

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")

    fun currentEditorName(): String = currentUserName()

    fun currentUserName(): String =
        auth.currentUser?.displayName?.trim()?.ifEmpty { null }
            ?: auth.currentUser?.email?.substringBefore("@")?.ifEmpty { null }
            ?: "User"

    private fun currentUserEmail(): String =
        auth.currentUser?.email.orEmpty()

    // =========================================================================
    // 1. USER PROFILE & MEMBERSHIPS (users/{uid})
    // =========================================================================
    suspend fun ensureUserDocumentExists(): Result<CloudUserAccount> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val ref = db.collection("users").document(uid)
            val snap = if (isOnlineProvider()) {
                ref.get().await()
            } else {
                try {
                    ref.get(Source.CACHE).await()
                } catch (_: Exception) {
                    return Result.success(
                        CloudUserAccount(id = uid, uid = uid, name = currentUserName(), email = currentUserEmail())
                    )
                }
            }
            if (!snap.exists()) {
                val payload = mapOf(
                    "uid" to uid,
                    "name" to currentUserName(),
                    "email" to currentUserEmail(),
                    "memberships" to emptyList<Map<String, Any>>(),
                    "pendingRequests" to emptyList<Map<String, Any>>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
                val created = ref.get().await()
                Result.success(parseUserAccount(created, uid))
            } else {
                Result.success(parseUserAccount(snap, uid))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserAccount(uid: String): Flow<CloudUserAccount> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snap -> parseUserAccount(snap, uid) }

    private fun parseUserAccount(snap: DocumentSnapshot, uid: String): CloudUserAccount {
        if (!snap.exists()) {
            return CloudUserAccount(
                id = uid,
                uid = uid,
                name = currentUserName(),
                email = currentUserEmail()
            )
        }
        val rawMemberships = (snap.get("memberships") as? List<*>)
            ?.mapNotNull { CloudMembershipEntry.fromMap(it as? Map<*, *>) }
            ?: emptyList()
        val rawPending = (snap.get("pendingRequests") as? List<*>)
            ?.mapNotNull { CloudPendingRequestRef.fromMap(it as? Map<*, *>) }
            ?: emptyList()
        return CloudUserAccount(
            id = snap.id,
            uid = snap.getString("uid").orEmpty().ifBlank { uid },
            name = snap.getString("name").orEmpty().ifBlank { currentUserName() },
            email = snap.getString("email").orEmpty().ifBlank { currentUserEmail() },
            memberships = rawMemberships,
            pendingRequests = rawPending,
            createdAt = snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = snap.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    // =========================================================================
    // 2. ORGANISATION CODE AVAILABILITY & EXACT LOOKUP (orgCodes/{code})
    // =========================================================================
    suspend fun lookupOrgCode(rawCode: String): Result<CloudOrgCodeLookup?> {
        val validation = OrgAccessEvaluator.validateOrgCode(rawCode)
        if (validation is OrgCodeValidation.Invalid) {
            return Result.failure(IllegalArgumentException(validation.reason))
        }
        val code = (validation as OrgCodeValidation.Valid).normalized
        val path = "orgCodes/$code"
        return try {
            requireUid()
            requireOnline("check or look up an Organisation Code")
            val snap = db.collection("orgCodes").document(code).get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    CloudOrgCodeLookup(
                        code = snap.id,
                        orgId = snap.getString("orgId").orEmpty(),
                        name = snap.getString("name").orEmpty(),
                        ownerUid = snap.getString("ownerUid").orEmpty(),
                        createdAt = snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. CREATE ORGANISATION (Atomic Transaction: org + code + first school + OWNER)
    // =========================================================================
    suspend fun createOrganisation(
        orgName: String,
        rawCode: String,
        contactMobile: String,
        contactEmail: String,
        firstSchoolName: String,
        firstSchoolAddress: String,
        firstSchoolMobile: String,
        firstSchoolEmail: String,
        firstSchoolLogoBase64: String
    ): Result<CloudMembershipEntry> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Please sign in first."))
        val cleanOrgName = orgName.trim()
        val cleanSchoolName = firstSchoolName.trim()
        if (cleanOrgName.length < 2) return Result.failure(IllegalArgumentException("Organisation name must be at least 2 characters."))
        if (cleanSchoolName.isEmpty()) return Result.failure(IllegalArgumentException("First school name is required."))
        val codeVal = OrgAccessEvaluator.validateOrgCode(rawCode)
        if (codeVal is OrgCodeValidation.Invalid) return Result.failure(IllegalArgumentException(codeVal.reason))
        val code = (codeVal as OrgCodeValidation.Valid).normalized

        val orgId = "org_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val firstSchoolId = "sch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "organisations/$orgId"

        return try {
            requireOnline("create an organisation and reserve its code")
            ensureUserDocumentExists()

            val orgCodeRef = db.collection("orgCodes").document(code)
            val orgRef = db.collection("organisations").document(orgId)
            val memberRef = orgRef.collection("members").document(uid)
            val schoolRef = orgRef.collection("schools").document(firstSchoolId)
            val userRef = db.collection("users").document(uid)
            val auditRef = orgRef.collection("auditLog").document("audit_org_created")

            val (defaultGroups, defaultClasses) = buildDefaultTemplateStructures()
            val ownerEntry = CloudMembershipEntry(
                orgId = orgId,
                orgName = cleanOrgName,
                orgCode = code,
                role = OrgRole.OWNER.name,
                status = MemberStatus.ACTIVE.wireValue
            )

            db.runTransaction { tx ->
                val existingCode = tx.get(orgCodeRef)
                if (existingCode.exists()) {
                    throw IllegalStateException("Organisation code '$code' is already taken.")
                }
                val userSnap = tx.get(userRef)
                val existingMemberships = (userSnap.get("memberships") as? List<*>)
                    ?.mapNotNull { CloudMembershipEntry.fromMap(it as? Map<*, *>) }
                    .orEmpty()
                    .filterNot { it.orgId == orgId }
                val updatedMemberships = (existingMemberships + ownerEntry).map { it.toMap() }

                tx.set(
                    orgCodeRef,
                    mapOf(
                        "orgId" to orgId,
                        "name" to cleanOrgName,
                        "ownerUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    orgRef,
                    mapOf(
                        "name" to cleanOrgName,
                        "code" to code,
                        "ownerUid" to uid,
                        "contactMobile" to contactMobile.trim().take(40),
                        "contactEmail" to contactEmail.trim().take(160),
                        "settings" to CloudOrgSettings().toMap(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    memberRef,
                    mapOf(
                        "uid" to uid,
                        "name" to currentUserName().take(120),
                        "email" to currentUserEmail().take(160),
                        "mobile" to contactMobile.trim().take(40),
                        "role" to OrgRole.OWNER.name,
                        "status" to MemberStatus.ACTIVE.wireValue,
                        "allSchools" to true,
                        "schools" to emptyList<String>(),
                        "permissions" to MemberPermissions.presetFor(OrgRole.OWNER).toFirestoreMap(),
                        "teacherProfileCompleted" to true,
                        "teacherPhotoBase64" to "",
                        "requestedSubjects" to emptyList<String>(),
                        "requestedClasses" to emptyList<String>(),
                        "assignedClassSubjects" to emptyList<String>(),
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    schoolRef,
                    mapOf(
                        "schoolKey" to firstSchoolId,
                        "name" to cleanSchoolName.take(120),
                        "address" to firstSchoolAddress.trim().take(300),
                        "mobile" to firstSchoolMobile.trim().take(40),
                        "email" to firstSchoolEmail.trim().take(160),
                        "logoBase64" to firstSchoolLogoBase64.take(215_000),
                        "active" to true,
                        "classGroups" to defaultGroups.map { it.toMap() },
                        "classes" to defaultClasses.map { it.toMap() },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    userRef,
                    mapOf(
                        "memberships" to updatedMemberships,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    auditRef,
                    mapOf(
                        "actorUid" to uid,
                        "actorName" to currentUserName().take(120),
                        "action" to "ORG_CREATED",
                        "targetType" to "organisation",
                        "targetId" to orgId,
                        "summary" to "Created organisation '$cleanOrgName' ($code) with first school '$cleanSchoolName'",
                        "schoolId" to firstSchoolId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(ownerEntry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. JOIN REQUESTS & ONE-TIME INVITE CODES
    // =========================================================================
    suspend fun submitJoinRequest(
        orgLookup: CloudOrgCodeLookup,
        displayName: String,
        message: String,
        requestedRole: OrgRole
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeRole = if (requestedRole in OrgRole.joinRequestRoles) requestedRole else OrgRole.TEACHER
        val reqPath = "organisations/${orgLookup.orgId}/joinRequests/$uid"
        return try {
            requireOnline("send a join request")
            ensureUserDocumentExists()
            val reqRef = db.collection("organisations").document(orgLookup.orgId)
                .collection("joinRequests").document(uid)
            val userRef = db.collection("users").document(uid)

            val pendingEntry = CloudPendingRequestRef(
                orgId = orgLookup.orgId,
                orgName = orgLookup.name,
                orgCode = orgLookup.code,
                requestedRole = safeRole.name,
                status = "pending"
            )

            val existingReq = reqRef.get().await()
            val batch = db.batch()
            if (existingReq.exists()) {
                batch.update(
                    reqRef,
                    mapOf(
                        "name" to displayName.trim().ifBlank { currentUserName() }.take(120),
                        "message" to message.trim().take(400),
                        "requestedRole" to safeRole.name,
                        "status" to "pending",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    reqRef,
                    mapOf(
                        "uid" to uid,
                        "name" to displayName.trim().ifBlank { currentUserName() }.take(120),
                        "email" to currentUserEmail().take(160),
                        "message" to message.trim().take(400),
                        "requestedRole" to safeRole.name,
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val userSnap = userRef.get().await()
            val existingPending = (userSnap.get("pendingRequests") as? List<*>)
                ?.mapNotNull { CloudPendingRequestRef.fromMap(it as? Map<*, *>) }
                .orEmpty()
                .filterNot { it.orgId == orgLookup.orgId }
            batch.update(
                userRef,
                mapOf(
                    "pendingRequests" to (existingPending + pendingEntry).map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reqPath)
            Result.failure(e)
        }
    }

    suspend fun cancelJoinRequest(orgId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val reqPath = "organisations/$orgId/joinRequests/$uid"
        return try {
            requireOnline("cancel a join request")
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val updatedPending = (userSnap.get("pendingRequests") as? List<*>)
                ?.mapNotNull { CloudPendingRequestRef.fromMap(it as? Map<*, *>) }
                .orEmpty()
                .filterNot { it.orgId == orgId }

            val batch = db.batch()
            batch.delete(db.collection("organisations").document(orgId).collection("joinRequests").document(uid))
            batch.update(
                userRef,
                mapOf(
                    "pendingRequests" to updatedPending.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, reqPath)
            Result.failure(e)
        }
    }

    /**
     * Checks all pending requests and existing memberships against server member documents so:
     * - Approved join requests immediately promote into `users/{uid}.memberships`.
     * - Rejected requests show their updated status.
     * - Removed members immediately lose access from their membership list.
     */
    suspend fun syncUserMembershipsAndPendingStatus(): Result<CloudUserAccount> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val account = ensureUserDocumentExists().getOrThrow()
            if (!isOnlineProvider()) {
                return Result.success(account)
            }
            var changed = false
            val newMemberships = mutableListOf<CloudMembershipEntry>()
            val newPending = mutableListOf<CloudPendingRequestRef>()

            // 1. Verify existing memberships (detect role changes or removal)
            for (entry in account.memberships) {
                val mSnap = try {
                    db.collection("organisations").document(entry.orgId)
                        .collection("members").document(uid).get().await()
                } catch (_: Exception) {
                    null
                }
                if (mSnap == null || !mSnap.exists()) {
                    changed = true
                    continue
                }
                val status = mSnap.getString("status").orEmpty()
                if (status != MemberStatus.ACTIVE.wireValue) {
                    changed = true
                    continue
                }
                val liveRole = mSnap.getString("role").orEmpty().ifBlank { entry.role }
                if (liveRole != entry.role) changed = true
                newMemberships.add(entry.copy(role = liveRole, status = MemberStatus.ACTIVE.wireValue))
            }

            // 2. Check pending join requests for approval/rejection
            for (req in account.pendingRequests) {
                val mSnap = try {
                    db.collection("organisations").document(req.orgId)
                        .collection("members").document(uid).get().await()
                } catch (_: Exception) {
                    null
                }
                if (mSnap != null && mSnap.exists() && mSnap.getString("status") == MemberStatus.ACTIVE.wireValue) {
                    val approvedRole = mSnap.getString("role").orEmpty().ifBlank { req.requestedRole }
                    if (newMemberships.none { it.orgId == req.orgId }) {
                        newMemberships.add(
                            CloudMembershipEntry(
                                orgId = req.orgId,
                                orgName = req.orgName,
                                orgCode = req.orgCode,
                                role = approvedRole,
                                status = MemberStatus.ACTIVE.wireValue
                            )
                        )
                    }
                    changed = true
                    continue
                }
                val reqSnap = try {
                    db.collection("organisations").document(req.orgId)
                        .collection("joinRequests").document(uid).get().await()
                } catch (_: Exception) {
                    null
                }
                if (reqSnap == null || !reqSnap.exists()) {
                    changed = true
                    continue
                }
                val liveStatus = reqSnap.getString("status").orEmpty().ifBlank { "pending" }
                if (liveStatus != req.status) changed = true
                newPending.add(req.copy(status = liveStatus))
            }

            if (changed) {
                db.collection("users").document(uid).update(
                    mapOf(
                        "memberships" to newMemberships.map { it.toMap() },
                        "pendingRequests" to newPending.map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(account.copy(memberships = newMemberships, pendingRequests = newPending))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createInviteCode(
        orgId: String,
        orgName: String,
        presetRole: OrgRole,
        allSchools: Boolean,
        schoolIds: List<String>,
        permissions: MemberPermissions,
        expiresInHours: Int
    ): Result<CloudInviteCode> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeRole = if (presetRole == OrgRole.OWNER) OrgRole.ADMIN else presetRole
        val code = generateShortInviteCode()
        val path = "organisations/$orgId/inviteCodes/$code"
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + expiresInHours.coerceIn(1, 720) * 3600_000L))
        return try {
            requireOnline("create an invite code")
            val payload = mapOf(
                "code" to code,
                "orgId" to orgId,
                "orgName" to orgName.take(120),
                "presetRole" to safeRole.name,
                "allSchools" to (allSchools || safeRole == OrgRole.ADMIN),
                "schools" to if (allSchools || safeRole == OrgRole.ADMIN) emptyList() else schoolIds.take(50),
                "permissions" to permissions.toFirestoreMap(),
                "createdByUid" to uid,
                "expiresAt" to expiresAt,
                "usedByUid" to "",
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("organisations").document(orgId)
                .collection("inviteCodes").document(code)
                .set(payload).await()
            logAudit(
                orgId = orgId,
                action = "INVITE_CREATED",
                targetType = "invite",
                targetId = code,
                summary = "Created one-time ${safeRole.label} invite code $code (expires in ${expiresInHours}h)"
            )
            Result.success(
                CloudInviteCode(
                    id = code,
                    code = code,
                    orgId = orgId,
                    orgName = orgName,
                    presetRole = safeRole.name,
                    allSchools = allSchools || safeRole == OrgRole.ADMIN,
                    schools = if (allSchools || safeRole == OrgRole.ADMIN) emptyList() else schoolIds,
                    permissions = permissions.toFirestoreMap(),
                    createdByUid = uid,
                    expiresAt = expiresAt,
                    usedByUid = ""
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteInviteCode(orgId: String, inviteCode: String): Result<Unit> {
        val path = "organisations/$orgId/inviteCodes/$inviteCode"
        return try {
            requireUid()
            requireOnline("revoke an invite code")
            db.collection("organisations").document(orgId)
                .collection("inviteCodes").document(inviteCode)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun redeemInviteCode(rawOrgCode: String, rawInviteCode: String): Result<CloudMembershipEntry> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val lookup = lookupOrgCode(rawOrgCode).getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalArgumentException("Organisation code not found."))
        val inviteCode = rawInviteCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (inviteCode.length < 6) {
            return Result.failure(IllegalArgumentException("Please enter a valid invite code."))
        }
        val path = "organisations/${lookup.orgId}/inviteCodes/$inviteCode"
        return try {
            requireOnline("redeem an invite code")
            ensureUserDocumentExists()
            val inviteRef = db.collection("organisations").document(lookup.orgId)
                .collection("inviteCodes").document(inviteCode)
            val memberRef = db.collection("organisations").document(lookup.orgId)
                .collection("members").document(uid)
            val userRef = db.collection("users").document(uid)

            val entry = db.runTransaction { tx ->
                val invSnap = tx.get(inviteRef)
                if (!invSnap.exists()) throw IllegalArgumentException("Invite code not found.")
                val usedBy = invSnap.getString("usedByUid").orEmpty()
                if (usedBy.isNotBlank()) throw IllegalStateException("This one-time invite code has already been used.")
                val exp = invSnap.getTimestamp("expiresAt")
                if (exp == null || exp.toDate().time <= System.currentTimeMillis()) {
                    throw IllegalStateException("This invite code has expired.")
                }

                val presetRole = invSnap.getString("presetRole").orEmpty().ifBlank { OrgRole.TEACHER.name }
                val allSchools = invSnap.getBoolean("allSchools") ?: false
                val schools = (invSnap.get("schools") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val rawPerms = invSnap.get("permissions") as? Map<*, *>
                val perms = MemberPermissions.fromMap(rawPerms).toFirestoreMap()

                val userSnap = tx.get(userRef)
                val existingMemberships = (userSnap.get("memberships") as? List<*>)
                    ?.mapNotNull { CloudMembershipEntry.fromMap(it as? Map<*, *>) }
                    .orEmpty()
                    .filterNot { it.orgId == lookup.orgId }
                val membershipEntry = CloudMembershipEntry(
                    orgId = lookup.orgId,
                    orgName = lookup.name,
                    orgCode = lookup.code,
                    role = presetRole,
                    status = MemberStatus.ACTIVE.wireValue
                )

                tx.update(
                    inviteRef,
                    mapOf(
                        "usedByUid" to uid,
                        "usedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    memberRef,
                    mapOf(
                        "uid" to uid,
                        "name" to currentUserName().take(120),
                        "email" to currentUserEmail().take(160),
                        "mobile" to "",
                        "role" to presetRole,
                        "status" to MemberStatus.ACTIVE.wireValue,
                        "allSchools" to allSchools,
                        "schools" to schools,
                        "permissions" to perms,
                        "teacherProfileCompleted" to (presetRole != OrgRole.TEACHER.name),
                        "teacherPhotoBase64" to "",
                        "requestedSubjects" to emptyList<String>(),
                        "requestedClasses" to emptyList<String>(),
                        "assignedClassSubjects" to emptyList<String>(),
                        "inviteCodeUsed" to inviteCode,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    userRef,
                    mapOf(
                        "memberships" to (existingMemberships + membershipEntry).map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                membershipEntry
            }.await()

            logAudit(
                orgId = lookup.orgId,
                action = "INVITE_REDEEMED",
                targetType = "member",
                targetId = uid,
                summary = "${currentUserName()} joined via one-time invite code $inviteCode as ${OrgRole.fromRaw(entry.role).label}"
            )
            Result.success(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 5. ORGANISATION, MEMBERS, REQUESTS, INVITES & AUDIT OBSERVATION
    // =========================================================================
    fun observeOrganisation(orgId: String): Flow<CloudOrganisation?> =
        db.collection("organisations").document(orgId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else CloudOrganisation(
                    id = snap.id,
                    name = snap.getString("name").orEmpty(),
                    code = snap.getString("code").orEmpty(),
                    ownerUid = snap.getString("ownerUid").orEmpty(),
                    contactMobile = snap.getString("contactMobile").orEmpty(),
                    contactEmail = snap.getString("contactEmail").orEmpty(),
                    settings = CloudOrgSettings.fromMap(snap.get("settings") as? Map<*, *>),
                    createdAt = snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                    updatedAt = snap.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                )
            }

    fun observeOwnMember(orgId: String, uid: String): Flow<CloudMember?> =
        observeOwnMemberWithMeta(orgId, uid).map { it.member }

    fun observeOwnMemberWithMeta(orgId: String, uid: String): Flow<MemberSnapshotWithMeta> =
        db.collection("organisations").document(orgId)
            .collection("members").document(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                MemberSnapshotWithMeta(
                    member = if (snap.exists()) parseMember(snap) else null,
                    isFromCache = snap.metadata.isFromCache(),
                    hasPendingWrites = snap.metadata.hasPendingWrites()
                )
            }

    fun observeOrgMembers(orgId: String): Flow<List<CloudMember>> =
        db.collection("organisations").document(orgId)
            .collection("members")
            .snapshots()
            .map { snap -> snap.documents.map { parseMember(it) }.sortedBy { it.name.lowercase() } }

    fun observeJoinRequests(orgId: String): Flow<List<CloudJoinRequest>> =
        db.collection("organisations").document(orgId)
            .collection("joinRequests")
            .snapshots()
            .map { snap ->
                snap.documents.map { doc ->
                    CloudJoinRequest(
                        id = doc.id,
                        uid = doc.getString("uid").orEmpty().ifBlank { doc.id },
                        name = doc.getString("name").orEmpty(),
                        email = doc.getString("email").orEmpty(),
                        message = doc.getString("message").orEmpty(),
                        requestedRole = doc.getString("requestedRole").orEmpty().ifBlank { OrgRole.TEACHER.name },
                        status = doc.getString("status").orEmpty().ifBlank { "pending" },
                        reviewedByUid = doc.getString("reviewedByUid"),
                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeInviteCodes(orgId: String): Flow<List<CloudInviteCode>> =
        db.collection("organisations").document(orgId)
            .collection("inviteCodes")
            .snapshots()
            .map { snap ->
                snap.documents.map { doc ->
                    CloudInviteCode(
                        id = doc.id,
                        code = doc.getString("code").orEmpty().ifBlank { doc.id },
                        orgId = doc.getString("orgId").orEmpty(),
                        orgName = doc.getString("orgName").orEmpty(),
                        presetRole = doc.getString("presetRole").orEmpty(),
                        allSchools = doc.getBoolean("allSchools") ?: false,
                        schools = (doc.get("schools") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        permissions = MemberPermissions.fromMap(doc.get("permissions") as? Map<*, *>).toFirestoreMap(),
                        createdByUid = doc.getString("createdByUid").orEmpty(),
                        expiresAt = doc.getTimestamp("expiresAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        usedByUid = doc.getString("usedByUid").orEmpty(),
                        usedAt = doc.getTimestamp("usedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }

    fun observeAuditLogs(orgId: String): Flow<List<CloudAuditLog>> =
        db.collection("organisations").document(orgId)
            .collection("auditLog")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(120)
            .snapshots()
            .map { snap ->
                snap.documents.map { doc ->
                    CloudAuditLog(
                        id = doc.id,
                        actorUid = doc.getString("actorUid").orEmpty(),
                        actorName = doc.getString("actorName").orEmpty(),
                        action = doc.getString("action").orEmpty(),
                        targetType = doc.getString("targetType").orEmpty(),
                        targetId = doc.getString("targetId").orEmpty(),
                        summary = doc.getString("summary").orEmpty(),
                        schoolId = doc.getString("schoolId"),
                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }
            }

    private fun parseMember(doc: DocumentSnapshot): CloudMember = CloudMember(
        id = doc.id,
        uid = doc.getString("uid").orEmpty().ifBlank { doc.id },
        name = doc.getString("name").orEmpty(),
        email = doc.getString("email").orEmpty(),
        mobile = doc.getString("mobile").orEmpty(),
        role = doc.getString("role").orEmpty().ifBlank { OrgRole.OTHER.name },
        status = doc.getString("status").orEmpty().ifBlank { MemberStatus.ACTIVE.wireValue },
        allSchools = doc.getBoolean("allSchools") ?: false,
        schools = (doc.get("schools") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        permissions = MemberPermissions.fromMap(doc.get("permissions") as? Map<*, *>).toFirestoreMap(),
        teacherProfileCompleted = doc.getBoolean("teacherProfileCompleted") ?: false,
        teacherPhotoBase64 = doc.getString("teacherPhotoBase64").orEmpty(),
        requestedSubjects = (doc.get("requestedSubjects") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        requestedClasses = (doc.get("requestedClasses") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        assignedClassSubjects = (doc.get("assignedClassSubjects") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        inviteCodeUsed = doc.getString("inviteCodeUsed"),
        lastActiveAt = doc.getTimestamp("lastActiveAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    )

    // =========================================================================
    // 6. MEMBER MANAGEMENT, TEACHER PROFILE, OWNERSHIP TRANSFER & DELETION
    // =========================================================================
    suspend fun approveJoinRequest(
        orgId: String,
        request: CloudJoinRequest,
        assignedRole: OrgRole,
        allSchools: Boolean,
        allowedSchoolIds: List<String>,
        permissions: MemberPermissions
    ): Result<Unit> {
        val actorUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeRole = if (assignedRole == OrgRole.OWNER) OrgRole.ADMIN else assignedRole
        val path = "organisations/$orgId/members/${request.uid}"
        return try {
            requireOnline("approve a join request")
            val orgRef = db.collection("organisations").document(orgId)
            val batch = db.batch()
            batch.set(
                orgRef.collection("members").document(request.uid),
                mapOf(
                    "uid" to request.uid,
                    "name" to request.name.ifBlank { "Member" }.take(120),
                    "email" to request.email.take(160),
                    "mobile" to "",
                    "role" to safeRole.name,
                    "status" to MemberStatus.ACTIVE.wireValue,
                    "allSchools" to (allSchools || safeRole == OrgRole.ADMIN),
                    "schools" to if (allSchools || safeRole == OrgRole.ADMIN) emptyList() else allowedSchoolIds.take(50),
                    "permissions" to permissions.toFirestoreMap(),
                    "teacherProfileCompleted" to (safeRole != OrgRole.TEACHER),
                    "teacherPhotoBase64" to "",
                    "requestedSubjects" to emptyList<String>(),
                    "requestedClasses" to emptyList<String>(),
                    "assignedClassSubjects" to emptyList<String>(),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                orgRef.collection("joinRequests").document(request.uid),
                mapOf(
                    "status" to "approved",
                    "reviewedByUid" to actorUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            logAudit(
                orgId = orgId,
                action = "MEMBER_APPROVED",
                targetType = "member",
                targetId = request.uid,
                summary = "Approved ${request.name} as ${safeRole.label}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectJoinRequest(orgId: String, request: CloudJoinRequest): Result<Unit> {
        val actorUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "organisations/$orgId/joinRequests/${request.uid}"
        return try {
            requireOnline("reject a join request")
            db.collection("organisations").document(orgId)
                .collection("joinRequests").document(request.uid)
                .update(
                    mapOf(
                        "status" to "rejected",
                        "reviewedByUid" to actorUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            logAudit(
                orgId = orgId,
                action = "REQUEST_REJECTED",
                targetType = "member",
                targetId = request.uid,
                summary = "Rejected join request from ${request.name}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemberAccess(
        orgId: String,
        targetMember: CloudMember,
        newRole: OrgRole,
        allSchools: Boolean,
        allowedSchoolIds: List<String>,
        permissions: MemberPermissions,
        assignedClassSubjects: List<String> = targetMember.assignedClassSubjects
    ): Result<Unit> {
        val path = "organisations/$orgId/members/${targetMember.uid}"
        return try {
            requireUid()
            requireOnline("update member access")
            val safeRole = if (newRole == OrgRole.OWNER) OrgRole.ADMIN else newRole
            val effectiveAllSchools = allSchools || safeRole == OrgRole.ADMIN
            db.collection("organisations").document(orgId)
                .collection("members").document(targetMember.uid)
                .update(
                    mapOf(
                        "role" to safeRole.name,
                        "allSchools" to effectiveAllSchools,
                        "schools" to if (effectiveAllSchools) emptyList() else allowedSchoolIds.take(50),
                        "permissions" to permissions.toFirestoreMap(),
                        "assignedClassSubjects" to assignedClassSubjects.take(100),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            logAudit(
                orgId = orgId,
                action = "ACCESS_UPDATED",
                targetType = "access",
                targetId = targetMember.uid,
                summary = "Updated ${targetMember.name} to ${safeRole.label} (schools: ${if (effectiveAllSchools) "All" else allowedSchoolIds.size})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeMember(orgId: String, targetMember: CloudMember): Result<Unit> {
        val path = "organisations/$orgId/members/${targetMember.uid}"
        return try {
            requireUid()
            requireOnline("remove a member")
            db.collection("organisations").document(orgId)
                .collection("members").document(targetMember.uid)
                .delete().await()
            logAudit(
                orgId = orgId,
                action = "MEMBER_REMOVED",
                targetType = "member",
                targetId = targetMember.uid,
                summary = "Removed ${targetMember.name} (${targetMember.orgRole.label}) from organisation"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveOrganisation(orgId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "organisations/$orgId/members/$uid"
        return try {
            requireOnline("leave an organisation")
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val updatedMemberships = (userSnap.get("memberships") as? List<*>)
                ?.mapNotNull { CloudMembershipEntry.fromMap(it as? Map<*, *>) }
                .orEmpty()
                .filterNot { it.orgId == orgId }

            val batch = db.batch()
            batch.delete(db.collection("organisations").document(orgId).collection("members").document(uid))
            batch.update(
                userRef,
                mapOf(
                    "memberships" to updatedMemberships.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun transferOwnership(
        org: CloudOrganisation,
        targetMember: CloudMember
    ): Result<Unit> {
        val currentOwnerUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "organisations/${org.id}"
        return try {
            requireOnline("transfer ownership")
            val orgRef = db.collection("organisations").document(org.id)
            val codeRef = db.collection("orgCodes").document(org.code)
            val targetRef = orgRef.collection("members").document(targetMember.uid)
            val selfRef = orgRef.collection("members").document(currentOwnerUid)
            val userRef = db.collection("users").document(currentOwnerUid)

            val userSnap = userRef.get().await()
            val updatedMemberships = (userSnap.get("memberships") as? List<*>)
                ?.mapNotNull { CloudMembershipEntry.fromMap(it as? Map<*, *>) }
                .orEmpty()
                .map { entry ->
                    if (entry.orgId == org.id) entry.copy(role = OrgRole.ADMIN.name) else entry
                }

            val batch = db.batch()
            batch.update(
                orgRef,
                mapOf(
                    "ownerUid" to targetMember.uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(codeRef, mapOf("ownerUid" to targetMember.uid))
            batch.update(
                targetRef,
                mapOf(
                    "role" to OrgRole.OWNER.name,
                    "allSchools" to true,
                    "permissions" to MemberPermissions.presetFor(OrgRole.OWNER).toFirestoreMap(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                selfRef,
                mapOf(
                    "role" to OrgRole.ADMIN.name,
                    "allSchools" to true,
                    "permissions" to MemberPermissions.presetFor(OrgRole.ADMIN).toFirestoreMap(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "memberships" to updatedMemberships.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            logAudit(
                orgId = org.id,
                action = "OWNERSHIP_TRANSFERRED",
                targetType = "organisation",
                targetId = targetMember.uid,
                summary = "Transferred ownership of '${org.name}' to ${targetMember.name}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteOrganisation(org: CloudOrganisation): Result<Unit> {
        val ownerUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "organisations/${org.id}"
        return try {
            requireOnline("delete an organisation")
            val orgRef = db.collection("organisations").document(org.id)
            val codeRef = db.collection("orgCodes").document(org.code)

            // 1. Delete org root & reserve code FIRST while members/{ownerUid} is still OWNER.
            // This flips !exists(orgPath(orgId)) / !existsAfter(orgPath(orgId)) to true so
            // append-only subcollections allow Owner purge without loosening normal immutability.
            val startBatch = db.batch()
            if (org.code.isNotBlank()) {
                startBatch.delete(codeRef)
            }
            startBatch.delete(orgRef)
            startBatch.commit().await()

            // 2. Delete all school subcollections + school docs
            val schoolsSnap = orgRef.collection("schools").get().await()
            for (schoolDoc in schoolsSnap.documents) {
                for (sub in listOf("students", "marks", "markEditLogs", "coScholastic", "termInfo")) {
                    deleteCollectionInChunks(schoolDoc.reference.collection(sub))
                }
                schoolDoc.reference.delete().await()
            }

            // 3. Delete all organisation-level subcollections (Phases 2–14 + Phase 15 deletedUsers)
            val orgSubcollections = listOf(
                "joinRequests",
                "inviteCodes",
                "auditLog",
                "deletedUsers",
                "teachers",
                "periodStructures",
                "classAssignments",
                "subjectRules",
                "timetableCells",
                "timetableLayouts",
                "teacherLeaves",
                "substitutions",
                "timetableUndoSnapshots",
                "teacherScopes",
                "teacherAttendance",
                "schoolCalendars",
                "studentAttendance",
                "studentAttendanceEditLogs",
                "feeStructures",
                "studentFeeOverrides",
                "feeReceipts",
                "feeMessageSentLogs",
                "schoolNotices",
                "feeAuditLogs",
                "schoolCertificates",
                "cashBookSettings",
                "cashBookEntries",
                "cashBookDayClosings",
                "cashBookAuditLogs",
                "enquirySettings",
                "admissionEnquiries",
                "examSchedules",
                "examRooms",
                "examSeatings",
                "examInvigilatorDuties",
                "stockTransactions",
                "transportRoutes",
                "salaryRuleConfigs",
                "staffSalaryProfiles",
                "salaryMonthlyRecords",
                "staffSalaryAdvances",
                "salaryMonthLocks",
                "salaryAuditLogs",
                "schoolClassOrders",
                "sessionTransitions",
                "promotionRecords",
                "bulkImportLogs",
                "backupExportLogs",
                "classDiaryEntries",
                "classDiaryHistory",
                "schoolBirthdayConfigs",
                "birthdayWishes"
            )
            for (sub in orgSubcollections) {
                deleteCollectionInChunks(orgRef.collection(sub))
            }

            // 4. Delete non-owner members first (owner stays active until very end for rules authorization)
            val membersSnap = orgRef.collection("members").get().await()
            for (mDoc in membersSnap.documents) {
                if (mDoc.id != ownerUid) {
                    mDoc.reference.delete().await()
                }
            }

            // 5. Finally remove owner's member doc and remove org from users/{uid}
            val userRef = db.collection("users").document(ownerUid)
            val userSnap = userRef.get().await()
            val finalBatch = db.batch()
            finalBatch.delete(orgRef.collection("members").document(ownerUid))
            if (userSnap.exists()) {
                val updatedMemberships = (userSnap.get("memberships") as? List<*>)
                    ?.mapNotNull { CloudMembershipEntry.fromMap(it as? Map<*, *>) }
                    .orEmpty()
                    .filterNot { it.orgId == org.id }
                finalBatch.update(
                    userRef,
                    mapOf(
                        "memberships" to updatedMemberships.map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            finalBatch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun deleteCollectionInChunks(colRef: com.google.firebase.firestore.CollectionReference) {
        while (true) {
            val snap = colRef.limit(200).get().await()
            if (snap.isEmpty) break
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    suspend fun completeTeacherProfile(
        orgId: String,
        name: String,
        mobile: String,
        photoBase64: String,
        requestedSubjects: List<String>,
        requestedClasses: List<String>,
        assignedSchoolIds: List<String> = emptyList(),
        wasAlreadyCompleted: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "organisations/$orgId/members/$uid"
        val cleanName = name.trim().ifBlank { currentUserName() }.take(120)
        val cleanMobile = mobile.trim().take(40)
        val cleanPhoto = photoBase64.take(215_000)
        val cleanSubjects = requestedSubjects.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        val cleanClasses = requestedClasses.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
        return try {
            db.collection("organisations").document(orgId)
                .collection("members").document(uid)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "mobile" to cleanMobile,
                        "teacherPhotoBase64" to cleanPhoto,
                        "requestedSubjects" to cleanSubjects,
                        "requestedClasses" to cleanClasses,
                        "teacherProfileCompleted" to true,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnFailureListener { e -> handleFirestoreError(e, OperationType.UPDATE, path) }

            // Also sync to Phase 4 teachers/{tchr_member_uid} so approved teachers appear automatically
            val teacherDocId = Phase4Repository.makeMemberTeacherDocId(uid)
            val teacherRef = db.collection("organisations").document(orgId)
                .collection("teachers").document(teacherDocId)
            val fullTeacherPayload = mapOf(
                "name" to cleanName,
                "mobile" to cleanMobile,
                "email" to currentUserEmail().take(160),
                "photoBase64" to cleanPhoto,
                "subjects" to cleanSubjects,
                "schoolIds" to assignedSchoolIds.distinct().take(50),
                "maxPeriodsPerDay" to 7L,
                "maxConsecutivePeriods" to 3L,
                "weeklyOffDays" to emptyList<String>(),
                "active" to true,
                "linkedMemberUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (wasAlreadyCompleted) {
                teacherRef.update(
                    mapOf(
                        "name" to cleanName,
                        "mobile" to cleanMobile,
                        "photoBase64" to cleanPhoto,
                        "subjects" to cleanSubjects,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener {
                    // If Phase 2 teacher completed profile before Phase 4 existed, create the doc now
                    teacherRef.set(fullTeacherPayload)
                        .addOnFailureListener { e2 ->
                            handleFirestoreError(e2, OperationType.CREATE, "organisations/$orgId/teachers/$teacherDocId")
                        }
                }
            } else {
                teacherRef.set(fullTeacherPayload)
                    .addOnFailureListener { e ->
                        handleFirestoreError(e, OperationType.CREATE, "organisations/$orgId/teachers/$teacherDocId")
                    }
            }

            logAuditNonBlocking(
                orgId = orgId,
                action = "TEACHER_PROFILE_UPDATED",
                targetType = "member",
                targetId = uid,
                summary = "Completed teacher profile for $cleanName (${cleanSubjects.size} subject(s), ${cleanClasses.size} class(es))"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun touchLastActive(orgId: String) {
        val uid = auth.currentUser?.uid ?: return
        val path = "organisations/$orgId/members/$uid"
        try {
            db.collection("organisations").document(orgId)
                .collection("members").document(uid)
                .update(
                    mapOf(
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnFailureListener { e -> handleFirestoreError(e, OperationType.UPDATE, path) }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    fun observeDeletedUserUids(orgId: String): Flow<Set<String>> =
        db.collection("organisations").document(orgId)
            .collection("deletedUsers")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }

    suspend fun deleteOwnAccount(
        memberships: List<CloudMembershipEntry>,
        pendingRequests: List<CloudPendingRequestRef> = emptyList(),
        deleteOwnedOrgsConfirmed: Boolean = false
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        val domainMemberships = memberships.map { it.toDomain() }
        val ownedOrgs = OrgAccessEvaluator.ownedActiveOrganisations(domainMemberships)
        if (ownedOrgs.isNotEmpty() && !deleteOwnedOrgsConfirmed) {
            val guardError = OrgAccessEvaluator.canDeleteOwnAccount(domainMemberships)
                ?: "Transfer ownership or choose to delete your owned organisation(s) first."
            return Result.failure(IllegalStateException(guardError))
        }

        return try {
            requireOnline("delete your account")

            // 1. If the owner explicitly confirmed deleting owned organisation(s), remove them completely first
            for (owned in ownedOrgs) {
                val orgSnap = runCatching {
                    db.collection("organisations").document(owned.orgId).get().await()
                }.getOrNull()
                val resolvedCode = owned.orgCode.ifBlank { orgSnap?.getString("code").orEmpty() }
                val resolvedName = owned.orgName.ifBlank { orgSnap?.getString("name").orEmpty() }
                deleteOrganisation(
                    CloudOrganisation(
                        id = owned.orgId,
                        name = resolvedName,
                        code = resolvedCode,
                        ownerUid = uid
                    )
                ).getOrThrow()
            }

            // 2. For normal (non-owner) memberships: preserve school records created by this user,
            //    mark UID in deletedUsers so author renders as "deleted user", log event, and remove membership.
            val ownedOrgIds = ownedOrgs.map { it.orgId }.toSet()
            for (m in memberships.filterNot { it.orgId in ownedOrgIds }) {
                val orgRef = db.collection("organisations").document(m.orgId)
                try {
                    val batch = db.batch()
                    batch.set(
                        orgRef.collection("deletedUsers").document(uid),
                        mapOf(
                            "uid" to uid,
                            "deletedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        orgRef.collection("auditLog").document("audit_del_${uid.take(12)}_${System.currentTimeMillis()}"),
                        mapOf(
                            "actorUid" to uid,
                            "actorName" to AuthorDisplayHelper.DELETED_USER_LABEL,
                            "action" to "ACCOUNT_DELETED",
                            "targetType" to "member",
                            "targetId" to uid,
                            "summary" to "Member deleted their own account; school records retained as ${AuthorDisplayHelper.DELETED_USER_LABEL}",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.delete(orgRef.collection("members").document(uid))
                    batch.delete(orgRef.collection("teacherScopes").document(uid))
                    batch.commit().await()
                } catch (_: Exception) {
                    // Fallback: attempt member removal alone if already inactive/removed
                    try {
                        orgRef.collection("members").document(uid).delete().await()
                    } catch (_: Exception) {
                        // Already removed
                    }
                }
            }

            // 3. Remove any pending join requests belonging to this user
            for (req in pendingRequests) {
                if (req.orgId.isNotBlank() && req.orgId !in ownedOrgIds) {
                    try {
                        db.collection("organisations").document(req.orgId)
                            .collection("joinRequests").document(uid).delete().await()
                    } catch (_: Exception) {
                        // Best-effort cleanup
                    }
                }
            }

            // 4. Delete private users/{uid} doc
            try {
                db.collection("users").document(uid).delete().await()
            } catch (_: Exception) {
                // Ignore if already deleted
            }

            // 5. Delete Firebase Auth account
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    fun logAuditNonBlocking(
        orgId: String,
        action: String,
        targetType: String,
        targetId: String,
        summary: String,
        schoolId: String? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        val entryId = "log_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val path = "organisations/$orgId/auditLog/$entryId"
        try {
            val payload = mutableMapOf<String, Any>(
                "actorUid" to uid,
                "actorName" to currentUserName().take(120),
                "action" to action.take(80),
                "targetType" to targetType,
                "targetId" to targetId.take(128),
                "summary" to summary.take(300),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!schoolId.isNullOrBlank()) {
                payload["schoolId"] = schoolId.take(128)
            }
            db.collection("organisations").document(orgId)
                .collection("auditLog").document(entryId)
                .set(payload)
                .addOnFailureListener { e -> handleFirestoreError(e, OperationType.CREATE, path) }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun logAudit(
        orgId: String,
        action: String,
        targetType: String,
        targetId: String,
        summary: String,
        schoolId: String? = null
    ) {
        logAuditNonBlocking(orgId, action, targetType, targetId, summary, schoolId)
    }

    companion object {
        private val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val SECURE_RANDOM = SecureRandom()

        fun generateShortInviteCode(length: Int = 8): String =
            (1..length).map { INVITE_ALPHABET[SECURE_RANDOM.nextInt(INVITE_ALPHABET.length)] }.joinToString("")

        fun buildDefaultTemplateStructures(): Pair<List<CloudClassGroupItem>, List<CloudSchoolClassItem>> {
            val groups = DefaultSchoolTemplate.groups.mapIndexed { idx, spec ->
                CloudClassGroupItem(
                    id = "grp_${spec.key}",
                    name = spec.name,
                    shortLabel = spec.shortLabel,
                    sortOrder = idx + 1,
                    templateKey = spec.key,
                    subjects = spec.defaultSubjects
                )
            }
            var classOrder = 1
            val classes = DefaultSchoolTemplate.groups.flatMap { spec ->
                spec.defaultClasses.map { clsName ->
                    CloudSchoolClassItem(
                        id = "cls_${StableDocId.sanitizeSegment(clsName).lowercase()}",
                        classGroupId = "grp_${spec.key}",
                        name = clsName,
                        sortOrder = classOrder++
                    )
                }
            }
            return groups to classes
        }
    }
}
