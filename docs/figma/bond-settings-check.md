# Bond settings, part 1, against Figma

The Bond settings root, the bond name editor, the relationship-type editor, the reveal time
picker and the leave confirmation as built, measured on a running app and compared with the
Figma frames. Measured 2026-11-06 on the preview build (`-PmoyiUiPreview=today`, fake
settings, no backend).

## Sources, and what they are worth

- **Geometry**: `scratchpad/figma/bond-settings.xml`, a `get_metadata` dump of the section:
  root 396:3 / 398:1509, bond name 661:1909 / 661:1929, leave 398:21 / 398:1572. Every node
  id and x/y/w/h below is real Figma data.
- **Not read from Figma**: the one screenshot allowed (396:3) was refused by the Starter
  plan's MCP limit. So no text content, colours or text styles came from the canvas. Copy
  comes from `states.md` §8 and §9, `copy.md` and `strings-proposed.md` §8; the rest is
  PROPOSED in `feature/bond/.../strings.xml`.

## How it was measured

Android emulator, 1080 × 2424 px at 420 dpi (411.4 × 923.4 dp, a 54.1 dp status bar),
read with `uiautomator dump`, as `today-check.md` did. **y** is the measured y minus the
54.1 top inset. Within 2 counts as a match. Light and dark were both screenshotted; the
numbers are the same in each.

**D1 moves everything up by 75.** Figma draws an eyebrow at 88 and the heading at 171;
under a back target the app drops the eyebrow and puts the heading at 96 (figma-deviations
D1, as on every pushed screen). So each Figma y below is compared at Figma − 75.

## Reaching it (debug only)

```
./gradlew :androidApp:assembleDebug -PmoyiUiPreview=today
```

Today opens on a preview day; its settings target (the gear) opens Bond settings on
`PreviewBondSettingsRepository`. Every save takes 800 ms, so Saving shows. Leave succeeds and
lands on the signed-in start, which in this build has no backend and shows the Generic
error: that is expected there, and proves the route.

## Root (396:3)

| Node | Figma y | Expected (− 75) | Built y | |
|---|---|---|---|---|
| back 396:5 | 24 | 24 | 24 (glyph 36) | match |
| heading 396:11 | 171 | 96 | 96.0 | match |
| group · this bond heading 562:1670 | 228 | 153 | 152.8 | match |
| row · Relationship type, label 396:14 | 279 | 204 | 203.0 | match |
| row · Bond name, label 396:17 | 335 | 260 | 259.0 | match |
| group · the day heading 562:1673 | 399 | 324 | 323.0 | match |
| row · Reveal time 396:23 | 506 * | 375 | 373.3 | match |
| row · Rest days 396:29 | 562 * | 431 | 429.3 | match |
| group · just you heading 562:1676 | 626 * | 495 | 493.3 | match |
| row · Sign out (in place of Account 575:1782) | 732 * | 545 | 543.6 | match |
| group · ending heading 562:1682 | 912 * | 609 | 607.6 | match |
| row · Leave this bond 396:32 | 963 * | 660 | 657.9 | match |

\* Rows below a left-out row move up by 56 each (one row) or by 147 (the Invite group, 91,
plus the reminder row, 56). The expected column already subtracts them. **Part 2 adds the
Shared time zone row and the Invite group back: see the root table in "Part 2" below,
which supersedes these numbers.**

Rows are 56 tall with their text 24 at 16, as 396:13 draws. Values sit at the end, `body`.
The underline follows `tokens.md` §1: Relationship type and Bond name values are underlined
(they navigate), Leave this bond is underlined in `danger`; Reveal time (OS surface), Rest
days (in place) and Sign out (in place) have none.

### Left out, with no dead row

| Figma row | Why |
|---|---|
| Your reminder 396:25 | Needs `/members/me/notifications`, which is not in `contracts/openapi.json` yet (a syncContract PR) |
| Block and leave 617:1833 | Later PR |
| Account 575:1782 | Its screen (help, delete your account) is not built; **Sign out** takes its place in the same group |

## Bond name (661:1909)

| Node | Figma y | Expected | Built y | |
|---|---|---|---|---|
| heading 661:1916 | 171 | 96 | 96.0 | match |
| line 661:1917 | 220 | 145 | not built | its words were not read; nothing invented |
| Field 661:1918 | 292 | 217 | 152.8 | 64 higher: the 49 + 48 line is absent (heading 33 + 24) |
| PrimaryAction 661:1920 | 772 | 772 | 773 | match |

The field carries onboarding's helper, "Only the two of you will see this.", where Figma's
83-tall field has one. Saving reads "Saving" on the button; a failure shows the inline error
8 above it with the name kept.

## Relationship type (not drawn in this section)

Onboarding 4's layout and its `RelationshipCard`s (341:24), with the current type checked.
The line drops "You can change it later" (false here). A tap saves and returns to the root.

## Reveal time and leave

- **Reveal time** opens `TimeOfDayPicker`, Material 3's dial in a dialog, starting at 21:00
  when none is set (states.md §8 default). The emulator is a 12-hour device, so it showed
  09:00 PM. Saving it showed 21:00 on the row. The value is `HH:mm` on both platforms;
  locale formatting is owed.
