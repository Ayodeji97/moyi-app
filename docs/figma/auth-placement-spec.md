# Moyi auth placement spec (from Figma, read-only)

Copied into the repository in PR #5 so component geometry can be checked against it.
`shared/designsystem/.../theme/MoyiDimens.kt` cites this file section by section.
Rulings on the disagreements in §6, given 2026-10-27: **Figma wins for placement and geometry;
tokens.md wins for colour.** D6 is ruled as "the 24 is added inside the system insets".

File `PUkAbShea3t47FKpNBOf7i`, read 2026-10-27. Every coordinate is in pt, measured from the frame's top-left corner (390 × 844).
Dark is authoritative. Source: `get_metadata` and `get_design_context` on the frames, plus `get_variable_defs` on 340:9.
**Partial read.** The Figma Starter-plan MCP call limit cut the session off before every planned call ran. See §7 for what was not read.

Screenshots and raw page metadata were captured alongside this read and are not committed
(the repository is public and the Figma file is not). Re-read the frames by node ID below.

---

## 0. Tokens as bound in Figma (dark), checked against tokens.md

| Token (Figma var) | Dark value | tokens.md dark | Match |
|---|---|---|---|
| `color/surface` | #171313 | #171313 | yes |
| `color/surface-raised` | #211A1A | #211A1A | yes |
| `color/ink` | #F8EFE9 | #F8EFE9 | yes |
| `color/ink-muted` | #CDBBB5 | #CDBBB5 | yes |
| `color/outline` | #493737 | #493737 | yes |
| `color/outline-strong` | #7E6A6A | #7E6A6A | yes |
| `color/danger` | #D4857C | #D4857C | yes |
| `type/title` | Newsreader Regular 400, 26 / 32.5, tracking 0 | 26 / 1.25 | yes |
| `type/body` | Roboto Regular 400, 16 / 24, tracking 0 | 16 / 1.50 | yes |
| `type/label` | Roboto Medium 500, 13 / 18.2, tracking 1.43 px, UPPERCASE | 13 / 1.40, +0.11em | yes (Compose must use 0.11em) |
| `action` (style) | Newsreader Regular 400, 16 / 24 | §2 "action" | yes |
| `code` (style) | Newsreader 26 / 32.5, tracking 28 % (7.28 px) | §2 "code" | yes |
| spacing | 8, 16, 24, 32, 48 used | 4·8·16·24·32·48·64 | yes |
| `radius/default` | 12 | 12 | yes |

Text boxes are drawn rounded up: a label line (18.2) occupies a 19 pt box and a title line (32.5) a 33 pt box. Every height below uses the drawn box.

---

## 1. Frame inventory (Screens page 425:2)

Section **Onboarding** 425:3, section **Account** 425:313. Every frame is 390 × 844. The light twin sits 940 pt below its dark frame.

| Screen / state | Dark node | Light node | Notes |
|---|---|---|---|
| Onboarding 1 · the promise · OPENING | **358:45** | 362:1435 | Root screen with no back button. intro + EntryCard sample + PrimaryAction + secondary link |
| Onboarding 1 · promise · tablet portrait / landscape | 792:2495 / 792:2508 | — | 834×1194 / 1194×834, in the Tablet section |
| Onboarding 2 · sign up · IDLE | **340:9** | 362:1375 | |
| Onboarding 2 · sign up · KEYBOARD UP | 362:1105 | 362:1485 | |
| Onboarding 2 · sign up · invalid · VALIDATION | 394:193 | 394:1502 | Field Error variants placed in the form |
| Onboarding 2 · sign up · no connection · INPUT KEPT | 394:240 | 394:1523 | Inline error placed above the action |
| Onboarding 3 · verify email · WAITING | **341:17** | 362:1388 | "Check your email" |
| Onboarding 3 · verify email · resent · ACKNOWLEDGED | 362:95 | 362:1461 | |
| Onboarding 3 · verify email · link expired · RECOVERY | 362:107 | 362:1473 | |
| Onboarding 3 · verify email · checking · RETURNING | 394:1422 | 394:1545 | PrimaryAction Sending, per states.md §1c |
| Account 1 · sign in · IDLE | **344:18** | 362:1708 | |
| Account 1 · sign in · KEYBOARD UP | 362:1284 | 362:1764 | |
| Account 2 · forgot password · IDLE | 344:41 | 362:1720 | |
| Account 3 · reset link sent · NON-DISCLOSING | 345:23 | 362:1729 | |
| Account 4 · reset password · IDLE | 345:30 | 362:1738 | |
| Account 5 · unverified gate · BLOCKED | 345:46 | 362:1746 | Root screen with no back button. Eyebrow sits at y 24 |
| Account 6 · rate limited · PAUSED | 345:53 | 362:1755 | This is the sign-in error state that exists in Figma |
| Splash · COLD START | 379:5 | 379:8 | Section "Launch" 425:1854. Wordmark 113×48 at 138.5, 398 |
| Generic error · RECOVERABLE | 384:341 | 384:459 | Section "Failure" |

