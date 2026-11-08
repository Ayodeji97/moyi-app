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
- **Here (session-ended PR, 2026-11-05):** built as its own root screen, `SessionEndedScreen`,
  for `ReuseDetected` only. Shape from auth-placement-spec §7: eyebrow "Account", heading, one
  muted line 16 under it, PrimaryAction "Sign in" pinned at the bottom, no back. "Sign in"
  replaces the stack with sign in. Copy approved by the owner 2026-11-05: heading "This sign-in
  has ended", line "It was used from two places at once, so we ended it to keep your entries
  safe. Anything else you are signed in on is unaffected. We have emailed you about it."
- **`Invalid` is not this screen.** `REFRESH_TOKEN_INVALID` (expired, revoked by a password reset,
  or not kept) has no "two places" story, so it keeps PR #6's neutral line on sign in: "You have
  been signed out. Sign in again to continue." (`login_session_ended`, proposed). A sign-out the
  person asked for shows nothing. The mapping is `routeAfterSessionEnd`.
- **Owed upstream:** `states.md` §1c still calls this a gap, and doc 13 says "routes to sign-in
  with an explanation". Both live outside this repository; Figma has no frame.

### 5. Sign-up name field

- **Today:** not in Figma and not in `states.md` §1 (Sign up lists email, password, 18+
  confirmation and consent). The owner asked for it in this session. `SKILL.md` §10: one
  `displayName`, no first/last split.
- **Here:** PR #6 uses `Field` `Text`, first in the form. Proposed copy: label "Your name",
  helper "What your person will see.", errors "Add your name to continue." and "Keep it to 80
  characters or fewer." The form scrolls; the action block stays pinned.

### 6. Verify-email paste-token field

- **Today:** not in Figma. `states.md` §1 screen 3 has instruction, resend, "Check again" and
  "Use a different address", with no input. Deep links are meant to make the token invisible.
- **Here:** PR #6 adds a `Field` `Text` for pasting the token from the local backend log, as a
  development stand-in until deep links. The whole link may be pasted; the app takes the
  value after `token=`. "Check again" submits it. Proposed copy: label "Code from the email",
  helper "Paste the code from the link we sent, or the whole link.", errors "Paste the code
  from the email first." and "That code does not match. Copy it again from the email."
  In the expired state the field is hidden. Decide whether it ships or is debug-only.

### 6a. Reset-password paste-code field

- **Today:** not in Figma. Choose a new password (345:30) draws only the new password, the
  consequence line and "Save and sign in"; the token is meant to arrive by deep link from the
  reset email, which this app does not handle yet.
- **Here:** the password-reset PR adds the same stand-in as §6: a `Field` `Text` first in the
  form, with verify's label, helper and errors ("Code from the email", "Paste the code from the
  link we sent, or the whole link.", "Paste the code from the email first.", "That code does not
  match. Copy it again from the email."). The reset link carries the token as `?token=` too
  (backend `VerificationEmailComposer.linkFor`, read only), so `extractVerificationToken` is
  reused unchanged. An expired or used link shows a new proposed error under the field. Decide
  with §6 whether it ships or is debug-only; when deep links land, both go.

### 7. 18+ and Terms consent control

- **Today:** `states.md` §1 puts "18+ confirmation, Terms and Privacy consent (FR-011)" on Sign up,
  and `strings-proposed.md` has `signup.consent`. Neither `tokens.md` nor `states.md` says how it
  looks, and a checkbox is **not one of the ten components**.
- **Figma** draws it as a loose frame on Sign up (§3.1 row 6): a 24 x 24 square, radius 0, 1 px
  `outlineStrong`, no fill, top-aligned, 16 from `body` `inkMuted` text; Terms and Privacy Policy
  are separate underlined links on their own row. `ConsentCheckbox` follows that.
