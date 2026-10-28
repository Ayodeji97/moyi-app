# Missing components and open design questions

This is input for the owner's brainstorm. Each item is something the client needs, or will need
soon, that the design system (`../.claude/skills/moyi-design-system/`: `tokens.md`, `states.md`,
`copy.md`) does not yet specify. Each says what the design system says **today** and what this
repository does until the owner decides.

The rule from `CLAUDE.md`: a component the design system lacks is raised here, not improvised.
Where a stand-in had to be built to make sign-up possible, it is the smallest version that stays
inside the tokens, and it is named below.

Recorded in PR #5 (design system), 2026-10-27.

## Loading and waiting

### 1. Loading skeleton

- **Today:** refused. `SKILL.md` §5 says "no skeleton shimmer as decoration", and `states.md` §1c
  says "`states.md` §3 and `tokens.md` §7 both refuse spinners and decorative skeletons; this
  product's answer to waiting is a control that says what it is doing". There is no skeleton
  frame in Figma.
- **Here:** none. Waiting is `PrimaryAction` in `Sending`, with a label the screen supplies.
- **The open question:** Today renders from cache instantly (`SKILL.md` §11), so a skeleton is
  needed only on a cold start with no cache, such as the first launch after sign-in. Decide what
  that first frame shows. "Nothing but `surface`" is a valid answer.

### 2. Loading overlay

- **Today:** not in the design system. The owner asked for one; the ruling for this build was
  "the design system wins; no spinner is invented".
- **Here:** none. `PrimaryAction` `Sending` disables the control, swaps the label and announces
  the change politely to screen readers.
- **The open question:** whether any action is long enough, or blocking enough, to need more than
  the label. If one is, it needs a state in `states.md` before it gets a component.

### 3. `PrimaryAction` `Sending` fails contrast in light only

- **Today:** Figma draws Sending as the whole button at 60 % opacity
  (`docs/figma/auth-placement-spec.md` §4.1). `tokens.md` gives no Sending look.
