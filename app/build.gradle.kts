plugins {
    alias(libs.plugins.hisaab.android.application)
    alias(libs.plugins.hisaab.android.compose)
    alias(libs.plugins.hisaab.hilt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.agniv.hisaab"

    defaultConfig {
        applicationId = "com.agniv.hisaab"
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        localeFilters += listOf("en", "hi")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.feature.expenses)
    implementation(projects.feature.auth)
    implementation(projects.feature.family)
    implementation(projects.feature.loans)
    implementation(projects.feature.tools)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
}
