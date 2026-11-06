# Archive, parts 1 to 4, against Figma

The Archive list, its empty state and the day view as built, measured on a running app and
compared with the Figma frames in section 425:317. The screens run on preview data in the
`-PmoyiUiPreview=today` build (`PreviewArchive.kt`); against a backend they use
`GET /bonds/{bondId}/days` and `GET /bonds/{bondId}/days/{date}`. Measured 2026-11-05.

Part 2 (Favourites) adds the All days / Favourites chips, the FavouriteToggle under each
revealed entry, the day card's bookmark and the Favourites empty state, against
`GET …/days?favourites=true` and `PUT` / `DELETE /entries/{entryId}/favourite`. Measured
2026-11-05 on the same emulator. **No Figma screenshot was read for part 2**: the one
attempt (440:1462) hit the Starter-plan rate limit, so 440:1462 / 440:1516, 440:1570 /
440:1621 and 438:1462 / 438:1497 were compared by geometry (`archive.xml`) only.

Part 3 (Search) adds the third chip, the search field in the navigation block's top slot,
results paged by cursor and the no-results message, against
`GET …/search?q=…&limit=20&cursor=…`. **No Figma screenshot was read for part 3 either**:
the one attempt (508:1509) hit the Starter-plan rate limit, so 508:1509 / 511:1740 (RESULTS),
511:1518 / 511:1777 (KEYBOARD UP) and 511:1691 / 511:1945 (no results) were compared by
geometry (`archive.xml`) only. The keyboard frames (511:1579, 511:1879) are the OS's.

Part 4 (Month jump) adds the month row (257:9, light 281:757) on All days and Favourites, the
month picker, and "Back to the newest days", against `GET …/days?until=YYYY-MM-DD` (with
`cursor` and `favourites=true` as they apply). Measured 2026-11-06 on the same emulator. **No
Figma screenshot was read for part 4**: the dump has no picker frame anywhere in section
425:317 (no node named picker, wheel or month list), so the one screenshot allowed was not
spent; 257:9 and 281:757 were compared by geometry only.

## Sources, and what they are worth

- **Geometry**: the `get_metadata` dump of section 425:317 (`archive.xml`, taken for this PR).
  Every node id and x/y/w/h below is real Figma data.
- **Screenshots**: 223:3 (with entries, dark) and 223:25 (empty, dark) were read. The Figma
  MCP then refused further calls (Starter plan rate limit), so **438:1462, 243:2 (day view)
  and the light frames 281:754, 281:789, 438:1497, 281:800 were not seen**. The day view is
  built from its geometry, the existing EntryCard and states.md §6.
