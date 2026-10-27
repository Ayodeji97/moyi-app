# Auth screens against Figma (PR #6)

The three auth screens as built, measured on running apps and compared with the Figma
frames in `auth-placement-spec.md` §3. Measured 2026-10-27.

**How.** The Figma frames are 390 × 844 and draw no system bars: 24 of padding top and
bottom, nothing else. The apps were measured from their accessibility trees, on screens of
a different size:

- **Android:** emulator, 1080 × 2424 px at 420 dpi, so 411.4 × 923.4 dp. The status bar is
  54.1 dp and the gesture bar 24 dp. Read with `uiautomator dump`.
- **iOS:** iPhone 17 simulator, 402 × 874 pt. Safe areas are 62 top and 34 bottom. Read with
  `idb ui describe-all`.

To compare like with like:

- **Content y** is the measured y minus the top system inset (54.1 dp or 62 pt). The scaffold
  adds its 24 inside the insets (D6 as ruled), so content y should equal Figma y.
- **Action-block elements** are pinned to the bottom, and these screens are taller than
  Figma's 844. They are compared by their **distance from the bottom inset** instead: Figma
  844 − y against (screen height − bottom inset − y).
- **Widths** are 358 in Figma. The column is the screen width minus 2 × 16: 379.4 dp on
  Android and 370 pt on iOS. A centred element's x is (width − w) / 2.

Within 2 counts as a match: Figma rounds a label line of 18.2 up to a 19 box, so the
measurements drift by about 1 after each label.

## One mismatch found and fixed

The first measurement put every text box 4 to 6 short. The eyebrow box was 15.2 against 19,
and a body helper 18.7 against 24. That made each Field about 9 short (105.9 against 115)
and moved the headings up 4.

The cause: Compose's default `LineHeightStyle` trims the space above a text's first line and
below its last. Figma keeps the whole line box. The fix sets
`LineHeightStyle(Alignment.Center, Trim.None)` on every Rosewood style (`MoyiTypography.kt`).
The tables below are measured after it.

## Sign up · IDLE (Figma 340:9)

| Element | Figma (x, y, w, h; gap above) | Android dp (content) | iOS pt (content) | Match? |
|---|---|---|---|---|
| Back target | 16, 24, 48 × 48 | 16, 24.0, 48 × 48 | 16, 24, 48 × 48 | yes |
| Eyebrow "NEW ACCOUNT" | 16, 88, 358 × 19; 16 | 16, 88.0, h 18.3 | 16, 88, h 18.3 | yes |
| Heading "Create your account" | 16, 171, h 33; 64 | 16, 170.3, h 32.8 | 16, 170.3, h 32.7 | yes |
| **Field · name (NEW)** | none; §7 derives 16, 228, h 115; 24 | 16, 227.0, 379.4 × 114.3 | 16, 227, 370 × 115.7 | yes (derived) |
| Field · email | 16, 228, h 115; 24. §7 moves it to 367 | 16, 365.3, h 114.3 | 16, 366.7, h 114.3 | yes (vs 367) |
| Field · password | 16, 367, h 115. §7 moves it to 506 | 16, 503.6 | 16, 445.7 (scrolled) | yes. It scrolls, as §7 requires |
| Consent row | 16, 580, 358 × 48 (264 from the bottom) | 264.0 from the bottom, h 48 | 264 from the bottom, h 48 | yes |
| Legal "Terms" / "Privacy Policy" | 16 / 80, 644, 48 / 99 wide (200 from the bottom) | 16 / 80, 200 from the bottom, 48 / 98.7 | 16 / 80, 200 from the bottom, 48 / 104.7 | yes. iOS's SF is wider than Roboto |
| PrimaryAction "Create account", Disabled | centred, 708, 176 × 48 (136 from the bottom); 16 | x 117.7 (centred), 136 from the bottom, 176 × 48 | x 113 (centred), 136 from the bottom, 176 × 48 | yes |
| Secondary "I already have an account" | 16, 772, 358 × 48; 16 | 16, 773.3, 379.4 × 48; 16 | 16, 768 (bottom 816 = 874 − 34 − 24), 370 × 48 | yes |
| Bottom padding | 24 | 24 above the gesture bar | 24 above the home indicator | yes |

## Check your email · WAITING (Figma 341:17)

