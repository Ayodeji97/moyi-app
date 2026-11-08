package com.moyi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * `moyi.sqlcipher`, applied to the root project: registers [SQLCIPHER_TASK], which
 * fetches SQLCipher for the Kotlin/Native test executables (see
 * [SqlCipherXcframeworkTask]). Registered once, at the root, so every module's
 * tests share one download.
 */
class SqlCipherConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        check(target == target.rootProject) { "moyi.sqlcipher belongs on the root project" }
        target.tasks.register(SQLCIPHER_TASK.removePrefix(":"), SqlCipherXcframeworkTask::class.java) {
            group = "build setup"
            description = "Downloads and verifies SQLCipher.xcframework $SQLCIPHER_VERSION for the iOS test executables."
            url.set(SQLCIPHER_XCFRAMEWORK_URL)
            sha256.set(SQLCIPHER_XCFRAMEWORK_SHA256)
            outputDir.set(sqlCipherDirectory(target.rootDir))
            // Apple binaries link only on macOS. Elsewhere (CI is Linux) the iOS link
            // tasks that depend on this are skipped, and so is this; `ditto` is macOS's.
            onlyIf("iOS binaries link only on macOS") { System.getProperty("os.name").startsWith("Mac") }
        }
    }
}

// Bumped together with the SQLCipher.swift package pin in iosApp.xcodeproj (and its
// Package.resolved): the checksum is the one in that package's Package.swift, so the
// tests run against the very binary the app ships. sqlcipher-android in the version
// catalog carries the same SQLCipher version.
internal const val SQLCIPHER_VERSION = "4.20.0"
internal const val SQLCIPHER_XCFRAMEWORK_URL =
    "https://github.com/sqlcipher/SQLCipher.swift/releases/download/$SQLCIPHER_VERSION/SQLCipher.xcframework.zip"
internal const val SQLCIPHER_XCFRAMEWORK_SHA256 = "1505174fe7f8a0fe81d6ebf87a64b17701b500c12a445ef6b68b9159f012d747"
internal const val SQLCIPHER_TASK = ":sqlcipherXcframework"

internal fun sqlCipherDirectory(rootDir: File): File = File(rootDir, "build/sqlcipher/$SQLCIPHER_VERSION")
