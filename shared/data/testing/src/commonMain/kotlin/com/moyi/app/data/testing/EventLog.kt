@file:OptIn(ExperimentalAtomicApi::class)

package com.moyi.app.data.testing

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * An ordered, thread-safe record of what happened, written by the fake store
 * and the mock server, so a test can assert ORDER ("the token was stored
 * before any request used it") rather than only counts.
 */
class EventLog {
    private val entries = AtomicReference(emptyList<String>())

    fun add(event: String) {
        while (true) {
            val current = entries.load()
            if (entries.compareAndSet(current, current + event)) return
        }
    }

    fun snapshot(): List<String> = entries.load()
}