**No frame exists for the following.** These are not in any section:
- a sign-in "wrong credentials" inline error;
- a sign-in sending state;
- a sign-up sending state;
- a session-ended screen;
- a verify-email paste-token entry.

---

## 2. Shared screen skeleton (every Detail auth screen)

The root frame uses vertical auto-layout. Its fill is `surface`. Padding is **16 left and right, 24 top and bottom**. Item spacing is **16**.
Children:

1. a `header`;
2. an `eyebrow`;
3. `content` (FILL height, padding-top **48**, item spacing **24**);
4. `action` (hug height, centred).

- **Side margin: 16** on both sides, so the content width is **358**.
- **Safe area or status bar: not drawn.** The frames use the Android Scaffold values (24 top, 24 bottom) with no status bar or nav bar painted. Scaffold 114:90 sets iOS to 59 top and 34 bottom. Whether the 24 is applied on top of the window insets is never stated. See §6 D6.
- **Header:** 358×48 at (16, 24). It holds `back`, a 48×48 target at (16, 24), with Icon/chevron-left 24×24 centred inside it at (28, 36). The icon stroke is 2 px, bound to `ink`. The header carries no title.
- **Eyebrow:** at (16, 88), 358×19. Style `label`, colour `inkMuted`.
- **Eyebrow to heading gap: 64.** That is the frame gap of 16 plus the content padding-top of 48.
- **Heading:** at (16, 171), 358×33. Style `title`, colour `ink`.
- **The action block's bottom edge is always y 820**, which leaves 24 of bottom padding.

---

## 3. Per-screen element tables (dark, top to bottom)

Columns: x, y, w, h are absolute in the frame. "Gap above" is the distance from the previous element's bottom edge.

### 3.1 Sign up · IDLE — 340:9

