# Ending and safety, against Figma

The leave and block confirms, close the box, and the archived bond as built, measured on a
running app and compared with the Figma frames. Measured 2026-11-06 on the preview build
(`-PmoyiUiPreview=today`, fake settings, no backend), light theme only.

## Sources, and what they are worth

- **Geometry**: `scratchpad/figma/ending.xml`, `safety.xml` and `bond-settings.xml`, the
  `get_metadata` dumps. Every node id and y below is real Figma data.
- **Not read from Figma**: the one screenshot allowed (399:21) was refused by the Starter
  plan's MCP limit. No text, colour or text style came from the canvas. Copy comes from
  `states.md` §8 and §9, `strings-proposed.md` §10 and `copy.md`; the rest is PROPOSED in
  `feature/bond/.../strings.xml`.

## How it was measured

As `bond-settings-check.md`: Android emulator, 1080 × 2424 px at 420 dpi, `uiautomator
dump`, **y** is the measured y minus the 54.1 top inset, within 2 counts as a match. Screens
under a back target are compared at Figma − 75 (D1). Actions are compared at their Figma y,
because the scaffold pins them to the bottom as Figma draws them.

## Reaching it (debug only)

```
./gradlew :androidApp:installDebug -PmoyiUiPreview=today
```

Today → the gear → scroll to Ending. The preview bond starts with Tunde's ask to close the
box waiting (Agree). Every call takes 800 ms. Leave and Block and leave end the bond, and
the signed-in start then shows the archived bond.

## Root, the Ending group (396:3)

| Row | Built y | |
|---|---|---|
| Leave this bond 396:32 | 669.3 | follows the part 2 root |
| Block and leave 617:1834 | 725.3 | 56 below, as 617:1833 draws |
| Close the box (not drawn) | 781.3 | one more row, 56 below; value Asked, or "Closes on 6 December" |

During the cooling-off the rows that change the bond (type, name, time zone, reveal time,
rest days, invite) are disabled; Sign out and the three Ending rows are not. Checked in the
dump (`enabled="false"`).

## Leave (398:21)

| Node | Figma y | Expected | Built y | |
|---|---|---|---|---|
| heading 398:27 | 171 | 96 | 96.0 | match |
| line 398:52 | 228 | 153 | 152.8 | match |
| withdraw row 398:53 | 324 | 249 | 224.8 | 24 high: the line is two lines here, three (72) in Figma |
| PrimaryAction 398:75 | 700 | 700 | label 713.5 | match |
| secondary 398:77 | 786.5 | 786.5 | 785.5 | match |

## Block (612:1827) and withdraw off (661:1949)

| Node | Figma y | Expected | Built y | |
|---|---|---|---|---|
| heading 612:1833 | 171 | 96 | 96.0 | match |
| line 612:1834 | 228 | 153 | 152.8 | match |
| withdraw row 612:1835 | 300 | 225 | 224.8 | match |
| consequence 612:1848 | 372 | 297 | 296.8 | match (72 tall, three lines; 96 drawn) |
| export 612:1850 | 658.5 | | not built | no export route (missing-components §31) |
| PrimaryAction 612:1839 | 708 | 708 | label 721.5 | match |
| cancel 612:1841 | 786.5 | 786.5 | 785.5 | match |

Withdraw off swaps only the consequence, to one PROPOSED line; every other y is the same.

## Close the box: Ask (399:38), Agree, Asked, cooling off (399:1525)

| Node | Figma y | Expected | Built y | |
|---|---|---|---|---|
| heading 399:46 / 399:1531 | 171 | 96 | 96.0 | match |
| line 399:47 / 399:1532 | 220 | 145 | 144.8 | match (16 under the heading, not 24; fixed after the first measure) |
| Ask · PrimaryAction 399:49 | 700 | 700 | label 713.5 | match |
| Ask · Cancel 399:52 | 786.5 | 786.5 | 785.5 | match |
| Cooling off · PrimaryAction 399:1534 | 700 | 700 | label 785.5 | 72 low: its secondary (export) is not built, so the primary takes the bottom slot |

Agree is Ask's layout with Decline as the secondary; Asked is the time zone's Proposed
layout (one secondary at 785.5). Neither is drawn (missing-components §30).

## Bond · archived (399:21, 614:1832)

| Node | Figma y | Built y | |
|---|---|---|---|
| eyebrow 399:27 | 24 | 24.0 | match: a root, so the eyebrow stays (D1) |
| heading 399:29 | 107 | 106.3 | match |
| line 399:30 | 156 | 155.0 | match |
| PrimaryAction 399:32 / 614:1838 | 628 / 700 | label 785.5 | lower: both secondaries are absent (Close the box refused, export not built) |

No back target. System Back on it leaves the app, as on Today.

## Walked on the emulator (preview build)

- Root → Close the box (Agree, "Tunde asked to close the box") → Close the box → **cooling
  off** ("The box closes on 6 December") → Back → root, edit rows disabled, row value
  "Closes on 6 December" → Close the box → Keep the box → root, value cleared.
- Close the box → **Ask** → Close the box → **Asked** ("your ask lapses on 13 November")
  → Cancel this request → root.
- After a reinstall: Agree → Decline → root.
- Block and leave → withdraw on (default) → tap the row → **withdraw off**, the
  consequence changes → Cancel → root.
- Leave this bond → withdraw off (default) → tap the row → on → Leave → the
  **archived bond** ("This bond has ended", Tunde named) → Back leaves the app.

Not walked: Start a new bond (the preview's onboarding has no backend), a failure on a
device (the ViewModel tests cover them), dark theme, and iOS.
