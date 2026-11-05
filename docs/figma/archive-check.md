# Archive, part 1, against Figma

The Archive list, its empty state and the day view as built, measured on a running app and
compared with the Figma frames in section 425:317. The screens run on preview data in the
`-PmoyiUiPreview=today` build (`PreviewArchive.kt`); against a backend they use
`GET /bonds/{bondId}/days` and `GET /bonds/{bondId}/days/{date}`. Measured 2026-11-05.

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

## Results

### Archive · with entries (223:3)

| Element | Figma | Built | |
|---|---|---|---|
| Back target | y 24, 48 x 48 | y 24, 48 x 48 | matches |
| Title "Your days" | y 96, h 48 | y 96, h 48 | matches |
| Navigation block (month row, chips) | y 168 to 312 | **not drawn** | owed |
| List | y 336 | y 168 | **168 higher**: the navigation block above it is owed |
| Month heading "SEPTEMBER 2025" | none (the month row names it) | y 168, `label`, a heading | **added**, see below |
| DayCard | 358 x 259, padding 16 | 369 x 258.3 at y 202 | matches (width is the device's) |
| Date "TUESDAY 2" | card y 16 | card y 16 | matches |
| "FROM TUNDE" | card y 51 | card y 50.3 | matches |
| Entry text | card y 74, two lines, h 65 | card y 72.8, two lines, ellipsis | matches |
| "FROM YOU" | card y 155 | card y 154.3 | matches |
| Cards apart | 16 (275 minus 259) | 16 | matches |
| Chevron | none (states.md §6) | none | matches |
| "Your streak" link, favourite bookmark | drawn | **not drawn** | owed |

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
| FavouriteToggle under each entry | 48 high, 8 below each card | **not drawn** | owed; everything below moves up 56 per entry |
| My EntryCard | 16 below the partner's block | 16 below the partner's card | matches (less the toggle) |
| "Delete what you wrote" | full-width 48 target, `danger`, underline | the same TextLink, `danger`, underline | matches |
| Long entry | scrolls, about 2.1 screens | scrolls | matches |

## Deviations, in short

1. **Positions above the list**: the month row and the chips are owed, so the list starts
   168 higher than Figma draws it.
2. **Month headings**: with the month row gone, nothing would say which month a day is in,
   so each month gets a `label` heading in the list. Interpreted, for the owner to check.
3. **Empty state**: start-aligned (StateBlock) where Figma centres, and no ghost day.
4. **Day view**: no FavouriteToggle and no Report, so the entries sit 56 closer per entry.
5. **D14**: the delete and Try again links are sentence case, as every secondary link is.

## Owed

- Month row with Jump to (`until`), the All days / Favourites / Search chips, the favourite
  bookmarks and FavouriteToggle, the "Your streak" link, and Report.
- The ghost day in the empty state, once it is a design-system component.
- **R3, screen capture**: the partner's entries here are not protected on this branch. They
  are when PR #17 merges, because `EntryCard` Partner protects itself there. The DayCard is
  drawn in this feature, not with EntryCard, so it needs the same protection added then.
- A cache and `If-None-Match` for both calls (doc 13 §5), with SQLDelight.
- iOS was built (framework and Xcode app) but not run on a simulator for this check.
