package com.moyi.app.data.database

/** One row for each table, with entry text that must never be found in the file in the clear. */
internal object Rows {
    const val SECRET = "You made the tea before the sun was up."

    fun day(userId: String) = Cached_day(
        user_id = userId,
        bond_id = "bond-1",
        bond_name = "Amara and Tunde",
        bond_type = "COUPLE",
        bond_status = "ACTIVE",
        partner_name = "Tunde",
        anchor_timezone = "Africa/Lagos",
        day_date = "2026-09-14",
        day_status = "REVEALED",
        my_entry_id = "e1",
        my_entry_text = SECRET,
        my_entry_status = "REVEALED",
        partner_entry_shape = "REVEALED",
        partner_entry_id = "e2",
        partner_entry_text = "You listened.",
        prompt_id = null,
        prompt_text = null,
        my_entry_reaction = null,
        partner_entry_reaction = null,
    )

    val draft = Draft(
        user_id = "ada",
        bond_id = "bond-1",
        kind = "NEW_ENTRY",
        entry_id = null,
        day_date = "2026-09-14",
        text = "You fixed the gate.",
    )

    val queued = Outbox_entry(
        idempotency_key = "key-1",
        user_id = "ada",
        bond_id = "bond-1",
        text = "You fixed the gate.",
        prompt_id = null,
        intended_at = 1_789_000_000_123L,
        day_date = "2026-09-14",
        refused = null,
    )

    val archived = Archive_day(
        user_id = "ada",
        bond_id = "bond-1",
        day_date = "2026-09-13",
        day_status = "REVEALED",
        my_entry_id = "e3",
        my_entry_text = SECRET,
        my_entry_status = "REVEALED",
        my_entry_marked = 1L,
        partner_entry_shape = "REVEALED",
        partner_entry_id = "e4",
        partner_entry_text = "You waited up.",
        partner_entry_marked = 0L,
        page_cursor = "cursor-1",
        my_entry_reaction = null,
        partner_entry_reaction = null,
    )
}
