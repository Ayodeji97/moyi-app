package com.moyi.app.domain.bond

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A bond's shared settings as this device last saw them (owner ask 2026-11-16): what one member
 * changes, the other finds out about the next time the app loads the bond. Only the settings
 * both members share, never who is in the bond, and never a word anyone wrote.
 */
data class SharedSettingsSnapshot(
    val bondId: String,
    val status: BondStatus,
    val name: String,
    val type: BondType,
    val anchorTimezone: String,
    val revealTime: TimeOfDay?,
    val strictMode: Boolean,
    val semanticSearchEnabled: Boolean,
    /** Both members asked to close the box: the 30-day cooling-off runs (`deletionScheduledFor`). */
    val deletionScheduled: Boolean,
    /** `pendingTimezoneChange`: [PendingProposal.value] is the proposed IANA zone. */
    val timezoneProposal: PendingProposal? = null,
    /** `pendingDeletionRequest`. */
    val deletionRequest: PendingRequest? = null,
    /** `pendingSemanticSearchRequest`. */
    val semanticSearchRequest: PendingRequest? = null,
)

/** A proposal with an id (the shared time zone): who made it, what it asks for, and when it lapses. */
data class PendingProposal(val id: String, val value: String, val byMe: Boolean, val expiresAt: Instant)

/**
 * A request without an id (closing the box, search by meaning). One is told from another by
 * who made it and when it lapses, which is seven days after it was made.
 */
data class PendingRequest(val byMe: Boolean, val expiresAt: Instant)

/** The shared settings, as what this device's own write touched (so its own change is not told back to it). */
enum class SharedSetting { NAME, TYPE, REVEAL_TIME, REST_DAYS, SEMANTIC_SEARCH, TIMEZONE, DELETION }

/**
 * One change to tell the person about, as one note on Today. The words never say who made
 * it: a change "the other member" made may have been the person's own, on another phone,
 * and a declined proposal and a withdrawn one look the same from here (the backend does not
 * record who cancelled; docs/backend-contract-gaps.md row 46).
 */
sealed interface SharedSettingNotice {
    val setting: SharedSetting

    /** My proposed zone is the shared one now, from the next Bond-day (BR-6). */
    data class TimezoneAgreed(val timezone: String) : SharedSettingNotice {
        override val setting get() = SharedSetting.TIMEZONE
    }

    /** My proposal is gone before it lapsed and the zone did not move: it stays [timezone]. */
    data class TimezoneKept(val timezone: String) : SharedSettingNotice {
        override val setting get() = SharedSetting.TIMEZONE
    }

    /** The other member's proposal to move to [proposed] is gone before it lapsed, unapplied. */
    data class TimezoneWithdrawn(val proposed: String) : SharedSettingNotice {
        override val setting get() = SharedSetting.TIMEZONE
    }

    /** My request to close the box was agreed: the cooling-off runs. */
    data object DeletionAgreed : SharedSettingNotice {
        override val setting get() = SharedSetting.DELETION
    }

    /** My request to close the box is gone before it lapsed, and the box is open. */
    data object DeletionKeptOpen : SharedSettingNotice {
        override val setting get() = SharedSetting.DELETION
    }

    /** The other member's request to close the box is gone before it lapsed. */
    data object DeletionWithdrawn : SharedSettingNotice {
        override val setting get() = SharedSetting.DELETION
    }

    /** The cooling-off was called off: the box stays. */
    data object DeletionCalledOff : SharedSettingNotice {
        override val setting get() = SharedSetting.DELETION
    }

    /** My request to turn search by meaning back on was agreed. */
    data object SemanticSearchAgreed : SharedSettingNotice {
        override val setting get() = SharedSetting.SEMANTIC_SEARCH
    }

    /** My request to turn search by meaning back on is gone before it lapsed, and it is still off. */
    data object SemanticSearchKeptOff : SharedSettingNotice {
        override val setting get() = SharedSetting.SEMANTIC_SEARCH
    }

    /** The other member's request to turn search by meaning back on is gone before it lapsed. */
    data object SemanticSearchWithdrawn : SharedSettingNotice {
        override val setting get() = SharedSetting.SEMANTIC_SEARCH
    }

    /**
     * The other member asked to turn search by meaning back on, and it waits for this person.
     * Told here because no push says so yet (moyi-backend#205; owner ruling 2026-11-17, 6). The
     * one note that names the other member: the request carries their member id, so it cannot
     * have been the reader's own, from another phone.
     */
    data object SemanticSearchAsked : SharedSettingNotice {
        override val setting get() = SharedSetting.SEMANTIC_SEARCH
    }

