plugins {
    alias(libs.plugins.hisaab.jvm.library)
}

// Pure Kotlin: models, use cases, repository interfaces. Filled in Phase 1.
dependencies {
    api(projects.core.common)
}
