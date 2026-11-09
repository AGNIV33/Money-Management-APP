plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.hisaab.android.compose)
    alias(libs.plugins.hisaab.hilt)
}

android {
    namespace = "com.agniv.hisaab.feature.auth"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    testImplementation(libs.kotlinx.coroutines.test)
}
