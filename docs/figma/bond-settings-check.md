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
plus the reminder row, 56). The expected column already subtracts them.

Rows are 56 tall with their text 24 at 16, as 396:13 draws. Values sit at the end, `body`.
The underline follows `tokens.md` §1: Relationship type and Bond name values are underlined
(they navigate), Leave this bond is underlined in `danger`; Reveal time (OS surface), Rest
days (in place) and Sign out (in place) have none.

### Left out, with no dead row

| Figma row | Why |
|---|---|
| Shared time zone 396:19 | FR-027's three-step flow (397:22, 397:67, 397:102) is a later PR |
| Your reminder 396:25 | Needs `/members/me/notifications`, which is not in `contracts/openapi.json` yet (a syncContract PR) |
| Invite group 562:1678, row 415:313 | Its screens (416:*) are a later PR |
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
