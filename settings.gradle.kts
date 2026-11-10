pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Lets Gradle download the pinned daemon JDK (gradle/gradle-daemon-jvm.properties).
// JDK 21 because detekt 1.23 crashes on JDK 25.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hisaab"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:domain")
include(":core:designsystem")
include(":core:database")
include(":core:network")
include(":core:data")
include(":core:ml")
// Feature modules are added when each feature is built.
include(":feature:expenses")
include(":feature:auth")
include(":feature:family")
include(":feature:loans")
