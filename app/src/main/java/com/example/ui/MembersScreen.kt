package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.cloud.CloudAuditLog
import com.example.data.cloud.CloudInviteCode
import com.example.data.cloud.CloudJoinRequest
import com.example.data.cloud.CloudMember
import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.MemberPermissions
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgRole
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MembersScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canEditMembers = uiState.canManageMembers()
    val isAdmin = uiState.isAdmin
    val isOwner = uiState.isOwner

    val tabs = remember(isAdmin) {
        buildList {
            add("Members (${uiState.orgMembers.size})")
            add("Requests (${uiState.pendingJoinRequestCount})")
            add("Invites")
            if (isAdmin) add("Audit Log")
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    if (selectedTab >= tabs.size) selectedTab = 0

    var editingMember by remember { mutableStateOf<CloudMember?>(null) }
    var removingMember by remember { mutableStateOf<CloudMember?>(null) }
    var transferTarget by remember { mutableStateOf<CloudMember?>(null) }
    var approvingRequest by remember { mutableStateOf<CloudJoinRequest?>(null) }
    var showCreateInviteDialog by remember { mutableStateOf(false) }

    val schoolItems = remember(uiState.schools, uiState.schoolDocIdByLongId) {
        uiState.schools.mapNotNull { sch ->
            val docId = uiState.schoolDocIdByLongId[sch.id] ?: return@mapNotNull null
            docId to sch.name
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header + Org Code Copy Bar
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Members & Access",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val orgCode = uiState.activeOrg?.code.orEmpty()
                        Text(
                            text = "Org Code: @$orgCode • ${uiState.orgMembers.size} active member(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val orgCode = uiState.activeOrg?.code.orEmpty()
                    if (orgCode.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Org Code", orgCode))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("copy_org_code_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Org Code", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Code", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label, maxLines = 1) },
                            modifier = Modifier.testTag("members_tab_$idx")
                        )
                    }
                }
            }
        }

        if (!uiState.isOnline) {
            OfflineActionNoticeBanner(
                message = "You are offline. Cached member details are visible, while approvals, invite codes, role changes, and removals require an internet connection.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        when (selectedTab) {
            0 -> {
                // MEMBERS DIRECTORY
                if (uiState.orgMembers.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyStatePanel(
                            icon = Icons.Default.People,
                            title = "No members found",
                            message = "Share your Organisation Code or create a one-time invite code to onboard staff."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.orgMembers, key = { it.uid }) { member ->
                            val isSelf = member.uid == uiState.currentMember?.uid
                            val canEditThis = canEditMembers &&
                                OrgAccessEvaluator.canEditTargetMember(uiState.accessProfile, member.toAccessProfile())
                            val canRemoveThis = isAdmin &&
                                OrgAccessEvaluator.canRemoveMember(
                                    uiState.accessProfile,
                                    member.toAccessProfile(),
                                    uiState.orgMembers.map { it.toAccessProfile() }
                                ) == null
                            val canTransferThis = isOwner &&
                                OrgAccessEvaluator.canTransferOwnership(uiState.accessProfile, member.toAccessProfile()) == null

                            MemberCard(
                                member = member,
                                isSelf = isSelf,
                                schoolNamesByDocId = schoolItems.toMap(),
                                canEdit = canEditThis,
                                canRemove = canRemoveThis,
                                canTransferOwnership = canTransferThis,
                                onEdit = { editingMember = member },
                                onRemove = { removingMember = member },
                                onTransfer = { transferTarget = member }
                            )
                        }
                    }
                }
            }

            1 -> {
                // PENDING & RECENT JOIN REQUESTS
                val pendingOnly = uiState.joinRequests.filter { it.status == "pending" }
                if (pendingOnly.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyStatePanel(
                            icon = Icons.Default.PersonAdd,
                            title = "No pending join requests",
                            message = "When a teacher or staff member requests to join using '@${uiState.activeOrg?.code.orEmpty()}', their request and message will appear here."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pendingOnly, key = { it.uid }) { req ->
                            JoinRequestAdminCard(
                                request = req,
                                canApprove = canEditMembers,
                                onApproveClick = { approvingRequest = req },
                                onRejectClick = { viewModel.rejectJoinRequest(req) }
                            )
                        }
                    }
                }
            }

            2 -> {
                // ONE-TIME INVITE CODES
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isAdmin) {
                        item {
                            Button(
                                onClick = { showCreateInviteDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("create_invite_code_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create One-Time Expiring Invite Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (uiState.inviteCodes.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                icon = Icons.Default.Key,
                                title = "No invite codes yet",
                                message = "Create a one-time invite code with preset role, schools, and permissions so a trusted staff member can join immediately without waiting."
                            )
                        }
                    } else {
                        items(uiState.inviteCodes, key = { it.code }) { inv ->
                            InviteCodeCard(
                                invite = inv,
                                orgCode = uiState.activeOrg?.code.orEmpty(),
                                canRevoke = isAdmin,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = "Org Code: ${uiState.activeOrg?.code.orEmpty()} | Invite Code: ${inv.code}"
                                    cm.setPrimaryClip(ClipData.newPlainText("School Invite", text))
                                },
                                onRevoke = { viewModel.deleteInviteCode(inv.code) }
                            )
                        }
                    }
                }
            }

            3 -> {
                // AUDIT LOG (Admins ONLY)
                if (uiState.auditLogs.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyStatePanel(
                            icon = Icons.Default.History,
                            title = "No audit log entries yet",
                            message = "Key actions (member approvals, role changes, marks edits, school updates) are logged automatically here."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.auditLogs, key = { it.id }) { log ->
                            AuditLogRowCard(log = log)
                        }
                    }
                }
            }
        }
    }

    // Dialog: Edit Existing Member Access
    editingMember?.let { member ->
        MemberAccessEditorDialog(
            title = "Edit Access: ${member.name}",
            subtitle = member.email.ifBlank { member.uid },
            initialRole = member.orgRole,
            initialAllSchools = member.allSchools,
            initialSelectedSchools = member.schools,
            initialPermissions = member.parsedPermissions,
            availableSchools = schoolItems,
            confirmLabel = "Save Access",
            onConfirm = { role, allSchools, schools, perms ->
                viewModel.updateMemberAccess(
                    targetMember = member,
                    newRole = role,
                    allSchools = allSchools,
                    allowedSchoolIds = schools,
                    permissions = perms
                ) { ok, _ ->
                    if (ok) editingMember = null
                }
            },
            onDismiss = { editingMember = null }
        )
    }

    // Dialog: Approve Pending Join Request (choose role, schools, permissions)
    approvingRequest?.let { req ->
        val defaultRole = req.roleEnum
        MemberAccessEditorDialog(
            title = "Approve ${req.name}",
            subtitle = if (req.message.isNotBlank()) "Message: \"${req.message}\"" else "Requested role: ${defaultRole.label}",
            initialRole = defaultRole,
            initialAllSchools = defaultRole == OrgRole.ADMIN,
            initialSelectedSchools = schoolItems.firstOrNull()?.first?.let { listOf(it) } ?: emptyList(),
            initialPermissions = MemberPermissions.presetFor(defaultRole),
            availableSchools = schoolItems,
            confirmLabel = "Approve & Grant Access",
            onConfirm = { role, allSchools, schools, perms ->
                viewModel.approveJoinRequest(
                    request = req,
                    assignedRole = role,
                    allSchools = allSchools,
                    allowedSchoolIds = schools,
                    permissions = perms
                )
                approvingRequest = null
            },
            onDismiss = { approvingRequest = null }
        )
    }

    // Dialog: Create One-Time Invite Code
    if (showCreateInviteDialog) {
        CreateInviteDialog(
            availableSchools = schoolItems,
            onCreate = { role, allSchools, schools, perms, hours ->
                viewModel.createInviteCode(role, allSchools, schools, perms, hours) {
                    showCreateInviteDialog = false
                }
            },
            onDismiss = { showCreateInviteDialog = false }
        )
    }

    // Dialog: Confirm Remove Member
    removingMember?.let { target ->
        ConfirmDangerDialog(
            title = "Remove ${target.name}?",
            message = "This immediately revokes ${target.name}'s access on the server. If their phone is offline, revocation takes effect the next time they connect to the internet.",
            confirmLabel = "Remove Member",
            onConfirm = {
                viewModel.removeMember(target)
                removingMember = null
            },
            onDismiss = { removingMember = null }
        )
    }

    // Dialog: Confirm Transfer Ownership
    transferTarget?.let { target ->
        ConfirmDangerDialog(
            title = "Transfer Ownership to ${target.name}?",
            message = "${target.name} will become the sole Owner of '${uiState.activeOrg?.name.orEmpty()}' and you will become an Admin. Only the new Owner will be able to delete the organisation or transfer ownership again.",
            confirmLabel = "Transfer Ownership",
            onConfirm = {
                viewModel.transferOwnership(target) {
                    transferTarget = null
                }
            },
            onDismiss = { transferTarget = null }
        )
    }
}

