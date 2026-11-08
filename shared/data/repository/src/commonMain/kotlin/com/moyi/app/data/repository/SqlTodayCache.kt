package com.moyi.app.data.repository

import com.moyi.app.data.database.Cached_day
import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Prompt
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayCache
import kotlinx.coroutines.flow.StateFlow

/**
 * [TodayCache] over the local database's `cached_day` table, one row keyed by the
 * signed-in person's id ([session]). Signed out, or still deciding, there is no
 * person: [read] is null and [write] does nothing.
 *
 * A row this build cannot read back (an enum name it does not know, a shape with a
 * missing field) is no cache at all, never a guess: Today then loads as it did
 * before there was a cache.
 */
class SqlTodayCache(
    private val database: LocalDatabase,
    private val session: StateFlow<SessionState>,
) : TodayCache {
    private val userId: String? get() = (session.value as? SessionState.SignedIn)?.user?.id

    override suspend fun read(): CachedDay? {
        val user = userId ?: return null
        return database.io { cachedDayQueries.forUser(user).executeAsOneOrNull() }?.toCachedDay()
    }

    override suspend fun write(day: CachedDay) {
        val row = day.toRow(userId ?: return)
        database.io {
            // One row in the table, ever: whoever it belonged to, it is replaced.
            transaction {
                cachedDayQueries.deleteAll()
                cachedDayQueries.insert(row)
            }
        }
    }

    override suspend fun clear() {
        database.io { cachedDayQueries.deleteAll() }
    }
}

internal fun CachedDay.toRow(userId: String): Cached_day {
    val partner = today.partnerEntry
    return Cached_day(
        user_id = userId,
        bond_id = bond.id,
        bond_name = bond.name,
        bond_type = bond.type.name,
        bond_status = bond.status.name,
        partner_name = bond.partnerName,
        anchor_timezone = bond.anchorTimezone,
        day_date = today.date,
        day_status = today.status.name,
        my_entry_id = today.myEntry?.id,
        my_entry_text = today.myEntry?.text,
        my_entry_status = today.myEntry?.status?.name,
        partner_entry_shape = when (partner) {
            is PartnerEntry.Submitted -> SUBMITTED
            is PartnerEntry.Revealed -> REVEALED
            is PartnerEntry.Deleted -> DELETED
            PartnerEntry.Locked -> LOCKED
            null -> null
        },
        partner_entry_id = when (partner) {
            is PartnerEntry.Submitted -> partner.id
            is PartnerEntry.Revealed -> partner.id
            is PartnerEntry.Deleted -> partner.id
            PartnerEntry.Locked, null -> null
        },
        partner_entry_text = when (partner) {
            is PartnerEntry.Submitted -> partner.text
            is PartnerEntry.Revealed -> partner.text
            is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
        },
        prompt_id = today.prompt?.id,
        prompt_text = today.prompt?.text,
    )
}

internal fun Cached_day.toCachedDay(): CachedDay? {
    val bond = Bond(
        id = bond_id,
        name = bond_name,
        type = enumOrNull<BondType>(bond_type) ?: return null,
        status = enumOrNull<BondStatus>(bond_status) ?: return null,
        invite = null,
        partnerName = partner_name,
        anchorTimezone = anchor_timezone,
    )
    val myEntry = my_entry_id?.let { id ->
        MyEntry(id = id, text = my_entry_text, status = enumOrNull<EntryStatus>(my_entry_status) ?: return null)
    }
    val partnerEntry = when (partner_entry_shape) {
        null -> null
        SUBMITTED -> PartnerEntry.Submitted(partner_entry_id ?: return null, partner_entry_text ?: return null)
        REVEALED -> PartnerEntry.Revealed(partner_entry_id ?: return null, partner_entry_text ?: return null)
        DELETED -> PartnerEntry.Deleted(partner_entry_id ?: return null)
        LOCKED -> PartnerEntry.Locked
        else -> return null
    }
    val promptId = prompt_id
    val promptText = prompt_text
    val prompt = if (promptId != null && promptText != null) Prompt(promptId, promptText) else null
    return CachedDay(
        bond = bond,
        today = Today(
            date = day_date,
            status = enumOrNull<BondDayStatus>(day_status) ?: return null,
            myEntry = myEntry,
            partnerEntry = partnerEntry,
            prompt = prompt,
        ),
    )
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

private const val SUBMITTED = "SUBMITTED"
private const val REVEALED = "REVEALED"
private const val DELETED = "DELETED"
private const val LOCKED = "LOCKED"