- **Measured:** the opacity applies to the button as one flattened layer, so the label (`surface`)
  and the fill (`ink`) are each blended at 60 % over the page (`surface`). The label stays
  `surface`; the fill lightens or darkens towards it. **Light: 4.19:1, below 4.5.** Dark: 6.41:1,
  which passes (a device pixel check read #171313 on #9E9793). WCAG 1.4.3 exempts inactive
  controls, but in this product the Sending label is the only feedback that something is
  happening (`states.md` §1c), so it should be readable in both themes.
- **Here:** built as Figma draws it. `TokenContrastTest.sendingLabelContrastIsPinned` pins both
  numbers, so a fix or a regression shows up. Options for the owner, for light: a higher opacity,
  or keep full opacity and let the label change carry the state.

### 3a. `PrimaryAction` `QueuedOffline`

- Figma §4.1 draws it as `surfaceRaised` with a 1 px `outlineStrong` stroke and an `ink` label
  ("Queued"). Built that way; no network logic.

## Account and sign-in

### 4. Session-ended screen (`TOKEN_REUSE_DETECTED`)

- **Today:** a recorded gap. `states.md` §1c: "the *session revoked* state `13` names ... has no
  frame and no copy". The API's fallback sentence is "This sign-in was used from two places at
  once, so it has been ended. Sign in again to continue." Only that sign-in is ended.
- **Here:** PR #6 routes to sign-in with a generic message, flagged as owed. No frame, no
  approved copy.

### 5. Sign-up name field

- **Today:** not in Figma and not in `states.md` §1 (Sign up lists email, password, 18+
  confirmation and consent). The owner asked for it in this session. `SKILL.md` §10: one
  `displayName`, no first/last split.
- **Here:** PR #6 uses `Field` `Text`. Its label and helper copy need approving.

### 6. Verify-email paste-token field

- **Today:** not in Figma. `states.md` §1 screen 3 has instruction, resend, "Check again" and
  "Use a different address", with no input. Deep links are meant to make the token invisible.
- **Here:** PR #6 adds a `Field` `Text` for pasting the token from the local backend log, as a
  development stand-in until deep links. Decide whether it ships or is debug-only.

### 7. 18+ and Terms consent control

- **Today:** `states.md` §1 puts "18+ confirmation, Terms and Privacy consent (FR-011)" on Sign up,
  and `strings-proposed.md` has `signup.consent`. Neither `tokens.md` nor `states.md` says how it
  looks, and a checkbox is **not one of the ten components**.
- **Figma** draws it as a loose frame on Sign up (§3.1 row 6): a 24 x 24 square, radius 0, 1 px
  `outlineStrong`, no fill, top-aligned, 16 from `body` `inkMuted` text; Terms and Privacy Policy
  are separate underlined links on their own row. `ConsentCheckbox` follows that.
- **Still open:** the **checked** state is not drawn (built as an `ink` fill with the Lucide check
  in `surface`); radius 0 is outside `tokens.md`'s 12-or-full (D3); the consent copy differs
  (Figma "…agree to the terms below." vs `signup.consent` "…agree to the Terms and the Privacy
  Policy."); and it is still not one of the ten components.

### 8. The verification and reset emails

- **Today:** a recorded gap (`states.md` §1c): the backend's plain-text copy is the design.
- **Here:** nothing; the client never renders them. Listed so the brainstorm covers them.

## Platform

### 9. Android splash and app icon

- **Today:** `states.md` §0: "the app icon does not exist", and the Android splash is `surface`
  plus the icon. It is the blocking brand asset for both stores.
- **Here:** the wizard's default green Android icon and a light system splash, seen during the
  PR #5 screenshots. It shows a white splash before a dark app.

## Conflicts found inside the design system

### 10. Side gutter: 16, 20 or 24

- `tokens.md` §2 measures the entry at "screen padding 20×2" (302 pt measure); §3's scale has no
  20; the Figma auth screens use **16** (content 358 on 390).
- **Here:** 16, per Figma (`MoyiDimens.Scaffold.Gutter`). `tokens.md` §2's measure figure is now
  out of date in a third direction and should be re-measured for the entry screens.

### 11. Figma versus tokens.md, as built

Ruling: Figma wins for placement and geometry, tokens.md for colour.

- **D1, Field Disabled stroke:** Figma binds `outlineStrong`; tokens.md and the component
  description say `outline`. Built `outline` (colour, so tokens.md).
- **D3, rhythm:** sign up's action block uses 16 between its parts, sign in and verify use 24.
  `MoyiScaffold` leaves the gaps inside `action` to the screen.
- **D6, safe area:** Figma draws 24/24 with no system bars. Built as 24 inside
  `WindowInsets.safeDrawing`, so on an iPhone the top is 59 + 24 and the bottom 34 + 24.
- **D8, forgot link:** built start-aligned and vertically centred in its 48 target, not
  top-aligned.
- **D11, label tracking:** built as 0.11em, not 1.43 px.
- **Label weight:** Figma's `type/label` is Medium (500); tokens.md is silent on weight. Built 500.

### 12. Newsreader's optical size

- `tokens.md` §2 chose the **variable** Newsreader for its `opsz` axis (6 to 72), so one file
  gives correct optical compensation at 16 and at 34.
- **Here:** the static 16pt instances (Regular, Medium), because google/fonts ships only the
  variable file and Compose's variable-font support on iOS was not something to bet the first
  build on. `entry` (34) and `title` (26) are therefore drawn with text-size optics. Worth
  revisiting with the variable font and `FontVariation.Settings` once it can be checked on iOS.

## Components the design system has and this PR does not build

`EntryCard`, `ComposeField`, `DayCell`, `StateBlock`, `InviteCode`, `Chip`, `FavouriteToggle`,
and `Field`'s `Select`, `Code` and `Multiline` types. None is needed for the auth flow. They are
built when a screen needs them, and they are not gaps in the design system.
