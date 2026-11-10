plugins {
    alias(libs.plugins.hisaab.android.library)
}

android {
    namespace = "com.agniv.hisaab.core.ml"
}

dependencies {
    implementation(projects.core.common)
    // Voice results use the app's PayMode; domain is plain Kotlin, so this adds nothing heavy.
    implementation(projects.core.domain)
}
