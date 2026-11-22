package com.moyi.app.feature.today.archive

/**
 * Whether this build offers "Remember when" (the Archive's fourth chip, FR-204).
 *
 * **Decided by the build type, for now** (owner ruling 2026-11-18): on in debug builds, off in
 * release builds (Android `release`, iOS `Release`), because production cannot yet say whether
 * a question is answered by meaning or by its words alone (contract gaps row 49). The app binds
 * it in `KoinInit` from the platform's build type; unbound, it is off.
 *
 * A later PR replaces it with the bond's `semanticSearchActive`, once the backend adds that
 * field and the contract is synced.
 */
data class RememberWhenGate(val offered: Boolean)