| # | Element (node) | x | y | w | h | Gap above | Style token | Colour token | Notes |
|---|---|---|---|---|---|---|---|---|---|
| 1 | header / back (367:17/367:18) | 16 | 24 | 48 | 48 | 24 (top pad) | — | icon `ink` | Back target 48. The chevron glyph sits at (28, 36), 24×24 |
| 2 | eyebrow "NEW ACCOUNT" (340:10) | 16 | 88 | 358 | 19 | 16 | label | inkMuted | uppercase, 1.43 px tracking |
| 3 | heading "Create your account" (340:12) | 16 | 171 | 358 | 33 | 64 | title | ink | Content pad-top 48 + frame gap 16 |
| 4 | Field · email (340:13), Type=Text Idle | 16 | 228 | 358 | 115 | 24 | — | — | Label at 228 (h19). The box at 255 is 56 tall, ending at 311. The helper at 319 (h24) ends at 343 |
| 4a | · label "EMAIL" | 16 | 228 | 358 | 19 | — | label | inkMuted | |
| 4b | · box | 16 | 255 | 358 | 56 | 8 | — | fill surfaceRaised, stroke outlineStrong 1 px | radius 12, padding 16, gap 8. Value set in body / ink |
| 4c | · helper "We will send a verification link." | 16 | 319 | 358 | 24 | 8 | body | inkMuted | |
| 5 | Field · password (340:19), Type=Secure Idle | 16 | 367 | 358 | 115 | 24 | — | — | Label 367, box 394–450, helper 458–482 |
| 5a | · eye reveal target | 309 | 395 | 48 | 54 | — | — | icon `ink` | 48 wide, flush with the inner right padding. The glyph is 24×24 at (321, 410). The tap height must be the box's full 56 (build instruction) |
| 5b | · helper "8 characters or more. Anything you like." | 16 | 458 | 358 | 24 | 8 | body | inkMuted | |
| — | flexible space | — | 482 | — | 82 | — | — | — | content ends at 564 |
| 6 | consent row (340:29) | 16 | 580 | 358 | 48 | 16 (98 from the helper) | — | — | Horizontal row, gap 16, top-aligned. The whole row toggles |
| 6a | · checkbox (340:30) | 16 | 580 | 24 | 24 | — | — | stroke outlineStrong 1 px, no fill | **radius 0** (a square). The checked state is not drawn |
| 6b | · consent text "I am 18 or over, and I agree to the terms below." | 56 | 580 | 318 | 48 | — | body | inkMuted | 2 lines |
| 7 | legal links (389:205) | 16 | 644 | 358 | 48 | 16 | — | — | Horizontal row, gap 16 |
| 7a | · "Terms" target / text | 16 / 17.5 | 644 / 656 | 48 / 45 | 48 / 24 | — | body 16/24 (**literal, not bound**) | ink, underline | own target, does not toggle consent |
| 7b | · "Privacy Policy" target / text | 80 | 644 / 656 | 99 | 48 / 24 | — | body (literal) | ink, underline | |
| 8 | PrimaryAction "Create account" (340:33) | **107** | **708** | **176** | **48** | 16 | action | **State=Disabled**: fill surface, stroke outline 1 px, label inkMuted | Centred: (358 − 176) / 2 = 91 inside the column |
| 9 | secondary target (359:3) | 16 | 772 | 358 | 48 | 16 | — | — | |
| 9a | · "I ALREADY HAVE AN ACCOUNT" | 86 | 786.5 | 218 | 19 | — | label metrics 13/18.2/1.43 (**literal**) | ink, underline | centred in the target |
| — | bottom padding | — | 820 | — | 24 | — | — | — | |

The `action` column spacing on this screen is **16**. On sign in and verify email it is 24.

**Validation variant (394:193).** The password field switches to Type=Secure State=Error and grows to 139: the box gets a 2 px `danger` stroke and the helper wraps to 2 lines in `danger` ("That password has appeared in a data breach. Please choose another."). The email field stays Idle in the drawn frame. The element tops do not move, because the slack absorbs the extra 24.

**No-connection variant (394:240).** The content block shrinks to 401 and the action block starts at **540** with height 280.
- Consent row: 540.
- Legal links: 604.
- **Inline error** (394:276): (16, 668), 358×24. Text "That didn't go through. Your details are still here." Body style, colour `danger`, **centred**.
- PrimaryAction "Try again" stays at **(107, 708)**. Gap from the error to the action: **16**.
- Secondary link: 772.

The error is placed **above the action**, and the blocks above it shift up 40 so the action does not move. The button in that frame is still drawn as State=Disabled (see D4).

**Keyboard up (362:1105).**
- Header spans the full width, 0–72.
- Content scrolls in 72–280 and the heading has scrolled off. The email field is at y 96.
- Action spans 280–544 with 8 of top padding: consent 288, links 352, PrimaryAction 416, secondary 480.
- The keyboard simulation is 544–844, 300 tall.

### 3.2 Check your email (verify email · WAITING) — 341:17

| # | Element (node) | x | y | w | h | Gap above | Style | Colour | Notes |
|---|---|---|---|---|---|---|---|---|---|
| 1 | header / back (367:23) | 16 | 24 | 48 | 48 | 24 | — | icon ink | |
| 2 | eyebrow "NEW ACCOUNT" (341:18) | 16 | 88 | 358 | 19 | 16 | label | inkMuted | |
| 3 | intro (350:16) | 16 | 171 | 358 | 97 | 64 | — | — | Vertical, gap **16** (one block) |
| 3a | · heading "Check your email" (341:20) | 16 | 171 | 358 | 33 | — | title | ink | |
| 3b | · line "We sent a link to amara@example.com. Open it to finish setting up." (341:21) | 16 | 220 | 358 | 48 | 16 | body | inkMuted | 2 lines. The email address is interpolated |
| 4 | resend target (359:4) | 16 | 292 | 358 | 48 | 24 | — | — | left-aligned row |
| 4a | · "SEND IT AGAIN" (341:23) | 16 | 306.5 | 107 | 19 | — | label metrics (literal) | ink, underline | |
| — | flexible space | — | 340 | — | 344 | — | — | — | content ends at 684 |
| 5 | PrimaryAction "Check again" (362:91) | **107** | **700** | 176 | 48 | 16 | action | **State=Idle**: fill ink, label surface | |
| 6 | secondary target (362:94) | 16 | 772 | 358 | 48 | **24** | — | — | |
| 6a | · "USE A DIFFERENT ADDRESS" (362:93) | 96.5 | 786.5 | 197 | 19 | — | label metrics (literal) | ink, underline | centred |

