plugins {
    `kotlin-dsl`
}

group = "com.moyi.buildlogic"

// compileOnly: the plugins themselves are put on the build's classpath once, by
// the root build.gradle.kts (`apply false`). Compiling against them here and
// shipping them again would load a second copy in another classloader.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "moyi.kmp.library"
            implementationClass = "com.moyi.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "moyi.kmp.compose"
            implementationClass = "com.moyi.buildlogic.KmpComposeConventionPlugin"
        }
        register("dependencyGuard") {
            id = "moyi.dependency.guard"
            implementationClass = "com.moyi.buildlogic.DependencyGuardConventionPlugin"
        }
    }
}
