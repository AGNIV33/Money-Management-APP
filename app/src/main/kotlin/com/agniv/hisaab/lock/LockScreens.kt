package com.agniv.hisaab.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.AppLock
import com.agniv.hisaab.R
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.InlineError
import com.agniv.hisaab.core.designsystem.component.KinlyLogo
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.SparkIllustration
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Board "Unlock (PIN)": logo, "Enter your PIN", dots, keypad with fingerprint, "Forgot PIN?". Wrong PIN shakes the
 * dots and shows "Incorrect PIN. Try again."; five wrong in a row locks the keypad for 30 seconds with a countdown.
 * Nothing of the person's money is visible behind it.
 */
@Suppress("CyclomaticComplexMethod") // One branch per keypad state (wrong, waiting, checking).
@Composable
fun PinLockScreen(
    lock: AppLock,
    onForgot: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pin by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    var shake by rememberSaveable { mutableIntStateOf(0) }
    var checking by rememberSaveable { mutableStateOf(false) }
    var lockedUntil by rememberSaveable { mutableLongStateOf(lock.pins.lockedUntil) }
    var now by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val lockedOut = now < lockedUntil
    val finger = lock.pins.useFingerprint && lock.fingerprintAvailable()

    // Ticks the countdown once a second while waiting.
    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(TICK_MS)
        }
        now = System.currentTimeMillis()
    }
    // Offer the fingerprint straight away, like the board's "Verify your identity" sheet.
    LaunchedEffect(Unit) { if (finger && !lockedOut) lock.authenticate() }

    fun submit(entry: String) {
        checking = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { lock.pins.check(entry) }
            checking = false
            when (result) {
                PinCheck.Ok -> lock.unlock()
                is PinCheck.Wrong -> {
                    wrong = true
                    shake++
                    pin = ""
                }
                is PinCheck.LockedOut -> {
                    wrong = false
                    shake++
                    pin = ""
                    lockedUntil = result.untilMillis
                }
            }
        }
    }

    LockLayout(dark = true) {
        KinlyLogo()
        Text(stringResource(R.string.pin_enter), style = MaterialTheme.typography.titleMedium)
        PinDots(pin.length, shake, error = wrong)
        when {
            lockedOut -> {
                val seconds = ((lockedUntil - now + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()
                LockoutBanner(seconds)
            }
            wrong -> InlineError(stringResource(R.string.pin_incorrect))
            else -> Spacer(Modifier.height(36.dp))
        }
        PinPad(
            enabled = !lockedOut && !checking,
            onDigit = { d ->
                wrong = false
                if (pin.length < PinAttempts.PIN_LENGTH) pin += d
                if (pin.length == PinAttempts.PIN_LENGTH) submit(pin)
            },
            onBackspace = { pin = pin.dropLast(1) },
            onFingerprint = if (finger) ({ lock.authenticate() }) else null,
        )
        TextButton(onClick = onForgot) { Text(stringResource(R.string.pin_forgot), color = MaterialTheme.colorScheme.primary) }
    }
}

private const val TICK_MS = 1_000L
private const val MS_PER_SECOND = 1_000L

@Composable
private fun LockoutBanner(seconds: Int) {
    val gold = HisaabTheme.extendedColors
    Row(
        Modifier
            .background(gold.goldContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        androidx.compose.material3.Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = gold.gold)
        Text(stringResource(R.string.pin_locked_out, seconds), style = MaterialTheme.typography.bodyMedium, color = gold.onGoldContainer)
    }
}

/** Centered column used by every lock screen; edge to edge, scrolls at large font sizes. */
@Composable
internal fun LockLayout(
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(if (dark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
    ) { content() }
}

/** The PIN flow's three steps. Change PIN starts at [CURRENT]. */
enum class PinStep { CURRENT, CREATE, CONFIRM }

/**
 * Board "Set App Lock": Create a PIN → Confirm PIN → Finish (and, for Change PIN, the current PIN first). A mismatch
 * shakes and starts the confirm step again. [onDone] runs after the new PIN is saved.
 */
@Suppress("CyclomaticComplexMethod") // Three steps, each with its own title, text and check.
@Composable
fun SetPinScreen(
    lock: AppLock,
    change: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(if (change) PinStep.CURRENT else PinStep.CREATE) }
    var entry by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var shake by rememberSaveable { mutableIntStateOf(0) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val full = entry.length == PinAttempts.PIN_LENGTH

    fun next() {
        when (step) {
            PinStep.CURRENT -> {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { lock.pins.check(entry) } == PinCheck.Ok
                    busy = false
                    if (ok) {
                        step = PinStep.CREATE
                        error = null
                    } else {
                        error = R.string.pin_incorrect
                        shake++
                    }
                    entry = ""
                }
            }
            PinStep.CREATE -> {
                first = entry
                entry = ""
                error = null
                step = PinStep.CONFIRM
            }
            PinStep.CONFIRM ->
                if (entry != first) {
                    error = R.string.pin_mismatch
                    shake++
                    entry = ""
                } else {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { lock.pins.setPin(first) }
                        lock.enableWithPin()
                        busy = false
                        onDone()
                    }
                }
        }
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KinlyTopBar(title = stringResource(if (change) R.string.pin_change else R.string.pin_set_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            SparkIllustration(Icons.Outlined.Lock, size = 96.dp)
            Text(
                stringResource(
                    when (step) {
                        PinStep.CURRENT -> R.string.pin_current_title
                        PinStep.CREATE -> R.string.pin_create_title
                        PinStep.CONFIRM -> R.string.pin_confirm_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(
                    when (step) {
                        PinStep.CURRENT -> R.string.pin_current_body
                        PinStep.CREATE -> R.string.pin_create_body
                        PinStep.CONFIRM -> R.string.pin_confirm_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PinDots(entry.length, shake, error = error != null)
            error?.let { InlineError(stringResource(it)) }
            PinPad(
                enabled = !busy,
                onDigit = { d -> if (entry.length < PinAttempts.PIN_LENGTH) entry += d },
                onBackspace = { entry = entry.dropLast(1) },
            )
            HButton(onClick = ::next, enabled = full && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (step == PinStep.CONFIRM) R.string.pin_finish else R.string.pin_continue))
            }
        }
    }
}

/**
 * Board "Forgot PIN": the only way back in without the PIN is to sign in again. [onSignIn] removes the PIN and
 * signs out (the normal sign-out, which first warns about anything not yet synced); the login screen follows.
 */
@Composable
fun ForgotPinScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KinlyTopBar(title = stringResource(R.string.pin_forgot_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            SparkIllustration(Icons.Outlined.ManageAccounts)
            Text(stringResource(R.string.pin_reset_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.pin_reset_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            HButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pin_sign_in_again)) }
            HOutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pin_back_to_pin)) }
        }
    }
}

/** Shown for the old fingerprint-only lock (set before the Kinly PIN existed). */
@Composable
fun SystemLockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    LockLayout {
        KinlyLogo()
        androidx.compose.material3.Icon(
            Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .size(72.dp)
                    .clickable(onClick = onUnlock),
        )
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
        HButton(onClick = onUnlock, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.lock_unlock)) }
    }
}