- **Text styles** come from `tokens.md` (§2: `title` is "also the preview-entry size in
  Archive"), and the measured card heights agree with them.

## How it was measured

Pixel 9a emulator, light mode, 1080 x 2424 px at 2.625 px/dp (411.4 dp wide), read with
`uiautomator dump`. **y** is the measured y minus the 54.1 dp status bar, so it compares
directly with Figma's 390 x 844 frame. Widths differ by the 21 dp the device is wider.

## Reaching the screens (debug only)

```
./gradlew :androidApp:assembleDebug -PmoyiUiPreview=today
```

Today opens on its preview day. **Tap the calendar target** for the Archive: thirty preview
days, served eight at a time with a cursor and 800 ms of latency, so paging can be watched by
scrolling. The newest day carries a 480-character partner entry (states.md §6's long case).
The list holds every shape: revealed, solo with their words (1 September), my tombstone (31
August) and a day still waiting with their entry locked (30 August, mine only).

- **Long-press "Your days"** to step: list, empty, the Generic error, and back to the list.
- **Tap a day** for the day view. On 2 September, "Delete what you wrote" asks in the
  platform dialog, then leaves the tombstone, in the day view and in the list.
- **Favourites**: 2 September (Tunde's), 28 August (mine) and 21 August (Tunde's) start
  marked, so their cards carry the bookmark. **Tap Favourites**: those three, paged as you
  scroll; the third preview page holds none but has a cursor, and is asked past. Toggling
  in a day view changes in place and the list follows it when you go back. **Tunde's entry
  on Wednesday 27 August always fails to mark**: the toggle goes back and the Generic
  failure line appears under it. In Favourites, a long press on "Your days" shows the
  Favourites empty state.

- **Search**: tap **Search** (the third chip; absent over an empty archive). The field
  takes focus and the keyboard comes up over the unfiltered list. Type **flat**: after
  300 ms of rest and the preview's 800 ms, 13 days, in two pages (scroll to see the second
  load). Type **umbrella**: "Nothing matched that." with the query kept. Type a word, then
  another before the first answers: only the last one's results appear. Tap a result,
  then back: the query and the results are as they were. A long press on "Your days" in
  Search steps to the empty and failed layouts (the failure is the inline error and Try
  again under the chips).
- **Month jump**: the archive now runs from 2 September back to 19 April 2025 (the thirty days,
  then one day in four). The row names the month at the top of the list and follows it as you
  scroll. **Jump to** opens the picker: September and August at first, because the picker
  reaches back only to the oldest day loaded; scroll down and it grows to April. Choose
  **June 2025**: the list starts at Monday 30 June, scrolled to the top, with "Back to the
  newest days" under the chips. In **Favourites**, choose **May 2025**: Thursday 29 May, the
  older favourite. Search swaps the row for the field.

## Results

### Archive · with entries (223:3)

| Element | Figma | Built | |
|---|---|---|---|
| Back target | y 24, 48 x 48 | y 24, 48 x 48 | matches |
| Title "Your days" | y 96, h 48 | y 96, h 48 | matches |
| Navigation block (month row, chips) | y 168 to 312 (row 80, 16, chips 48) | y 168 to 288 (row 56, 16, chips 48) | **24 shorter**: states.md's 56 row, see part 4 |
| List | y 336 | y 312 | **24 higher**, as above |
| Month heading "SEPTEMBER 2025" | none (the month row names it) | y 312, `label`, a heading | **kept**, see below |
| DayCard | 358 x 259, padding 16 | 369 x 258.3 at y 346 | matches (width is the device's) |
| Date "TUESDAY 2" | card y 16 | card y 16 | matches |
| "FROM TUNDE" | card y 51 | card y 50.3 | matches |
| Entry text | card y 74, two lines, h 65 | card y 72.8, two lines, ellipsis | matches |
| "FROM YOU" | card y 155 | card y 154.3 | matches |
| Cards apart | 16 (275 minus 259) | 16 | matches |
| Chevron | none (states.md §6) | none | matches |
| "Your streak" link | drawn | **not drawn** | owed |
| Favourite bookmark (440:1510) | 24 x 24 at card x 302, in the date row | 24 x 24, flush with the card's trailing padding, in the date row | matches; filled, `ink`; only for a day holding **my** mark |

### Archive · favourites (440:1462; light 440:1516)

| Element | Figma | Built | |
|---|---|---|---|
| Month jump row | y 168, h 80 | y 168, h 56 | states.md's 56 (part 4) |
| Chips row | y 264, h 48 | y 240, h 48 | **24 higher**: the shorter row |
| Chip · All days | x 0, 100 x 48 (pill 40) | x 0, 100.2 wide (label 68.2 + 2 x 16), 48 target, 40 pill | matches |
| Chip · Favourites | x 108, 120 x 48 | x 108, 120 wide | matches (8 apart) |
| Chip · Search | x 236, 89 x 48 | x 236.2, 89.1 | matches (part 3) |
| Chip label | node names "All days", "Favourites" | `label`, so "ALL DAYS", "FAVOURITES" | the widths agree with `label` to the pixel; see "For you to check" in the PR |
| List | y 336 | y 312 | 24 higher, as the chips |
| Day cards | DayCard with the bookmark | the same DayCard, filtered to days holding my mark | matches |

### Archive · search (RESULTS 508:1509 / 511:1740; KEYBOARD UP 511:1518 / 511:1777)

Measured 2026-11-05 on the same Pixel 9a, `-PmoyiUiPreview=today`, with `uiautomator`.

| Element | Figma | Built | |
|---|---|---|---|
| Navigation block | y 168, h 179: field 115, chips 16 under it | field y 168, h 114.3; chips' 48 row at y 298.3 | matches (Figma 299) |
| Field · search | 358 x 115 at x 0, label, 56 box, helper | `Field` (Text): label at y 168, helper at y 258.3 (h 24) | the design-system Field, unchanged |
| Field label / helper | "Search your days" / "Looks through everything you have both written." | the same, from states.md §6 | matches |
| Chips | All days x 0 (100), Favourites x 108 (120), Search x 236 (89) | Search at x 236.2, 89.1 wide (label 57.1 + 2 x 16) | matches |
| List | y 371 (24 under the chips) | first month heading at y 370.3 | matches |
| Month row | not drawn in search (the field replaces it) | absent; the field takes its slot | matches (part 4) |
| Results | DayCards, the first two lines of each entry, no snippet, no highlight | the same DayCard, month headings as in All days | the open question of states.md §6 is left open |
| KEYBOARD UP | field in Focus (`ink`, heavier stroke), empty, unfiltered list under it | focused on choosing Search (`focused=true`, `mInputShown=true`), unfiltered list | matches |
| Paging | not drawn | "flat": 13 days, 28 August to 4 August, the second page loaded on scrolling; 30 August (locked) absent | as the contract |
| Back from a result | not drawn | the query and results kept; the keyboard is not raised again | as the brief |

### Archive · search, no results (511:1691; light 511:1945)

| Element | Figma | Built | |
|---|---|---|---|
| Field | keeps the failing query | keeps it | matches |
| Chips | drawn, Search selected | drawn, Search selected | the way back |
| "Nothing matched that." | list y 0, x 0, h 33 | y 394.3 (list y 24), x 24 in the list, h 32.8 | the StateBlock's 24 inset, as Favourites empty |
| "Try another word, or go back to all days." | 16 under (y 49), h 24 | 15.9 under, h 24 | as above |

### Archive · month jump (257:9 in 223:3; light 281:757 in 281:754)

Measured 2026-11-06, Pixel 9a, `-PmoyiUiPreview=today`, with `uiautomator`.

| Element | Figma | Built | |
|---|---|---|---|
| Row | x 0, y 168, 358 x **80** | x 0, y 168, 379.4 x **56**, `outline` hairline, radius 12 | **states.md §6's 56** (the design system wins); Figma's 80 is for the owner (missing-components §17) |
| Calendar glyph | x 16, 24 x 24, centred | x 16, 24 x 24, centred, `ink` | matches |
| "September 2026" | x 48, 222 x 24 | x 48, `body` (24 high), one line | matches |
| "Jump to" | 64 x 48 target at x 278, label metrics, ending 16 from the edge | `TextLink`, `body` sentence case (D14), ending 16 from the edge | D14 |
| Screen reader | not drawn | one stop: "Showing September 2025. Jump to a month.", a button | interpreted |
| Gap to the chips | 16 | 16 (chips at 240) | matches |
| Row follows the scroll | not drawn | "September 2025", then "May 2025" after scrolling down twelve screens | as the brief |
| The picker | **not drawn** (no frame in 425:317) | Material 3 `AlertDialog` in Rosewood, "Jump to a month", one 48 row per month newest first, Cancel | platform dialog (missing-components §16) |
| Months offered | not drawn | September and August at first; September to April 2025 after scrolling to the end | no month after the Bond-day, none before the oldest loaded |
| After a jump (June 2025) | not drawn | the list from Monday 30 June, at its top; the row "June 2025" | `until=2025-06-30` |
| "Back to the newest days" | not drawn | `TextLink` under the chips, y 312, 48; the list 16 under it (y 376) | interpreted; PROPOSED copy |
| Favourites, May 2025 | not drawn | Thursday 29 May (the older favourite), Favourites still selected | `until` and `favourites=true` |
| Back from a day view after a jump | not drawn | the list where it was, not scrolled to the top again | as the brief |
| Search | the field replaces the row | the field at 168, chips at 298.3 | matches (part 3) |

### Archive · favourites, empty (440:1570; light 440:1621)

| Element | Figma | Built | |
|---|---|---|---|
| Chips | drawn | drawn, Favourites selected | they are the way back |
| "No favourites yet." | list y 0, x 0, h 33 | list y 24, x 24, h 32.8 | the StateBlock's 24 inset |
| "Mark a day and it will be easy to find again." | 16 under the heading, one line | 16 under the heading, two lines | the inset narrows it to 302 wide |
| "Only you can see your favourites." | 32 under the line | 24 under the line, at the StateBlock's inset | StateBlock's bottom padding; muted body |

### Archive · day view, favourites (438:1462; light 438:1497)

### Archive · empty (223:25)

| Element | Figma | Built | |
|---|---|---|---|
| Ghost day (265:15) | 358 x 84 dashed card above the copy | **not drawn** | owed: not a design-system component |
| Heading | y 463.5, centred | y 406, start-aligned | the StateBlock, which is start-aligned; the block centres in the space the ghost day leaves |
| Line | y 536.5, centred | y 487.6 | as above |
| Write | PrimaryAction 176 x 48 at y 592.5 | PrimaryAction at y 571.8, centred | opens the composer |

### Archive · day view (438:1462; long entry 243:2)

| Element | Figma | Built | |
|---|---|---|---|
| Back and date | one row at y 24; date at x 48 (64 on screen) | same row; date at x 64 | matches |
| Date text | "Tuesday 2 September" (node name) | "TUESDAY · 2 SEPTEMBER" | Today's `dayHeader`, as on Today; `label` uppercases it |
| Report link | x 302, 56 x 48 | **not drawn** | owed: the backend has no report endpoint |
| Partner EntryCard | y 88 | y 88 | matches |
| FavouriteToggle under each entry | 48 high, 8 below each card, x 0; 179 wide (Idle), 231 (Marked) | 48 high, 8 below each card, x 0; 153.5 wide (Idle), 202.3 (Marked) | the label is `body`, sentence case; Figma's widths fit `label` uppercase (see the PR) |
| Toggle glyph | 24, outline (Idle) / filled (Marked) | 24, Lucide `bookmark`, outline / filled, `ink` | matches |
| Toggle before the reveal, on a solo day's unlocked entry, on a tombstone | not drawn | absent | matches states.md §6 |
| A failed toggle | not drawn | back as it was, the Generic failure line under it (`InlineError`) | interpreted |
| My EntryCard | 16 below the partner's block | 16 below the partner's block | matches |
| "Delete what you wrote" | full-width 48 target, `danger`, underline | the same TextLink, `danger`, underline | matches |
| Long entry | scrolls, about 2.1 screens | scrolls | matches |

## Deviations, in short

1. **Positions above the list**: the month row is states.md's 56, not Figma's 80, so the
   chips and the list sit 24 higher than Figma draws them (240 and 312, not 264 and 336).
2. **Month headings** stay in the list: the row names only the month at the top, and Search
   has no row. Figma draws none. Interpreted, for the owner to check.
3. **Empty state**: start-aligned (StateBlock) where Figma centres, and no ghost day.
4. **Day view**: no Report link. The FavouriteToggle is drawn (part 2), its label in
   sentence-case `body` where Figma's widths imply uppercase `label`.
5. **D14**: the delete and Try again links are sentence case, as every secondary link is.
6. **Chips**: 24 higher than Figma outside search (the 56 row); in search they sit where
   Figma draws them, under the field.
7. **Favourites empty**: the StateBlock's 24 inset, so the copy sits 24 in and the line wraps.
8. **Search no results**: the same StateBlock inset, where Figma has the message flush at x 0.
9. **Search failure**: not drawn in Figma; the inline Generic failure line and Try again
   take the list's place, with the field and chips kept.
10. **Month picker**: not drawn anywhere; the platform's simple dialog (Material 3), the same
    Compose dialog on iOS. It reaches back only to the oldest day loaded.
11. **"Back to the newest days"**: not drawn; a TextLink under the chips after a jump.
12. **"Jump to"** is sentence-case `body` (D14) where Figma draws label metrics.

## Owed

The Archive is built (parts 1 to 4). What is still owed, in one place:

1. **Report** on the day view (Figma 438:1462, x 302, 56 x 48). The endpoint exists in the
   backend's newer contract; this repository's `contracts/openapi.json` predates it, so it
   needs `./gradlew syncContract` (its own PR, since that diff is the API changing), then the
   link and its flow.
2. **"Your streak"** link in the title row (430:1435, 100 x 48), and the Streak screen it opens.
3. **R3, screen capture**: the partner's entries are not protected on this branch. When PR #17
   merges, `EntryCard` Partner protects itself; the `DayCard` here is drawn without EntryCard,
   so it needs the same protection added then (`TODO(#17)` in `ArchiveScreen.kt`).
4. **The month picker**, if the owner wants one drawn (missing-components §16), and how far
   back it reaches: today only to the oldest day loaded (the bond's `createdAt` or a server
   list of months would let it reach the start).
5. **Month row height**: states.md's 56 or Figma's 80 (missing-components §17).
6. **Search results**: a match-centred snippet or a highlight (states.md §6, open).
7. **Favourite announcements**: "Added to favourites" / "Removed from favourites"
   (strings-proposed.md §6a) are not used yet; the toggle's new label is announced instead.
8. **The ghost day** in the empty state, once it is a design-system component.
9. **A cache and `If-None-Match`** for the list, the day and search (doc 13 §5), with SQLDelight.
10. **Figma screenshots**: none of parts 2 to 4 was compared with a screenshot (rate limit);
    every frame since 223:3 and 223:25 was compared by geometry only.
11. **iOS on a simulator**: the framework and the Xcode app were built for every part, but the
    Archive has not been run on an iOS simulator.
