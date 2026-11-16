package com.moyi.app.domain.bond

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * [SharedSettingsNoticesImpl]: reads note what changed, this device's own writes never come
 * back as notes, and a note goes once it is dismissed.
 */
class SharedSettingsNoticesTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val store = MemorySettingsStore()
    private val notices = SharedSettingsNoticesImpl(store, now = { now })

    private val base = SharedSettingsSnapshot(
        bondId = "b1",
        status = BondStatus.ACTIVE,
        name = "Us",
        type = BondType.COUPLE,
        anchorTimezone = "Europe/London",
        revealTime = TimeOfDay(21, 0),
        strictMode = false,
        semanticSearchEnabled = true,
        deletionScheduled = false,
    )

    private val theirProposal = base.copy(
        timezoneProposal = PendingProposal(id = "p1", value = "Africa/Lagos", byMe = false, expiresAt = now + 5.days),
    )

    private suspend fun waiting() = notices.notices("b1").first().map { it.notice }

    @Test
    fun theFirstReadOnlyRemembers() = runTest {
        notices.observe(base)

        assertEquals(base, store.snapshots["b1"])
        assertTrue(waiting().isEmpty())
    }

    @Test
    fun aLaterReadNotesWhatChanged() = runTest {
        notices.observe(base)
        notices.observe(base.copy(name = "Tea"))

        assertEquals(listOf(SharedSettingNotice.NameChanged("Tea")), waiting())
        assertEquals("Tea", store.snapshots["b1"]?.name, "the new reading is remembered")
    }

    @Test
    fun aNoteWaitsUntilDismissedThenGoes() = runTest {
        notices.observe(base)
        notices.observe(base.copy(name = "Tea"))
        notices.observe(base.copy(name = "Tea"))

        val note = notices.notices("b1").first().single()
        notices.dismiss("b1", note.id)

        assertTrue(waiting().isEmpty())
    }

    @Test
    fun myOwnChangeOnThisDeviceIsNotNoted() = runTest {
        notices.observe(base)
        notices.recordOwn(base.copy(name = "Mine"), own = setOf(SharedSetting.NAME))
        notices.observe(base.copy(name = "Mine"))

        assertTrue(waiting().isEmpty())
    }

    @Test
    fun aChangeTheOtherMadeStillShowsUpInTheAnswerToMyOwnWrite() = runTest {
        // A proposal takes no If-Match, so its answer can carry the other member's rename too.
        notices.observe(base)
        notices.recordOwn(base.copy(name = "Theirs", timezoneProposal = theirProposal.timezoneProposal!!.copy(byMe = true)), own = setOf(SharedSetting.TIMEZONE))

        assertEquals(listOf(SharedSettingNotice.NameChanged("Theirs")), waiting())
    }

    @Test
    fun myAgreeingOnThisDeviceIsNotNoted() = runTest {
        notices.observe(theirProposal)
        notices.recordOwn(base.copy(anchorTimezone = "Africa/Lagos"), own = setOf(SharedSetting.TIMEZONE))

        assertTrue(waiting().isEmpty())
    }

    @Test
    fun myDecliningOnThisDeviceIsNotNotedAsTheirWithdrawal() = runTest {
        notices.observe(theirProposal)
        notices.clearOwnRequest("b1", SharedSetting.TIMEZONE)
        notices.observe(base)

        assertTrue(waiting().isEmpty())
    }

    @Test
    fun myCallingOffTheCoolingOffOnThisDeviceIsNotNoted() = runTest {
        notices.observe(base.copy(status = BondStatus.PENDING_DELETION, deletionScheduled = true))
        notices.clearOwnRequest("b1", SharedSetting.DELETION)
        notices.observe(base)

        assertTrue(waiting().isEmpty())
    }

    @Test
    fun theirWithdrawalIsNotedWhenItWasNotMe() = runTest {
        notices.observe(theirProposal)
        notices.observe(base)

        assertEquals(listOf(SharedSettingNotice.TimezoneWithdrawn("Africa/Lagos")), waiting())
    }

    @Test
    fun aStoreThatFailsNeverFailsTheCaller() = runTest {
        store.failing = true

        notices.observe(base)
        notices.recordOwn(base, setOf(SharedSetting.NAME))
        notices.clearOwnRequest("b1", SharedSetting.TIMEZONE)
        notices.dismiss("b1", 1)

        assertTrue(waiting().isEmpty())
    }

    @Test
    fun eachBondKeepsItsOwnSnapshot() = runTest {
        notices.observe(base)
        notices.observe(base.copy(bondId = "b2", name = "Other"))

        assertTrue(waiting().isEmpty(), "a second bond's first reading is not a change to the first")
    }
}

/** The store in memory, as the database keeps it. */
internal class MemorySettingsStore : SharedSettingsStore {
    val snapshots = mutableMapOf<String, SharedSettingsSnapshot>()
    private val pending = mutableListOf<Pair<String, PendingNotice>>()
    private var nextId = 1L
    var failing = false

    override suspend fun snapshot(bondId: String): SharedSettingsSnapshot? {
        check(!failing)
        return snapshots[bondId]
    }

    override suspend fun save(snapshot: SharedSettingsSnapshot, notices: List<SharedSettingNotice>) {
        check(!failing)
        snapshots[snapshot.bondId] = snapshot
        notices.forEach { pending += snapshot.bondId to PendingNotice(nextId++, it) }
    }

    override suspend fun notices(bondId: String): List<PendingNotice> {
        check(!failing)
        return pending.filter { it.first == bondId }.map { it.second }
    }

    override suspend fun removeNotice(bondId: String, id: Long) {
        check(!failing)
        pending.removeAll { it.first == bondId && it.second.id == id }
    }
}
