package com.agniv.hisaab

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.feature.assistant.AssistantScreen
import com.agniv.hisaab.feature.auth.LoginScreen
import com.agniv.hisaab.feature.auth.OnboardingScreen
import com.agniv.hisaab.feature.family.setup.FamilySetupScreen
import com.agniv.hisaab.lock.ForgotPinScreen
import com.agniv.hisaab.lock.PinLockScreen
import com.agniv.hisaab.lock.ResetBySignIn
import com.agniv.hisaab.lock.SystemLockScreen
import com.agniv.hisaab.navigation.KinlyNavHost
import com.agniv.hisaab.navigation.Tab
import com.agniv.hisaab.navigation.TabContent
import com.agniv.hisaab.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val settings by lazy { AppSettings(applicationContext) }
    private val lock by lazy { AppLock(this, settings) }

    // Set when a smart alert or report notification is tapped; Home opens the Insights sheet, then clears it.
    private var openInsights by mutableStateOf(false)

    // Settings → Theme; read here so the whole app switches at once.
    private var themeMode by mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        lock.onStart()
    }

    override fun onStop() {
        super.onStop()
        lock.onStop()
    }

    // The chosen language applies to every screen, before anything is drawn.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings(newBase).wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash stays until we know whether a saved login exists, so Login never flashes for signed-in users.
        installSplashScreen().setKeepOnScreenCondition { viewModel.screen.value == RootScreen.SPLASH }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openInsights = intent?.getBooleanExtra(EXTRA_OPEN_INSIGHTS, false) == true
        themeMode = settings.theme
        viewModel.start(
            familyName = getString(R.string.local_family_name),
            memberName = getString(R.string.local_member_name),
            alreadySeenOnboarding = settings.onboardingDone,
        )
        setContent {
            val dark =
                when (themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }
            HisaabTheme(darkTheme = dark) {
                if (lock.locked) LockGate() else AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_INSIGHTS, false)) openInsights = true
    }

    /** What shows while the app is locked: the PIN keypad (or the old fingerprint-only prompt), and Forgot PIN. */
    @Composable
    private fun LockGate() {
        if (!lock.usesPin) {
            SystemLockScreen(onUnlock = { lock.authenticate() })
            return
        }
        var forgot by rememberSaveable { mutableStateOf(false) }
        var resetting by rememberSaveable { mutableStateOf(false) }
        if (forgot) {
            ForgotPinScreen(onBack = { forgot = false }, onSignIn = { resetting = true })
        } else {
            PinLockScreen(lock, onForgot = { forgot = true })
        }
        if (resetting) {
            ResetBySignIn(
                lock = lock,
                localFamilyName = getString(R.string.local_family_name),
                localMemberName = getString(R.string.local_member_name),
                onCancel = { resetting = false },
            )
        }
    }

    @Composable
    private fun AppRoot() {
        val screen by viewModel.screen.collectAsStateWithLifecycle()
        var assistantConsent by remember { mutableStateOf(settings.assistantConsent) }
        var avatar by remember { mutableStateOf(settings.avatar) }
        when (screen) {
            RootScreen.SPLASH -> Unit
            RootScreen.ONBOARDING ->
                OnboardingScreen(
                    onLanguage = {
                        settings.language = it
                        recreate()
                    },
                    onFinish = {
                        settings.onboardingDone = true
                        viewModel.finishOnboarding()
                    },
                )
            RootScreen.LOGIN -> LoginScreen()
            RootScreen.FAMILY_SETUP -> FamilySetupScreen()
            RootScreen.HOME ->
                KinlyNavHost(
                    settings = settings,
                    lock = lock,
                    avatar = avatar,
                    onAvatar = {
                        settings.avatar = it
                        avatar = it
                    },
                    onLanguage = {
                        settings.language = it
                        recreate()
                    },
                    onTheme = {
                        settings.theme = it
                        themeMode = it
                    },
                    onAccountDeleted = {
                        // The phone's own settings tied to this person go too; language stays so the app still reads right.
                        settings.avatar = null
                        avatar = null
                        settings.assistantConsent = false
                        assistantConsent = false
                        lock.disable()
                        // Exported files and bill photos waiting in the cache are personal data too.
                        java.io.File(cacheDir, "exports").deleteRecursively()
                        java.io.File(cacheDir, "scans").deleteRecursively()
                    },
                    content =
                        TabContent(
                            assistant = {
                                AssistantScreen(
                                    consentGiven = assistantConsent,
                                    onConsent = {
                                        settings.assistantConsent = true
                                        assistantConsent = true
                                    },
                                    onDismiss = {},
                                )
                            },
                            settings = {
                                SettingsScreen(
                                    settings = settings,
                                    avatar = avatar,
                                    actions = it,
                                )
                            },
                        ),
                    openTab = if (openInsights) Tab.INSIGHTS else null,
                    onTabOpened = { openInsights = false },
                )
        }
    }

    companion object {
        const val EXTRA_OPEN_INSIGHTS = "open_insights"
    }
}
