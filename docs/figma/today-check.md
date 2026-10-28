# The daily loop against Figma (PR #8)

Today, Write, Reveal and delete-after-reveal as built, measured on a running app and
compared with the Figma frames. **UI only**: every screen runs on fake data
(`FakeDailyLoop`), and every real call or rule is a `TODO(logic)` in the code.
Measured 2026-10-28.

## Sources, and what they are worth

- **Geometry**: `scratchpad/figma/screens.xml`, the `get_metadata` dump of the Screens page
  (425:2) taken 2026-10-27, and the daily-loop spec built from it
  (`onboarding-today-spec.md`). Every node id and x/y/w/h below is real Figma data.
- **Not read from Figma** (the MCP refused the session, spec §7): text content, text styles,
  colours, component internals (EntryCard 63:2, ComposeField 78:2, StateBlock 72:2), the two
  button labels, and the motion board 180:21. Those come from `tokens.md`, `states.md` and
  `copy.md`, and are marked *derived* below.

## How it was measured

Android emulator, 1080 × 2424 px at 420 dpi: 411.4 × 923.4 dp, a 54.1 dp status bar and a
24 dp gesture bar. Read with `uiautomator dump`, as `docs/figma/auth-screens-check.md` did.

- **y** is the measured y minus the 54.1 top inset, so it compares directly with Figma.
- The content area ends at 821.3 (Figma 820), so centred content lands within about 1 of
  Figma's position when its height matches.
- **Widths**: the column is 379.4 (Figma 358), so long text wraps a little later. Entry
  heights still matched Figma's line counts for the fake text (3 lines own, 5 partner).
- Within 2 counts as a match (Figma rounds a label line of 18.2 to a 19 box).

## Reaching the screens (debug only)

There is no backend behind these screens yet, so a debug build can start on them directly:

```
./gradlew :androidApp:assembleDebug -PmoyiUiPreview=today
```

- **Only the `-P` switch counts.** `local.properties`, `gradle.properties` and
  `ORG_GRADLE_PROJECT_moyiUiPreview` are ignored: those persist, so a value left in one would
  ride into the next build, a release included. It is the one switch the README's "UI preview
  (debug only)" describes, shared with `-PmoyiUiPreview=onboarding`.
- **Debug only.** With it set, the build fails unless every requested task contains `Debug`,
  or the only task is Xcode's `embedAndSignAppleFrameworkForXcode` with `CONFIGURATION=Debug`.
  So `assemble`, `build`, `assembleRelease`, an Xcode Release build and anything with `CI` set
  all fail at configuration.
- **iOS**: Xcode's "Compile Kotlin Framework" build phase runs
  `./gradlew :shared:embedAndSignAppleFrameworkForXcode` with no switch. To preview on iOS, add
  ` -PmoyiUiPreview=today` to the end of that line locally, build the Debug configuration, and
  revert the line before committing. Left in by mistake, it cannot ship: a Release build or CI
  fails on the guard above.
- `moyiUiPreview` is a BuildKonfig field in `:shared` (`UiPreviewConfig.UI_PREVIEW`). Empty,
  the default, is the real app. `today` starts at Today without waiting for a session and
  switches on the debug gestures. Other UI PRs add their own value to the same property
  (`uiPreviewScreens` in `shared/build.gradle.kts`); an unknown value fails the build.
- **Long-press the date** on Today to step through the states in order: not written,
  written-waiting, (the reveal plays and settles) revealed, tombstone, partner not joined,
  solo you wrote, solo they wrote, queued, and round again.
- **Long-press beside the button** on Write (not on it) to step: empty, past 400, over
  limit, sending, editing, save failed, queued.
- **Long-press the date** on Reveal to replay it.
- The real taps work too: Write → type → Send goes to written-waiting; "Edit what you wrote"
  opens the composer with Save; "Delete what you wrote" after the reveal asks, then leaves
  the tombstone; the settings icon opens the signed-in placeholder, where Sign out is.
- Reduced motion: Android *Remove animations*, iOS *Reduce Motion*.

Release builds must never set the property. There is no release flavor yet, so nothing
enforces that beyond the default being empty.

## Results

Match: ✓ within 2. ✗ a difference, with the reason. "—" not drawn in Figma.

### Today · not written (159:2)

