package com.moyi.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails when the backend's contract and the vendored copy differ byte for byte.
 *
 * The backend file is [Internal], not an input: it may be absent (CI, a fresh
 * clone without the sibling, a worktree), and an absent input file fails
 * Gradle's validation before the action can decide to skip. The task declares
 * no outputs, so it is never up to date and always reads both files afresh.
 * Only files are touched at execution, never `Project`, so the configuration
 * cache can store it.
 */
@DisableCachingByDefault(because = "A byte comparison of two files is cheaper than a cache lookup")
abstract class CheckContractInSyncTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val vendored: RegularFileProperty

    @get:Internal
    abstract val backend: RegularFileProperty

    @TaskAction
    fun check() {
        val backendFile = backend.get().asFile
        val vendoredFile = vendored.get().asFile
        if (!backendFile.isFile) {
            logger.lifecycle(
                "checkContractInSync: skipped, no backend contract at ${backendFile.path}. " +
                    "Set moyi.backendDir (local.properties or -P) to compare against a checkout elsewhere.",
            )
            return
        }
        if (!backendFile.readBytes().contentEquals(vendoredFile.readBytes())) {
            throw GradleException(
                "contracts/openapi.json differs from ${backendFile.path}.\n" +
                    "The vendored contract is a byte copy and is never hand-edited. Run `./gradlew syncContract` " +
                    "and open a PR for the contract change on its own: that diff is the API changing.",
            )
        }
        logger.lifecycle("checkContractInSync: contracts/openapi.json matches ${backendFile.path}.")
    }
}