**Resent (362:95).** The resend target is replaced by a text node "resend acknowledgement" (362:102) at (16, 292), 358×24, a single body line. The text and colour were not read (see §7). states.md gives the copy "Sent again. You can ask for another in a minute." in a muted style. Everything else is identical.

**Link expired (362:107).** The intro is the same size. The resend target 362:113 is **hidden**. The PrimaryAction and secondary link keep their positions. The copy was not read. states.md gives "That link has expired." / "Links last 24 hours and work once. We can send you a new one."

**Checking (394:1422).** The geometry matches WAITING. The PrimaryAction is expected to be State=Sending per states.md §1c (not verified in Figma).

### 3.3 Sign in · IDLE — 344:18

| # | Element (node) | x | y | w | h | Gap above | Style | Colour | Notes |
|---|---|---|---|---|---|---|---|---|---|
| 1 | header / back (367:256) | 16 | 24 | 48 | 48 | 24 | — | icon ink | |
| 2 | eyebrow "ACCOUNT" (344:19) | 16 | 88 | 358 | 19 | 16 | label | inkMuted | |
| 3 | heading "Sign in" (344:21) | 16 | 171 | 358 | 33 | 64 | title | ink | |
| 4 | Field · email (344:22) Text Idle, **helper hidden** | 16 | 228 | 358 | **83** | 24 | — | — | label 228–247, box 255–311 |
| 5 | Field · password (344:28) Secure Idle, helper hidden | 16 | 335 | 358 | 83 | **24** | — | — | label 335–354, box 362–418. Eye glyph at (321, 378) |
| 6 | forgot target (359:23) | 16 | 442 | 358 | 48 | 24 | — | — | full-width row, left-aligned |
| 6a | · "I FORGOT MY PASSWORD" (344:36) | 16 | 442 | 358 | 48 | — | label metrics (literal) | ink, underline | **The text box fills the 48 target and is top-aligned**, so the glyphs paint at about 442–460, not centred. See D8 |
| — | flexible space | — | 490 | — | 194 | — | — | — | content ends at 684 |
| 7 | PrimaryAction "Sign in" (344:38) | **107** | **700** | 176 | 48 | 16 | action | State=Idle: fill ink, label surface | |
| 8 | secondary target (359:25) | 16 | 772 | 358 | 48 | 24 | — | — | |
| 8a | · "CREATE AN ACCOUNT" (344:40) | 117.5 | 786.5 | 155 | 19 | — | label metrics (literal) | ink, underline | centred |

**How the two links are placed.**
- **"I forgot my password"** sits inside the **content** block, directly under the password field, 24 below it. It is a full-width 48 target with left-aligned text, in the label metrics, `ink`, underlined. It stays with the form, so on keyboard-up it scrolls with the fields.
- **"Create an account"** sits inside the **action** block, 24 below the PrimaryAction. It is a full-width 48 target with centred text, pinned to the bottom with the button.

**Inline error relative to the action.** No sign-in error frame exists. The only drawn pattern is sign up's no-connection state: a **centred body-style line in `danger`, one action-gap above the PrimaryAction, inside the action block**, with the button keeping its position. For sign in, apply it like this:
- The error is the **first child of the action block**.
- It sits **24 above the button**, using sign in's action gap.
- The button stays at y 700, so the error sits at y 652–676.
- Equally valid: keep the action block bottom-anchored and let the error push nothing. The button must not move.

Field-level validation (a malformed email) uses the Field Error variant: a 2 px danger stroke and a danger helper, with the helper un-hidden.

