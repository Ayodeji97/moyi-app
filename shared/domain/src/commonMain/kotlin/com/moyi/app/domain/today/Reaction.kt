package com.moyi.app.domain.today

/**
 * A reaction (FR-048, ADR-0037): one small, fixed acknowledgement a member leaves on the
 * **partner's revealed entry**. Every value the contract's `ReactionRequest.type` declares,
 * the owner's set of 2026-10-21. The names are the wire's; how each is shown is the
 * presentation layer's (the design draws none yet: docs/missing-components.md §45).
 *
 * The rules, all the server's and mirrored here only so no control is offered that would
 * be refused (ADR-0037 decisions 1 to 5):
 * - **one per member per entry**: leaving another replaces it, and it can be taken back;
 * - **only on the partner's entry**, and only once it is revealed ([reactable]);
 * - **both members are sent the same value**, on the entry it was left on: mine on
 *   [PartnerEntry.Revealed.reaction], the partner's on [MyEntry.reaction];
 * - **no time is kept or sent**, and nothing counts reactions: there is nothing to show
 *   but which one, if any.
 */
enum class Reaction { HEART, THANK_YOU, SMILE, MOVED, ME_TOO }

/**
 * The partner's entry, when **I** may react to it: revealed and read in full. Null for
 * every other shape: a locked entry (not shown), a tombstone (`409 ENTRY_IMMUTABLE`), and a
 * solo day's unlocked entry, which FR-048 does not cover (the server would take it, but
 * only a revealed entry is offered the control). My own entry never takes one
 * (`409 REACTION_ON_OWN_ENTRY`), and nothing here can name it.
 *
 * Whether the bond is still open is the caller's to add: on an ended bond the server
 * refuses both leaving and taking back (`409 BOND_ARCHIVED`).
 */
fun PartnerEntry?.reactable(): PartnerEntry.Revealed? = this as? PartnerEntry.Revealed
