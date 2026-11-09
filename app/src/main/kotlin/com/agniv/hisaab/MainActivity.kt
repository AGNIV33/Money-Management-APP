package com.agniv.hisaab

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.feature.auth.LoginScreen
import com.agniv.hisaab.feature.auth.OnboardingScreen
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeScreen
import com.agniv.hisaab.feature.family.info.FamilySheet
import com.agniv.hisaab.feature.family.setup.FamilySetupScreen
import dagger.hilt.android.AndroidEntryPoint
import com.agniv.hisaab.feature.family.R as FamilyR

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val settings by lazy { AppSettings(applicationContext) }

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
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                var showFamily by rememberSaveable { mutableStateOf(false) }
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
                        ExpensesHomeScreen(
                            title = stringResource(R.string.app_name),
                            headerAction = {
                                TextButton(onClick = { showFamily = true }) {
                                    Text("👪 " + stringResource(FamilyR.string.family_button))
                                }
                            },
                        )
                        if (showFamily) FamilySheet(onDismiss = { showFamily = false })
                    }
                }
            }
        }
    }
}
