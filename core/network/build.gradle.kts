plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.agniv.hisaab.core.network"
}

// DTOs now; the supabase-kt client is added with login (task 1.2/1.3).
dependencies {
    implementation(projects.core.common)
    api(libs.kotlinx.serialization.json)
}
