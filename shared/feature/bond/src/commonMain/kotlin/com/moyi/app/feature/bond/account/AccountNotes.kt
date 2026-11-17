package com.moyi.app.feature.bond.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A note Account shows as a card at its top until the person taps OK (docs/missing-components.md §87). */
enum class AccountNote {
    /**
     * A reset link opened while signed in was another account's: that account's new password
     * is saved, and this phone is signed in as before (§84).
     */
    OtherAccountPasswordSaved,
}

/**
 * The note waiting on Account, one app-wide holder (a Koin single): the shell posts it when
 * the reset reports another account's link, and Account's ViewModel, which may not exist yet,
 * reads it. It stays until [dismiss] (OK), so leaving Account and coming back still shows it.
 * The shell clears it when the session ends, so the next person to sign in never sees it.
 * Held in memory only: a process restart drops it, and the email the backend sends that
 * account says the same.
 */
class AccountNotes {
    private val current = MutableStateFlow<AccountNote?>(null)
    val note: StateFlow<AccountNote?> = current.asStateFlow()

    fun post(note: AccountNote) {
        current.value = note
    }

    fun dismiss() {
        current.value = null
    }
}
