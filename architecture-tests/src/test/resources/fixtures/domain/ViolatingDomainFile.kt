// Fixture for ImportRulesSelfTest. NOT compiled: it lives under test
// resources. Every import below except the last three is forbidden in domain.
package com.moyi.app.domain.fixture

import android.os.Build
import androidx.lifecycle.ViewModel
import app.cash.sqldelight.db.SqlDriver
import com.moyi.app.data.network.ModuleInfo
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import org.koin.core.module.Module
import platform.UIKit.UIDevice
import com.moyi.app.core.Result
import kotlinx.coroutines.flow.Flow
import androidxish.Fake

// Kotlin 2.x syntax, so the self-test also proves Konsist parses it.
@JvmInline
value class EntryId(val value: String)

sealed interface Outcome {
    data object Done : Outcome
    data class Failed(val reason: String) : Outcome
}

context(id: EntryId)
fun describe(): String = when (val n = id.value.length) {
    0 -> "empty"
    else -> "length $n"
}