| Element | Figma | Ours | Match |
|---|---|---|---|
| nav row | 358 × 48 at (16, 24) | 48 tall at 24 | ✓ |
| date | 262 × 48 at x 16, label style *(derived)* | x 16, text centred in the 48 (y 38.9) | ✓ |
| calendar target | 48 × 48 at x 278 (390 − 16 − 96), glyph 24 | 48 × 48 at x 299.4 (411.4 − 16 − 96), glyph 24 | ✓ (same rule) |
| settings target | 48 × 48 at x 326 | 48 × 48 at x 347.4 | ✓ (same rule) |
| content top | 88 | 88 | ✓ |
| prompt | y 136, h 96, entry 34/1.40 *(derived)* | y 136.0, h 95.2, entry | ✓ |
| PrimaryAction | 176 × 48 at y 772, 48 above the bottom | 176 × 48, ends at the content bottom | ✓ |
| button label | not in the corpus | "Write" *(proposed)* | — |

### Today · written-waiting (159:9)

| Element | Figma | Ours | Match |
|---|---|---|---|
| own EntryCard | 358 × 226 at y 221 | 225.2 tall at y 245.7 | ✗ 24 lower, see below |
| card to note gap | 16 | 16 | ✓ |
| waiting StateBlock | 358 × 224 at y 463 | DayNote stand-in, 176.7 tall | ✗ owed to PR #7 |

The card is 24 low only because the stand-in note is 48 shorter than the StateBlock and the
pair is centred. **StateBlock is PR #7's component**; after the rebase, `DayNote` is replaced
by it and the pair re-centres. Edit and delete affordances are inside it (states.md §4).

### Today · revealed (159:19)

| Element | Figma | Ours | Match |
|---|---|---|---|
| title "Your day is open." | (16, 88) h 33 | (16, 88.0) h 32.8 | ✓ |
| partner EntryCard first | 358 × 321 at y 197 | 320.4 tall at y 174.5 | ✗ 22.5 higher, see below |
| own EntryCard second, gap 16 | 358 × 226 at y 534 | 225.2 tall at y 510.9, gap 16 | ✗ same shift |
| "Delete what you wrote" | not on 159:19 | 48 target under my card, `danger`, underlined | ✗ added |

**Deliberate.** states.md §6 (2026-09-07) puts "Delete what you wrote" on the revealed day as
a 48 target in `danger` with an underline, and §5 says delete after reveal is permitted. 159:19
predates that and draws no link, and states.md itself says which surface owns it "is still
unsettled". It lives in the centred group, so the cards sit 24 higher than Figma. Owner call:
keep it here, or move it to Archive's day view.

### Today · tombstone (420:54)

| Element | Figma | Ours | Match |
|---|---|---|---|
| layout | Reveal surface: no nav row, title at 59 | Today's revealed layout: nav row, title at 88 | ✗ deliberate |
| partner card | 321 at y 242 | 320.4 | ✓ size |
| own tombstone | 358 × 107, recessed | 106.3, `surface` fill, "From you" | ✓ |

Figma draws the tombstone on the Reveal surface, but the delete confirm returns to Today, and
states.md flags that drawing it on Reveal makes the tombstone a dead end. Built on Today's
layout so the nav row stays.

### Today · partner not joined (188:481)

