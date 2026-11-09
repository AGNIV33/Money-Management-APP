plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.hisaab.android.compose)
}

android {
    namespace = "com.agniv.hisaab.core.designsystem"
}

dependencies {
    api(projects.core.common)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
}
