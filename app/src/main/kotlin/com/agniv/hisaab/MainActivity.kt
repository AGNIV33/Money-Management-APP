package com.agniv.hisaab

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.feature.auth.LoginScreen
import com.agniv.hisaab.feature.auth.OnboardingScreen
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeScreen
import com.agniv.hisaab.feature.family.info.FamilySheet
import com.agniv.hisaab.feature.family.setup.FamilySetupScreen
import com.agniv.hisaab.feature.loans.LoansScreen
import com.agniv.hisaab.feature.tools.ToolsScreen
import com.agniv.hisaab.feature.udhaar.UdhaarScreen
import dagger.hilt.android.AndroidEntryPoint
import com.agniv.hisaab.feature.family.R as FamilyR
import com.agniv.hisaab.feature.loans.R as LoansR
import com.agniv.hisaab.feature.tools.R as ToolsR
import com.agniv.hisaab.feature.udhaar.R as UdhaarR

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val settings by lazy { AppSettings(applicationContext) }
    private val lock by lazy { AppLock(this, settings) }

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
        viewModel.start(
            familyName = getString(R.string.local_family_name),
            memberName = getString(R.string.local_member_name),
            alreadySeenOnboarding = settings.onboardingDone,
        )
        setContent {
            HisaabTheme {
                if (lock.locked) {
                    LockScreen(onUnlock = { lock.authenticate() })
                } else {
                    AppRoot()
                }
            }
        }
    }

    @Composable
    private fun AppRoot() {
        val screen by viewModel.screen.collectAsStateWithLifecycle()
        var showFamily by rememberSaveable { mutableStateOf(false) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
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
            RootScreen.HOME -> {
                HomeTabs(onFamily = { showFamily = true }, onSettings = { showSettings = true })
                if (showFamily) FamilySheet(onDismiss = { showFamily = false })
                if (showSettings) {
                    SettingsSheet(
                        settings = settings,
                        lock = lock,
                        onLanguage = {
                            settings.language = it
                            recreate()
                        },
                        onDismiss = { showSettings = false },
                    )
                }
            }
        }
    }
}

/** Home with the bottom tab bar. Inner screens have their own Scaffolds; this one only adds the bar. */
@Composable
private fun HomeTabs(
    onFamily: () -> Unit,
    onSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.emoji) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                HomeTab.HOME ->
                    ExpensesHomeScreen(
                        title = stringResource(R.string.app_name),
                        headerAction = {
                            Row {
                                TextButton(onClick = onSettings) { Text("⚙️") }
                                TextButton(onClick = onFamily) {
                                    Text("👪 " + stringResource(FamilyR.string.family_button))
                                }
                            }
                        },
                    )
                HomeTab.LOANS -> LoansScreen()
                HomeTab.UDHAAR -> UdhaarScreen()
                HomeTab.TOOLS -> ToolsScreen()
            }
        }
    }
}

private enum class HomeTab(
    val emoji: String,
    val label: Int,
) {
    HOME("🏠", R.string.tab_home),
    LOANS("🏦", LoansR.string.loans_tab),
    UDHAAR("🤝", UdhaarR.string.udhaar_tab),
    TOOLS("🧮", ToolsR.string.tools_tab),
}
