package com.moyi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * `moyi.contract`, applied to the root project: the vendored API contract.
 *
 * - `syncContract` copies `<backendDir>/contracts/openapi.json` over
 *   `contracts/openapi.json`. It only reads the backend.
 * - `checkContractInSync` (part of `check`) fails if the two differ, and skips
 *   with a log line when the backend checkout is absent, as on CI.
 *
 * `backendDir` is `moyi.backendDir` from `-P` or `local.properties`, else the
 * sibling `../moyi-backend`. A git worktree of this repo is not next to the
 * backend, so it sets the property.
 */
class ContractConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(this == rootProject) { "moyi.contract is applied to the root project only" }
        pluginManager.apply("base")

        // Captured as a value: a lambda that reads `layout` would capture the
        // Project, which the configuration cache cannot store.
        val rootDir = layout.projectDirectory
        val vendored = rootDir.file("contracts/openapi.json")
        val backendDir = moyiLocalProperty("moyi.backendDir")
            .map { rootDir.dir(it) }
            .orElse(rootDir.dir("../moyi-backend"))
        val backendContract = backendDir.map { it.file("contracts/openapi.json") }

        // A plain task rather than a Copy: a Copy whose source is missing ends
        // NO-SOURCE and reads as success, so a sync that did nothing looked done.
        tasks.register("syncContract") {
            group = "contract"
            description = "Copies the backend's contracts/openapi.json into contracts/ (read-only on the backend)."
            val source = backendContract
            val target = vendored
            doLast {
                val from = source.get().asFile
                check(from.isFile) { "No backend contract at $from. Set moyi.backendDir in local.properties." }
                from.copyTo(target.asFile, overwrite = true)
            }
        }

        val checkTask = tasks.register<CheckContractInSyncTask>("checkContractInSync") {
            group = "verification"
            description = "Fails if contracts/openapi.json differs from the backend's copy."
            this.vendored.set(vendored)
            backend.set(backendContract)
        }
        tasks.named("check") { dependsOn(checkTask) }
        Unit
    }
}
