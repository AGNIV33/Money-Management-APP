plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.hisaab.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.agniv.hisaab.core.data"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.core.common)
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