**Rate limited (345:53), the only sign-in error frame.**
- Heading "Try again in 15 minutes" and the line "Sign in is paused for a short while. Your account is fine." sit in the intro block at 171 and 220.
- **There is no PrimaryAction.**
- The action block (16, 772, 358×48) holds a single secondary link, "I FORGOT MY PASSWORD", centred.

**Keyboard up (362:1284).**
- Header 0–72.
- Content 72–400: email 96, password 203, forgot 310.
- Action 400–544: PrimaryAction at (107, 408), secondary at 480.
- Keyboard 544–844.

### 3.4 Other auth frames (listed, not measured element by element)

- **Forgot password (344:41):**
  - intro (heading plus a 2-line body) at 171.
  - Field · email (83, helper hidden) at y 292.
  - action (16, 772): PrimaryAction only.
- **Reset link sent (345:23):** intro at 171. The action holds one secondary link (123 wide), centred at y 772.
- **Reset password (345:30):**
  - heading at 171.
  - Field · new password (115) at 228.
  - "consequence" body text, 2 lines, at 367.
  - PrimaryAction at y 772.
- **Unverified gate (345:46):**
  - No header (Root).
  - eyebrow at y 24, content starting at 59, intro at 107 (heading plus a 3-line body, 72).
  - The action holds one centred secondary link (107 wide) at y 772. No PrimaryAction.
- **Promise (358:45):**
  - No header. Content at 24, padding-top 48, so the intro starts at **72**.
  - Heading is 2 lines (65 tall). The body line is at 153.
  - EntryCard sample 358×144 at y 249.
  - PrimaryAction at (107, 700). Secondary at 772.

On the bottom-pinned screens with one control, the action block is 48 tall at y 772. On screens with a button plus a link it is 120 tall at y 700.

---

## 4. Components

### 4.1 PrimaryAction — set 99:34 (page 92:2)

Auto-layout horizontal, centred.
- Height **48** (fixed).
- **min-width 176**. The button hugs its label; 176 is a floor and never a fixed width.
- Padding left and right **32**. No vertical padding: the label is centred in the 48.
- Radius **12**. Label style `action`: Newsreader 16 / 24, centred, single line.

| State (node) | W × H | Fill | Stroke | Label colour | Other |
|---|---|---|---|---|---|
| Idle (99:26) | ≥176 × 48 | ink | none | surface | |
| Sending (99:28) | ≥176 × 48 | ink | none | surface | **opacity 60 %** on the whole button. No spinner. Canonical label "Sending" |
| Queued (99:30) | ≥176 × 48 | surfaceRaised | outlineStrong 1 px | ink | label "Queued" |
| Disabled (99:32) | ≥176 × 48 | surface | outline 1 px | inkMuted | |

- Placement on screens: horizontally centred, x = 107 at 390 wide. The touch target is the button itself, 48 tall.
- **Screenshot note:** the component-set render (PrimaryAction-set.png) shows Queued as near-black and the Disabled label as nearly invisible. The same Disabled variant renders correctly on screen 340:9. This looks like a mode or frame-fill artefact of the set's frame. The code values above are authoritative.

### 4.2 Field — set 296:10 (page 294:2)

20 variants: Type {Text, Secure, Select, Code, Multiline} × State {Idle, Focus, Error, Disabled}. Width 350 in the set; screens stretch it to 358.

The structure is vertical with **gap 8**: label (`label`, inkMuted, 19 tall) → box → helper (`body`, 24 per line).
- **The label sits above the box with an 8 gap. The helper sits below the box with an 8 gap.**
- Box: horizontal auto-layout, padding **16** all round, item gap 8, radius **12**, items centred (Multiline is top-aligned).
- Value text: `body`, ink. In Disabled it is inkMuted. Code uses the `code` style.

| Type | Box height | Total height (1-line helper) | Trailing element |
|---|---|---|---|
| Text | 56 | 115 (83 with the helper hidden) | — |
| Secure | 56 | 115. Error is **139** because its helper wraps to 2 lines | `reveal target` 48 wide × the inner height. Icon/eye 24 is centred, so the glyph's right edge is 28 inside the box's outer edge. The tappable height is the full 56 (build instruction). Revealed state: Lucide eye-off, not drawn |
| Select | 56 | 139 (helpers are 2 lines) | Icon/chevron-down 24 |
| Code | 65 | 148 (2-line helper) | — |
| Multiline | 140 | 223. Error is 199 | — |

