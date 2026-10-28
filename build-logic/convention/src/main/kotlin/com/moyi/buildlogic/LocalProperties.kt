package com.moyi.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.StringReader
import java.util.Properties

/**
 * A Moyi build setting: the Gradle property `-P<name>` if given, else `<name>`
 * from the root `local.properties`, else absent.
 *
 * `local.properties` is read through `providers.fileContents`, so the
 * configuration cache records the file as an input and a changed value
 * invalidates the cached configuration instead of being silently ignored.
 */
fun Project.moyiLocalProperty(name: String): Provider<String> {
    val fromFile = providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text -> Properties().apply { load(StringReader(text)) }.getProperty(name)?.trim().orEmpty() }
        .filter { it.isNotEmpty() }
    return providers.gradleProperty(name).orElse(fromFile)
}