    /** Search by meaning was turned off (either member may, alone). */
    data object SemanticSearchTurnedOff : SharedSettingNotice {
        override val setting get() = SharedSetting.SEMANTIC_SEARCH
    }

    data class NameChanged(val name: String) : SharedSettingNotice {
        override val setting get() = SharedSetting.NAME
    }

    /** Null: the day now opens as soon as both have written. */
    data class RevealTimeChanged(val time: TimeOfDay?) : SharedSettingNotice {
        override val setting get() = SharedSetting.REVEAL_TIME
    }

    /** Rest days are the opposite of strict mode (states.md §8). */
    data class RestDaysChanged(val on: Boolean) : SharedSettingNotice {
        override val setting get() = SharedSetting.REST_DAYS
    }

    data class TypeChanged(val type: BondType) : SharedSettingNotice {
        override val setting get() = SharedSetting.TYPE
    }
}

/**
 * What changed between [previous] (as this device last saw the bond) and [current], as of [now].
 * Pure: the rules, with nothing stored or sent.
 *
 * - **Nothing to compare with** ([previous] null): nothing, but for one case. The first look
 *   only remembers, except a live request by the other member to turn search by meaning on:
 *   it waits for the person's answer and nothing else tells them of it (no push yet, #205).
 * - **T-09: never about who is in the bond.** Both readings must be of a bond with two people
 *   in it that has not ended ([BondStatus.ACTIVE] or [BondStatus.PENDING_DELETION]). Leaving
 *   and blocking end the bond and cancel every open proposal (ADR-0030); read as "withdrawn",
 *   that would be a notice that someone left. A partner joining is not a setting either.
 * - **A proposal or request that lapsed** (its `expiresAt` has passed): nothing. Nobody did it.
 * - **The other member's proposal applied** is the person's own agreement (on another phone):
 *   nothing. **My proposal applied**: agreed. **Gone unapplied before it lapsed**: mine, it
 *   stays as it was; theirs, withdrawn. A proposal opening is told by the backend's push, but
 *   for a request to turn search by meaning on, which has none: it is a note here.
 * - **Changes either member makes alone**: the new value.
 *
 * One note per change, in this order: time zone, closing the box, search by meaning, name,
 * reveal time, rest days, relationship type.
 */
fun sharedSettingsNotices(
    previous: SharedSettingsSnapshot?,
    current: SharedSettingsSnapshot,
    now: Instant,
): List<SharedSettingNotice> = diff(previous?.toMillis(), current.toMillis(), now)

/**
 * The snapshot with every time to the millisecond, as a store keeps it (SQLDelight: epoch
 * milliseconds). The server sends microseconds, so a request read again from the server would
 * otherwise never equal the same request read back from the store, and every read would take a
 * live request for a new one (or for one that went).
 */
private fun SharedSettingsSnapshot.toMillis(): SharedSettingsSnapshot = copy(
    timezoneProposal = timezoneProposal?.let { it.copy(expiresAt = it.expiresAt.toMillis()) },
    deletionRequest = deletionRequest?.let { it.copy(expiresAt = it.expiresAt.toMillis()) },
    semanticSearchRequest = semanticSearchRequest?.let { it.copy(expiresAt = it.expiresAt.toMillis()) },
)

private fun Instant.toMillis(): Instant = Instant.fromEpochMilliseconds(toEpochMilliseconds())

private fun diff(
    previous: SharedSettingsSnapshot?,
    current: SharedSettingsSnapshot,
    now: Instant,
): List<SharedSettingNotice> {
    if (previous == null) {
        return listOfNotNull(SharedSettingNotice.SemanticSearchAsked.takeIf { current.status in TwoPeopleOpen && current.waitingSemanticSearchAsk(now) })
    }
    if (previous.status !in TwoPeopleOpen || current.status !in TwoPeopleOpen) return emptyList()
    return buildList {
        timezone(previous, current, now)?.let(::add)
        deletion(previous, current, now)?.let(::add)
        semanticSearch(previous, current, now)?.let(::add)
        if (current.name != previous.name) add(SharedSettingNotice.NameChanged(current.name))
        if (current.revealTime != previous.revealTime) add(SharedSettingNotice.RevealTimeChanged(current.revealTime))
        if (current.strictMode != previous.strictMode) add(SharedSettingNotice.RestDaysChanged(on = !current.strictMode))
        if (current.type != previous.type) add(SharedSettingNotice.TypeChanged(current.type))
    }
}