| Element | Figma (x, y, w, h; gap above) | Android dp (content) | iOS pt (content) | Match? |
|---|---|---|---|---|
| Back target | 16, 24, 48 × 48 | 16, 24.0, 48 × 48 | 16, 24, 48 × 48 | yes |
| Eyebrow "NEW ACCOUNT" | 16, 88, h 19 | 16, 88.0, h 18.3 | 16, 88, h 18.3 | yes |
| Heading "Check your email" | 16, 171, h 33; 64 | 16, 170.3, h 32.8 | 16, 170.3, h 32.7 | yes |
| Line "We sent a link to {email}…" | 16, 220, 358 × 48; 16 | 16, 219.0, h 48 (2 lines) | 16, 219, h 72 (3 lines: a longer address) | yes |
| **Paste-token field (NEW)** | none; §7 derives 16, 292, h 115; 24 | 16, 291.0, h 138.3 (2-line helper) | 16, 315, h 139.7 (after the 3-line line) | yes (derived) |
| "Send it again" | 16, 292, 358 × 48; 24. §7 moves it to 431, or 455 with a 2-line helper | 16, 453.3, 379.4 × 48 | 24 below the field, 48 tall | yes |
| PrimaryAction "Check again", Idle | centred, 700, 176 × 48 (144 from the bottom) | centred, 144 from the bottom, 176 × 48 | centred, 144 from the bottom, 176 × 48 | yes |
| Secondary "Use a different address" | 16, 772, 358 × 48; **24** | 16, 773.3, h 48; 24 | 16, 768, h 48; 24 | yes |

## Sign in · IDLE (Figma 344:18)

**Deliberate deviation: no header.** Sign in is the app's root while the promise screen it
backs onto in Figma is not built. So it has no back target, and the eyebrow takes the
header's place at content y 24, as the unverified gate (345:46) does. Every element below the
header is therefore **64 higher** than in Figma (48 header + 16 gap). The "Figma − 64" column
is the comparison.

| Element | Figma (x, y, w, h; gap) | Figma − 64 | Android dp (content) | iOS pt (content) | Match? |
|---|---|---|---|---|---|
| Back target | 16, 24, 48 × 48 | none | none | none | **deviation** (root) |
| Eyebrow "ACCOUNT" | 16, 88, h 19 | 24 | 16, 24.0, h 18.3 | 16, 24, h 18.3 | yes |
| Heading "Sign in" | 16, 171, h 33; 64 | 107 | 16, 106.3, h 32.8 | 16, 106.3, h 32.7 | yes |
| Field · email (helper hidden) | 16, 228, 358 × 83; 24 | 164 | 16, 163.0, h 82.3 | 16, 163, h 83.7 | yes |
| Field · password (helper hidden) | 16, 335, 358 × 83; 24 | 271 | 16, 269.3, h 82.3 | 16, 270.7, h 83.7 | yes |
| "I forgot my password" | 16, 442, 358 × 48; 24, text centred vertically (D8) | 378 | 16, 375.6, 379.4 × 48 | 16, 378.3, 370 × 48 | yes |
| PrimaryAction "Sign in", Idle | centred, 700, 176 × 48 (144 from the bottom) | n/a (pinned) | centred, 144 from the bottom, 176 × 48 | centred, 144 from the bottom, 176 × 48 | yes |
| Secondary "Create an account" | 16, 772, 358 × 48; 24 | n/a | 16, 773.3, h 48; 24 | 16, 768, h 48; 24 | yes |
| InlineError (no frame; §3.3 derived) | first child of the action block, 24 above the button | n/a | 24 above the button | 24 above the button (624 + 48 = 672; button 696) | yes (derived) |

## States drawn in Figma and checked on the app

- **Sign up, no connection (394:240).** The InlineError "That didn't go through. Your details
  are still here." is centred, in `danger`, 16 above the action. The action reads "Try again"
  and is **Idle**, not the Disabled that Figma draws (D4, and states.md §1c). Every input is
  kept. Screenshot `android-13-register-offline.png`.
- **Sign up, keyboard up (362:1105).** The fields scroll under the header and the action block
  rides above the keyboard. Screenshot `android-02-register-keyboard-up.png`.

## Not checked

- Light mode. Every measurement here is dark; the geometry is mode-independent, but light
  colours were not looked at on device.
- The verify sub-states Resent (362:95) and Link expired (362:107) on device. They are covered
  by ViewModel tests and previews. The Figma text for both was never read (spec §7).
- 200 % font scale on device. The content scrolls by construction; the pinned action block on
  sign up holds four parts and will take much of a small screen at 200 %. That is worth
  walking before release.
