import java.util.Properties

plugins {
    alias(libs.plugins.hisaab.android.application)
    alias(libs.plugins.hisaab.android.compose)
    alias(libs.plugins.hisaab.hilt)
    alias(libs.plugins.hilt)
}

// Supabase URL + publishable (anon) key come from local.properties, which is never committed.
// Only the anon key may live in the app; the service-role key never does (CLAUDE.md §4.5).
val localProps =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun localProp(key: String): String = localProps.getProperty(key, "")

android {
    namespace = "com.agniv.hisaab"

    defaultConfig {
        applicationId = "com.agniv.hisaab"
        versionCode = 1
        versionName = "0.0.1"

        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
}
