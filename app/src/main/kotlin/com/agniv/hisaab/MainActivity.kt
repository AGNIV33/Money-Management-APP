package com.agniv.hisaab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.designsystem.component.HSummaryCard
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HisaabTheme {
                PlaceholderHome()
            }
        }
    }
}

// Phase 0 placeholder; replaced by the NavHost + real Home in Phase 1.
@Composable
private fun PlaceholderHome() {
    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            HSummaryCard(
                title = stringResource(R.string.home_month_spent),
                amount = Money.ZERO,
                subtitle = stringResource(R.string.home_coming_soon),
            )
        }
    }
}

@Preview
@Composable
private fun PlaceholderHomePreview() {
    HisaabTheme { PlaceholderHome() }
}
