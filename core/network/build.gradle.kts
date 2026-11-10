import java.util.Properties

plugins {
    alias(libs.plugins.hisaab.android.library)
    alias(libs.plugins.hisaab.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase URL + publishable key come from local.properties, which is never committed.
// Only the publishable key may live in the app; the service-role key never does (CLAUDE.md §4.5).
val localProps =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun localProp(key: String): String = localProps.getProperty(key, "")

android {
    namespace = "com.agniv.hisaab.core.network"
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("GOOGLE_WEB_CLIENT_ID")}\"")
    }
}

dependencies {
    implementation(projects.core.common)
    // The live voice connection implements the domain's LiveSession.
    implementation(projects.core.domain)
    api(libs.kotlinx.serialization.json)
    api(platform(libs.supabase.bom))
    api(libs.supabase.auth)
    api(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
