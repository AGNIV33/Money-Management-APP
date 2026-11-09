plugins {
    alias(libs.plugins.hisaab.android.library)
}

android {
    namespace = "com.agniv.hisaab.core.data"
}

// Filled in Phase 1 (see IMPLEMENTATION_PLAN.md).
dependencies {
    implementation(projects.core.common)
}
