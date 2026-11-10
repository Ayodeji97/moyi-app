package com.moyi.app.data.repository

import com.moyi.app.data.database.Cached_day
import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryMediaStatus
import com.moyi.app.domain.today.EntryVoice
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Prompt
import com.moyi.app.domain.today.Reaction
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
    // The media's shape only: the signed URL is a credential and is never written here.
    val myImage = today.myEntry?.image
    val myVoice = today.myEntry?.voice
    val partnerImage = when (partner) {
        is PartnerEntry.Submitted -> partner.image
        is PartnerEntry.Revealed -> partner.image
        is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
    }
    val partnerVoice = when (partner) {
        is PartnerEntry.Submitted -> partner.voice
        is PartnerEntry.Revealed -> partner.voice
        is PartnerEntry.Deleted, PartnerEntry.Locked, null -> null
    }
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
        my_entry_reaction = today.myEntry?.reaction?.name,
        partner_entry_reaction = (partner as? PartnerEntry.Revealed)?.reaction?.name,
        my_photo_id = myImage?.id,
        my_photo_status = myImage?.status?.name,
        my_photo_width = myImage?.width?.toLong(),
        my_photo_height = myImage?.height?.toLong(),
        my_photo_sensitive = myImage?.sensitive?.toFlag(),
        my_voice_id = myVoice?.id,
        my_voice_status = myVoice?.status?.name,
        my_voice_duration_ms = myVoice?.durationMs?.toLong(),
        my_voice_sensitive = myVoice?.sensitive?.toFlag(),
        partner_photo_id = partnerImage?.id,
        partner_photo_status = partnerImage?.status?.name,
        partner_photo_width = partnerImage?.width?.toLong(),
        partner_photo_height = partnerImage?.height?.toLong(),
        partner_photo_sensitive = partnerImage?.sensitive?.toFlag(),
        partner_voice_id = partnerVoice?.id,
        partner_voice_status = partnerVoice?.status?.name,
        partner_voice_duration_ms = partnerVoice?.durationMs?.toLong(),
        partner_voice_sensitive = partnerVoice?.sensitive?.toFlag(),
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
    val myImage = cachedImage(my_photo_id, my_photo_status, my_photo_width, my_photo_height, my_photo_sensitive) ?: return null
    val myVoice = cachedVoice(my_voice_id, my_voice_status, my_voice_duration_ms, my_voice_sensitive) ?: return null
    val partnerImage = cachedImage(partner_photo_id, partner_photo_status, partner_photo_width, partner_photo_height, partner_photo_sensitive)
        ?: return null
    val partnerVoice = cachedVoice(partner_voice_id, partner_voice_status, partner_voice_duration_ms, partner_voice_sensitive) ?: return null
    val myEntry = my_entry_id?.let { id ->
        MyEntry(
            id = id,
            text = my_entry_text,
            status = enumOrNull<EntryStatus>(my_entry_status) ?: return null,
            image = myImage.value,
            voice = myVoice.value,
            reaction = my_entry_reaction?.let { enumOrNull<Reaction>(it) ?: return null },
        )
    }
    val partnerEntry = when (partner_entry_shape) {
        null -> null
        SUBMITTED -> PartnerEntry.Submitted(
            id = partner_entry_id ?: return null,
            text = partner_entry_text ?: return null,
            image = partnerImage.value,
            voice = partnerVoice.value,
        )
        REVEALED -> PartnerEntry.Revealed(
            id = partner_entry_id ?: return null,
            text = partner_entry_text ?: return null,
            image = partnerImage.value,
            voice = partnerVoice.value,
            reaction = partner_entry_reaction?.let { enumOrNull<Reaction>(it) ?: return null },
        )
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

/**
 * A column read back: [value] is null when the entry had none. The function returning the
 * holder itself null means the columns hold something this build cannot read (an unknown
 * status), and the whole row is then no cache, never a guess.
 */
private class Cached<T>(val value: T?)

private fun Boolean.toFlag(): Long = if (this) 1L else 0L

/** The photo as Today was last shown it, without its URL: a card drawn from it keeps its size. */
private fun cachedImage(id: String?, status: String?, width: Long?, height: Long?, sensitive: Long?): Cached<EntryImage>? {
    if (id == null) return Cached(null)
    return Cached(
        EntryImage(
            id = id,
            status = enumOrNull<EntryMediaStatus>(status) ?: return null,
            url = null,
            width = width?.toInt(),
            height = height?.toInt(),
            sensitive = sensitive == 1L,
        ),
    )
}

/** The voice note as Today was last shown it, without its URL: its row is reserved, at its length. */
private fun cachedVoice(id: String?, status: String?, durationMs: Long?, sensitive: Long?): Cached<EntryVoice>? {
    if (id == null) return Cached(null)
    return Cached(
        EntryVoice(
            id = id,
            status = enumOrNull<EntryMediaStatus>(status) ?: return null,
            url = null,
            durationMs = durationMs?.toInt(),
            sensitive = sensitive == 1L,
        ),
    )
}
