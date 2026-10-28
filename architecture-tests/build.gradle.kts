import org.gradle.api.tasks.PathSensitivity

// Konsist reads source files, so it runs on the JVM, outside the KMP source
// sets. The rules live here and look at shared/** as plain files.
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platformLauncher)
}

tasks.test {
    useJUnitPlatform()
    // The tests read ../shared/**/src, which Gradle does not otherwise know is
    // an input. Without this the build cache would replay an old pass after a
    // shared source changed and broke a rule.
    inputs.files(
        fileTree(rootDir.resolve("shared")) {
            include("**/src/**")
            exclude("**/build/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("sharedSources")
}