- **Leave** is the platform dialog (C2): title "Leave this bond?", the line, Leave (danger)
  and Cancel. Figma's full screen 398:21 is replaced by it, which also leaves out FR-029a's
  withdraw checkbox (398:53): see the PR's "For you to check".

## Walked on the emulator

Today → gear → root; Rest days On → Off in place; Reveal time → picker → Save → 21:00;
Bond name → edit → Save → back on the root with the new name; Relationship type → My close
friend → back on the root; Leave → dialog → Leave → signed-in start; Back from the root →
Today. Not walked: a failure, a conflict, sign out (the ViewModel tests cover them), and iOS.

## Part 2: the shared time zone and the invite

Measured 2026-11-06 on the same emulator and preview build, light theme only (dark was not
screenshotted this time). The one screenshot allowed (397:22) was again refused by the
Starter plan's MCP limit: geometry from the metadata dump, words from `states.md` §2, §8,
`strings-proposed.md` §6b, or PROPOSED. Expected is Figma − 75 (D1), less any left-out
row above it (the reminder row, 56).

### Root (396:3), rows added

| Node | Figma y | Expected | Built y | |
|---|---|---|---|---|
| row · Shared time zone, label 396:20 | 450 | 375 | 373.3 | match |
| row · Reveal time 396:23 | 506 | 431 | 429.3 | match |
| row · Rest days 396:29 | 562 | 487 | 485.3 | match |
| group · just you heading 562:1676 | 626 | 551 | 549.3 | match |
| row · Sign out (Account 575:1782) | 733 | 602 * | 599.6 | 2.4 high |
| group · invite heading 562:1679 | 797 | 666 * | 663.6 | 2.4 high |
| row · Invite 415:314 | 848 | 717 * | 713.9 | 3.1 high |

\* Minus the reminder row (56). The drift below the left-out reminder row is the 1.7 per
group heading that part 1 already showed (18.3 built against 19 drawn), now across one
more group; not corrected here. The Invite group shows only while the bond waits for its
second member or holds a live code (the preview's ACTIVE bond holds one, so it shows).
Its value is Active or None (PROPOSED).

### Time zone: Propose (397:22), Proposed (397:67), Confirm (397:102)

| Node | Figma y | Expected | Built y | |
|---|---|---|---|---|
| heading 397:28 / 397:73 / 397:108 | 171 | 96 | 96.0 | match |
| Propose · Field 397:53 (box) | 228 (instance top) | 153 | box 179, label above it | match: the box sits under its label as `Field` draws it |
| Propose · rules 397:63 | 364 | 289 | 315.0 | 26 low: the helper is two lines here (the in-file wording with the city), one in Figma |
| Propose · PrimaryAction 397:65 | 772 | 772 | label 785.5 | match |
| Proposed · line 397:98 | 228 | 153 | 152.8 | match |
| Proposed · secondary 397:100 | 786.5 | 786.5 | 785.5 | match |
| Confirm · line 397:133 | 260 | 185 | 152.8 | 32 high: the heading is one line here ("Tunde proposed London time"), two in Figma (65) |
| Confirm · PrimaryAction 397:135 | 700 | 700 | label 713.5 | match |
| Confirm · secondary 397:137 | 786.5 | 786.5 | 785.5 | match |

The city list (no frame; `missing-components.md` §19) is the heading "Choose a city", a
search `Field`, and one settings row per city with its IANA id as the value.

### Invite: Active (416:44), Revoked (416:97)

| Node | Figma y | Expected | Built y | |
|---|---|---|---|---|
| heading 416:50 / 416:103 | 171 | 96 | 96.0 | match |
| Active · line 416:78 | 228 | 153 | 152.8 | match |
| Active · InviteCode 416:79 | 300 | 225 | caption 248.8 (padding 24) | match |
| Active · PrimaryAction 416:93 | 700 | 700 | label 713.5 | match |
| Active · secondary 416:95 | 786.5 | 786.5 | 785.5 | match |
| Revoked · line 416:131 | 228 | 153 | 152.8 | match |
| Revoked · PrimaryAction 416:133 | 772 | 772 | label 785.5 | match |

### Walked on the emulator (preview build)

- Root → Shared time zone (Lagos) → **Confirm** ("Tunde proposed London time") → Decline →
  root, still Lagos.
- Root → **Propose** → zone field → city list → search "lond" → London → Propose with
  London → Propose change → **Proposed** ("London time", the states.md sentence with
  Tunde) → Cancel this proposal → root, Lagos.
- Restarted → Confirm → Agree → root reads London → Propose → Tokyo → Propose change →
  "The shared time zone changed less than 30 days ago, so it cannot change again yet." 8
  above the action, Tokyo kept.
- Root → Invite (Active) → Copy code → Copied → Cancel this invite → **Revoked** → Create a
  new invite → Active with a new code (M4XR9T) → Share invite: the Android sheet with "Join
  me on Moyi. Open https://getmoyi.app/i/M4XR9T or enter the code M4XR9T in the app."; Back
  closes it and the screen stays where it was.
- Today, stepped to Partner not joined → Share the invite again → the same sheet with the
  **same** live code (M4XR9T, none issued); Back closes it, Today unchanged.

Not walked: Proposing and Agreeing as Sending (too quick to catch in a dump at 800 ms), a
404 or 409 on a device (the ViewModel tests cover them), dark theme, and iOS.
