plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.hisaab.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.agniv.hisaab.core.database"
}

room {
    // Exported schemas are checked in so future migrations can be tested against them.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.common)
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
