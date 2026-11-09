plugins {
    alias(libs.plugins.hisaab.jvm.library)
}

// Pure Kotlin: models, use cases, repository interfaces. No Android here.
dependencies {
    api(projects.core.common)
    implementation(libs.javax.inject)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
