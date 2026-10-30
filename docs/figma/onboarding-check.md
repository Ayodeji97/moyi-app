# Onboarding screens against Figma (PR #7)

The onboarding and join screens as built, measured on running apps and compared with the
element tables in the onboarding spec (`onboarding-today-spec.md` §3, geometry read from the
Figma metadata of 2026-10-27). Measured 2026-10-28.

**What the Figma side is.** Every x, y, w and h below is Figma metadata. Text, styles,
colours and the insides of `InviteCode`, `StateBlock` and `EntryCard` were **not** read from
Figma (the MCP limit was hit), so those are compared only by their outer box.

**How.** The same method as `auth-screens-check.md`:

- **iOS:** iPhone 17 simulator, 402 × 874 pt, safe areas 62 top and 34 bottom, read with
  `idb ui describe-all`.
- **Android:** a Pixel 8 Pro emulator (`emulator-5556`, 1344 × 2992 px at 480 dpi, so
  448 × 997.3 dp; status bar 50.3, gesture bar 24), read with `uiautomator dump`. The usual
  `emulator-5554` was in use by the PR #8 session and had no room for a second install, so
  a second AVD was booted instead of evicting its build.
- **Content y** is the measured y minus the top inset; it should equal Figma's y.
- **Action-block elements** sit on the bottom, so they are compared by distance from the
  bottom inset: Figma 844 − y against the measured distance.
- **Widths:** Figma's column is 358; ours is the screen width minus 32 (370 on iOS, 416 on
  Android). A centred element's x shifts by half the difference.
- Within 2 counts as a match (a label line of 18.2 is drawn 19 in Figma).

Reached through the debug UI preview (README, "UI preview"): `-PmoyiUiPreview=onboarding`.

## Defect found by running, and fixed