| State | Box fill | Box stroke | Stroke weight | Helper colour |
|---|---|---|---|---|
| Idle | surfaceRaised | outlineStrong | **1** | inkMuted |
| Focus | surfaceRaised | **ink** | **2** | inkMuted |
| Error | surfaceRaised | **danger** | **2** | **danger** |
| Disabled | **surface** | **outlineStrong** (bound; the description says `outline`, see D1) | 1 | inkMuted, with the value in inkMuted |

The Field-set screenshot confirms these, except the set frame (814 tall) clips the Multiline row, which runs to 1020.
- Icon touch target is 48.
- Minimum box height is 56, above the 48 dp and 44 pt minimums.

Proposed copy in the variants:
- Email error: "That does not look like an email address yet."
- Disabled helper: "Verify your email to continue."

### 4.3 Scaffold — set 114:90 (page 114:2)

This is a spec only, with zero instances by design: screens conform to it but never instance it.
- 390 × 844, fill surface, padding left and right 16, item spacing 16.
- **Android:** padding top and bottom 24. **iOS:** top 59, bottom 34.
- Root: the header holds the title only (`title`).
- Detail: the header is a row with gap 8: back 48×48 with chevron 24, then the title (`title`, FILL).
- Content slot: FILL.

The auth screens follow Detail **without the header title**. They put an eyebrow below the header and the title inside the content (D5).

---

## 5. Key measurements

| Measure | Value |
|---|---|
| Screen side margin | 16 / 16, content width 358 |
| Top and bottom padding | 24 / 24 (Android). iOS per Scaffold: 59 / 34 |
| Header | 48 tall at y 24. Back target 48×48, chevron 24 |
| Eyebrow | y 88, label style, inkMuted |
| Heading | y **171**, `title` Newsreader 26/32.5, ink, **64 below the eyebrow** |
| Heading to first field | 24 |
| Field box height | **56** (Code 65, Multiline 140) |
| Label to box gap | **8** (label 19 tall). Box to helper: 8 |
| Field to field gap | **24** (bottom of the helper, or of the box if the helper is hidden, to the next label) |
| PrimaryAction | **176 × 48 min**, radius 12, x 107 (centred), **y 700** when a link follows (sign in, verify) and **y 708** on sign up |
| Action to secondary link | 24 (sign in, verify), **16** (sign up) |
| Secondary link | full-width 48 target, label metrics, ink, underlined, text centred |
| Inline form error | centred body in danger, one action-gap above the PrimaryAction (16 on sign up) |

---

## 6. Disagreements (Figma vs the written system, and Figma vs itself)

- **D1 — Field Disabled stroke.** The Field component description and tokens.md (§4 / §7b) both say Disabled uses `outline`. **All five Disabled variants bind `outlineStrong`.** Pick one before the code is verified.
- **D2 — Unbound text styles.** The secondary links ("I already have an account", "Send it again", "I forgot my password", "Create an account", "Use a different address") use literal 13 / 18.2 / 1.43 / Medium / uppercase. The legal links use literal 16 / 24. tokens.md §3 and §4 claim every node binds and there is no unstyled text. The colours are bound (`ink`) and the metrics match `label` and `body`, so build them with the `label` and `body` tokens.
- **D3 — Rhythm.** tokens.md §3 says "24 between blocks, 16 inside a block". Sign up's action column uses **16** between the consent, legal links, button and secondary link. Sign in and verify use **24** between the button and the link. The checkbox is radius 0, while tokens.md allows 12 or full only.
- **D4 — Sign up's button state.**
  - Sign up IDLE draws PrimaryAction as **State=Disabled**, which implies it is disabled until consent is checked. No written rule says so.
  - The no-connection frame also draws "Try again" as Disabled. That contradicts states.md §1c, under which the button becomes Try again after a submit, and a submit requires consent to have been given. It should be Idle.