/** The notes left once the settings this device changed itself are taken out. */
fun List<SharedSettingNotice>.withoutOwn(own: Set<SharedSetting>): List<SharedSettingNotice> =
    filter { it.setting !in own }

/** A bond with both people in it that has not ended: the only one whose changes are told. */
private val TwoPeopleOpen = setOf(BondStatus.ACTIVE, BondStatus.PENDING_DELETION)

private fun timezone(previous: SharedSettingsSnapshot, current: SharedSettingsSnapshot, now: Instant): SharedSettingNotice? {
    val proposal = previous.timezoneProposal ?: return null
    if (current.timezoneProposal?.id == proposal.id) return null
    val applied = current.anchorTimezone == proposal.value && previous.anchorTimezone != proposal.value
    return when {
        proposal.byMe && applied -> SharedSettingNotice.TimezoneAgreed(proposal.value)
        applied -> null
        proposal.expiresAt <= now -> null
        current.anchorTimezone != previous.anchorTimezone -> null
        proposal.byMe -> SharedSettingNotice.TimezoneKept(current.anchorTimezone)
        else -> SharedSettingNotice.TimezoneWithdrawn(proposal.value)
    }
}

private fun deletion(previous: SharedSettingsSnapshot, current: SharedSettingsSnapshot, now: Instant): SharedSettingNotice? {
    val request = previous.deletionRequest
    if (request == null) {
        return SharedSettingNotice.DeletionCalledOff.takeIf { previous.deletionScheduled && !current.deletionScheduled }
    }
    if (current.deletionRequest == request) return null
    val applied = current.deletionScheduled
    return when {
        request.byMe && applied -> SharedSettingNotice.DeletionAgreed
        applied -> null
        request.expiresAt <= now -> null
        request.byMe -> SharedSettingNotice.DeletionKeptOpen
        else -> SharedSettingNotice.DeletionWithdrawn
    }
}

/** The other member's request to turn search by meaning on, still live and still off: it waits for the reader. */
internal fun SharedSettingsSnapshot.waitingSemanticSearchAsk(now: Instant): Boolean {
    val request = semanticSearchRequest ?: return false
    return !request.byMe && request.expiresAt > now && !semanticSearchEnabled
}

private fun semanticSearch(previous: SharedSettingsSnapshot, current: SharedSettingsSnapshot, now: Instant): SharedSettingNotice? {
    // A new ask from the other member (not the one already seen): it waits for the reader's answer.
    if (current.waitingSemanticSearchAsk(now) && current.semanticSearchRequest != previous.semanticSearchRequest) {
        return SharedSettingNotice.SemanticSearchAsked
    }
    val request = previous.semanticSearchRequest
    if (request == null || current.semanticSearchRequest == request) {
        val turnedOff = previous.semanticSearchEnabled && !current.semanticSearchEnabled
        return SharedSettingNotice.SemanticSearchTurnedOff.takeIf { turnedOff }
    }
    val applied = current.semanticSearchEnabled && !previous.semanticSearchEnabled
    return when {
        request.byMe && applied -> SharedSettingNotice.SemanticSearchAgreed
        applied -> null
        request.expiresAt <= now -> null
        current.semanticSearchEnabled -> null
        request.byMe -> SharedSettingNotice.SemanticSearchKeptOff
        else -> SharedSettingNotice.SemanticSearchWithdrawn
    }
}

/** A note waiting to be read, with the id the store gave it. */
data class PendingNotice(val id: Long, val notice: SharedSettingNotice)

/**
 * Where the last-seen settings and the notes waiting to be read are kept: on this device, for
 * the person signed in, wiped with the session. The data layer's to implement.
 */
interface SharedSettingsStore {
    suspend fun snapshot(bondId: String): SharedSettingsSnapshot?

    /** Replaces [snapshot]'s bond's snapshot and adds [notices] after any already waiting, together. */
    suspend fun save(snapshot: SharedSettingsSnapshot, notices: List<SharedSettingNotice>)

    /** The notes waiting for [bondId], oldest first. */
    suspend fun notices(bondId: String): List<PendingNotice>

    suspend fun removeNotice(bondId: String, id: Long)
}