- **Still open:** the **checked** state is not drawn (built as an `ink` fill with the Lucide check
  in `surface`); radius 0 is outside `tokens.md`'s 12-or-full (D3); and it is still not one of
  the ten components. The consent copy follows Figma ("I am 18 or over, and I agree to the terms
  below.", with the two links directly under it), not `signup.consent` ("…agree to the Terms and
  the Privacy Policy."); `strings-proposed.md` should be brought in line.

### 8. The verification and reset emails

- **Today:** a recorded gap (`states.md` §1c): the backend's plain-text copy is the design.
- **Here:** nothing; the client never renders them. Listed so the brainstorm covers them.

### 7a. Every string PR #6 proposed

All live in `shared/feature/auth/src/commonMain/composeResources/values/strings.xml`, marked
`PROPOSED` there. None is in `copy.md` or `strings-proposed.md`; each needs the owner's approval.

| Key | String | Why it exists |
|---|---|---|
| `register_name_label` / `_helper` | Your name / What your person will see. | Name field (§5) |
| `register_name_error_blank` / `_long` | Add your name to continue. / Keep it to 80 characters or fewer. | Name validation |
| `register_password_error_short` | That password is too short. Use 8 characters or more. | Client check under 8; `VALID_PASSWORD` for a sent password under 8 |
| `register_password_error_long` | That password is too long. Use 128 characters or fewer. | Client check over 128; `VALID_PASSWORD` for a sent password of 8 or more (the server's 128-character and 512-octet limits) |
| `register_action_sending` | Creating your account | Sending label (states.md §1c) |
| `verify_token_label` / `_helper` | Code from the email / Paste the code from the link we sent, or the whole link. | Paste field (§6) |
| `verify_token_error_blank` / `_invalid` | Paste the code from the email first. / That code does not match. Copy it again from the email. | `VERIFICATION_TOKEN_INVALID` |
| `verify_expired_action_sending` | Sending a new link | Sending label for "Send a new link" |
| `login_password_error_blank` | Add your password to sign in. | Empty password |
| `login_action_sending` | Signing in | Sending label |
| `login_error_rate_limited` | Sign in is paused for a short while. Your account is fine. Try again in a few minutes. | `ratelimit.line` plus a "when" (states.md §1b); the full-screen 345:53 is not built |
| `auth_error_rate_limited` | This is paused for a short while. Try again in a few minutes. | 429 on register, verify, resend |
| `login_session_ended` | You have been signed out. Sign in again to continue. | Session ended (§4) |
| `home_greeting` / `home_line` | Hello, {name} / You are signed in. The rest of Moyi is on its way. | Temporary placeholder; removed with it in the Bond settings PR |
| `home_sign_out` / `_sending` | Sign out / Signing out | Temporary placeholder; the words moved to Bond settings (`settings_row_sign_out`, `settings_signing_out`) |

### 7c. Every string the password-reset PR proposed

In the same `strings.xml`, marked `PROPOSED`. The rest of the three screens' copy is
`strings-proposed.md` (`reset.*`) or reused from sign in, sign up and verify.

| Key | String | Why it exists |
|---|---|---|
| `reset_request_action` | Send the link | Forgot password's PrimaryAction; `strings-proposed.md` has no key for it |
| `reset_request_action_sending` | Sending the link | Its Sending label (states.md §1c) |
| `reset_action_sending` | Saving | "Save and sign in" while saving and signing in |
| `reset_token_error_expired` | That link has expired. Go back and ask for a new one. | `410 PASSWORD_RESET_TOKEN_EXPIRED` under the paste field (§6a) |

### 7b. Destinations PR #6 draws but cannot reach yet

- **Terms** and **Privacy Policy** on sign up: no published URL, so the links do nothing.
- **I forgot my password** on sign in: reached since the password-reset PR (2026-11-05).
- **Sign in is the root** while the promise screen (Onboarding 1) is unbuilt, so it has no
  back control and its eyebrow sits at the top of the content (as the unverified gate does).

## Platform

### 9. Android splash and app icon

- **Today:** `states.md` §0: "the app icon does not exist", and the Android splash is `surface`
  plus the icon. It is the blocking brand asset for both stores.
- **Here:** the wizard's default green Android icon and a light system splash, seen during the
  PR #5 screenshots. It shows a white splash before a dark app.
- **Done in the first-impression PR.** The owner chose the icon on 2026-10-30: the o-dot mark,
  variant V1 "open gap", generated by `tools/icon/make_icon.py` (see its README). Android has the
  adaptive icon with a monochrome layer, and its API 31+ splash shows the mark in `ink` on
  `surface` with no white flash. iOS has the icon in Any, Dark and Tinted, and a launch screen
  that shows the wordmark on `surface`.

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

`DayCell`, `StateBlock`, `InviteCode`, `Chip`, `FavouriteToggle`, and `Field`'s `Select`, `Code`
and `Multiline` types. They are built when a screen needs them, and they are not gaps in the
design system. `EntryCard` and `ComposeField` arrived with the daily loop (PR #8); `StateBlock`
and `InviteCode` arrive with onboarding (PR #7), and the daily loop's `DayNote` stand-ins give
way to `StateBlock` at the rebase.

## The daily loop (PR #8, UI only)

### 13. Strings PR #8 proposed

Each is marked PROPOSED in `shared/feature/today/.../strings.xml`; none is approved.

| Key | String | Why |
|---|---|---|
| `today_write_action` | Write | Today's PrimaryAction; the corpus has no label (spec gap) |
| `write_send` | Send | the composer's PrimaryAction; the corpus has no label (spec gap) |
| `write_saving` | Saving | Save in flight, mirroring Sending |
| `write_queued` | Waiting to send | PrimaryAction QueuedOffline |
| `write_counter` | %1$d / 500 | the counter past 400 |
| `today_author_you` / `today_author_partner` | From you / From %1$s | EntryCard author labels ("From you" is fixed for the tombstone by states.md) |
| `today_edit` | Edit what you wrote | parallels the established "Delete what you wrote" |
| `today_tombstone_line` | You deleted what you wrote. | the one body line on the tombstone (420:59) |
| `today_nav_archive` / `today_nav_settings` | Archive / Bond settings | spoken names of the nav icons |
| `delete_heading` | Delete what you wrote? | the delete dialog's title (420:67; the full screen became a dialog, D11) |
| `delete_confirm` | Delete | the delete dialog's destructive answer |
| `write_discard_title` / `write_discard_title_editing` | Discard what you wrote? / Discard your changes? | the composer's discard dialog (D10) |
| `write_discard_confirm` / `write_discard_keep` | Discard / Keep writing | its two answers (D10) |
| `ds_close` | Close | spoken name of the X that closes a task on Android (D10) |
| `ds_compose_dismiss_prompt` | Hide the prompt | spoken name of the composer's X |

Two corpus lines were adapted, not invented: the waiting line is copy.md's sentence split at
its full stop into a heading and a line (the StateBlock shape), and save failed uses states.md
§10's form without "Try again?", because the button under it already says Try again.

### 14. Delete-after-reveal and the tombstone: which surface

`states.md` §6 says it is unsettled. PR #8 puts the link on Today · revealed and the tombstone
on Today's layout (not on the Reveal surface Figma draws it on), so nothing dead-ends. The link
pushes the revealed cards 24 above 159:19. Owner call.

### 15. The motion board was never read

The reveal's timing, easing and choreography are placeholders (`RevealMotion`,
`TODO(motion)`), pending Figma 180:21. The design system has no motion tokens yet.

## Onboarding

### 16. The time-zone picker (bond-timezone-picker PR, 2026-11-06)

- **Today:** `tokens.md` §4 has `Field` `Select` ("tapping it opens a picker the screen owns"),
  and `strings-proposed.md` decides the picker's load-failure line (`field.timezone.error`). The
  design system has no picker, list row or search component, and Figma draws no picker frame.
- **Here, interim:** a full screen inside Create bond: `MoyiScaffold` with its back arrow,
  `ScreenHeader` ("Choose your shared time zone"), a `Field` used as the search, and one row per
  zone (city in `body`/`ink`, region in `body`/`inkMuted`, a `Check` glyph on the chosen one) in a
  full-width target at least 48 tall. Header, search and rows scroll together. No new token,
  colour or component. System back and the iOS edge swipe close it (`LayerBackHandler`).
- **The open question:** whether the picker is this full screen, a sheet, or the platform's own
  picker; what a list row looks like (it will recur in Bond settings and Archive); and whether
  the city names should be the platform's localised ones rather than the IANA id's last part.
- **Strings proposed:** `timezone_picker_heading`, `timezone_picker_search_label`,
  `timezone_picker_empty`, and a new wording for `field_timezone_error_unsupported` (the old
  one sent people to their phone's settings, written before the picker existed).
## Archive (PR for Archive, part 4: month jump)

### 17. The month picker

- **Today:** `states.md` §6 says month jump is route 1 and "Tapping opens a month picker", and
  draws the 56 pt row (Figma 257:9) with "Jump to". **No picker is drawn**: there is no picker
  frame in the Archive section (425:317), no row for one in `tokens.md` §4, and `SKILL.md` says
  nothing about it. `strings-proposed.md` §8 records "Jump to" as deliberately unwired.
- **Here:** no component of ours. The most conservative option: the platform's **simple
  dialog**, Material 3's `AlertDialog` in Rosewood (as `ConfirmDialog` on Android), titled
  "Jump to a month" (PROPOSED), a list of months newest first, each a 48 dp row in `body`, and
  Cancel. Tapping a month chooses it and closes the dialog. **The same Compose dialog on iOS**:
  the iOS month and year wheel (`UIDatePicker` in `.yearAndMonth`, iOS 17.4+) would need a
  hosting sheet with Done and Cancel that the design system does not draw either.
- **Which months:** from the current Bond-day's month (never a later one) back to the month of
  the oldest day the Archive has loaded so far. The client knows of no earlier day: the
  contract has no "oldest day" or "months with days", and the bond's `createdAt` is not
  carried into the app. So the picker grows as you scroll or jump further back.
- **The open questions:** (1) a drawn picker, or keep the platform dialog; (2) on iOS, the
  wheel in a sheet instead; (3) how far back it should reach: the bond's start
  (`BondResponse.createdAt`, one more call) or a server-side list of months.

### 18. The month row is 56 in `states.md`, 80 in Figma

- `states.md` §6 sizes it twice ("the 56 pt month-jump row"; the field "costs 59 pt" against
  it). Figma's frame 257:9 is 80 tall (the glyph at y 28, the 48 target at y 16). **Built 56**
  (the design system wins), so the chips sit at 240 and the list at 312, 24 above Figma's 264
  and 336. One of the two needs correcting.
## Bond settings, part 1

### 19. The OS time picker (reveal time; later, your reminder)

- **Today:** `states.md` §8: Reveal time and Your reminder "open an **OS time picker**. No
  in-app screen". The app had no platform time picker.
- **Here:** `TimeOfDayPicker` (designsystem): Material 3's `TimePicker` dial in an
  `AlertDialog`, on **both** platforms, in Rosewood through `MoyiTheme`'s Material roles.
  On Android that is close to the platform's own picker; on iOS it is not (the native one is
  a `UIDatePicker` in time mode, as a wheel or a compact popover).
- **The open question:** whether iOS gets `UIDatePicker` (an `expect`/`actual`, as
  `ConfirmDialog` does), and whether the dial's colours need their own check in light.
  The picker has no way to clear a reveal time back to "when you have both written"; no
  screen offers it yet.

### 20. The settings row

- **Today:** Figma draws the Bond settings rows as loose frames (396:13 and its siblings:
  label and value, `body`, 56 tall), not one of the ten components, and `tokens.md` §1 sets
  their rule: an underline only where the row navigates.
- **Here:** `SettingsRow` and `SettingsToggleRow`, local to `feature/bond`, the smallest
  version inside the tokens: 16 above and below a 24 line, the whole row the target, the value
  underlined when the row navigates (the label when it has no value), `danger` with the
  underline kept for Leave this bond. Rest days is a switch to assistive technology.
- **The open question:** whether rows become a design-system component (Account and the
  later settings screens will want them too).

### 21. The ETag conflict state

- **Today:** `states.md` §8 names it, "This changed while you were looking at it.", and says
  it is worth drawing; there is no frame.
- **Here:** the screen reloads and shows the line as the inline error at the row or editor
  that was saved, with the person's input kept. Needs a frame and the owner's wording.

## Bond settings, part 2

### 22. The time-zone picker

- **Today:** `states.md` §8 and Figma 397:53 draw the zone as a `Field · Select` whose
  chevron opens "a picker the screen owns"; no picker is drawn, and neither platform has a
  system time-zone picker an app can open (Android's lives in Settings, iOS has none).
  Onboarding's create-bond field has the same gap (its `OnTimeZoneClick` is a TODO).
- **Here:** the most conservative form the existing pieces allow, local to `feature/bond`:
  a screen with the heading "Choose a city", a `Field · Text` to search, and one
  `SettingsRow` per region zone the platform knows (kotlinx-datetime's list, `UTC` and
  `Etc/…` left out because the backend refuses them), the city as the label and the IANA
  id as the value. A tap chooses and returns. Names are not localised: the city is read
  out of the id (`America/Argentina/Buenos_Aires` is Buenos Aires).
- **The open question:** a drawn picker (and whether onboarding shares it), localised zone
  names (ICU on Android, `NSTimeZone.localizedName` on iOS), and whether the device's zone
  is offered first.

### 23. The 30-day refusal

- **Today:** `states.md` §8 says the refusal names a date; there is no frame.
- **Here:** an `InlineError` 8 above Propose change (or Agree), without the date, because
  the contract has none (`backend-contract-gaps.md` row 15).


## Ending and safety

Numbered from 30 so they do not collide with sections other open PRs add; renumbered at merge.

### 30. Close the box: the two states between asking and the cooling-off

- **Today:** Figma draws the ask (399:38 BOTH MUST AGREE) and the cooling-off (399:1525),
  but not what each member sees after one of them has asked: the one who asked, waiting,
  and the other, asked to agree. The API has both (`pendingDeletionRequest`, ADR-0030).
- **Here:** the time zone's two-party layouts, reused. *Asked* is the Proposed layout
  (397:67): the heading "You asked to close the box", when the ask lapses, and the
  secondary Cancel this request. *Agree* is the Confirm layout (397:102): who asked, Close
  the box (`danger`) and Decline. Words PROPOSED.
- **Also not drawn:** a Close the box row on the Bond settings root (396:3 has Leave and
  Block and leave only). It is added to Ending, underlined in `danger`, with the value
  Asked, or "Closes on 6 December" during the cooling-off.

### 31. Export and the archive link on the ending screens

- **Today:** "Export your archive" is drawn on the archived screens, the block confirm and
  the cooling-off (states.md §9, FR-009 MUST), and the archived screen links to the bond's
  Archive. The vendored contract has no export route, and the Archive is on another PR
  stack (#22 to #25).
- **Here:** both are left out rather than drawn dead. The block consequence drops "Export
  first if you want to keep them." with it, and the archived line does not promise an
  export. Owed together with the export screen (`Account 8 · export · REQUESTED`).

### 32. Bond · archived: READ ONLY and BLOCKED are one screen

- **Today:** 399:21 offers Close the box; 614:1832 does not. ADR-0030 refuses a deletion
  request on every archived bond, so states.md §9 now says neither offers it.
- **Here:** one screen for both, with no back, built from nothing but the bond list, so a
  blocked person and a blocker see the same thing (`26` §2.1). Its one action is Start a
  new bond (PROPOSED); Figma's primary label was not read.

## Motion and keyboard (2026-11-08 smoke test)

### 33. Motion beyond the reveal: screen transitions and the busy control

- **Today:** `SKILL.md` §5 sets the motion budget at one animation, the reveal, and
  `SKILL.md` "Navigation is instant, with no transition". `PrimaryAction`'s KDoc said "no
  animation". The one exception was the iOS Write sheet (D10).
- **Owner ruling, 2026-11-08** (during the PR smoke test): "please handle screen
  transition animation properly. ui animations on the screen. loading state on the
  button". The owner owns the design system, so this amends §5; `SKILL.md`, `tokens.md`
  and the Figma motion board are the owner's to update. Nothing outside `moyi-app` is
  changed here.
- **Built, as functional motion only** (no bounce, no overshoot, no decoration; the
  reveal stays the one expressive moment). Values live in `MoyiMotion`:
  - **Screens:** Android, Material's shared axis X (a 10 % slide with a fade through,
    300 ms). iOS, the navigation push (full-width slide over a third-width parallax,
    350 ms, a UIKit-like decelerate curve), which matches the edge swipe. Write keeps its
    sheet on iOS; on Android it now rises 10 % and fades in.
  - **The busy control:** `PrimaryAction` cross-fades its label (150 ms), eases its dim
    and width, and while Sending its label breathes between 100 % and 55 % (900 ms each
    way). Still no spinner: the words say what is happening, the breath says they are live.
  - **Reduced motion:** iOS Reduce Motion turns pushes into a 200 ms cross-fade and stops
    the breath. Android's "Remove animations" scales every Compose animation to nothing.
- **The open question:** whether the owner wants the Figma motion board and `tokens.md`
  to name these durations, or a different curve on either platform.

### 34. The keyboard: focus on arrival, and actions under it

- **Built:** a screen whose job is typing opens with its field focused and the keyboard
  up (`rememberAutoFocus`): register, verify, forgot and choose a new password, join with
  a code, create a bond, rename the bond. Sign in focuses the password when it arrives
  with the email filled (after verifying or a reset). A prefilled field opens with the
  cursor after the last character. Tapping `PrimaryAction` puts the keyboard away.
- **Fixed:** with a tall keyboard, a sign-in error stacked the actions below the fold,
  so the error and "Sign in" were hidden under the keyboard and nothing seemed to happen.
  `MoyiScaffold` now scrolls to the action block when it grows while stacked.
- **Not changed, raised:** whether submitting with the keyboard's Done should also put the
  keyboard away (iOS apps differ); and the settings rows, whose values read as underlined
  links with no chevron (§20, the settings row, is still open).

## Offline: the outbox (offline-first, part 3)

### 35. Queued for a day that has closed

- **Today:** `states.md` §4 says the Queued state names "which day it will count for" when the
  entry is for an earlier Bond-day, and §10 draws one line: "It'll send when you're back online.
  It will count for Tuesday, 2 September."
- **The catch:** BR-3a (c) refuses `intendedAt` on a Bond-day that has closed, and the nightly
  close job closes every Bond-day at its own midnight (SOLO, EMPTY, or REVEALED), so an entry
  written yesterday and sent today counts for **today**, not yesterday (backend `DayAssignment`,
  read only; `docs/backend-contract-gaps.md` row 25). Saying "It will count for yesterday" would
  be false.
- **Here:** three lines, chosen by `QueuedDay`. Written today: §10's drawn line with today's date.
  Written on a day that has ended (PROPOSED `today_queued_line_earlier`): "It'll send when you're
  back online. Saturday, 7 November has closed, so it will count for the day it sends." Written
  more than 36 hours ago (PROPOSED `today_queued_line_late`): "It'll send when you're back online.
  It will count for the day it sends."
- **The open question:** keep these, or should the product change so a day stays open for late
  entries (a grace period on the close, a backend change)?

### 36. A queued entry the server refused

- **Today:** not in the design system. A queued entry can be refused when it is finally sent: `409
  ENTRY_ALREADY_EXISTS` (written from another phone), `409 DAY_CLOSED` (both had written and the
  day opened; seen on the emulator), `422`, the bond gone.
- **Here:** the words go back to Write as the new-entry draft (never over a draft already there:
  both are kept), and Today says so once, in `InlineError` under the prompt or my card, until Write
  is opened. PROPOSED `today_queued_refused`: "That didn't send. Your words are kept in Write,
  ready to send again." PROPOSED `today_queued_refused_written`: "You had already written for that
  day, so these words didn't send. They're kept in Write for your next entry."
- **The open question:** the wording, and whether `danger` is right for a refusal that lost
  nothing.

### 37. Signing out with an entry still waiting to send

- **Today:** not in the design system. Sign out wipes everything on the phone (FR-124), the outbox
  included, so a voluntary sign-out while offline would delete words nobody has seen.
- **Here:** the most conservative option that does not trap the person. Sign out first tries to
  send (one pass, at most 10 seconds). If something is still waiting, the platform `ConfirmDialog`
  asks, its safe answer the dismiss: PROPOSED `settings_sign_out_unsent_title` "Your entry hasn't
  sent yet", `_line` "Signing out deletes it from this phone. Stay signed in and it will send when
  you're back online.", `_confirm` "Sign out and delete it" (destructive), `_dismiss` "Stay signed
  in". An involuntary end (refresh refused, token reuse) asks nobody: privacy wins.
- **The open question:** the copy, and whether the words should instead be kept somewhere that
  survives a sign-out (which the session wipe forbids today).
