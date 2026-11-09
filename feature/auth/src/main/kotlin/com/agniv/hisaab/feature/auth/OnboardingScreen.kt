package com.agniv.hisaab.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.Spacing

private class Slide(
    val emoji: String,
    val title: Int,
    val body: Int,
)

private val slides =
    listOf(
        Slide("🏠", R.string.onboarding_1_title, R.string.onboarding_1_body),
        Slide("⏰", R.string.onboarding_2_title, R.string.onboarding_2_body),
        Slide("💡", R.string.onboarding_3_title, R.string.onboarding_3_body),
    )

/**
 * First launch: pick a language (big buttons, DESIGN.md §7.1), then three short slides. Skip is always there.
 * [onLanguage] gets "en" or "hi"; the app recreates itself in that language.
 */
@Composable
fun OnboardingScreen(
    onLanguage: (String) -> Unit,
    onFinish: () -> Unit,
) {
    // 0 = language, 1..n = slides. Saved so rotating the phone doesn't restart it.
    var step by rememberSaveable { mutableIntStateOf(0) }
    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (step == 0) {
                    LanguagePick(onLanguage = {
                        // Step first: choosing a language recreates the screen, and the step must survive that.
                        step = 1
                        onLanguage(it)
                    })
                } else {
                    SlideContent(slides[step - 1])
                }
            }
            if (step > 0) {
                Dots(current = step - 1, total = slides.size)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.l),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
                    Button(
                        onClick = { if (step < slides.size) step += 1 else onFinish() },
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(stringResource(if (step < slides.size) R.string.onboarding_next else R.string.onboarding_start))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePick(onLanguage: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            // Shown in both languages on purpose: the reader hasn't chosen one yet.
            "Choose your language\nअपनी भाषा चुनें",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { onLanguage("en") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
        ) { Text("English", style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick = { onLanguage("hi") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
        ) { Text("हिंदी", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun SlideContent(slide: Slide) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(slide.emoji, style = MaterialTheme.typography.displayLarge)
        Text(
            stringResource(slide.title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(stringResource(slide.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Dots(
    current: Int,
    total: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
            )
        }
    }
}
