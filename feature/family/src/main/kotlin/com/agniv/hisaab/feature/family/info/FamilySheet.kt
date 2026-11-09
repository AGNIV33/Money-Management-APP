package com.agniv.hisaab.feature.family.info

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.feature.family.R

/** Family name, invite code to share, and who is in the family (task 1.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySheet(
    onDismiss: () -> Unit,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareText = state.family?.inviteCode?.let { stringResource(R.string.family_share_text, it) }
    val shareTitle = stringResource(R.string.family_share)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(state.family?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            state.family?.inviteCode?.let { code ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.family_invite_code), style = MaterialTheme.typography.titleMedium)
                        Text(code, style = DisplayAmount, textAlign = TextAlign.Center)
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
            Text(
                stringResource(R.string.family_members, state.members.size),
                style = MaterialTheme.typography.titleMedium,
            )
            state.members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val me = if (member.id == state.myMemberId) stringResource(R.string.family_you) else ""
                    Text(member.name + me, style = MaterialTheme.typography.bodyLarge)
                    if (member.role == Role.ADMIN) {
                        Text(
                            stringResource(R.string.family_admin),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
