package com.moyi.app.domain.bond

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * How a bond is searched, as the last answer that carried the bond said.
 *
 * - [enabled]: the couple's setting, `semanticSearchEnabled` (consent: their words may be sent
 *   to the embedding provider).
 * - [active]: `semanticSearchActive` (ADR-0070): search and "Remember when" ask by meaning now.
 *   True only when [enabled] is on **and** the server can run the meaning half (a provider
 *   configured, a threshold for its model, the operator's switch on). It does not promise every
 *   entry is embedded yet. Production's provider is `none`, so it is false there for every bond.
 */
data class SearchByMeaning(val enabled: Boolean, val active: Boolean)

/**
 * The [SearchByMeaning] of each bond this device has read in this run, for screens that follow it
 * without reading the bond themselves (the archive's search helper, Remember when). The bond
 * repositories [record] it from every answer that carries a bond: `GET /bonds`, `GET /bonds/{id}`,
 * and the writes that answer with the bond.
 *
 * **In memory only.** Unknown (null) until the bond is read in this run, which Today does on every
 * load; a screen treats unknown as "words only", so it never promises more than the search does.
 */
interface SearchByMeaningStatus {
    /** [bondId]'s status, again whenever it changes; null until the bond has been read. */
    fun of(bondId: String): Flow<SearchByMeaning?>

    fun record(bondId: String, status: SearchByMeaning)
}

/** [SearchByMeaningStatus] over a map in memory. */
class InMemorySearchByMeaningStatus : SearchByMeaningStatus {
    private val known = MutableStateFlow<Map<String, SearchByMeaning>>(emptyMap())

    override fun of(bondId: String): Flow<SearchByMeaning?> = known.map { it[bondId] }.distinctUntilChanged()

    override fun record(bondId: String, status: SearchByMeaning) = known.update { it + (bondId to status) }
}
