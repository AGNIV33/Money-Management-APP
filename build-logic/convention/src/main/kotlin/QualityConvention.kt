import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

private const val DETEKT_KOTLIN_VERSION = "2.0.21"

internal fun Project.applyQualityPlugins() {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }
    // detekt 1.23 only understands JVM targets up to 22; the build JDK (25) would leak in otherwise.
    tasks.withType<Detekt>().configureEach { jvmTarget = "17" }
    tasks.withType<DetektCreateBaselineTask>().configureEach { jvmTarget = "17" }
    // detekt 1.23 is built on Kotlin 2.0 and refuses to run if the project's newer Kotlin leaks in.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(DETEKT_KOTLIN_VERSION)
        }
    }
}
