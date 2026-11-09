package com.agniv.hisaab.feature.family.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.family.R

@Composable
fun FamilySetupScreen(viewModel: FamilySetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        if (state.checking) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.setup_checking))
                }
            }
            return@Scaffold
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(
                    selected = state.mode == SetupMode.CREATE,
                    onClick = { viewModel.onEvent(SetupEvent.Mode(SetupMode.CREATE)) },
                    label = { Text(stringResource(R.string.setup_create)) },
                )
                FilterChip(
                    selected = state.mode == SetupMode.JOIN,
                    onClick = { viewModel.onEvent(SetupEvent.Mode(SetupMode.JOIN)) },
                    label = { Text(stringResource(R.string.setup_join)) },
                )
            }
            OutlinedTextField(
                value = state.myName,
                onValueChange = { viewModel.onEvent(SetupEvent.MyName(it)) },
                label = { Text(stringResource(R.string.setup_my_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            when (state.mode) {
                SetupMode.CREATE ->
                    OutlinedTextField(
                        value = state.familyName,
                        onValueChange = { viewModel.onEvent(SetupEvent.FamilyName(it)) },
                        label = { Text(stringResource(R.string.setup_family_name)) },
                        placeholder = { Text(stringResource(R.string.setup_family_name_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                SetupMode.JOIN ->
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = { viewModel.onEvent(SetupEvent.Code(it)) },
                        label = { Text(stringResource(R.string.setup_code)) },
                        supportingText = { Text(stringResource(R.string.setup_code_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
            state.error?.let { error ->
                Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
                if (error == SetupError.NETWORK) {
                    TextButton(onClick = { viewModel.onEvent(SetupEvent.Retry) }) { Text(stringResource(R.string.setup_retry)) }
                }
            }
            Button(
                onClick = { viewModel.onEvent(SetupEvent.Submit) },
                enabled = !state.busy,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            state.busy -> R.string.setup_working
                            state.mode == SetupMode.CREATE -> R.string.setup_create_button
                            else -> R.string.setup_join_button
                        },
                    ),
                )
            }
            Text(
                stringResource(R.string.setup_keeps_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun SetupError.messageRes(): Int =
    when (this) {
        SetupError.NEED_NAME -> R.string.setup_error_name
        SetupError.BAD_CODE -> R.string.setup_error_code_length
        SetupError.INVALID_CODE -> R.string.setup_error_code
        SetupError.FULL -> R.string.setup_error_full
        SetupError.NETWORK -> R.string.setup_error_network
        SetupError.UNKNOWN -> R.string.setup_error_unknown
    }