| Element | Figma | Ours | Match |
|---|---|---|---|
| own EntryCard | 226 at y 143.5 | at y 318.1 | ✗ InviteCode missing |
| PartnerPending StateBlock | 168 at 385.5 | DayNote stand-in | ✗ owed to PR #7 |
| InviteCode | 358 × 195 at 569.5 | not built (PR #7's component) | ✗ owed to PR #7 |

### Today · solo days (188:491, 188:501)

| Element | Figma | Ours | Match |
|---|---|---|---|
| date | "Monday · 1 September" | the day before | ✓ |
| you wrote: card + Solo StateBlock | 226 at 237, block 192 at 479 | card at 322.3, DayNote 24 | ✗ owed to PR #7 |
| they wrote: partner card | 358 × 321 at y 273.5 | 320.4 at y 274.7 | ✓ |
| they wrote: line | body, 24 at y 610.5 | 24 at y 611.0 | ✓ |

### Today · queued offline (384:327)

| Element | Figma | Ours | Match |
|---|---|---|---|
| own card + Offline StateBlock | card 226 at 228.5, block 209 at 470.5 | card at 310.1, DayNote two lines | ✗ owed to PR #7 |
| copy | states.md §10 | "It'll send when you're back online. It will count for Tuesday, 2 September." | ✓ |

### Write, keyboard up (181:3, 181:43, 181:83, 419:193, 419:291)

| Element | Figma | Ours | Match |
|---|---|---|---|
| header | 0 to 72, back 48 × 48 at (16, 24), no title | back 48 × 48 at (16, 24) | ✓ |
| ComposeField top | y 96 | 96.2 | ✓ |
| text and glyphs at 20 from the box edge | tokens.md §6 | prompt text x 36.2, photo glyph x 36.2 (16 + 20) | ✓ |
| ComposeField to button | 24 (456 → 480) | 24 | ✓ (fixed, was 16) |
| button to keyboard | 16 (528 → 544) | 16 | ✓ (fixed, was 0) |
| PrimaryAction | 176 × 48 centred | 176 × 48 centred | ✓ |
| field height | 360 (with a 300 keyboard) | 348 (this keyboard is taller) | ✓ fills the space |
| counter | past 400 only, neutral | "412 / 500" from 401, `inkMuted` | ✓ |
| over limit | `danger` counter, action likely Disabled *(not read)* | `danger` counter, 2 stroke, Disabled | ✓ (Disabled is our reading) |
| sending | State=Sending, 60 %, "Sending" | the same, keyboard stays up | ✓ |
| editing | label "Save" | "Save", no prompt | ✓ |
| empty | action state not read | Disabled (blank cannot be sent, FR-041) | — interpreted |
| Send label | not in the corpus | "Send" *(proposed)* | — |

### Write · save failed (384:229)

| Element | Figma | Ours | Match |
|---|---|---|---|
| error line | 358 × 24 at y 448, `danger`, centred | 24 tall, 24 below the field, 8 above the button | ✓ |
| field shrinks | 360 → 328 | shrinks by the error's 32 | ✓ |
| button | at 480, gap 8, "Try again" | gap 8, "Try again" | ✓ |
| text kept, keyboard up | states.md §10 | yes | ✓ |

### Reveal (179:3 before, 179:8 after)

| Element | Figma | Ours | Match |
|---|---|---|---|
| nav row | none | none | ✓ |
| date | (16, 24) h 19 | (16, 24.0) h 18.3 | ✓ |
| title (after) | (16, 59) h 33 | (16, 58.3) h 32.8 | ✓ |
| partner card (after) | 358 × 321 at y 182.5 | 320.4 at y 183.6 | ✓ |
| own card (after), gap 16 | 226 at y 519.5 | 225.2 at y 520.0 | ✓ |
| before | own card at 206.5 + waiting block 224 | the same as Today's waiting, centred | ✗ StateBlock owed (PR #7) |
| reduced motion (179:23, 179:28) | same geometry | same geometry, cross-fade only | ✓ |
| motion values | board 180:21, **never read** | placeholders, `TODO(motion)` | — |

### Delete · confirm (420:64)

| Element | Figma | Ours | Match |
|---|---|---|---|
| eyebrow | y 24, label | y 24.0, "Your entry" *(proposed)* | ✓ |
| heading | y 107, h 33 | y 106.3, h 32.8 | ✓ |
| line, 3 lines | y 156, h 72 | y 155.0, h 72.0 | ✓ |
| PrimaryAction, danger fill, surface label | at (107, 700) | 176 × 48 at y 701.3 | ✓ |
| Cancel | target at 772, text 57 wide | target at 773.3, text 56.4 wide | ✓ |

## iOS

iPhone 17 simulator (402 × 874 pt), signed ad hoc as `docs/session.md` describes, built with
`moyiUiPreview=today` (then read from `local.properties`, which no longer works: see "Reaching the screens"), driven with Maestro (`longPressOn` a point for the
debug switch). Every Today state, every Write state, the reveal after, the settle, delete
confirm, the tombstone and the settings route render as on Android. Not measured node by node:
`idb` is not installed on this machine any more, so the iOS row is by eye against the Android
measurements. The simulator had its hardware keyboard connected, so Write shows no software
keyboard on iOS and the composer fills the screen. The reveal's before frame was too quick for
Maestro to catch; Android has it.

## Two defects found by running, fixed

1. **The window panned up when the composer took focus**, pushing the header and the top of
   the field off the screen. The activity had no `windowSoftInputMode`; it now asks for
   `adjustResize`, so the keyboard reaches Compose as an inset.
2. **Sending dropped the keyboard.** A disabled (or read-only) text field loses focus, so the
   keyboard went down and the button jumped. The field stays editable and the ViewModel
   ignores edits while sending.

Plus the two composer gaps in the Write table, which also change auth's keyboard-up frames
(the scaffold's keyboard gap is shared), towards Figma.

## Owed

- **PR #7**: StateBlock (waiting, partner pending, solo, offline) and InviteCode on partner
  not joined. Every `DayNote` is a stand-in marked `TODO(PR #7)`.
- **Figma, when the MCP is available**: the motion board 180:21, the Today and Send labels,
  StateBlock and EntryCard internals, the over-limit and empty action states.
- **Not drawn anywhere**: Today for EMPTY, SUSPENDED and FROZEN days; "revealed while
  editing" (409 ENTRY_IMMUTABLE); the paper grain asset on EntryCard (tokens.md §8 item 5).
