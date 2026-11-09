package com.agniv.hisaab.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onGoogle: () -> Unit = {
        scope.launch {
            val token = requestGoogleToken(context, viewModel.googleClientId)
            viewModel.onEvent(
                if (token != null) LoginEvent.GoogleToken(token.idToken, token.rawNonce) else LoginEvent.GoogleFailed,
            )
        }
    }
    Scaffold { padding ->
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
                stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyLarge)

            when (state.step) {
                LoginStep.EMAIL -> EmailStep(state, viewModel::onEvent)
                LoginStep.CODE -> CodeStep(state, viewModel::onEvent)
            }

            state.error?.let {
                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
            }

            if (state.googleAvailable && state.step == LoginStep.EMAIL) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = onGoogle,
                    enabled = !state.busy,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                ) { Text(stringResource(R.string.login_google)) }
            }
        }
    }
}

@Composable
private fun EmailStep(
    state: LoginUiState,
    onEvent: (LoginEvent) -> Unit,
) {
    OutlinedTextField(
        value = state.email,
        onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
        label = { Text(stringResource(R.string.login_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onEvent(LoginEvent.SendCode) },
        enabled = !state.busy && state.email.isNotBlank(),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
    ) { Text(stringResource(if (state.busy) R.string.login_sending else R.string.login_send_code)) }
}

@Composable
private fun CodeStep(
    state: LoginUiState,
    onEvent: (LoginEvent) -> Unit,
) {
    Text(stringResource(R.string.login_code_sent, state.email), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.code,
        onValueChange = { onEvent(LoginEvent.CodeChanged(it)) },
        label = { Text(stringResource(R.string.login_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onEvent(LoginEvent.Verify) },
        enabled = !state.busy && state.code.length == LoginViewModel.CODE_LENGTH,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
    ) { Text(stringResource(R.string.login_verify)) }
    TextButton(onClick = { onEvent(LoginEvent.SendCode) }, enabled = !state.busy) {
        Text(stringResource(R.string.login_resend))
    }
    TextButton(onClick = { onEvent(LoginEvent.ChangeEmail) }, enabled = !state.busy) {
        Text(stringResource(R.string.login_change_email))
    }
}

private fun LoginError.messageRes(): Int =
    when (this) {
        LoginError.INVALID_EMAIL -> R.string.login_error_email
        LoginError.WRONG_CODE -> R.string.login_error_code
        LoginError.TOO_MANY -> R.string.login_error_too_many
        LoginError.NETWORK -> R.string.login_error_network
        LoginError.GOOGLE_FAILED -> R.string.login_error_google
        LoginError.UNKNOWN -> R.string.login_error_unknown
    }
