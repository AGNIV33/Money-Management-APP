plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.hisaab.android.compose)
    alias(libs.plugins.hisaab.hilt)
}

android {
    namespace = "com.agniv.hisaab.feature.insights"
}

// Insights sheet, report card, AI tips and the optional income card. Home shows its card through a slot,
// so feature:expenses does not depend on this module (no feature-to-feature links).
dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.ml)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
