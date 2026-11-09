package com.agniv.hisaab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.feature.auth.LoginScreen
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash stays until we know whether a saved login exists, so Login never flashes for signed-in users.
        installSplashScreen().setKeepOnScreenCondition { viewModel.screen.value == RootScreen.SPLASH }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.start(
            familyName = getString(R.string.local_family_name),
            memberName = getString(R.string.local_member_name),
        )
        setContent {
            HisaabTheme {
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                when (screen) {
                    RootScreen.SPLASH -> Unit
                    RootScreen.LOGIN -> LoginScreen()
                    RootScreen.HOME -> ExpensesHomeScreen(title = stringResource(R.string.app_name))
                }
            }
        }
    }
}
