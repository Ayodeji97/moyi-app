# Streak, against Figma

The Streak screen as built, measured on a running app and compared with the Figma frames in
section 425:318: current 227:3, rest day used 401:70, ended 401:160, strict mode 401:250
(light 281:847, 401:1800, 401:1860, 401:1920). It runs on preview data in the
`-PmoyiUiPreview=today` build (`PreviewStreak.kt`); against a backend it uses
`GET /bonds/{bondId}/streak`. Measured 2026-11-06.

## Sources, and what they are worth

- **Geometry**: the `get_metadata` dump of section 425:318 (`streak.xml`). Every node id and
  x/y/w/h below is real Figma data; the text values are the node names.
- **Screenshots**: **none**. The one attempt (227:3) hit the Starter-plan rate limit. So the
  DayCell's look in each state (fill, ring, dash) was **not seen**; it is built from
  states.md §7 (frozen is a dashed ring in `outlineStrong`; no entry is a bare numeral; the
  key's Today is a ring) and, for Complete, interpreted as a filled `ink` disc.

## How it was measured

Pixel 9a emulator, light mode, 1080 x 2424 px at 2.625 px/dp (411.4 dp wide), read with
`uiautomator dump`. **y** is the measured y minus the 54.1 dp status bar, so it compares
directly with Figma's 390 x 844 frame. Widths differ by the 21 dp the device is wider.

## Reaching the screens (debug only)

```
./gradlew :androidApp:assembleDebug -PmoyiUiPreview=today
```

Today opens on its preview day. Tap the calendar target for the Archive, then **Your streak**.
Each answer of the preview repository is the next state, after 800 ms: current, rest day
used, ended, strict mode, the Generic error, and round again. **Long-press the title** to ask
again (or Try again on the error).

## Results

### Archive · title row (430:1434)

| Element | Figma | Built | |
|---|---|---|---|
| "Your streak" (430:1435) | 100 x 48 at x 258, label metrics | TextLink, text at x 315, 80.4 wide, `body` sentence case | trailing, as drawn; D14 |
| Title "Your days" | y 96, h 48 row | y 96 row, the text centred in it | unchanged |

### Streak · current (227:3)

| Element | Figma | Built | |
|---|---|---|---|
| Back | y 24, 48 x 48 | y 24 (glyph at 36.2) | matches |
| Title "Days you both showed up" | y 104, h 33 | y 96, h 32.8 | **8 higher**: 24 under the back, as every screen with one (ScreenHeader) |
| "Now" / "Longest" | y 169, label h 19; Longest at x 87 | y 160.8, h 18.3; Longest at x 85.3 in the frame | 8 higher, as the title; 48 apart |
| Numerals 22 / 41 | 23 under, h 48 | 22.4 under, h 47.6 | `entry` |
| "The last four weeks" | y 272 | y 262.9 | 9 higher |
| Calendar | y 323, 4 rows of 7, cells 48, rows 56 apart | y 313.1, rows 56 apart, cells 48, 55.2 apart | the width is the device's |
| Key | y 571; entries at x 0, 138, 251; swatch 20, label at +28 | y 562.3; entries at x 0, 137.7, 250.9; swatch 20, label at +28 | matches (16 apart) |
| "No entry (left blank)" | y 28 in the key, no swatch | 27 under, no swatch | matches |
| Footer | y 650, two lines (48) | y 639.6, h 48 | "2 rest days left. ..." (digits, see the PR) |

### Rest day used (401:70), ended (401:160), strict mode (401:250)

| Element | Figma | Built | |
|---|---|---|---|
| Rest day used footer | "Yesterday slipped by. We used a rest day. One left." | the same; yesterday's cell is the rest day | matches |
| Ended numerals | Now 0 (20 wide), Longest at x 81 | 0 (18.7 wide), Longest 80.8 in | matches |
| Ended footer | "Your streak ended at 21 days. That is 21 days of showing up. No rest days were left." | "Your streak ended. No rest days were left." | **the number is not sent** (contract gap 16) |
| Strict key | Both wrote at 0, Today at 138, no Rest day | Today at 137.7; no Rest day | matches |
| Strict footer | "Rest days are off." | the same | matches |
| Strict calendar | the withdrawn history (states.md §7) | a past rest day stays a rest day (FR-073) | the frame is to be redrawn |

### Not drawn, and built from states.md

| Element | Built |
|---|---|
| Loading | the bare `surface` |
| A failed load | the Generic error StateBlock with Try again (measured: heading at y 386.3) |
| Solo day | drawn blank, as no entry: its DayCell shape and key are not drawn (states.md §7) |
| A date with no cell inside the window | drawn blank |
| A bond younger than four weeks | the grid starts at its first cell; a short last row keeps its columns |
| Milestones | not built: no frame draws them |
