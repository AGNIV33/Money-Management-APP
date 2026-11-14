// Generates the Baseline + Startup Profile for :app and measures cold start (Macrobenchmark).
// Needs a phone or emulator: ./gradlew :app:generateBaselineProfile  (see DOCS/IMPLEMENTATION_PLAN.md 5.1)
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.agniv.hisaab.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 28 // Macrobenchmark needs API 28+; the app itself still supports 26.
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Use whatever phone or emulator is connected; nothing is started by the build.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
