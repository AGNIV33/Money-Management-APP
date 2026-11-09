plugins {
    alias(libs.plugins.hisaab.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.kotlinx.coroutines.test)
}