- **D5 — Scaffold vs the screens.** The Scaffold Detail header puts the title next to the back target (gap 8). The auth screens put no title in the header, an eyebrow below it, and the title 48 into the content.
- **D6 — Safe area.** The screens are drawn with Android 24/24 and no status or navigation bar. The Scaffold doc says Android is "edge-to-edge with window insets", and tokens.md calls 24/24 the inset itself. **Unresolved: is the 24 replaced by the system insets, or added to them?** Ruled 2026-10-27: added inside them; see §8 for the keyboard-up exception.
- **D7 — Sign-in error coverage.** states.md §1c specifies a single sign-in error ("That email and password do not match. Check both and try again.") and a session-revoked explanation. Figma draws **neither**. Only rate limited (345:53) exists, and its heading hard-codes "15 minutes", while states.md says the copy comes from `Retry-After` or the server `detail` (for example "3 minutes").
- **D8 — Forgot-link alignment.** On sign in, the forgot text node fills its 48 target and is top-aligned. Every other secondary link is centred (offset 14.5). Build it centred and treat the Figma pixel offset as a drawing artefact.
- **D9 — Field description.** It says "four types" while the set has five (Multiline). tokens.md §4 is correct (five).
- **D10 — Field set frame.** It is 814 tall, but the variants run to 1020, so Multiline is clipped in renders.
- **D11 — Label tracking.** Figma uses 1.43 px, by design (tokens.md §2). Compose must use **0.11em**. Do not copy the px value.

---

## 7. Elements the app needs that Figma lacks

| Need | Status in Figma | Placement to build (derived from the system) |
|---|---|---|
| **Sign-up name field** (API requires `displayName`) | Absent. Sign up has email and password only | Field Type=Text, label "NAME" (proposal), placed first after the heading at y 228. That pushes email to 367 and password to 506 and adds 139. Content then needs about 703 against the 441 available, so **the content must scroll** (as the keyboard-up frame already does) and the action block stays bottom-pinned. Product and copy ruling owed |
| **Verify-email paste-token field** | Absent. Verify assumes a link tap | Field Type=Text (not Code: the token is not 6 characters) between the intro and "Send it again", 24 below the line (y 292). "Send it again" moves to 292 + 115 + 24 = 431. The "Check again" PrimaryAction should submit the token when the field is filled. Ruling owed |
| **Session-ended screen** (`TOKEN_REUSE_DETECTED`) | Absent. Recorded in states.md §1c as owed before Phase 6 | Same shape as rate limited and the unverified gate: eyebrow "ACCOUNT", heading plus one line (API fallback copy: "This sign-in was used from two places at once, so it has been ended. Sign in again to continue."), PrimaryAction "Sign in" at y 772. No back button (Root) |
| **Loading or sending states** | Only the PrimaryAction Sending variant (60 % opacity) and verify "Checking" exist. Sign up and sign in sending are not drawn | Swap the PrimaryAction to Sending and keep the label wording to the action (for example "Signing in"). No spinner, no skeleton (states.md §1c, tokens.md §7). Fields stay editable or are disabled; this is not specified |
| Sign-in inline credential error | Absent | See §3.3: centred danger body, first child of the action block, 24 above the button |
| Checkbox checked state | Absent | — |
| Eye-off (password revealed) | Not drawn by design (Lucide import) | Same 48 target |
| Focus state on screens | Only in the Field set | 2 px ink stroke, no change in layout |

### Not read (call limit reached)

The Figma MCP Starter-plan limit was hit after 15 calls. The following were not read:
- **Text and colour of the verify sub-states**, Resent (362:102) and Link expired (heading, line, button label). The geometry came from metadata.
- **Text of forgot password, reset link sent, reset password and the unverified gate.** Only their geometry was read.
- **Screenshots of the error and sub-state frames.**
- `get_variable_defs` on the Field and PrimaryAction sets. The colour bindings came from `get_design_context` variable references instead.
- **Light-mode values.** These are taken from tokens.md and were not re-read from Figma.
- **Pages StateBlock 72:2 and the Foundations pages 8:3, 8:4 and 8:5.** They were not opened. Their values were cross-checked through the bound variables only.

---

## 8. As built

Where the code departs from a literal reading of the sections above, and why.

- **Scaffold bottom padding with the keyboard up.** At rest the action block sits 24 inside
  `WindowInsets.safeDrawing` (D6, as ruled). While the soft keyboard is visible
  (`WindowInsets.ime` bottom > 0) the bottom padding is **0**, so the action block sits flush on
  the keyboard, as the keyboard-up frame draws it (362:1105: action ends at 544, keyboard starts
  at 544). `safeDrawing` already contains the IME inset, so nothing else changes.