@Composable
private fun MemberCard(
    member: CloudMember,
    isSelf: Boolean,
    schoolNamesByDocId: Map<String, String>,
    canEdit: Boolean,
    canRemove: Boolean,
    canTransferOwnership: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTransfer: () -> Unit
) {
    val lastActiveMs = member.lastActiveAt?.toDate()?.time ?: 0L
    val isRecentlyOnline = lastActiveMs > 0L && (System.currentTimeMillis() - lastActiveMs) < 5 * 60_000L
    val lastActiveText = when {
        isRecentlyOnline -> "Online now"
        lastActiveMs > 0L -> "Last active ${SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(lastActiveMs))}"
        else -> "Joined recently"
    }
    val schoolsLabel = if (member.orgRole == OrgRole.OWNER || member.orgRole == OrgRole.ADMIN || member.allSchools) {
        "All schools"
    } else if (member.schools.isEmpty()) {
        "No schools assigned"
    } else {
        member.schools.mapNotNull { schoolNamesByDocId[it] }.ifEmpty { member.schools }.joinToString(", ")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("member_card_${member.uid}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudentPhotoThumbnail(
                    photoBase64 = member.teacherPhotoBase64,
                    studentName = member.name.ifBlank { "M" },
                    size = 44.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isSelf) "${member.name} (You)" else member.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isRecentlyOnline) PassGreen else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                    Text(
                        text = "${member.email.ifBlank { member.mobile }} • $lastActiveText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (member.orgRole == OrgRole.OWNER) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = member.orgRole.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (member.orgRole == OrgRole.OWNER) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Schools: $schoolsLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (member.orgRole == OrgRole.TEACHER && (member.requestedSubjects.isNotEmpty() || member.requestedClasses.isNotEmpty())) {
                Text(
                    text = "Requested: ${member.requestedClasses.joinToString(", ")} • ${member.requestedSubjects.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (canEdit || canRemove || canTransferOwnership) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canEdit) {
                        OutlinedButton(
                            onClick = onEdit,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("edit_member_${member.uid}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Access", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (canTransferOwnership) {
                        OutlinedButton(
                            onClick = onTransfer,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("transfer_owner_${member.uid}")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Make Owner", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (canRemove) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.testTag("remove_member_${member.uid}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove member",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinRequestAdminCard(
    request: CloudJoinRequest,
    canApprove: Boolean,
    onApproveClick: () -> Unit,
    onRejectClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("join_request_card_${request.uid}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(request.name.ifBlank { "Applicant" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(request.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = AbsentAmberContainer) {
                    Text(
                        text = request.roleEnum.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AbsentAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            if (request.message.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${request.message}\"",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (canApprove) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApproveClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("approve_request_${request.uid}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve & Set Access")
                    }
                    OutlinedButton(
                        onClick = onRejectClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("reject_request_${request.uid}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteCodeCard(
    invite: CloudInviteCode,
    orgCode: String,
    canRevoke: Boolean,
    onCopy: () -> Unit,
    onRevoke: () -> Unit
) {
    val expired = invite.expiredNow()
    val statusText = when {
        invite.used -> "Used"
        expired -> "Expired"
        else -> "Active"
    }
    val (badgeBg, badgeFg) = when {
        invite.used || expired -> FailRedContainer to FailRed
        else -> PassGreenContainer to PassGreen
    }
    val expFormatted = invite.expiresAt?.toDate()?.let {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(it)
    } ?: "—"

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = invite.code,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Org: @$orgCode • Preset: ${invite.roleEnum.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = "Expires: $expFormatted • Schools: ${if (invite.allSchools) "All" else "${invite.schools.size} selected"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!invite.used && !expired) {
                    OutlinedButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Invite")
                    }
                }
                if (canRevoke) {
                    TextButton(
                        onClick = onRevoke,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Revoke")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogRowCard(log: CloudAuditLog) {
    val timeText = log.createdAt?.toDate()?.let {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(it)
    } ?: "Just now"
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.action.replace('_', ' '),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(log.summary, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "By ${log.actorName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================================
// ACCESS EDITOR DIALOG (Preset Roles + Per-School List + 8-Module None/View/Edit)
// =============================================================================
@Composable
private fun MemberAccessEditorDialog(
    title: String,
    subtitle: String,
    initialRole: OrgRole,
    initialAllSchools: Boolean,
    initialSelectedSchools: List<String>,
    initialPermissions: MemberPermissions,
    availableSchools: List<Pair<String, String>>,
    confirmLabel: String,
    onConfirm: (OrgRole, Boolean, List<String>, MemberPermissions) -> Unit,
    onDismiss: () -> Unit
) {
    var role by remember { mutableStateOf(if (initialRole == OrgRole.OWNER) OrgRole.ADMIN else initialRole) }
    var allSchools by remember { mutableStateOf(initialAllSchools || role == OrgRole.ADMIN) }
    val selectedSchools = remember { mutableStateListOf<String>().apply { addAll(initialSelectedSchools) } }
    var permissions by remember { mutableStateOf(initialPermissions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Role Preset (fills defaults — customizable below)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrgRole.assignablePresets.forEach { preset ->
                        FilterChip(
                            selected = role == preset,
                            onClick = {
                                role = preset
                                permissions = MemberPermissions.presetFor(preset)
                                if (preset == OrgRole.ADMIN) allSchools = true
                            },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Access to All Schools", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = allSchools || role == OrgRole.ADMIN,
                        enabled = role != OrgRole.ADMIN,
                        onCheckedChange = { allSchools = it }
                    )
                }

                if (!allSchools && role != OrgRole.ADMIN) {
                    Text("Allowed Schools:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    availableSchools.forEach { (docId, name) ->
                        val checked = docId in selectedSchools
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedSchools.remove(docId) else selectedSchools.add(docId)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (docId !in selectedSchools) selectedSchools.add(docId)
                                    } else {
                                        selectedSchools.remove(docId)
                                    }
                                }
                            )
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider()

                Text("Module Permissions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                AppModule.entries.forEach { module ->
                    val currentLvl = if (role == OrgRole.ADMIN) AccessLevel.EDIT else permissions.levelFor(module)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(module.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccessLevel.entries.forEach { lvl ->
                                FilterChip(
                                    selected = currentLvl == lvl,
                                    enabled = role != OrgRole.ADMIN,
                                    onClick = { permissions = permissions.withModule(module, lvl) },
                                    label = {
                                        Text(
                                            when (lvl) {
                                                AccessLevel.NONE -> "None"
                                                AccessLevel.VIEW -> "View"
                                                AccessLevel.EDIT -> "Edit"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val effectivePerms = if (role == OrgRole.ADMIN) MemberPermissions.presetFor(OrgRole.ADMIN) else permissions
                    onConfirm(role, allSchools || role == OrgRole.ADMIN, selectedSchools.toList(), effectivePerms)
                },
                modifier = Modifier.testTag("confirm_member_access_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateInviteDialog(
    availableSchools: List<Pair<String, String>>,
    onCreate: (OrgRole, Boolean, List<String>, MemberPermissions, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var role by remember { mutableStateOf(OrgRole.TEACHER) }
    var allSchools by remember { mutableStateOf(true) }
    val selectedSchools = remember {
        mutableStateListOf<String>().apply {
            availableSchools.firstOrNull()?.first?.let { add(it) }
        }
    }
    var hours by remember { mutableIntStateOf(48) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New One-Time Invite Code", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Preset Role", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrgRole.assignablePresets.forEach { preset ->
                        FilterChip(
                            selected = role == preset,
                            onClick = {
                                role = preset
                                if (preset == OrgRole.ADMIN) allSchools = true
                            },
                            label = { Text(preset.shortLabel) }
                        )
                    }
                }

                Text("Expires In", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(24 to "24h", 48 to "48h", 168 to "7 days").forEach { (h, label) ->
                        FilterChip(
                            selected = hours == h,
                            onClick = { hours = h },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("All Schools", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = allSchools || role == OrgRole.ADMIN,
                        enabled = role != OrgRole.ADMIN,
                        onCheckedChange = { allSchools = it }
                    )
                }

                if (!allSchools && role != OrgRole.ADMIN) {
                    availableSchools.forEach { (docId, name) ->
                        val checked = docId in selectedSchools
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedSchools.remove(docId) else selectedSchools.add(docId)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { c ->
                                    if (c) {
                                        if (docId !in selectedSchools) selectedSchools.add(docId)
                                    } else {
                                        selectedSchools.remove(docId)
                                    }
                                }
                            )
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        role,
                        allSchools || role == OrgRole.ADMIN,
                        selectedSchools.toList(),
                        MemberPermissions.presetFor(role),
                        hours
                    )
                },
                modifier = Modifier.testTag("confirm_create_invite_button")
            ) {
                Text("Generate Code")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
