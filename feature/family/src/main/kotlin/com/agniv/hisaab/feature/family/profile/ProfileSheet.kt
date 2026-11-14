package com.agniv.hisaab.feature.family.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.feature.family.R

/** Emoji people can pick as their picture; the first letter of the name is used until they pick one. */
val AvatarChoices = listOf("🙂", "😎", "🧑", "👩", "👨", "👧", "👦", "🧓", "🦁", "🐯", "🐼", "🌸", "⭐", "🚀")

/** Round profile picture: the chosen emoji, or the first letter of the name. */
@Composable
fun AvatarCircle(
    name: String?,
    avatar: String?,
    modifier: Modifier = Modifier,
    size: Int = 36,
) {
    Box(
        modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            avatar ?: name
                ?.trim()
                ?.firstOrNull()
                ?.uppercase()
                .orEmpty()
                .ifEmpty { "🙂" },
            style = if (size >= BIG_AVATAR) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private const val BIG_AVATAR = 56

/** The profile button at the top of Home. Shows the signed-in person's picture; tap opens [ProfileSheet]. */
@Composable
fun ProfileButton(
    avatar: String?,
    onClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(key = "profile-button"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val label = stringResource(R.string.profile_open)
    AvatarCircle(
        state.me?.name,
        avatar,
        Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/**
 * Profile (IMPLEMENTATION_PLAN 4.10): who you are, your picture, the app's settings (passed in by the app), your
 * family with invite code and members, export / delete account (5.6), and sign out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    avatar: String?,
    onAvatar: (String?) -> Unit,
    localFamilyName: String,
    localMemberName: String,
    onSignedOut: () -> Unit,
    onAccountDeleted: () -> Unit,
    onDismiss: () -> Unit,
    settingsContent: @Composable () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(key = "profile-sheet"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editName by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Header(state, avatar, onEditName = { editName = true })
            AvatarPicker(avatar, onAvatar)
            MessageLine(state.message)
            HorizontalDivider()
            settingsContent()
            HorizontalDivider()
            FamilySection(state, viewModel)
            HorizontalDivider()
            PrivacySection(
                busy = state.busy,
                message = state.message,
                onDelete = { viewModel.deleteAccount(localFamilyName, localMemberName, onAccountDeleted) },
            )
            HorizontalDivider()
            OutlinedButton(onClick = { confirmSignOut = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_sign_out))
            }
        }
    }

    if (editName) {
        NameDialog(
            initial = state.me?.name.orEmpty(),
            onSave = {
                viewModel.rename(it)
                editName = false
            },
            onDismiss = { editName = false },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.profile_sign_out_title)) },
            text = { Text(stringResource(R.string.profile_sign_out_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut(false, localFamilyName, localMemberName, onSignedOut)
                }) { Text(stringResource(R.string.profile_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.pendingOnSignOut?.let { count ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPending,
            title = { Text(stringResource(R.string.profile_pending_title)) },
            text = { Text(stringResource(R.string.profile_pending_text, count)) },
            confirmButton = {
                TextButton(onClick = { viewModel.signOut(true, localFamilyName, localMemberName, onSignedOut) }) {
                    Text(stringResource(R.string.profile_sign_out_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPending) { Text(stringResource(R.string.profile_stay)) } },
        )
    }
}

@Composable
private fun Header(
    state: ProfileUiState,
    avatar: String?,
    onEditName: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        AvatarCircle(state.me?.name, avatar, size = BIG_AVATAR)
        Column(Modifier.weight(1f)) {
            Text(state.me?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            state.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        TextButton(onClick = onEditName, enabled = !state.busy) { Text(stringResource(R.string.profile_edit_name)) }
    }
}

@Composable
private fun AvatarPicker(
    current: String?,
    onPick: (String?) -> Unit,
) {
    Text(stringResource(R.string.profile_picture), style = MaterialTheme.typography.titleMedium)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item(key = "initial") {
            TextButton(onClick = { onPick(null) }) {
                Text(
                    stringResource(R.string.profile_use_initial) +
                        if (current ==
                            null
                        ) {
                            " ✓"
                        } else {
                            ""
                        },
                )
            }
        }
        items(AvatarChoices, key = { it }) { emoji ->
            Text(
                emoji + if (emoji == current) "✓" else "",
                style = MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier
                        .clickable { onPick(emoji) }
                        .padding(Spacing.xs),
            )
        }
    }
}

@Composable
private fun MessageLine(message: ProfileMessage?) {
    val res =
        when (message) {
            null -> return
            ProfileMessage.NETWORK -> R.string.profile_err_network
            ProfileMessage.NOT_ALLOWED -> R.string.profile_err_not_allowed
            ProfileMessage.LAST_ADMIN -> R.string.profile_err_last_admin
            ProfileMessage.BAD_NAME -> R.string.profile_err_name
            ProfileMessage.FAILED -> R.string.profile_err_failed
            // Shown next to the Delete account button instead (PrivacySection).
            ProfileMessage.DELETE_OFFLINE, ProfileMessage.DELETE_LAST_ADMIN, ProfileMessage.DELETE_FAILED -> return
        }
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun NameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_name)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.profile_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FamilySection(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
) {
    val context = LocalContext.current
    val code = state.family?.inviteCode
    val shareText = code?.let { stringResource(R.string.family_share_text, it) }
    val shareTitle = stringResource(R.string.family_share)
    Text(state.family?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
    code?.let {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.family_invite_code), style = MaterialTheme.typography.titleMedium)
                Text(it, style = DisplayAmount, textAlign = TextAlign.Center)
                Text(stringResource(R.string.family_invite_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = {
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                context.startActivity(Intent.createChooser(send, shareTitle))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(shareTitle) }
    }
    Text(stringResource(R.string.family_members, state.members.size), style = MaterialTheme.typography.titleMedium)
    state.members.forEach { member -> MemberRow(member, state, viewModel) }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AvatarCircle(member.name, null, size = 32)
        Text(
            member.name + if (isMe) stringResource(R.string.family_you) else "",
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = Spacing.m),
        )
        if (member.role == Role.ADMIN) {
            Text(
                stringResource(R.string.family_admin),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Only admins manage others, and nobody manages themselves here (rename is in the header).
        if (state.isAdmin && !isMe) {
            Box {
                TextButton(onClick = { menu = true }, enabled = !state.busy) { Text("⋯") }
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
        }
    }
    HorizontalDivider()
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
