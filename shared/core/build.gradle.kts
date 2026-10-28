import com.moyi.buildlogic.GenerateApiErrorCodesTask

plugins {
    id("moyi.kmp.library")
    id("moyi.dependency.guard")
}

// Core is what domain sees through `api`, so a framework added here would reach
// domain transitively. The guard keeps core to the same allow-list (stdlib,
// coroutines); widen it here deliberately (e.g. Kermit, datetime) when needed.

// ApiErrorCode is generated from the contract's ProblemDetail.code enum, so
// domain and feature code can switch on server codes without seeing the
// generated network client, and a new code never needs a hand edit.
val generateApiErrorCodes = tasks.register<GenerateApiErrorCodesTask>("generateApiErrorCodes") {
    contract.set(rootProject.layout.projectDirectory.file("contracts/openapi.json"))
    packageName.set("com.moyi.app.core")
    outputDir.set(layout.buildDirectory.dir("generated/apiErrorCodes"))
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateApiErrorCodes.flatMap { it.outputDir })
        }
    }
}
