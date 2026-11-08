package com.moyi.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Downloads Zetetic's `SQLCipher.xcframework.zip`, refuses it unless its SHA-256 is
 * [sha256], and unpacks it into [outputDir].
 *
 * It is the same zip, by the same checksum, that the Xcode app gets through the
 * `SQLCipher.swift` Swift package (`iosApp.xcodeproj`, Package.resolved). The app
 * links it through SPM; this copy is for the Kotlin/Native test executables, which
 * Xcode never builds (`moyi.kmp.library` links them against it). Unpacked with
 * `ditto` so the framework's symlinks and code signature survive.
 */
abstract class SqlCipherXcframeworkTask @Inject constructor(
    private val exec: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val zip = File(temporaryDir, "SQLCipher.xcframework.zip")
        URI(url.get()).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        val actual = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == sha256.get()) { "SQLCipher.xcframework.zip checksum mismatch: expected ${sha256.get()}, got $actual" }
        exec.exec { commandLine("ditto", "-x", "-k", zip.absolutePath, out.absolutePath) }
        zip.delete()
    }
}
