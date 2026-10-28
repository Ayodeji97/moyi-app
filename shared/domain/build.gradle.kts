plugins {
    id("moyi.kmp.library")
    id("moyi.dependency.guard")
}

// Domain depends on no framework (CLAUDE.md). checkDependencyGuard, part of
// `check`, fails the build if anything beyond these is declared here.
moyiDependencyGuard {
    allowedProjects.add(":shared:core")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
