plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.hisaab.android.compose)
    alias(libs.plugins.hisaab.hilt)
}

android {
    namespace = "com.agniv.hisaab.feature.family"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.kotlinx.coroutines.test)
}
