package com.moyi.app.data.session

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/** Keeps every Kermit line so a test can check what was logged, and that no token was. */
class CapturingLogWriter : LogWriter() {
    data class Entry(val severity: Severity, val tag: String, val message: String)

    val entries = mutableListOf<Entry>()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        entries += Entry(severity, tag, message)
    }

    fun tagged(tag: String): List<Entry> = entries.filter { it.tag == tag }
}
