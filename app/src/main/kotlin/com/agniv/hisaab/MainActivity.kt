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
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash up until the phone-only family exists, so Home never flashes empty.
        installSplashScreen().setKeepOnScreenCondition { !viewModel.ready.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.start(
            familyName = getString(R.string.local_family_name),
            memberName = getString(R.string.local_member_name),
        )
        setContent {
            HisaabTheme {
                val ready by viewModel.ready.collectAsStateWithLifecycle()
                if (ready) ExpensesHomeScreen(title = stringResource(R.string.app_name))
            }
        }
    }
}