/**
 * Tells the person what changed in a bond's shared settings since this device last saw it
 * (owner ask 2026-11-16). The client half of it: the next time the app loads the bond, one
 * note on Today per change. The real fix is a push to the other member (row 46).
 *
 * The repositories call it: [observe] on every read of a bond, [recordOwn] and
 * [clearOwnRequest] after every write this device makes, so the person's own change never
 * comes back as a note. None of them ever fails the call they sit in.
 */
interface SharedSettingsNotices {
    /** A read of the bond: compares with what was last seen, keeps a note per change, remembers this. */
    suspend fun observe(current: SharedSettingsSnapshot)

    /** This device's own write, answered with [current]: remembered, and changes to [own] are not noted. */
    suspend fun recordOwn(current: SharedSettingsSnapshot, own: Set<SharedSetting>)

    /**
     * This device declined, withdrew or called off [setting]'s waiting request ([SharedSetting.TIMEZONE],
     * [SharedSetting.DELETION] or [SharedSetting.SEMANTIC_SEARCH]). The call answers `204` with no bond, so the remembered one is
     * changed to match, and the next read does not take the person's own answer for the other's.
     */
    suspend fun clearOwnRequest(bondId: String, setting: SharedSetting)

    /** The notes waiting for [bondId], oldest first, again whenever they change. */
    fun notices(bondId: String): Flow<List<PendingNotice>>

    /**
     * [bondId]'s shared settings as this device last read or wrote them, again whenever they
     * change; null before the first read. For screens that follow a shared setting without
     * reading the bond themselves (the archive's search helper follows search by meaning).
     */
    fun snapshot(bondId: String): Flow<SharedSettingsSnapshot?>

    /** The person dismissed the note (or followed it to Bond settings): it is not shown again. */
    suspend fun dismiss(bondId: String, id: Long)
}

/**
 * [SharedSettingsNotices] over a [SharedSettingsStore]. One change at a time, so a read and a
 * write answering together cannot both compare with the same snapshot. A store that fails is
 * skipped, never thrown: a note is not worth a failed load.
 */
class SharedSettingsNoticesImpl(
    private val store: SharedSettingsStore,
    private val now: () -> Instant = { Clock.System.now() },
) : SharedSettingsNotices {
    private val mutex = Mutex()

    /** Bumped after every change to the store, so [notices] reads again. */
    private val revision = MutableStateFlow(0L)

    override suspend fun observe(current: SharedSettingsSnapshot) = recordOwn(current, own = emptySet())

    override suspend fun recordOwn(current: SharedSettingsSnapshot, own: Set<SharedSetting>) = change {
        val previous = store.snapshot(current.bondId)
        val notices = sharedSettingsNotices(previous, current, now()).withoutOwn(own)
        if (previous != current || notices.isNotEmpty()) store.save(current, notices)
        // A note asking for an answer goes once nothing waits for one: answered here or on another
        // phone, withdrawn, or lapsed. Unlike the others it is not news but a question.
        if (!current.waitingSemanticSearchAsk(now())) {
            store.notices(current.bondId)
                .filter { it.notice == SharedSettingNotice.SemanticSearchAsked }
                .forEach { store.removeNotice(current.bondId, it.id) }
        }
    }

    override suspend fun clearOwnRequest(bondId: String, setting: SharedSetting) = change {
        val previous = store.snapshot(bondId) ?: return@change
        val cleared = when (setting) {
            SharedSetting.TIMEZONE -> previous.copy(timezoneProposal = null)
            SharedSetting.DELETION -> previous.copy(
                deletionRequest = null,
                deletionScheduled = false,
                status = if (previous.status == BondStatus.PENDING_DELETION) BondStatus.ACTIVE else previous.status,
            )
            SharedSetting.SEMANTIC_SEARCH -> previous.copy(semanticSearchRequest = null)
            else -> previous
        }
        if (cleared != previous) store.save(cleared, emptyList())
    }

    override fun notices(bondId: String): Flow<List<PendingNotice>> =
        revision.map { quietly { store.notices(bondId) } ?: emptyList() }

    override fun snapshot(bondId: String): Flow<SharedSettingsSnapshot?> =
        revision.map { quietly { store.snapshot(bondId) } }.distinctUntilChanged()

    override suspend fun dismiss(bondId: String, id: Long) = change { store.removeNotice(bondId, id) }

    private suspend fun change(block: suspend () -> Unit) {
        mutex.withLock { quietly { block() } }
        revision.update { it + 1 }
    }

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
