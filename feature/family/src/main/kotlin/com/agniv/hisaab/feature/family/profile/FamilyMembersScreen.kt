package com.agniv.hisaab.feature.family.profile

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.InitialAvatar
import com.agniv.hisaab.core.designsystem.component.KinlySelectField
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.MoreButton
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.feature.family.R
import kotlinx.coroutines.launch

/**
 * Board "Family Members": family banner, every member with an Admin / Member chip and (for admins) a ⋮ menu to
 * change the role or remove, Invite Member, and the join code. Members see "You are a member" instead of the
 * admin tools. With only you in the family, the empty "No family members yet" state.
 */
@Composable
fun FamilyMembersScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(key = "family-members"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var inviting by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val adminHint = stringResource(R.string.invite_admin_after)
    val alone = !state.busy && state.members.size <= 1

    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.family_members_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.isAdmin && !alone) {
                HButton(
                    onClick = { inviting = true },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(Spacing.screen),
                ) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.invite_member), modifier = Modifier.padding(start = Spacing.s))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "banner") {
                HCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        Modifier.padding(Spacing.l),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Icon(
                            Icons.Outlined.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Text(
                                state.family?.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                stringResource(R.string.family_members, state.members.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            if (alone) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Groups,
                        title = stringResource(R.string.family_empty_title),
                        body = stringResource(R.string.family_empty_body),
                        action = stringResource(R.string.invite_member).takeIf { state.isAdmin },
                        actionIcon = Icons.Outlined.PersonAdd,
                        onAction = { inviting = true },
                    )
                }
            } else {
                item(key = "list") {
                    HCard(Modifier.fillMaxWidth()) {
                        state.members.forEachIndexed { i, member ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                            MemberRow(member, state, viewModel)
                        }
                    }
                }
            }
            if (!state.isAdmin && !state.busy) item(key = "member-note") { MemberNote() }
            state.family?.inviteCode?.let { code -> item(key = "code") { JoinCode(code) } }
        }
    }

    if (inviting) {
        InviteSheet(
            familyCode = state.family?.inviteCode.orEmpty(),
            onDismiss = { inviting = false },
            onSent = { role ->
                inviting = false
                if (role == Role.ADMIN) scope.launch { snackbar.showSnackbar(adminHint) }
            },
        )
    }
}

@Composable
private fun MemberRow(
    member: Member,
    state: ProfileUiState,
    viewModel: ProfileViewModel,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val isMe = member.id == state.myMemberId
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        InitialAvatar(member.name)
        Column(Modifier.weight(1f)) {
            Text(
                member.name + if (isMe) stringResource(R.string.family_you) else "",
                style = MaterialTheme.typography.titleSmall,
            )
            state.memberEmails[member.id]?.let { email ->
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RoleChip(member.role)
        // Only admins manage others, and nobody manages themselves here.
        if (state.isAdmin && !isMe) {
            Box {
                MoreButton(onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val makeAdmin = member.role != Role.ADMIN
                    DropdownMenuItem(
                        text = { Text(stringResource(if (makeAdmin) R.string.profile_make_admin else R.string.profile_make_member)) },
                        onClick = {
                            menu = false
                            viewModel.setRole(member.id, if (makeAdmin) Role.ADMIN else Role.MEMBER)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_remove), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            confirmRemove = true
                        },
                    )
                }
            }
        } else {
            Box(Modifier.size(48.dp))
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.profile_remove_title, member.name)) },
            text = { Text(stringResource(R.string.profile_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.remove(member.id)
                }) { Text(stringResource(R.string.profile_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Gold "Admin" or grey "Member" chip. */
@Composable
private fun RoleChip(role: Role) {
    val admin = role == Role.ADMIN
    val colors = HisaabTheme.extendedColors
    Text(
        stringResource(if (admin) R.string.family_admin else R.string.family_member),
        style = MaterialTheme.typography.labelMedium,
        color = if (admin) colors.onChipWarn else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .background(if (admin) colors.chipWarn else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = Spacing.s, vertical = 3.dp),
    )
}

@Composable
private fun MemberNote() {
    val gold = HisaabTheme.extendedColors
    HCard(Modifier.fillMaxWidth(), containerColor = gold.goldContainer) {
        Row(
            Modifier.padding(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = gold.gold)
            Column {
                Text(stringResource(R.string.family_you_member), style = MaterialTheme.typography.titleSmall, color = gold.onGoldContainer)
                Text(
                    stringResource(R.string.family_you_member_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = gold.onGoldContainer,
                )
            }
        }
    }
}

/** The family's join code, with Share, for anyone joining another way. */
@Composable
private fun JoinCode(code: String) {
    val context = LocalContext.current
    val text = stringResource(R.string.family_share_text, code)
    val title = stringResource(R.string.family_share)
    HCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.family_invite_code), style = MaterialTheme.typography.titleSmall)
            Text(code, style = DisplayAmount, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.family_invite_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, title))
            }) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(title, modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}

/**
 * Board "Invite Family Member": email or mobile number and a role. Send Invite opens the phone's own email or SMS
 * app with the join message already written (the person sends it; Kinly sends nothing by itself). Everyone joins as
 * a Member; picking Admin reminds the admin to make them Admin once they join.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(
    familyCode: String,
    onDismiss: () -> Unit,
    onSent: (Role) -> Unit,
) {
    val context = LocalContext.current
    var contact by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.MEMBER) }
    val email = Patterns.EMAIL_ADDRESS.matcher(contact.trim()).matches()
    val phone = contact.filter(Char::isDigit).length in PHONE_DIGITS && contact.all { it.isDigit() || it in "+ -" }
    val message = stringResource(R.string.family_share_text, familyCode)
    val subject = stringResource(R.string.invite_subject)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.titleLarge)
            KinlyTextField(
                value = contact,
                onValueChange = { contact = it },
                label = stringResource(R.string.invite_contact),
                leadingIcon = Icons.Outlined.MailOutline,
                keyboardType = KeyboardType.Email,
                isError = contact.isNotBlank() && !email && !phone,
            )
            Text(stringResource(R.string.invite_role), style = MaterialTheme.typography.titleSmall)
            KinlySelectField(
                value = stringResource(if (role == Role.ADMIN) R.string.family_admin else R.string.family_member),
                options = listOf(Role.MEMBER, Role.ADMIN),
                optionLabel = { stringResource(if (it == Role.ADMIN) R.string.family_admin else R.string.family_member) },
                onSelect = { role = it },
                leadingIcon = Icons.Outlined.Groups,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                HOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                HButton(
                    onClick = {
                        val intent =
                            if (email) {
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(contact.trim())))
                                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                                    .putExtra(Intent.EXTRA_TEXT, message)
                            } else {
                                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(contact.trim()))).putExtra("sms_body", message)
                            }
                        runCatching { context.startActivity(intent) }
                        onSent(role)
                    },
                    enabled = familyCode.isNotEmpty() && (email || phone),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.invite_send)) }
            }
        }
    }
}

// An Indian mobile number: 10 digits, or 12–13 with the +91 country code.
private const val MIN_PHONE_DIGITS = 10
private const val MAX_PHONE_DIGITS = 13
private val PHONE_DIGITS = MIN_PHONE_DIGITS..MAX_PHONE_DIGITS
