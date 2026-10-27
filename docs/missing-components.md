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

### 3. `PrimaryAction` `QueuedOffline`: what it looks like

- **Today:** `tokens.md` §4 names the state (idle / sending / queued-offline / disabled) and says
  nothing about its look. `states.md` §4 says the queued entry is drawn on the Failure page as a
  Today state.
- **Here:** built as a state that looks like `Sending` (the `ink` fill, a label from the caller,
  not tappable). No network logic. Confirm or redraw.

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
- **Here:** a stand-in `ConsentCheckbox`: the whole row toggles with `Role.Checkbox`, at least
  48 tall; a 20 dp square with a 2 dp `outlineStrong` border, filled `ink` with the Lucide check in
  `surface` when ticked. It is square-cornered because the system has only radius 12 and full,
  and 12 on a 20 dp box reads as a radio button.
- **Still open:** the "Terms" and "Privacy Policy" words are not links yet. `tokens.md` §1 says how
  an inline link looks (`ink` plus underline inside an `inkMuted` sentence); it needs the URLs and
  a decision on whether one sentence holds two links or two controls.

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

### 10. Side gutter: 20 or 24

- `tokens.md` §2 measures the entry at "screen padding 20×2", which gives the 302 pt measure.
  `tokens.md` §3 says the scale is `4 · 8 · 16 · 24 · 32 · 48 · 64`, and its audit removed every
  20 it found. Both cannot hold.
- **Here:** 24, on the scale (`MoyiDimens.Scaffold.Gutter`, one line to change). At 24 the
  entry measure is 4 pt narrower per side than the measure the type scale was chosen against.

### 11. Field stroke weights and paddings have no stated numbers

- `tokens.md` §4 requires Focus and Error to change stroke **weight** and gives no values, and
  gives no internal paddings or label gap for `Field` or `PrimaryAction`.
- **Here:** Idle 1, Focus and Error 2, Disabled 1 (`outline`); label gap 8; helper gap 8; box
  padding 16. All in `MoyiDimens`, each marked "interpreted". The Figma comparison pass should
  confirm or correct them.

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
