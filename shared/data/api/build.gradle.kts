plugins {
    id("moyi.kmp.library")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.openapiGenerator)
}

// Generated code, plus ONE hand-written file: src/commonMain's PartnerEntryWire,
// which the generator is told to use for a `oneOf` it cannot generate (row 10 of
// docs/backend-contract-gaps.md). The OpenAPI client is generated from the vendored
// contract into build/generated/openapi (never committed, never edited), and only
// its src/commonMain/kotlin is compiled here. See README.md.
val contract = rootProject.layout.projectDirectory.file("contracts/openapi.json")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set(contract.asFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    // A removed schema must not leave its stale class behind.
    cleanupOutput.set(true)
    packageName.set("com.moyi.app.api")
    // No serializationLibrary: `multiplatform` implies kotlinx.serialization,
    // and naming it again emits every annotation twice.
    // No enumUnknownDefaultCase: with this library it adds a dead case and an
    // unknown value still throws; error codes are read raw instead (network).
    // No typeMappings: 7.26 maps date-time to kotlin.time.Instant, date to
    // kotlinx.datetime.LocalDate, uuid and uri to String.
    configOptions.set(
        mapOf(
            "dateLibrary" to "kotlinx-datetime",
            "omitGradleWrapper" to "true",
        ),
    )
    // docs/backend-contract-gaps.md, row 10: the partner entry's `oneOf` generates
    // a sealed class with no subclasses, which throws on every decode. The inline
    // schema (its raw name, not the generated `DayResponsePartnerEntry`) is mapped
    // to the one hand-written file in this module, a dispatcher over the GENERATED
    // EntryResponse, LockedEntryResponse and ErasedEntryResponse.
    schemaMappings.set(mapOf("DayResponse_partnerEntry" to "com.moyi.app.api.wire.PartnerEntryWire"))
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

openApiValidate {
    inputSpec.set(contract.asFile.absolutePath)
}

tasks.named("check") { dependsOn(tasks.openApiValidate) }

// The generator writes a whole Gradle project; only its common Kotlin is ours.
// It is copied into a directory of its own because AGP 9 probes the folders
// next to every Kotlin source dir (src/commonMain/baselineProfiles and the
// like), and inside the generator's tree that probe reads openApiGenerate's
// output without a declared dependency, which Gradle rejects.
val generatedApiSources = tasks.register<Sync>("syncGeneratedApiSources") {
    from(tasks.openApiGenerate.map { layout.buildDirectory.dir("generated/openapi/src/commonMain/kotlin").get() })
    into(layout.buildDirectory.dir("generated/apiSources/kotlin"))
}

kotlin {
    // Generated code is not ours to tidy: it never fails the build on a
    // warning, whatever the convention plugins turn on later. Lint (when it
    // lands) excludes build/ for the same reason.
    compilerOptions {
        allWarningsAsErrors.set(false)
    }

    sourceSets {
        commonMain {
            // A srcDir from a task provider gives every compile task (Android,
            // both iOS targets, metadata) a dependency on the generation.
            kotlin.srcDir(generatedApiSources)
            dependencies {
                // In the generated public API: HttpClient, Instant, LocalDate, @Serializable.
                api(libs.ktor.client.core)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinxJson)
            }
        }
    }
}