**The iOS app crashed building the onboarding graph.** `CreateBondRoute(type: RelationshipType)`:
Compose Navigation on iOS has no NavType for an enum argument ("could not find any NavType for
argument type"). It compiled, every unit test passed, and `./gradlew build` was green. The
route now carries the type's name (`CreateBondRoute(typeName)`), and an iOS test
(`OnboardingGraphTest`) builds the real graph and navigates every argument. The test was run
against the old route and fails there (2 of 2).

Two smaller fixes from looking at the running app: the time-zone field's label was spoken
twice (once as text, once as the box's name), and Words capitalisation turned a typed bond
name into "Amara And Tunde" (now Sentences).

## Onboarding 1 · the promise (358:45)

**Superseded by the owner's variant C ruling (2026-10-30, `figma-deviations.md` D13):** the block is
now centred above the actions and holds two sample cards, so the y values below describe the
earlier top-aligned build and no longer match. Figma has not been redrawn.

| Element | Figma | iOS | Android | Match |
|---|---|---|---|---|
| Heading (reciprocity), y / h | 72 / 65 | 72 / 65.3 | 72 / 65.3 | yes |
| Line, y / h (gap 16) | 153 / 48 | 153.3 / 48 | 153.3 / 48 | yes |
| Sample card, y | 225 | 225.3 | 225.3 | yes |
| Sample card, h | 144 | 130 | 130 | **no**, see below |
| Begin, from bottom (700) | 144 | 144 | 144 | yes |
| Begin, w × h | 176 × 48 | 176 × 48 | 176 × 48 | yes |
| "I already have an account", from bottom (772) | 72 | 72 | 72 | yes |
| Its text width | 218 | (SF) | 219.3 | yes |

**Sample card height, open.** Figma's sample `EntryCard` is 144; ours is 24 + label 19 + 16 +
one `entry` line 47.6 + 24 = 130. The `EntryCard` page (63:2) was not read, so the internal
gap or padding that makes 144 is unknown. The card is a local stand-in until PR #8's
`EntryCard` lands (`TODO(ui)` in `PromiseScreen.kt`), which should settle it.

## Onboarding 4 · relationship type (341:24, SELECTED 359:79)

| Element | Figma | iOS | Android | Match |
|---|---|---|---|---|
| Back target | 16, 24, 48 × 48 | 16, 24, 48 × 48 | 16, 24, 48 × 48 | yes |
| Eyebrow, y | 88 | 88 | 88 | yes |
| Heading, y / h | 171 / 33 | 170.3 / 32.7 | 170.3 / 32.7 | yes |
| Line, y / h | 220 / 48 | 219 / 48 | 219 / 48 | yes |
| Card · partner, y / h | 316 / 81 | 315 / 80.7 | 315 / 80.7 | yes |
| Card · friend, y | 413 | 411.7 | 411.7 | yes |
| Card · family, y | 510 | 508.3 | 508.3 | yes |
| Card label inset | 40, 340 | 40, (merged) | 40, 339 | yes |
| Check (selected), 24 in from the card end | right edge 350 of 374 | right edge 362 of 386 | n/a | yes |
| PrimaryAction | none | none | none | yes |

The 1-pt drift accumulates from the 18.3 label line, as in the auth check.

## Onboarding 5 · create bond (342:17)

| Element | Figma | iOS | Android | Match |
|---|---|---|---|---|
| Eyebrow, y | 88 | 88 | 88 | yes |
| Heading, y | 171 | 170.3 | 170.4 | yes |
| Field · bond name, y / h | 228 / 115 | 227 / 115.7 | 227 / 114.3 | yes |
| Field · time zone, y / h | 367 / 139 | 366.7 / 138.3 | 367.4 / 138.3 | yes |
| Time-zone box h | 56 | 56 | 56 | yes |
| Create bond, from bottom (772) | 72 | 72 | 72 | yes |
| Failed (394:1441): error 8 above the button | gap 8 | preview only | preview only | by construction |
| Keyboard up (362:1196) | action on the keyboard | not shown (hardware keyboard on the simulator) | not shown (the emulator's Gboard was in hardware-keyboard mode) | **not verified** |

Failed and keyboard-up are previews (`CreateBondFailedPreview`, `CreateBondKeyboardUpPreview`),
as the brief asked. The keyboard-up layout comes from `MoyiScaffold`, which auth's
keyboard-up frame already verified.

## Onboarding 6 · invite handoff (342:48, copied 394:1468)

| Element | Figma | iOS | Android | Match |
|---|---|---|---|---|
| Back | none | none | none | yes |
| Eyebrow, y | 24 | 24 | 24 | yes |
| Heading, y | 107 | 106.3 | 106.3 | yes |
| Line, y / h | 156 / 48 | 155 / 48 | 155 / 48 | yes |
| InviteCode, y | 228 | 227 (caption 251 − 24) | 227 | yes |
| InviteCode, h | 195 | 195 (227 to 422) | 195 | yes |
| Share invite, from bottom (700) | 144 | 144 | 144 | yes |
| Continue, from bottom (772) | 72 | 72 | 72 | yes |
| Copied state | content changes only | glyph and words change, same box | same | yes |

The InviteCode's inner layout is interpreted (component page 219:2 not read): padding 24,
caption, 24, code, 24, a 48 copy target. It lands on Figma's 195 to the point.

## Join · code from link, prefilled (308:4)

| Element | Figma | iOS | Android | Match |
|---|---|---|---|---|
| Back | none | none | none | yes |
| Eyebrow, y | 24 | 24 | | yes |
| Heading, y | 107 | 106.3 | | yes |
| Line, y / h | 156 / 24 | 155 / 24 | | yes |
| Field · code, y / h | 196 / 148 | 195 / 147.3 | | yes |
| Continue, from bottom (772) | 72 | 72 | | yes |

## Join · manual entry, keyboard up (309:42)

| Element | Figma | iOS | Match |
|---|---|---|---|
| Back | 16, 24 | 16, 24 | yes |
| Eyebrow, y | 88 | 88 | yes |
| Heading, y (gap 16, not 64) | 123 | 122.3 | yes |
| Line, y / h | 172 / 48 | 171 / 48 | yes |
| Field · code, y / h | 236 / 148 | 235 / 147.3 | yes |
| Character error (`0`, `O`, `1`, `I`, `L`, `U`) | in file | shown as typed, `danger`, 2 px stroke | yes |

## Join · confirm (310:14)

| Element | Figma | iOS | Match |
|---|---|---|---|
| Eyebrow, y | 88 | 88 | yes |
| Heading, y | 171 | 170.3 | yes |
| Heading, h | 33 | 65.3 | **data** |
| Reciprocity line, y | 220 | 251.7 | follows the heading |
| Type line, y | 284 | 315.7 | follows the heading |
| Join, from bottom (700) | 144 | 144 | yes |
| Not now, from bottom (772) | 72 | 72 | yes |
| Not now target, w | 67 | 74 | yes (font metrics; it hugs its text, not full width) |

The fake names ("Amara invited you to Amara and Tunde.") wrap to two lines; Figma's sample
fits one. Every gap is still 16, so the lines move down by the extra 32. Real names will do
the same; nothing in the spec caps them.

## Join · cannot be used (310:23)

| Element | Figma | iOS | Match |
|---|---|---|---|
| Eyebrow, y | 88 | 88 (Android 88) | yes |
| StateBlock, w | 350, native | 350 (x 26 on a 402 screen, Figma x 20 on 390; Android x 49 on 448) | yes |
| StateBlock, h | 185 | 185 (heading 33, line 24, action 48, gaps 16, padding 24) | yes |
| StateBlock, vertical | centred in the content under the eyebrow | centred: 345.7 to 530.7 in 123 to 754 | yes (rule) |
| StateBlock, y | 379 | 345.7 | differs with screen height |
| "Enter a different code" | → manual entry | → manual entry, this screen replaced | yes |

The absolute y differs because the iPhone 17's safe content is 730 tall against Figma's 796;
the rule (centred in what is left under the eyebrow) is what matches.

## Navigation, clicked through

On the iPhone simulator (and the Android emulator where noted):

- Promise → Begin → sign up; sign up → "I already have an account" → sign in; back → the
  promise (Android).
- Promise → "I already have an account" → sign in; back → the promise (Android).
- Relationship type → a card → create bond; back → relationship type with that card checked.
- Create bond, empty name → field error; with a name → invite handoff, with no back.
- Invite → Copy code → Copied; Continue → the placeholder (`onOnboardingFinished`). In the
  preview there is no session, so it reads "Hello, " with no name: expected there, not in a
  signed-in run.
- Join prefilled → Continue → confirm; Not now → back to the code. (Android: to confirm.)
- Join manual (keyboard up on arrival, Android) → `ZZZZZZ` → cannot be used (both) → "Enter a different code" → manual; back → the
  first manual screen (the failed screen is not left behind).

## Not verified

- The keyboard-up frames on a device (see create bond).
- Light mode on a device. Previews cover it.
- 200 % font scale on a device.
- TalkBack and VoiceOver walks (owed before release, SKILL.md §8).
- **Typing on the Android emulator dropped and doubled characters** ("abc def" arrived as
  "aa dede") while the host's load average was over 20 and the emulator's over 40. iOS
  typing was clean. Auth's fields hoist their value the same way (a `String` through the
  ViewModel), so this is not new to this PR; worth re-checking on an idle machine, and a
  reason to look at `TextFieldState` (see the PR's concepts). The emulator also raised one
  ANR on that screen: "Input dispatching timed out ... Waited 5059ms for KeyEvent", with the
  emulator's load at 38 / 45 / 50. The same screen and typing ran without a stall on iOS.

## Wired to the backend (feat/onboarding-wiring)

Run 2026-10-29 against an isolated backend copy (port 8092, its own Postgres and Valkey
containers, removed afterwards), on a Pixel 8 Pro emulator and an iPhone 17 simulator
(iOS 26.3, ad-hoc signed for the Keychain).

| Step | Device | Result |
|---|---|---|
| Sign in, verified, no bond | Android | Start check (`GET /bonds`, `GET /me`) → relationship type, as a root with its eyebrow |
| Create bond "Amara and Tunde", device zone Europe/Lisbon | Android | `POST /bonds` 201 → invite handoff with the real code (XHB4Q2, matched in the database) |
| Copy code, Share invite | Android | Copied state; the system share sheet with the link and the code |
| Continue; relaunch | Android | Today; a cold launch goes straight to Today (pending bond) |
| Sign in, unverified, no bond | iOS | Unverified gate (345:46) with the address |
| Send it again | iOS | The Resent acknowledgement |
| Verify via the API, return to the app | iOS | The gate re-reads `GET /me` and moves to relationship type |
| "I have an invite code", paste `http://localhost:3000/i/xhb4q2` | iOS | The field holds `XHB4Q2` |
| Continue | iOS | Confirm: "Amara invited you to Amara and Tunde.", the couple line |
| Join; relaunch | iOS | `POST /invites/XHB4Q2/accept` 200; bond ACTIVE with two members; Today, and Today again on a cold launch |

Not run on a device: the cannot-be-used screen from a real `404` (the API's `404
INVITE_NOT_USABLE` for the spent code and `422` for a malformed one were checked with curl;
the mapping is unit-tested), offline and rate-limited states (unit-tested), the Generic
error (preview and unit test only). Today itself still shows fake data.

### Not in Figma

- **"I have an invite code"** on relationship type. Manual entry (309:42) has a back, but
  nothing in Figma leads to it; without a way in, a joiner without a link could only create
  a bond. Owner ruling owed on the link, its copy and its place.
- **Join errors other than "cannot be used"** sit inline above the action, the code kept,
  following create bond's failed state (394:1441). No frame exists for them.
