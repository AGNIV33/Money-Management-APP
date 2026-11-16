package com.agniv.hisaab.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.component.HBusyButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.InlineError
import com.agniv.hisaab.core.designsystem.component.KinlyLogo
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.component.PillTabs
import com.agniv.hisaab.core.designsystem.motion.shake
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Board "Welcome + Login": language pills, logo, "Your family's money, together.", a drawn landscape, then a card
 * with email → "Send code" → six code boxes → "Continue", plus Continue with Google and the privacy note.
 * A wrong code turns the boxes red and shakes them.
 */
@Composable
fun LoginScreen(
    language: String,
    onLanguage: (String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shake by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state.error) { if (state.error == LoginError.WRONG_CODE) shake++ }
    val onGoogle: () -> Unit = {
        scope.launch {
            val token = requestGoogleToken(context, viewModel.googleClientId)
            viewModel.onEvent(
                if (token != null) LoginEvent.GoogleToken(token.idToken, token.rawNonce) else LoginEvent.GoogleFailed,
            )
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            PillTabs(
                options = listOf("English", "हिंदी"),
                selected = if (language == "hi") 1 else 0,
                onSelect = { onLanguage(if (it == 1) "hi" else "en") },
                fill = false,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(top = Spacing.m),
            )
            KinlyLogo()
            Tagline()
            Text(
                stringResource(R.string.login_tagline_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Landscape()
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            when (state.step) {
                LoginStep.EMAIL -> EmailStep(state, viewModel::onEvent)
                LoginStep.CODE -> CodeStep(state, shake, viewModel::onEvent)
            }
            state.error?.takeIf { it != LoginError.WRONG_CODE }?.let { InlineError(stringResource(it.messageRes())) }
            if (state.googleAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.login_or),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = Spacing.m),
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
                HOutlinedButton(onClick = onGoogle, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "G",
                        style = MaterialTheme.typography.titleMedium,
                        color = GOOGLE_BLUE,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Text(stringResource(R.string.login_google), modifier = Modifier.padding(start = Spacing.m))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.login_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }
    }
}

@Suppress("MagicNumber") // Google's brand blue for the "G".
private val GOOGLE_BLUE = Color(0xFF4285F4)

/** "Your family's money, together." with "together." in green, as on the board. */
@Composable
private fun Tagline() {
    val green = MaterialTheme.colorScheme.primary
    val text =
        buildAnnotatedString {
            append(stringResource(R.string.login_tagline_start))
            append("\n")
            withStyle(SpanStyle(color = green)) { append(stringResource(R.string.login_tagline_end)) }
        }
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
}

/** Board illustration: soft green hills, a gold house and two round trees. Drawn, so it costs no APK size. */
@Suppress("MagicNumber") // Positions of the hills, house and trees as shares of the picture.
@Composable
private fun Landscape() {
    val back = MaterialTheme.colorScheme.primaryContainer
    val mid = MaterialTheme.colorScheme.primary.copy(alpha = HILL_ALPHA)
    val front = MaterialTheme.colorScheme.primary
    val house = HisaabTheme.extendedColors.gold
    val roof = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(LANDSCAPE_H.dp)
            .clearAndSetSemantics {},
    ) {
        val w = size.width
        val h = size.height

        fun hill(
            startY: Float,
            peakX: Float,
            peakY: Float,
            color: Color,
        ) {
            val p =
                Path().apply {
                    moveTo(0f, h * startY)
                    quadraticTo(w * peakX, h * peakY, w, h * startY * HILL_END)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
            drawPath(p, color)
        }
        hill(0.55f, 0.3f, 0.05f, back)
        hill(0.75f, 0.7f, 0.25f, mid)
        // House on the middle hill.
        val hx = w * 0.62f
        val hy = h * 0.42f
        val hw = w * 0.09f
        drawRect(
            house,
            topLeft = Offset(hx, hy),
            size =
                androidx.compose.ui.geometry
                    .Size(hw, hw * 0.75f),
        )
        drawPath(
            Path().apply {
                moveTo(hx - hw * 0.15f, hy)
                lineTo(hx + hw / 2, hy - hw * 0.55f)
                lineTo(hx + hw * 1.15f, hy)
                close()
            },
            roof,
        )
        drawCircle(front, radius = w * 0.04f, center = Offset(w * 0.82f, h * 0.45f))
        drawCircle(front, radius = w * 0.03f, center = Offset(w * 0.18f, h * 0.55f))
        hill(0.9f, 0.4f, 0.6f, front)
    }
}

private const val HILL_ALPHA = 0.55f
private const val LANDSCAPE_H = 120
private const val HILL_END = 1.1f

@Composable
private fun EmailStep(
    state: LoginUiState,
    onEvent: (LoginEvent) -> Unit,
) {
    Text(stringResource(R.string.login_welcome), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.login_email_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    KinlyTextField(
        value = state.email,
        onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
        label = stringResource(R.string.login_email),
        placeholder = "name@example.com",
        leadingIcon = Icons.Outlined.MailOutline,
        keyboardType = KeyboardType.Email,
        isError = state.error == LoginError.INVALID_EMAIL,
    )
    HBusyButton(
        text = stringResource(R.string.login_send_code),
        busyText = stringResource(R.string.login_sending),
        busy = state.busy,
        enabled = state.email.isNotBlank(),
        onClick = { onEvent(LoginEvent.SendCode) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeStep(
    state: LoginUiState,
    shake: Int,
    onEvent: (LoginEvent) -> Unit,
) {
    Text(stringResource(R.string.login_verify_title), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.login_code_sent, maskEmail(state.email)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OtpBoxes(
        code = state.code,
        error = state.error == LoginError.WRONG_CODE,
        shake = shake,
        onChange = { onEvent(LoginEvent.CodeChanged(it)) },
        onDone = { if (state.code.length >= LoginViewModel.MIN_CODE_LENGTH) onEvent(LoginEvent.Verify) },
    )
    if (state.error == LoginError.WRONG_CODE) InlineError(stringResource(R.string.login_error_code))
    HBusyButton(
        text = stringResource(R.string.login_continue),
        busyText = stringResource(R.string.login_verifying),
        busy = state.busy,
        enabled = state.code.length >= LoginViewModel.MIN_CODE_LENGTH,
        onClick = { onEvent(LoginEvent.Verify) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.login_no_code), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onEvent(LoginEvent.SendCode) }, enabled = !state.busy) { Text(stringResource(R.string.login_resend)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { onEvent(LoginEvent.ChangeEmail) }, enabled = !state.busy) {
            Text(stringResource(R.string.login_change_email))
        }
    }
}

/**
 * Six boxes (eight if the code is longer) over one hidden text field, so typing, paste and the SMS/email code
 * suggestion all work as in a normal field. TalkBack reads it as one "Code" field.
 */
@Composable
private fun OtpBoxes(
    code: String,
    error: Boolean,
    shake: Int,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val boxes = maxOf(LoginViewModel.MIN_CODE_LENGTH, code.length)
    val label = stringResource(R.string.login_code)
    BasicTextField(
        value = code,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .shake(shake)
                .semantics { contentDescription = label },
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally)) {
                repeat(boxes) { i ->
                    val ch = code.getOrNull(i)?.toString().orEmpty()
                    val active = i == code.length
                    val border =
                        when {
                            error -> MaterialTheme.colorScheme.error
                            active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 56.dp)
                            .border(if (active || error) 2.dp else 1.dp, border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ch,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
    )
}

/** "agniv@kinly.app" → "a***@kinly.app", as on the board. */
internal fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    return email.first() + "***" + email.substring(at)
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
