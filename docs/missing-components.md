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

- **Superseded by §58:** the field is now the code field, taking six digits or the link.

- **Today:** not in Figma. `states.md` §1 screen 3 has instruction, resend, "Check again" and
  "Use a different address", with no input. Deep links are meant to make the token invisible.
- **Here:** PR #6 adds a `Field` `Text` for pasting the token from the local backend log, as a
  development stand-in until deep links. The whole link may be pasted; the app takes the
  value after `token=`. "Check again" submits it. Proposed copy: label "Code from the email",
  helper "Paste the code from the link we sent, or the whole link.", errors "Paste the code
  from the email first." and "That code does not match. Copy it again from the email."
  In the expired state the field is hidden. Decide whether it ships or is debug-only.

### 6a. Reset-password paste-code field

- **Superseded by §58:** the field is now the code field, taking six digits or the link.

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
| `verify_expired_action_sending` | Sending a new email | Sending label for "Send a new email" (reworded by owner ruling 2026-11-09, §58) |
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
| `reset_request_action` | Send the code (was Send the link until §61) | Forgot password's PrimaryAction; `strings-proposed.md` has no key for it |
| `reset_request_action_sending` | Sending the code (was Sending the link until §61) | Its Sending label (states.md §1c) |
| `reset_action_sending` | Saving | "Save and sign in" while saving and signing in |
| `reset_token_error_expired` | That link has expired. Go back and ask for a new one. | `410 PASSWORD_RESET_TOKEN_EXPIRED` under the paste field (§6a) |

### 7b. Destinations PR #6 draws but cannot reach yet

- **Terms** and **Privacy Policy** on sign up: reached since the legal-consent PR (2026-11-09), at the URLs `GET /meta/legal` names (§54).
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
| `write_discard_keep_draft` | Keep as a draft | its third answer, closing and keeping the draft (owner ruling 2026-11-08; §38) |
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
- **Update (PR feat/export-and-deletion):** the export screen now exists, reached from
  Account (§57), and the archived bond draws its "Export your archive" link (owner ruling
  2026-11-09), opening it directly. Linking it from the block confirm and the cooling-off is
  still owed.

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
  button". The owner owns the design system, so this amends §5.
- **Status, 2026-11-08: recorded in the design system.** The owner has amended `SKILL.md`
  §5 ("one expressive moment, plus functional motion") and added `tokens.md` §3a (motion
  tokens, the values below). `MoyiMotion` follows them. Only the Figma motion board is
  still to be drawn.
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
- **Settled:** `tokens.md` §3a names these durations and curves. Still open: the Figma
  motion board.

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
- **Owner ruling, 2026-11-08 (`strings-proposed.md` §12):** the product changes, on the
  backend. A Bond-day is settled only after a grace of about 12 hours past its end, so an entry
  written offline at 23:00 and sent at 07:00 counts for the day it was written; missed day,
  rest day, streak and notifications are decided once, after the grace. FR-045 and BR-3a were
  amended to 12 h the same day. Recorded as a backend ask in `docs/backend-contract-gaps.md`
  row 25. `today_queued_line` and `today_queued_line_late` are approved; `today_queued_line_earlier`
  is **held** until the backend ships the grace, when it becomes "It will count for {day}."
  Until then the app says `today_queued_line_late` for a closed earlier day too (as
  `strings-proposed.md` §12 directs), so `today_queued_line_earlier` is no longer shown.

### 36. A queued entry the server refused

- **Today:** not in the design system. A queued entry can be refused when it is finally sent: `409
  ENTRY_ALREADY_EXISTS` (written from another phone), `409 DAY_CLOSED` (both had written and the
  day opened; seen on the emulator), `422`, the bond gone.
- **Here:** the words go back to Write as the new-entry draft (never over a draft already there:
  both are kept), and Today says so once, in `InlineError` under the prompt or my card, until Write
  is opened. PROPOSED `today_queued_refused`: "That didn't send. Your words are kept in Write,
  ready to send again." PROPOSED `today_queued_refused_written`: "You had already written for that
  day, so these words didn't send. They're kept in Write for your next entry."
- **Owner ruling, 2026-11-08 (`strings-proposed.md` §12):** both lines approved as written, and
  **neutral, not `danger`**: nothing was lost, and `danger` is reserved for the irreversible
  (`SKILL.md` §3). Built: the line is a `DayNote` in `inkMuted`, like Today's other informational
  lines, with an outlined **Open Write** action (PROPOSED `today_queued_refused_open_write`: the
  ruling names the action, the key is not in `strings-proposed.md` yet). Open Write is Today's
  Write action, which opens the composer on the kept words and clears the line.
- **Still open:** with `today_queued_refused_written` the day already holds my entry, so Open Write
  opens a new-entry composer on a day that cannot take one: a Send there is refused (`409`, Save
  failed, words kept). The words are meant for the next day; whether the action should wait for
  that day, or open them some other way, is the owner's call.

### 37. Signing out with an entry still waiting to send

- **Today:** not in the design system. Sign out wipes everything on the phone (FR-124), the outbox
  included, so a voluntary sign-out while offline would delete words nobody has seen.
- **Here:** the most conservative option that does not trap the person. Sign out first tries to
  send (one pass, at most 10 seconds). If something is still waiting, the platform `ConfirmDialog`
  asks, its safe answer the dismiss: PROPOSED `settings_sign_out_unsent_title` "Your entry hasn't
  sent yet", `_line` "Signing out deletes it from this phone. Stay signed in and it will send when
  you're back online.", `_confirm` "Sign out and delete it" (destructive), `_dismiss` "Stay signed
  in". An involuntary end (refresh refused, token reuse) asks nobody: privacy wins.
- **Owner ruling, 2026-11-08 (`strings-proposed.md` §12):** the four strings approved as written.
  And Sign out must be reachable offline: Bond settings did not load offline, so the row could
  not be reached. Built: when the bond's settings fail to load, the Generic error (with Try
  again) keeps the **Just you** group under it, so Sign out and its unsent-entry question work
  with no connection.
- **Still open:** whether the words should instead be kept somewhere that survives a sign-out
  (which the session wipe forbids today).

### 38. A confirmation with a third answer

- **Today:** `SKILL.md` §3 and D10/D11 give confirmations two answers, the destructive one and the
  safe one, in the platform dialog (`ConfirmDialog`).
- **Owner ruling, 2026-11-08:** closing Write with words asks "Discard what you wrote?" (or
  "Discard your changes?") with three answers: Discard (deletes the draft), **Keep as a draft**
  (closes, the words come back next time Write opens), Keep writing.
- **Built, the most conservative way:** `ConfirmDialog` takes an optional `alternativeLabel` and
  `onAlternative`; left out, the dialog is unchanged. Both platforms stack the three in one order:
  Keep as a draft, Discard, Keep writing. Android: end-aligned `TextButton`s in the Material
  dialog, the third plain `ink` (Material's own button row wrapped the third alone onto a second
  row, out of order, so the three are stacked as Material stacks buttons that do not fit). iOS: a
  default-style action in the `UIAlertController` alert; with three, iOS stacks them and puts
  Cancel (Keep writing) at the bottom.
- **The open question:** the string (PROPOSED `write_discard_keep_draft` "Keep as a draft") and
  whether the design system should list the three-answer dialog as a component variant.

## Notifications (push, part A)

### 39. Bond settings › Notifications

- **Today:** not drawn. `states.md` §8 lists *Reminder* ("per-member, respects each member's quiet
  hours") among Bond settings' controls and draws a *Your reminder* row that opens the OS time
  picker; nothing draws the six kinds of push ADR-0045 lets a member turn off, quiet hours, or what
  the screen says when the phone does not allow notifications.
- **Here:** a screen of its own, reached from a **Notifications** row in *Just you* (Navigates),
  built only from Bond settings' own rows so it adds no component: the heading, one muted line
  (PROPOSED `notifications_line`), then *This phone* (only while the phone was never asked or said
  no), *What to hear about* (six `SettingsToggleRow`s, the reminder's time under the reminder as an
  Opens row), and *Quiet hours* (a toggle, and when on, From and Until as Opens rows). Every string
  is PROPOSED (`notifications_*`, `settings_row_notifications`). The *Your reminder* row is no
  longer on the root: the reminder's time lives with the reminder.
- **The permission, when:** only on this screen, when the person turns a kind of notification on
  while the phone has never been asked, or taps *Allow notifications*. Never at launch or in
  onboarding (doc 13 §7). Denied is said once, with *Open phone settings*, and never asked again.
- **Open questions for the owner:** the copy; quiet hours' default when first turned on (PROPOSED
  22:00 to 07:00); whether the prompt should also come after the first entry is sent (doc 13 §7,
  "after the user has seen the value"; `states.md` §3a says onboarding "already spends a prompt on
  notifications", which this build does not do); iOS provisional authorisation (doc 13 §7), not
  used here.


## Photos on entries

### 40. A photo on an EntryCard, and what a photo entry does offline

- **Today:** not drawn. `tokens.md` §4 gives `EntryCard` "author label, text" and no media slot;
  `states.md` §4 says only "Voice note and photo are optional and secondary. Text is always
  sufficient (NFR-103)". Nothing draws a photo on a card, its loading state, the partner's
  *processing* state (`EntryImageResponse.PROCESSING`), the author's *not shared* state
  (`NOT_SHARED`, owner ruling Q10), or the *sensitive* cover (Q16: "blurred, behind a tap to
  view").
- **Here (interim, the most conservative reading):** the photo sits **under the words**, the
  card's full inner width, its own shape kept between 3:4 and 2:1 (4:3 when the server gives no
  size; **superseded by §70**: a height cap at 3:4 and no floor for wide photos), the card's 12 radius, on `surface` until it loads: no spinner, no shimmer, no fade.
  Processing is one muted line and no image (PROPOSED `ds_entry_photo_on_its_way`, "A photo is on
  its way."); not shared is the photo (when the server still gives it) with one muted line under
  it (PROPOSED `ds_entry_photo_not_shared`, "This photo is not shared."); sensitive is the same
  `surface` frame with one muted line instead of the photo (PROPOSED `ds_entry_photo_sensitive`,
  "This photo may be sensitive. Tap to see it."), uncovered by a tap. No blur: a blur would
  still show the shape of what is in it, and Compose has no blur below Android 12. The same block
  is used on Today, the reveal, the Archive's day view and, under the two-line excerpt, the
  Archive list's day card. A locked or deleted card draws no photo and says nothing of one.
- **Capture protection:** the partner's photo is inside their EntryCard, so the card's existing
  protection covers it. In the Archive list it is drawn outside an EntryCard, so it protects the
  screen itself (`EntryPhotoView(protectFromCapture = true)`); the list's partner **words** are
  still unprotected (the existing `TODO(#17)` on `DayCard`).
- **Offline (doc 13 §5 says media is "uploaded directly to R2 from the background worker; the
  entry references the media id once complete"):** this PR takes the conservative reading. A
  photo entry is **not queued**. The upload runs at the Send tap, in the composer, and the words
  are sent only once the server has processed the photo, because an entry can attach only a
  `READY` asset (backend `MediaAssetStore.claim`, read only). Any failure, the upload's or the
  entry's, offline or not, keeps the words and the photo in the composer with "That didn't save.
  Your words and your photo are still here." (PROPOSED `write_save_failed_photo`) and Try again,
  which resumes where it stopped (bytes already in storage are not sent again; the completion is
  replayed with its key; the entry is resent with the same key and `intendedAt`). The outbox still
  holds words only. The **draft** keeps the words, not the photo: closing asks first (D10) when a
  photo is attached, and *Keep as a draft* keeps the words only.
- **Why not the outbox:** queuing the photo means keeping it on disk until the network returns.
  The local database is SQLCipher-encrypted (#40); a photo file beside it would not be, unless it
  is encrypted too, and the worker would then need to upload it in the background on both
  platforms (WorkManager, BGTaskScheduler) and survive the 24-hour reaper of unattached uploads.
  That is a privacy and a scope decision, so it is the owner's.
- **No disk cache for photos:** Coil keeps photos in memory only, for the same reason (a disk
  cache would hold the partner's photos as plain files). A photo is loaded again after the app
  restarts, and is not shown offline. The Today and Archive caches do not keep the photo or its
  URL (the URL is a credential that lasts 24 hours). Since §70, Today's cache keeps the photo's
  size and the voice note's length (never the URL), so its cards keep their size on open.
- **Open questions for the owner:** (1) the card layout, the four states and their copy; (2)
  queue photo entries offline (an encrypted file in the outbox) or keep failing them in the
  composer; (3) keep the photo with the draft (same encryption question); (4) an encrypted disk
  cache for photos, so they show offline; (5) whether the Archive list shows the photo at all, or
  only the day view; (6) "reduce data usage" (`SKILL.md` §11) when the list loads many photos.

### 41. Adding a photo in the composer

- **Today:** `ComposeField` draws the "Add a photo" icon (tokens.md §6) and nothing after the tap:
  no source choice, no attached-photo preview, no remove control, no upload state, no copy for a
  photo the phone could not open or the server would not take.
- **Here (interim):**
  - **Source:** when the phone has a camera, the platform dialog (`ConfirmDialog`, as D10's
    question) asks: title "Add a photo", answers "Choose a photo", "Take a photo", "Cancel" (all
    PROPOSED `write_photo_source_*`). Without a camera (the iOS simulator) the library opens at
    once. The pickers are the system's: Android's Photo Picker (no storage permission) and the
    camera app (no camera permission declared); iOS PHPicker (no library permission) and
    `UIImagePickerController` (PROPOSED `NSCameraUsageDescription`: "Moyi uses the camera only
    when you take a photo to add to your entry.").
  - **Attached:** inside the composer's box, above the toolbar, a 96 dp-tall preview at the
    card's radius with a 48 target beside it that removes it (PROPOSED spoken names
    `ds_compose_attached_photo`, `ds_compose_remove_photo`). Picking again replaces it. Hidden
    while editing: an edit sends words only (`PATCH` with `imageMediaId` is not used yet).
  - **Sending:** `PrimaryAction` Sending reads "Sending your photo" (PROPOSED
    `write_sending_photo`) while the photo uploads and is processed, then "Sending". The remove
    control is hidden while it is on its way.
  - **Refused** (the server failed to process it, or moderation held it before it was
    attached): "This photo can't be sent. Remove it to send your words." (PROPOSED
    `write_photo_refused`), and Send is disabled until it is removed. **Unreadable** (the phone
    could not open it): "That photo couldn't be opened. Try another one." (PROPOSED
    `write_photo_unreadable`); whatever was attached stays.
- **Open questions for the owner:** the source choice as a dialog or a sheet; the preview's size
  and place; the copy.

### 42. Viewing a photo larger

- **Today:** not drawn. Nothing in `states.md` or Figma opens a photo full-screen.
- **Here:** nothing; tapping a photo does nothing (except uncovering a sensitive one). Not
  improvised.
- **Open question for the owner:** a full-screen viewer (with pinch to zoom, and the capture
  protection a partner's photo needs) or none. **Answered 2026-11-10:** a viewer, §70.

## Voice notes on entries

### 43. A voice note on an EntryCard

- **Today:** not drawn. `tokens.md` §4 gives `EntryCard` no media slot; `states.md` §4 says only
  "Voice note and photo are optional and secondary"; `SKILL.md` §11 says "voice notes on demand".
  Nothing draws a player, its loading state, the partner's *processing* state
  (`EntryVoiceResponse.PROCESSING`), the author's *not shared* state (Q10), or what *sensitive*
  (Q16) means for sound. There are no play, pause or stop glyphs in `tokens.md` §6.
- **Here (interim, the most conservative reading):** under the words (and under the photo, when
  there is one), a 48 target with Lucide's `play` / `pause` glyph tinted `ink`, and beside it, in
  `inkMuted`, the length ("0:42") before it plays and "0:12 of 0:42" while it plays or is paused
  (PROPOSED `ds_voice_progress`). No speed control. **Since 2026-11-09 the time follows a
  waveform you tap or drag to move through, and is one clock (§46).** Spoken names:
  "Play the voice note" / "Pause the voice note" (PROPOSED `ds_voice_play`, `ds_voice_pause`).
  - **On demand only:** nothing is fetched until play is tapped. While it is fetched the line
    reads "Getting the voice note" (PROPOSED `ds_voice_loading`): the control says what it is
    doing, no spinner. It could not be played: "This voice note couldn't be played. Tap play to
    try again." (PROPOSED `ds_voice_unplayable`).
  - **Processing:** one muted line, no player: "A voice note is on its way." (PROPOSED
    `ds_entry_voice_on_its_way`). **Not shared:** the player (when the server still gives the
    URL) and "This voice note is not shared." (PROPOSED). **Sensitive:** "This voice note may be
    sensitive. Tap play to hear it." above the player (PROPOSED): nothing plays without a tap
    anyway, so the tap is the cover.
  - **One at a time, and it stops with the screen:** starting one pauses any other; leaving the
    screen releases the player; the app going to the background pauses it; a call pauses it
    (Android audio focus, iOS session interruption), as does unplugging headphones on Android.
  - **Where:** Today, the reveal, the Archive's day view and the Archive list's day card, as the
    photo is. A locked or deleted card draws nothing and says nothing of one. A partner's voice
    note is inside their EntryCard, protected with it; in the Archive list it protects the screen
    itself (`VoiceNoteView(protectFromCapture = true)`).
- **No cache:** the file is never written to disk. Android streams it (media3 ExoPlayer with
  `DefaultHttpDataSource`, no `CacheDataSource`); iOS fetches it into memory through an ephemeral
  `URLSession` (no `URLCache`) because AVFoundation cannot open Ogg (backend-contract-gaps row 30).
  Played again after it ends, it is not fetched again while the screen is open. The Today and
  Archive SQL caches do not keep the voice note or its URL.
- **Open questions for the owner:** (1) the player's look and copy; (2) a scrubber or a waveform;
  (3) whether the Archive list offers the player or only the day view; (4) "reduce data usage".

### 44. Recording a voice note in the composer

- **Today:** `ComposeField` draws the "Record a voice note" icon (tokens.md §6) and nothing after
  the tap: no permission moment, no recording state, no limit, no listen-back, no remove.
- **Here (interim):**
  - **Permission at the tap, not before:** Android asks for `RECORD_AUDIO` with the system dialog
    the first time the mic is tapped; iOS asks with PROPOSED `NSMicrophoneUsageDescription`
    ("Moyi uses the microphone only when you record a voice note to add to your entry."). Refused,
    now or before: "Moyi can't use the microphone. Allow it in Settings to record a voice note."
    (PROPOSED `write_voice_microphone_denied`). No settings deep link is improvised.
  - **Recording:** inside the box, above the toolbar, where the photo preview goes: a 48 stop
    target (Lucide `square`, "Stop recording") and "Recording 0:12 of 1:00" (PROPOSED
    `ds_compose_recording`). **Since 2026-11-09 (owner ruling, §49): trash (discard, no
    confirmation), the live waveform (§47), a `danger` recording dot and "0:12 of 1:00", then a
    tick (stop and keep); the stop square is gone. The screen reader still hears "Recording 0:12
    of 1:00".** The mic icon is hidden while one is recording or recorded (FR-043: at
    most one). Send is disabled while recording. At 1:00 it stops itself (the backend allows 60 s
    plus 1 s grace) and keeps the minute.
  - **Interruptions keep what was said:** a call, Siri, another app taking the microphone (Android:
    transient exclusive audio focus lost), or the app going to the background stops the recording
    where it is, and it becomes the voice note. Closing Write while recording stops it first, then
    asks the D10 question; Discard throws it away.
  - **Recorded:** a 48 play/pause target to listen back (from memory, nothing leaves the phone),
    "Your voice note, 0:42" (PROPOSED `ds_compose_recorded`; since 2026-11-09 the waveform of what
    was recorded and its clock, this line its spoken name, §46), then two 48 targets: Lucide
    `rotate-ccw` "Record again" and the X "Remove the voice note" (PROPOSED). Both are hidden while
    it is being sent. Not offered while editing (an edit sends words only).
  - **Sending:** after any photo, `PrimaryAction` Sending reads "Sending your voice note"
    (PROPOSED `write_sending_voice`) while it uploads and is transcoded, then "Sending". The entry
    carries `voiceMediaId` (and `imageMediaId` when there is a photo: one of each, FR-042/043).
  - **Failures keep everything:** "That didn't save. Your words and your voice note are still
    here." (PROPOSED `write_save_failed_voice`; with a photo too, `write_save_failed_photo_voice`)
    and Try again, which resumes (bytes already in storage are not sent again). **Refused** (too
    long, unreadable, or held by moderation before it was attached): "This voice note can't be
    sent. Remove it to send your words." (PROPOSED `write_voice_refused`). A recording that did not
    work: "That recording didn't work. Try recording again." (PROPOSED `write_voice_failed`).
  - **Offline:** as a photo entry (§40): never queued; it fails in the composer and keeps the
    words and the recording. The draft keeps the words, not the recording.
- **Encoding:** AAC-LC in MPEG-4 (`audio/mp4`), mono, 44.1 kHz, 64 kbit/s, on both platforms
  (`VoiceFormat.MP4` accepts Android's `mp42` and iOS's `M4A ` brands; a minute is about 480 KB,
  under the 5 MiB cap). The server keeps Opus at 24 kbit/s, so recording higher only spends
  upload. The recording is a file only while it records (MPEG-4 writes its index last, so it
  needs a seekable file): `cacheDir/voice` on Android, the temporary directory on iOS. It is read
  into memory and deleted the moment it stops; a file a killed process left is deleted before the
  next recording.
- **Open questions for the owner:** (1) the recorder's look, its place in the box, and the copy;
  (2) hold to record instead of tap to start and stop; (3) a settings deep link from the refused
  line; (4) queue voice entries offline (the §40 question, for sound); (5) keep the recording with
  the draft.

## Reactions

### 45. Leaving, changing and seeing a reaction

- **Today:** not drawn. ADR-0037 question 9: `states.md` has no reaction in any section and
  `tokens.md` §4's `EntryCard` has no slot for one; the owner ruled on 2026-10-21 that the gaps
  are to be written into `states.md` as specifications and drawn when Figma is available. As of
  this PR, `states.md` §5a's amendment still says "not yet written". `copy.md` names no reaction.
  What is fixed elsewhere: the set (the contract's `ReactionRequest.type`, the owner's five of
  2026-10-21: `HEART`, `THANK_YOU`, `SMILE`, `MOVED`, `ME_TOO`); one per member per entry; only
  on the partner's revealed entry (FR-048 amended, ADR-0037 decision 4); shown to the author
  (FR-064's one exception, question 6); no time stored or sent, no count; no event and no push
  (decision 10; T-09: nothing a partner could use to watch the other); refused on an ended bond,
  both leaving and taking back (question 11, `409 BOND_ARCHIVED`).
- **Here (interim, the most conservative reading), built only from what the system has:**
  - **Where:** under the partner's revealed entry on Today (Revealed, and Tombstone, where my
    entry is deleted but theirs still shows), on the reveal screen once its motion has settled
    (ruling 3 below), and in the Archive day view. Not on a solo day's unlocked entry (still
    `SUBMITTED`: FR-048 says revealed, though the server would take one; ruling 4), not on a
    tombstone, never on my own entry, and not on a bond that takes no writes (ruling 5).
  - **The control:** a muted line, then the five as **emoji and word**, one `Chip` each (the
    Archive filter's pill; the word uppercased there, the emoji drawn before it in `body` size),
    wrapping inside the column (320 at the narrowest) and at large text, at most one selected.
    Tapping another replaces mine; tapping mine takes it back. No confirmation, no undo toast, as
    the `FavouriteToggle`. The line names the next action: "React to what Tunde wrote." with none,
    nothing once one is chosen (the pill on the card says which, §67; the line
    "Tap another to change it, or the same one to take it back." was removed there) (PROPOSED
    `reaction_prompt`).
  - **Accessibility:** the set is a `selectableGroup` of radio buttons; each is spoken by its
    **word only** (the emoji is drawn, never spoken) with its selected state; on the selected
    one, a double tap is announced as "Take back your reaction" (PROPOSED `reaction_take_back`),
    since a radio button is not usually cleared. 48 pt targets (the Chip's), and selected differs
    by fill and border, not hue (NFR-104).
  - **The partner's reaction on my entry:** the interim `ReactionBadge` under my card (ruling 6
    below): "❤️ Tunde sent a heart." / "🙏 Tunde said thank you." / "😊 Tunde smiled at this." /
    "🥺 Tunde was moved by this." / "🤝 Tunde is grateful for that too." (the sentences are
    approved, `reaction_from_partner_*`). Spoken as the sentence only. Not a live region, and
    nothing else changes: it does not announce itself when it arrives, so it is no "seen" signal
    beyond the one the owner accepted (ADR-0037 question 6, decision 10).
  - **Waiting and failing:** optimistic, as a favourite (a reaction is reversible): the choice
    shows at once; the caches (Today's, the Archive's) keep it only once the server answers
    `204`. A failure (offline included) puts the previous choice back and shows the Generic error
    line under the set, "That didn't work. Nothing was lost. Try again in a moment." A refusal
    that means the day moved (`404`, any `409`) reads the day again. A tap while one is on its
    way is ignored. Never queued offline: a reaction needs the network.
- **Owner rulings, 2026-11-09** (the six open questions of PR #50), and how each is applied:
  1. **An emoji and the word on each chip.** The words are approved. The emoji are PROPOSED
     (`reaction_emoji_*`): HEART ❤️ (U+2764 U+FE0F), THANK_YOU 🙏 (U+1F64F), SMILE 😊 (U+1F60A),
     MOVED 🥺 (U+1F97A, pleading face; swapped from 🥹, Emoji 14, at the owner's word so
     older Android phones do not draw an empty box), ME_TOO 🤝 (U+1F91D, handshake). They are set
     in the system face (`FontFamily.Default`), so the platform's colour emoji font draws them
     by fallback: Apple Color Emoji on iOS, Noto Color Emoji on Android (Compose's bundled
     EmojiCompat brings newer emoji to older Android versions that have Google
     Play services). The emoji is never in a chip's spoken label.
  2. **The five partner sentences are approved**, as are the five chip words; their `strings.xml`
     comments say so. The emoji, `reaction_prompt`, and `reaction_take_back`
     stay PROPOSED.
  3. **The set is on the reveal screen too**, under the partner's entry as on Today. It is laid
     out with the after frame from the turn, so nothing moves when it appears, but it is
     invisible, silent to screen readers and ignores taps until the turn's motion has settled
     (`RevealMotion.RevealMillis`, the longer of the two transitions). Then it fades in over
     `MoyiMotion.CrossFadeMillis` (opacity only, no movement), or appears at once under Reduce
     Motion / Remove animations. A tap goes to the daily loop as Today's does; the moment does
     not settle into Today while a reaction is on its way (it waits for the answer, so leaving
     cannot cancel the call). The reveal still settles on its own after its hold.
  4. **Solo day: no reactions**, as built.
  5. **No set on an ended bond.** The status comes from data the app already has: Today's day
     carries its bond's status from `GET /bonds` (the domain `Bond`, mapped from the generated
     `BondResponse`), or from the cached day offline (`cached_day.bond_status`). The Archive list
     keeps the status it was loaded with, and the day view hides the set when that status (or
     Today's) does not take writes. "Takes writes" is the server's own rule
     (`BondMembership.takesWrites`): `PENDING_MEMBER` or `ACTIVE`. So the set is hidden on
     `ARCHIVED` and `DELETED`, and also during the deletion cooling-off (`PENDING_DELETION`),
     where the server refuses a reaction with `409 BOND_ARCHIVED` just the same. The same rule
     now gates Today's and the reveal's set (`DayView.canReact`). Unknown status (nothing loaded,
     nothing cached): the set is drawn as before. The partner's reaction on my entry is a read,
     so it stays shown on an ended bond.
  6. **The partner's reaction is more visible.** Proposed treatment, the interim `ReactionBadge`:
     a pill (`shapes.full`) in `surfaceRaised` with an `outline` hairline (the EntryCard's own
     pair, so it belongs to the card above it, and not `outlineStrong`, which marks a control:
     this does nothing when tapped), padded `md` across and `sm` down, the emoji then the
     sentence in `body` and `ink` (the old line was `inkMuted`), `sm` apart, wrapping and never
     cut at large text. No new colour. Spoken as the sentence; not a live region, no announce on
     arrival (ADR-0037 decision 10 stands).

## Voice waveforms and Today's action pair (owner request 2026-11-09)

The owner asked for voice notes to look like the voice messages people know (WhatsApp, iMessage,
Telegram) in Rosewood's quiet manner, and for Today's waiting note to stop stacking its two
buttons. Nothing below is in Figma, `tokens.md` or `states.md`. All of it is PROPOSED.

### 46. A voice note drawn as a waveform (`VoiceWaveform`)

- **Today:** not drawn. §43 and §44 drew a play target and a clock, and listed "a scrubber or a
  waveform" as an open question.
- **Here (PROPOSED):** rounded vertical bars, 3 wide with 2 between, at most 32 tall and at
  least a 3 dot, centred in a 48 tall band that is the touch target. Bars already played are
  `ink`, the rest `outlineStrong`: **Rosewood has no accent** (tokens.md §1), so the split is the
  value step from `outlineStrong` to `ink`, not a hue, and `outlineStrong` is the token measured
  at 3:1 for a graphic that carries meaning. Colour is never the only cue: the clock beside it
  says the position in words.
  - **Seeking:** tap anywhere in the band to move there; drag and the bars follow the finger,
    and the player moves when it lifts. Before the first play a seek fetches nothing: play then
    starts there. Seeking never starts or stops playback. Right to left, it plays from the right.
  - **Accessibility:** one node, spoken "Position in the voice note" (PROPOSED
    `ds_voice_position`; in the composer, "Your voice note, 0:42") with the value "0:12 of 0:42",
    adjusted as a slider (`setProgress`). The clock is not read a second time.
  - **The row (replaced by §68, one line):** play/pause (48), the waveform filling the row, and under the bars the clock in
    `inkMuted`, "0:12 of 0:42" (how far of the length; "0:00 of 0:42" before the first play).
    Under the bars, not beside them, because "0:12 of 0:42" beside the bars left the composer's
    listen-back (play, bars, clock, Record again, Remove) about 30 for the bars on a 320 phone.
    "Getting the voice note" and "couldn't be played" stay as lines under the row.
  - **The shape:** in the composer, the levels measured while recording (§47). On an entry, the
    server sends none (backend-contract-gaps row 31), so the bars are **a stand-in shape seeded
    by the media id**: the same each time, the same on both platforms, gentle (between 0.2 and
    0.8), and honest about length and position only. It becomes the real shape when the
    backend sends levels, with no change to the screen.
- **Owner rulings 2026-11-09 (PR #53):** (1) **keep the stand-in shape** for a voice note from
  the server; it is replaced by the real peaks when the backend sends them (backend-contract-gaps
  row 31). (3) **The clock reads "0:12 of 0:42"** while playing or paused, "0:00 of 0:42" before
  the first play (`ds_voice_progress`). Still open: (2) `ink` and `outlineStrong` for played and
  unplayed.

### 47. The live waveform while recording (`LiveWaveform`)

- **Today:** §44's "Recording 0:12 of 1:00" and nothing that moves with the voice (the owner did
  not like a bare counting timer).
- **Here (PROPOSED):** the row of §49 (trash, these bars, the recording dot and "0:12 of 1:00"
  in `inkMuted`, the tick). The bars move **from the first frame** the row is shown (owner ruling
  2026-11-09): each 80 ms of recording is a bar whether or not its level has arrived, and a
  quiet bar never drops below a gentle idle ripple (3.5 to 6.5 of the 32 band), so the row is
  visibly listening before anyone speaks. The newest bar slides in smoothly, frame by frame,
  drawn in the draw phase only. The idle ripple is drawn, never kept: listening back shows the
  real levels. The level is measured, not drawn for show: Android polls
  `MediaRecorder.getMaxAmplitude`, iOS reads `AVAudioRecorder.peakPower` with metering on, both
  every 80 ms, on one decibel scale (-50 dB and quieter is silence). The newest bar is at the
  end and the older ones scroll off the start. The levels are kept with the recording (on the
  phone only, never sent) and become its listen-back waveform (§46).
  - **Reduce Motion** (iOS) or **Remove animations** (Android): nothing scrolls. The band stands
    for the whole minute; each bar appears in its place, left to right, and the time still to
    come is a row of `outlineStrong` dots, so the limit is visible too.
  - **Accessibility:** the bars are decorative; the clock is spoken "Recording 0:12 of 1:00".
- **Owner ruling 2026-11-09:** (1) the recording mark is a `danger` dot (§49). Still open: (2)
  hold to record (§44 question 2).

### 48. Two outlined actions side by side (`SecondaryActionPair`)

- **Today:** Today's waiting note stacked "Edit what you wrote" and "Delete what you wrote" as
  two full-width outlined buttons, 8 apart (owner ruling 2026-11-06). The owner found the stack
  wasteful and asked for them side by side if that passes a UX check.
- **The check:** two equal buttons, 8 apart, on a 320 wide phone (288 inside the 16 gutters)
  are 140 wide each; less the 32 padding each side, the label has 76. "Delete what you wrote" in
  `body` needs about 160, so side by side **with those labels it wraps on every phone width**,
  even at 390. Short labels fit: "Edit" and "Delete" need about 30 and 50. (Tested on the iOS
  simulator: `SecondaryActionPairTest`.)
- **Here (PROPOSED):** "Edit" and "Delete" (PROPOSED `today_edit_short`, `today_delete_short`)
  side by side, equal width, Edit first, Delete in `danger`, both 48 tall. The note above
  ("Written." and "Yours is saved...") already says whose entry they change, and each is
  **spoken** in full, "Edit what you wrote" and "Delete what you wrote", which contain the
  visible word (WCAG 2.5.3, label in name). When either label does not fit in half the width
  (a large font, a long translation, a narrow window) both stack full width as before, so a
  label never wraps inside a half-width button and no target shrinks. Measured, not guessed:
  the layout compares each button's natural width with half the row.
  - **Not changed:** the single "Delete what you wrote" after the reveal on Today (a lone
    full-width button, nothing to sit beside) and the Archive day's delete link (an underlined
    `TextLink`, alone). Both keep the full label.
- **Open questions for the owner:** (1) the short labels; (2) whether `copy.md` should record
  them as the waiting note's actions.

### 49. The recording row: discard, recording mark, keep (owner ruling 2026-11-09, PR #53)

- **Today:** §47 drew a stop target, the live bars and the clock. The owner asked for the layout
  people know from WhatsApp, with one difference: the end of the row keeps the recording, it
  does not send it, because a voice note is part of an entry and the entry is still sent with
  the composer's Send.
- **Here (PROPOSED):** in one row, start to end:
  - **Trash** (48 target, 24 glyph in `ink`, spoken "Delete this recording", PROPOSED
    `ds_compose_discard_recording`): stops and throws the recording away **at once, with no
    confirmation**. An unsent recording is not irreversible in `states.md`'s sense (§4, "Delete
    before reveal: free, no confirmation"; §3, "confirm the irreversible, only"), so nothing
    asks. The recorder is cancelled, not stopped: nothing is kept, and the voice slot is empty
    again. The glyph is `ink`, not `danger`, because `danger` is the destructive confirm.
  - **The live waveform** (§47), filling the row.
  - **The recording mark**: an 8 dot in **`danger`** before "0:12 of 1:00". It breathes on
    `motion.breathe` (900 ms each way, alpha 1.0 to 0.55, tokens.md §3a) and holds still under
    Reduce Motion or Remove animations. The dot and the clock are one node to a screen reader,
    "Recording 0:12 of 1:00". **Note against tokens.md §1:** `danger` is reserved for
    destructive confirmation; the owner ruled it for this mark. `danger` on `surface` measures
    6.54:1 dark and 5.57:1 light, above the 3:1 a graphic needs. If `tokens.md` should record
    this second use of red, that is a design-system change.
  - **Tick** (48, `ink`, spoken "Stop and keep the recording", PROPOSED
    `ds_compose_keep_recording`): stops and keeps it; the listen-back row (§46) takes its place.
    A tick, not a send arrow, because it does not send.
- **Icons:** Lucide `check` was already in `MoyiIcons` (`Check`). Lucide **`trash`** (v1.40.0,
  three paths, verbatim) is **added, PROPOSED**: `MoyiIcons.Trash`. `MoyiIcons.Stop` (Lucide
  `square`) is removed: nothing uses it now.
- **Open questions for the owner:** (1) whether `tokens.md` §1 should name the recording mark as
  `danger`'s second use; (2) the spoken names "Delete this recording" and "Stop and keep the
  recording" for `copy.md`.

## Deleting before the reveal (owner ruling 2026-11-09)

### 50. Delete before the reveal asks first

- **Today:** `states.md` §4 (the row "Delete before reveal | Free. **No confirmation**, it is
  not irreversible yet", line ~386, and the paragraph under it, line ~397) and `SKILL.md` §3
  ("confirm the irreversible, only") made the waiting note's Delete immediate.
- **Owner ruling, 2026-11-09:** "for a destructive action that is not reversible, we should have
  a confirmation for the user to know that this is the action they want to perform... ask if
  the user really wants to delete and what deleting means." The words are gone once deleted, so
  the delete is irreversible for the person who wrote them, even if the day is not.
- **Built:** the Delete on Today's waiting note (`WaitingNote`, the Waiting layout, which also
  draws `PENDING_REVEAL`) opens the same `ConfirmDialog` as Delete after the reveal: same title
  (`delete_heading` "Delete what you wrote?"), same destructive confirm (`delete_confirm`
  "Delete", `danger` on Android, the destructive style on iOS), same dismiss (`delete_cancel`
  "Cancel", copy.md: bare "Cancel" dismisses a dialog). Only the message differs, because
  deleting means something else here: **PROPOSED** `delete_before_reveal_line` "This cannot
  be undone. You can write again today." (shortened at the owner's ask, 2026-11-09; the title
  already says what goes) Every other pre-reveal delete was checked: the Archive
  day's Delete is drawn only for a revealed day (`canDelete = revealed`), so it already asks.
- **"You can write again today" was checked against the backend**, not assumed: a pre-reveal
  `DELETE` erases the row and frees the day's BR-2 slot, and a new entry is accepted
  (`moyi-backend` `RevealTest`, "deleting on a PENDING_REVEAL day returns it to PARTIAL, and a
  rewrite follows the reveal rule again"); the partner is shown nothing of it (ADR-0032
  question 2). After the reveal the same rewrite is `409 DAY_CLOSED`, which is why the line is
  only used before it.
- **Not changed (cheap to redo, instant, as in WhatsApp):** the trash while recording a voice note
  (§49) and "Remove the voice note" / "Remove the photo" in Write. Nothing has been sent there.
- **Owed outside this repo:** `states.md` §4's row and paragraph, and `SKILL.md` §3's list of
  what is confirmed, need amending in the design system. Open question for the owner: the
  message string, for `copy.md`.

## Links that open the app (universal links and App Links)

### 52. Verify, reset and invite opened from a link

- **Today:** `states.md` §2 draws the invite side: "Join · code from link" is the prefilled
  code screen, a root with no back (`SKILL.md` §9), and the code resolves only after sign up or
  sign in and verify. Nothing draws verify or reset reached from the emailed link: the
  drawn verify screen (341:17) names the address it wrote to, and Choose a new password
  (345:30) signs in with the address typed on Forgot password. A link opened on a phone that
  never asked (the email read on another phone, or the app reinstalled) has neither.
- **Here:** the link fills the existing paste field (§6, §6a), so every outcome is a state
  the screens already have: verify checks it at once and lands on sign in ("Your email is
  confirmed"), the expired layout, or the field's "does not match"; reset waits for the new
  password and shows "That link has expired" under the field for a spent one. When the
  address is not known:
  - **Verify (PROPOSED copy):** the line drops the address, `verify_line_no_address` "Open the
    email we sent, copy the link and paste it below."; "Send it again" and "Use a different
    address" are not drawn (there is no address to send to or change). Expired says
    `verify_expired_line_no_address` "Links last 24 hours and work once. Sign in and we can
    send you a new one." and its action is "Sign in" (`login_action`, reused): signed in,
    the unverified gate sends a new link.
  - **Reset (PROPOSED copy):** the password is saved and sign in follows with
    `login_password_changed` "Your new password is saved. Sign in with it." It is shown too
    when the sign-in after a save fails, which until now landed on sign in with no word.
- **Signed in as someone else:** `states.md` and `copy.md` say nothing. What this PR does:
  an invite opens the prefilled code screen for whoever is signed in, and the confirm step
  names the inviter and the bond before anything is accepted (`states.md` §2, "Why there is
  a confirm step"), so a wrong account can back out. A verify link confirms the address it
  was sent to (the token is the proof) and returns to the signed-in start, which changes
  nothing for the signed-in account. A reset link opens Choose a new password; "Save and
  sign in" signs in with the signed-in address, so for another account's link the save
  succeeds and sign in follows, with the line above.
- **No new component.** Every screen is an existing one; no spinner while a link is checked
  (the action says "Checking", `states.md` §1c).
- **Open questions for the owner:** (1) the three strings; (2) whether a reset link opened
  while signed in should say whose password it changes; (3) §6 and §6a asked whether the
  paste fields go "when deep links land": they stay here, as the fallback for an email read
  on a computer and for the typed six-digit code the backend now sends (row 20 of
  `backend-contract-gaps.md`).

## Typed codes from the email (backend #155, gaps row 20)

### 58. One code field for the six digits or the link

- **Today:** the verify and reset emails carry a link **and** a six-digit code ("Or type this
  code in the app. It works for 15 minutes", backend `VerificationEmailComposer`, read only).
  `states.md` §1 draws verify with no input and Choose a new password (345:30) with only the
  new password; §6 and §6a added a paste field for the link's token, and §52 kept it as the
  fallback for an email read on a computer.
- **Here: one field, not two.** The paste field becomes the code field, and it takes either:
  six digits typed or pasted, or the whole link pasted. A paste containing `token=` is a
  link and is sent as the link would be when opened; anything else is reduced to its digits,
  six at most (so "123 456", "123-456" and "Your code: 123456" all work, and an Arabic or
  Devanagari keyboard's numerals become ASCII). Why one: a person holding an email does not
  know, and should not have to know, which box a link goes in and which a code goes in; one
  field keeps one label, one error slot and one focus order, and the link case costs one
  `token=` check. Two fields would put two empty inputs on a screen whose job is one thing.
- **Component:** the design system's `Field` `Code` (`tokens.md` §4): one control in the
  code style, not six boxes (six boxes announce as six controls, break paste and fail at 200 %
  text). No new component. The keyboard is the number pad (`NumberPassword`) and the field is
  marked a one-time code for autofill (`ContentType.SmsOtpCode`, iOS `.oneTimeCode`), so iOS
  can offer a code it finds in Mail above the keyboard. Its name for a screen reader is its
  label, "6-digit code"; a refusal is the field's error and a polite live region while it
  shows, so a refusal that arrives with no button pressed is spoken.
- **Verify:** the sixth digit checks the code at once, with `PrimaryAction` in Sending
  ("Checking"); no spinner. A refused code is not sent again until it changes (each wrong try
  spends one of five). "Check again" sends what is typed; with fewer than six it says so.
  "Send it again" clears the field: a newer email voids the old code.
- **Choose a new password:** the code field comes first, as §6a placed the paste field. The
  sixth digit moves focus to the new password and sends nothing: the server checks a reset
  code only with the new password (gaps row 39). A link that opened the app (§52) or was
  pasted is held as the proof and the helper says so; typing a digit makes the code the proof.
- **PROPOSED copy** (all in `strings.xml`, marked PROPOSED):
  - `code_label` "6-digit code"; `code_helper` "It is in the email we sent. You can paste the
    whole link here instead."
  - `code_error_blank` "Type the 6 digits from the email first."; `code_error_short` "A code
    has 6 digits. Check the email for the rest."
  - `code_error_invalid` (422 `VERIFICATION_TOKEN_INVALID` / `PASSWORD_RESET_TOKEN_INVALID`
    for a code), **approved 2026-11-09**: "That code did not work. Check the digits, or send a
    new email." The server gives one answer for wrong, expired, too many wrong tries and
    spent, on purpose (a distinct answer would say which addresses hold a live code), so the
    sentence names the way out for all of them, without the numbers. **There is no separate
    "too many attempts" message because the server never says so.**
  - `code_error_link_invalid` (422 for a link): "That link did not work. Copy it again from
    the email, or type the code."
  - `code_error_no_address`: "A code needs the address it was sent to. Open the link in the
    email instead." Shown when the screen was opened by a link on a phone that does not know
    the address (§52): a code alone names no account.
  - `code_error_rate_limited` (429 on a code: ten a day per address, ADR-0023 amended
    2026-11-08), above the action: "That is a lot of codes for one day. Open the link in the
    email instead, or try again tomorrow." A link's 429 keeps `auth_error_rate_limited`.
  - `code_error_unavailable` (503 `CODE_CHECK_UNAVAILABLE`), above the action with a retry:
    "We cannot check codes just now. Open the link in the email, or try again in a minute."
  - `reset_link_ready`, the helper while a link is the proof: "The link from the email is
    ready. Choose your new password."
  - Rewritten (were approved interim lines that said to copy and paste the link):
    `verify_line` "We sent a 6-digit code to %1$s. Type it below, or open the link in the same
    email."; `verify_line_no_address` "Open the link in the email we sent on this phone, or
    paste it below."; `reset_sent_line`, **approved 2026-11-09**: "If that address has an
    account, we sent it a 6-digit code. Type it below with your new password." (still
    identical whether or not the account exists).
- **Removed:** `verify_token_label`, `_helper`, `_error_blank`, `_error_invalid` (§6, §6a),
  replaced by the `code_*` strings above.
- **Owner ruling, 2026-11-09:** (1) the invalid-code sentence drops the numbers (15 minutes,
  5 tries): they are backend defaults and would drift if configured. `code_error_invalid` and
  `reset_sent_line` are approved as written above. (2) The expired layout's action names the
  email, since the new email carries a code too: `verify_expired_action` "Send a new email"
  (was `copy.md`'s "Send a new link") and `verify_expired_action_sending` "Sending a new
  email", approved. (3) Choose a new password stays one screen (the code field above the
  password) until the backend can check a reset code alone (gaps row 39); a two-step flow is
  reconsidered then. The other `code_*` strings and `reset_link_ready` stay PROPOSED.
### 56. The launch splash animates the logo (owner request 2026-11-09)

- **Today:** `states.md` §0: "Splash: the wordmark on `surface`. Nothing else. **No motion.**"
  iOS showed the wordmark (a PDF drawn by `scripts/render-wordmark.swift`), Android 12+ the
  mark, and the first Compose frame was an empty `surface`, so iOS's wordmark vanished at the
  hand-over.
- **Owner request:** "a bit of luxury animation on the splash: the two big circles of the
  logo, one coming first, the other on it, then the text; not too much." This amends §0's
  "No motion", as the 2026-11-08 ruling (§33) amended `SKILL.md` §5.
- **Here:** both native launch frames are `surface` alone and static (iOS `UILaunchScreen`
  keeps only its colour; Android 31+ gets a transparent splash icon), and the first Compose
  frame starts as that same surface. Over it the mark (96 high, `ink`) builds: the ring fades
  in and grows from 92 % (0 to 300 ms), the dot fades in and settles into place under it (220
  to 520 ms), both on `motion.push` (300 ms, `FastOutSlowIn`); the wordmark (Newsreader
  Medium, 48, 24 below the mark) fades in and rises 8 (`spacing.sm`) from 320 to 670 ms,
  over the dot's last 200 ms (350 ms, `LinearOutSlowIn`; owner ruling 2026-11-09, PR #56: it
  was easy to miss); logo and wordmark hold together to 1100 ms and fade out together into
  the app (`motion.crossFade`, 200 ms). The app composes underneath
  from the moment the session is known. **Hand-off** (`SplashTimeline.handOff`): signed out,
  the splash plays out (1.3 s in all); signed in, it waits until the first screen has drawn
  something (the cached day, Today's first answer, or another screen; never a bare surface)
  and leaves at most 400 ms after that, playing the rest of the entrance faster when it must. Reduce Motion / Remove
  animations: the finished lockup, still, under the same rule. Decorative: screen readers
  hear nothing from it, and it swallows touches while it shows.
- **Removed:** the iOS wordmark PDFs and their script, Android's `splash_mark.xml` and the
  `ink` colour resource that only it used.
- **Open for the owner:** `states.md` §0 still says "No motion" and "the wordmark on
  `surface`"; the lockup (mark above wordmark, 96 / 24 / 48) is not drawn in Figma.
## Reporting (FR-100, ADR-0053)

### 53. The report flow, its reasons, and where it starts

- **Today:** Figma's Safety section draws `Report · choose target` (607:1795), `compose` (599:1782,
  with `KEYBOARD UP` 607:1825), `sent` (608:1811) and `failed · INPUT KEPT` (608:1841), and the
  Archive day view's header action (617:1835). `26` §3.2 puts entry reporting there and only
  there: "a report control cannot live" on Reveal, and never under an entry. The copy is
  `strings-proposed.md` §10, "IN FILE, not decided".
- **Built as drawn:** the day view's **Report** at the end of the back row (a `TextLink`, its own
  48 target; `MoyiScaffold` gains a `backAction` slot for it), shown once the other member has a
  name. It opens choose target when the day shows the partner's words (their entry, read in full),
  else the form about the member. My own entry is never offered, and nothing on Today or the
  reveal offers a report. The form is `Field` Select for the reason and `Field` **Multiline** for
  the detail: Multiline is `tokens.md` §4's fifth type (box 140, top-aligned, no trailing icon),
  built here for the first time. A failure keeps the form and puts the error line 8 above Send.
  Sent is a root with one action, back to where the report started (SKILL.md §9).
- **Not drawn, PROPOSED:**
  - **The reasons.** The contract takes five (`NON_CONSENSUAL`, `MINOR_SEXUAL_CONTENT`,
    `VIOLENCE_OR_SELF_HARM`, `HARASSMENT`, `OTHER`); §10 drew four others (contract gap row 36).
    Words: "How they’re treating me" (§10's `treating`, for `HARASSMENT`, as ADR-0053 maps it),
    "Something shared without my consent", "Violence, threats or self-harm", "Sexual content
    involving a child", "Something else" (§10's `other`). The label is "Why are you reporting
    this?", not §10's "What are you reporting?", which repeats choose target's heading and asks
    what rather than why. Before a choice the field reads "Choose one". ADR-0053 question 4 gives
    these words to the owner.
  - **The reason picker.** The `Field` Select opens a pushed list of the five, each a settings
    row; a tap chooses and returns. The time-zone picker's shape. Not drawn anywhere.
  - **Bond settings › Report {partnerName}**, in Ending after Block and leave, underlined, not
    `danger` (it ends nothing), straight to the form about the member. The design reaches the
    member only from a day view; this row exists so a member with no day to open (or who is
    reporting how they are treated, T-09, which is about no one day) can still reach it. It stays
    enabled during the cooling-off: a report is never contact (ADR-0053 decision 14).
  - **Sent, for an entry:** a second line, "If that entry has a photo or voice note, it stays
    hidden from you until a person has looked at it." True by ADR-0053 decision 4 (held back from
    the reporter at once). Without it, the photo turning to "processing" reads as a fault.
  - **The errors:** no reason ("Choose a reason to send this."), a detail over 500 ("Keep it to
    500 characters or fewer."), `429` ("Reports are paused for a while. Your report is still
    here, and the ones you already sent are with us."), `404` ("This can no longer be reported
    from here. Nothing was sent."), `409 REPORT_ON_OWN_ENTRY` (a guard: "That entry is yours, so
    it cannot be reported. Nothing was sent."). Sending reads "Sending report".
- **Deliberately not built:**
  - **Block and leave on the sent screen.** ADR-0053 names leaving or blocking as the reporter's
    remedy, but the drawn sent screen carries one action, and SKILL.md §9 rules a second out on
    acknowledgement screens. Block and leave stays one row away in Bond settings. Whether sent
    should mention it is the owner's question.
  - **A partner tombstone.** The server lets a tombstone be reported (ADR-0053 decision 2), but
    this day view does not draw the partner's tombstone, so it offers only the member then.
  - **After the bond ends.** The archived screen links nowhere (§31), so neither the day view
    nor Bond settings, and so no report, is reachable once a bond has ended, though the server
    allows it then (ADR-0053: "a member who has just blocked their partner may need it most").
    Owed with the archived screen's link to the Archive.
- **Owed:** `KEYBOARD UP` (607:1825) is the scaffold's ordinary keyboard behaviour, not measured
  against the frame. Neither TalkBack nor VoiceOver has walked the flow.
## Terms and Privacy (legal consent, 2026-11-09)

### 54. Terms and Privacy: the re-consent step and the settings row

- **Today:** `states.md` §1 row 2 puts "18+ confirmation, Terms and Privacy consent (FR-011)"
  on Sign up, and `SKILL.md` §12 says the documents are "versioned with recorded consent"
  (FR-103). Nothing in `states.md`, `copy.md` or Figma draws what happens when the documents
  change after someone accepted them, or where a signed-in person reads them again.
- **Built (PROPOSED), from the house parts only:** one screen, `LegalScreen`
  (feature:auth `legal/`), in two modes.
  - **The re-consent step.** The shell opens it over Today, once per launch, when `GET /me`'s
    `legalAccepted` differs from `GET /meta/legal`'s Terms or Privacy version. Back target,
    ScreenHeader (heading "Our Terms and Privacy Policy have changed"; its eyebrow,
    "Terms and Privacy", is not drawn under a back target, D1), a `body` `inkMuted` line naming the next step, the three documents as
    TextLinks, then PrimaryAction **Accept** (Sending "Accepting") with **Not now** as a
    centred TextLink under it. Not a gate: the backend refuses nothing for an old consent
    (`docs/backend-contract-gaps.md` row 37), so blocking the app would be the client
    inventing a rule.
  - **The read view, from Bond settings.** A new row, **Terms and Privacy**, in *Just you*
    between Notifications and Sign out (`RowKind.Navigates`, underlined). Also on the failed
    load, beside Sign out, since it needs nothing the bond holds. Same screen, heading "What
    you agreed to"; Accept appears only if the consent is out of date, and the screen stays
    open after it.
  - **Loading** is the bare `surface`; a failed load is the Generic error with Try again.
  - **Offline:** the check runs only once Today shows with its day in (the cached day opens
    straight to Today, owner ruling 2026-11-08); a check that cannot be made shows nothing
    and is tried again on the next foreground.
- **Sign up:** a link tapped before `GET /meta/legal` could be read shows an InlineError under
  the two links (`register_legal_unavailable`).
- **The data-processing summary** is the third link ("How we handle your data"): `/meta/legal`
  serves it and says it is "shown, never accepted on its own". Sign up still shows only the
  two Figma draws.
- **Strings, all PROPOSED** (feature:auth `legal_*`, `register_legal_unavailable`; feature:bond
  `settings_row_legal`): eyebrow "Terms and Privacy"; headings "Our Terms and Privacy Policy
  have changed" and "What you agreed to"; lines "Read them below, then tap Accept to agree to
  the new versions. Your entries stay exactly as they are." and "You have accepted the current
  versions. Tap one to read it."; "How we handle your data"; "Accept" / "Accepting"; "Not now";
  "That didn't go through. Check your connection, then try again."; "That page didn't open.
  Check your connection, then tap the link again." The failed load reuses the Generic error.
- **Owner questions:** (1) Is a non-blocking prompt right, or should a changed Terms block
  until accepted (needs a backend refusal code first)? (2) Is *Just you* the right group for
  the row, or does it belong on the unbuilt Account screen (`strings-proposed.md`
  `settings.row.account`)? (3) The heading and line need a human pass for `copy.md`'s voice.
## Contact support (FR-102)

### 55. Get help: where it opens from, and the form's undrawn parts

- **Today:** `strings-proposed.md` §9 has Support's copy (IN FILE, not decided) and `SKILL.md`
  lists 14 Support screens plus `Account 7 · settings`, whose `account.help` "Get help" opens
  Support. Figma's Support section was not read for this PR. The Account screen is not built
  (Bond settings shows Sign out in its place in Just you).
- **Built:**
  - **Bond settings · Just you → Get help** (`settings_row_help`, `account.help`'s words), a
    navigating row between Notifications and Sign out, on the loaded root and on the failed
    load (it needs nothing from the bond). **PROPOSED placement**: in place of the Account
    screen's row, until that screen exists.
  - **The Generic error on the signed-in start → Get help**, a second `TextLink` under Try
    again (`generic_error_get_help`). `StateBlock` gained the second action that its Waiting
    variant already has (`tokens.md` §4), the same gap apart. **PROPOSED**: the Generic error is
    drawn with one action. It is offered only there, the one Generic error with no other way
    out (no back, no settings). Today's, the Archive's and the Streak's errors keep one action.
  - **Support · form**: back, eyebrow Support, heading Get help, `support.privacy` as a muted
    line, `Field · Text` Subject, `Field · Multiline` Message (the variant `tokens.md` §4 added
    for this body; the same code as PR #58's, so the two merge cleanly), and Send, which says
    **Sending** (PROPOSED word) while the request is out. A send that did not go is an
    `InlineError` above the action (`support.failed.error`) and the action reads Try again
    (`support.failed.retry`). The placeholders `support.subject.placeholder` and
    `support.message.placeholder` are not shown: `Field` has no placeholder slot.
  - **Support · sent** and **Support · paused** (`429`): a centred `StateBlock` with one action,
    no header and no back (`SKILL.md`, acknowledgement screens). The action is
    `support.back` "Back to settings" from settings, and **PROPOSED** "Go back" from the
    Generic error, where "settings" would be untrue.
  - **PROPOSED field errors**, checked before sending against the contract's limits (120 and
    5000 code points): "Add a subject so we know what this is about.", "Tell us what is going
    on.", "A subject can be up to %1$d characters.", "A message can be up to %1$d characters.",
    and "Something in here can't be sent. Change it and try again." (a NUL or broken character,
    or a `422` naming the field).
- **Not built:** **Support · queued** (`support.queued`, "Saved. It'll send when you're back
  online."). It needs an outbox for support messages; offline is the failed state instead,
  with the words kept on screen (not across an app restart).
- **Open questions for the owner:** (1) Get help's place until the Account screen exists, and
  whether the Generic error should carry it; (2) `support.sent.line` promises "Usually within a
  day", which nothing in the backend guarantees (the acknowledgement email is best effort and
  goes only to a confirmed address); (3) the PROPOSED strings above; (4) whether offline should
  queue (`support.queued`) or stay a failure.
## Account: export and deletion (PR feat/export-and-deletion, 2026-11-09)

Every string below is **PROPOSED** unless it names its source in `strings.xml`.

### 57. Account, Export your archive, Delete your account

- **Today:** Figma draws `Account 7 · settings` (eyebrow "Account", heading "Your account",
  rows Get help and Delete your account, reached by Bond settings' **Account** row), an
  `Account deletion` screen whose words are not in the corpus, and `Account 8 · export ·
  REQUESTED`, an acknowledgement that promises the archive "arrives by email". The backend as
  built (e273a416) emails **no link**: the person downloads in the app, and no push is described
  (`backend-contract-gaps.md` row 32). Nothing draws a READY, EXPIRED or FAILED export, or the
  pending account deletion.
- **Here (PROPOSED placement):** Bond settings › Just you › **Account** (between Notifications
  and Sign out, also under the failed-load error, because it needs nothing from the bond) →
  **Your account**, with two rows:
  - **Export your archive** (the drawn label from the ending screens, placed here) → one screen
    whose content follows the latest export: never asked / EXPIRED / FAILED offer **Prepare my
    archive**; PREPARING is the acknowledgement (no progress, nothing to watch; it polls quietly
    while open, backing off 3 s to 60 s, never in the background); READY gives the size, the day
    and time the download stops working, a line that the file is readable by anyone who has it,
    and **Download**, which opens the signed URL in the phone's browser (Safari or Chrome), not
    the in-app browser: a zip has to land in Files or Downloads. The URL is never drawn, logged
    or kept in saved state.
  - **Delete your account** (underlined in `danger`, value "Deleting on 23 November" while
    pending) → the confirm: what happens today (nothing), when (14 days), what goes and what
    stays for the other person, that it cannot be undone after, a link **Export your archive
    first**, the password field (the backend asks for it again), **Delete my account**
    (`danger`, primary: no friction on leaving) and Cancel. Once asked, the same screen shows
    **Your account will be deleted on {date}** with **Keep my account** as the primary, as
    Keep the box is during the cooling-off.
  - **Get help** was left out (no support screen yet), with no dead row; added since, with Terms and Privacy (§61).
- **Reachable with no bond (owner ruling 2026-11-09, "it should be moved"):** Account is its
  own navigation graph beside Bond settings, not inside it, so every signed-in person reaches
  it. Bond settings keeps its **Account** row (Figma 575:1782 draws it there).
- **Owner rulings 2026-11-09 (second round), on the placement first proposed here:**
  1. **No centred "Your account" link; an account target at the top right instead.** The
     screens a person with no open bond lands on, none of which can reach Bond settings, draw
     a person glyph (Lucide `user`, lucide-static v1.40.0, verbatim, its circle written as a
     path like every other glyph in `MoyiIcons`) in a 48 x 48 target, announced as **Your
     account** (`account.heading`'s words, a string resource). It pushes Account; back returns.
     - **Account · unverified gate** and **Bond · archived**: both roots with no back.
     - **Relationship type as the signed-in root.** Left out when it is pushed from the
       archived bond (back drawn): the rule stays, because the scaffold's top row there is the
       back target (a screen has a back target or its own top row, never both), and the
       archived screen one step back already has the account target.
     - **PROPOSED component: `TrailingNavRow`** (`designsystem/component/NavTarget.kt`). The
       design system has no top bar ("no persistent chrome"), so this is the closest existing
       thing, Today's `nav` row (Figma 159:2: 48 tall, 48 x 48 targets at the end, 24 glyphs
       in `ink`), with nothing at the start. Today's target is now the shared `NavTarget`, so
       both draw the same control. It sits in `MoyiScaffold`'s `header` slot, which already
       exists for a root screen's own top row; the eyebrow and heading move down by the row
       and its 16 gap (64), as on Today. Figma draws none of the three with it: owed a frame.
  2. **Sign out lives inside Account too.** Account gains a **Sign out** row (Bond settings'
     words, `RowKind.Acts`, "Signing out" while it runs), between Export your archive and
     Delete your account, and also under the failed load, because it needs nothing `/me`
     holds. It runs the very sequence Bond settings runs (`SignOut`, shared): one more try to
     send entries still waiting, the approved "Your entry hasn't sent yet" question if any are
     left, then the session ends and the shell routes to sign in. This is how a person on the
     three no-bond screens signs out. **Bond settings keeps its own Sign out row**: Figma
     draws it there (575:1782's group), and taking it away would add a step for everyone who
     has a bond.
  3. **Bond · archived draws "Export your archive"**, as its frames do (399:21, 614:1832;
     states.md §9): the centred secondary link under Start a new bond, which opens the export
     screen directly, with no Account under it; back returns to the archived bond.
- **Open questions for the owner:** (1) every string in the "Account (Figma Account 7 ·
  settings ...)" block of `feature/bond` `strings.xml`, for `copy.md`; (2) whether the
  export's ready line should name a time (it does: "until 10 November at 17:30") or only the
  day; (3) a Figma frame for the account target on the three no-bond screens.
### 59. The streak after a run ends, and a day a time zone change skipped (PR feat/streak-details)

- **Today:** `states.md` §7 draws four streak states (227:3, 401:70, 401:160, 401:250). §7a
  recorded two gaps the contract has since closed (ADR-0034 amendment): `lastRunLength` (the run
  the latest break ended) and a `FROZEN` cell's `reason` (`REST_DAY` or `TIMEZONE_SHIFT`).
- **Built, from a Figma line:** the ended footer now names the length, as 401:219 draws it:
  `streak_ended_at` "Your streak ended at %1$d days. That is %1$d days of showing up. No rest
  days were left." Shown only when `current` is 0 and `lastRunLength` is not null (ADR-0034: it
  stays set while the next run grows). **PROPOSED:** its `one` form ("ended at one day"), and
  the Strict-mode twin `streak_ended_at_strict` ("... Rest days are off.").
- **Built, PROPOSED copy, no frame:** when yesterday is `FROZEN` because an approved change of
  the shared time zone stepped over it (BR-6, `04` §8.5), and a run is going on:
  `streak_timezone_shift` "Yesterday was skipped when your shared time zone changed, so there
  was nothing to write. Your streak carries on." It replaces "We used a rest day", which would be
  false (no rest day is spent) and would imply someone missed a day. It wins over "Rest days are
  off." in Strict mode, where a frozen yesterday under that line would read as a contradiction.
  With no run going on it is not said ("carries on" would be untrue); the ordinary line shows.
- **Built, PROPOSED copy:** a screen reader hears such a cell as `streak_cell_timezone`
  "%1$s, skipped when the time zone changed" instead of "rest day".
- **Not designed, raised rather than improvised:** the cell keeps the Rest day shape
  (`DayCellState.Frozen`) and the key still calls that shape "Rest day". A distinct shape and
  key entry for a skipped date would be a fifth (sixth, with Solo) calendar state with its own
  3:1 check. Related, already true before this PR: in Strict mode the Rest day key is hidden
  (401:296) though a past rest day or a skipped date can still be in the window (FR-073).
- **Neutral fallback:** a `FROZEN` cell with no reason, or a reason added after this build, is
  drawn as a rest day and gets the ordinary footer: nothing is said about why.
- **Owed outside this repo:** `states.md` §7/§7a rows for these two lines, and the copy for
  `copy.md`.

## Editing an entry's photo and voice note (PR feat/edit-replace-media)

### 60. Edit before the reveal can replace the photo or voice note, never remove it

- **Today:** Write in Edit offered no media (`WriteState.photosOffered` and `voiceOffered` were
  false while editing) and the PATCH carried the words only. `states.md` §4 says "Edit before
  reveal: free, no warning, no friction", and nothing drawn says the media are fixed once sent.
- **The backend** (`EntryChangesController`, `ChangeEntry.MediaChange`) takes `imageMediaId` and
  `voiceMediaId` on `PATCH /entries/{id}`: given, it attaches that upload in place of the
  entry's (or adds one to an entry with none); absent, it keeps the entry's. It cannot take one
  off (docs/backend-contract-gaps.md row 41).
- **Built, interim, not drawn:**
  - Edit shows the entry's own photo (`SentPhoto`, the §41 preview at 96 tall, from the
    server's signed URL) and voice note (`SentVoice`, the §44 recorded row playing from the
    server, its waveform the §46 placeholder) inside the composer's box, where a new one would
    sit. Neither has a Remove. The photo carries a 48 **Replace** target (the `Image` glyph,
    spoken "Replace the photo") that opens the same picker as Add a photo; the voice note
    carries **Record again** (the `RecordAgain` glyph, the same name as §44) that records with
    the §49 recording row. While the entry's own photo is shown, the toolbar does not also
    offer Add a photo; while its own voice note is shown, it does not offer the microphone.
  - A new photo or recording takes the slot exactly as in a new entry. Its remove control (and
    the trash while recording) throws only the new one away and puts the entry's own back; on
    a slot that had one, that control is spoken "Keep the photo you had" / "Keep the voice note
    you had" instead of "Remove the photo" / "Remove the voice note".
  - At Save the new media upload as at Send, the photo first, with the same action states
    (`write_sending_photo` "Sending your photo", `write_sending_voice` "Sending your voice
    note"), then "Saving" while the PATCH goes with the new ids by name. Failures are the
    §40 and §44 ones: Save failed keeps everything and Try again resumes the upload; a refused
    photo or voice note says so above the action and must be removed (which puts the entry's
    own back) before the words can go.
  - An entry with no photo or voice note can add one in Edit the same way.
  - **Offline:** an edit is never queued (unchanged), with or without media: it needs a
    connection, and without one it is Save failed with the words and the new media kept.
- **PROPOSED copy** (spoken names, icon only, `ds_` strings): `ds_compose_sent_photo` "The photo
  on your entry"; `ds_compose_replace_photo` "Replace the photo"; `ds_compose_sent_voice` "The
  voice note on your entry"; `ds_compose_keep_sent_photo` "Keep the photo you had";
  `ds_compose_keep_sent_voice` "Keep the voice note you had".
- **Open questions for the owner:** (1) the five spoken names above, for `copy.md`; (2) whether
  Edit should say in words that a photo or voice note cannot be taken off yet, or whether its
  missing Remove is enough until the backend adds one (row 41); (3) the `Image` glyph for
  Replace, the same as Add a photo, since no swap glyph is drawn; (4) the library or camera
  question (§41) keeps its title "Add a photo" when it is opened by Replace (answered in §61: it now says "Replace the photo").

## Follow-ups from the integration smoke test (2026-11-09, PR fix/smoke-followups-2026-11-09)

### 61. Four fixes the smoke test found after PRs #56 to #69

- **Account carries Get help and Terms and Privacy.** A person with no bond reaches Account
  only through the account icon (§57) on the unverified gate, relationship type and the
  archived bond, and Bond settings, which held both rows (§54, §55), is out of reach. Account
  now shows **Get help** (the drawn row of `Account 7 · settings`, strings-proposed.md §9
  `account.help`) and **Terms and Privacy** (Bond settings' `settings_row_legal`) between
  Export your archive and Sign out, also under the failed load. They reuse Bond settings'
  strings and open the same screens: Support and `LegalRoute`, pushed over Account. Support
  opened from Account says **Go back** (`support_back_generic`, PROPOSED in §55) when done,
  not "Back to settings", since a person with no bond has no settings behind it
  (`SupportOrigin.Account`). Bond settings keeps both rows, so a person in a bond sees them
  twice (in Just you, and one tap deeper in Account). **Owner ruling 2026-11-10:** keep them
  in both.
- **Forgot password names the code.** Since §58 the reset email carries a 6-digit code and a
  link, and "Check your email" already says so (`reset_sent_line`, approved 2026-11-09). The
  screen before it still promised a link. **Approved 2026-11-10** (owner ruling), replacing
  the 2026-11-05 words:

  | Key | Was | Now |
  |---|---|---|
  | `reset_request_line` | Enter the email on your account. If there is one, a link is on its way. | Enter the email on your account. If there is one, we will send it a 6-digit code to reset your password. |
  | `reset_request_action` | Send the link | Send the code |
  | `reset_request_action_sending` | Sending the link | Sending the code |

  The line still never says whether the address has an account (states.md §1b): it says
  what happens if there is one. `reset_token_error_expired` ("That link has expired") and
  `reset_link_ready` stay: only a link is ever answered 410, and the second is shown only
  when a link opened the screen.
- **The library or camera question says Replace when a photo is already there.** Answers §60's
  open question (4) the conservative way: the title follows what the pick does. **Approved
  2026-11-10** (owner ruling):
  `write_photo_source_title_replace` "Replace the photo" (the words of
  `ds_compose_replace_photo`, the Replace target that opens it), shown when the entry's own
  photo is in the slot (Edit) or a photo was already picked (`WriteState.replacingPhoto`);
  "Add a photo" otherwise. The answers (Choose a photo, Take a photo, Cancel) are unchanged.
- **A verify link opened by someone already verified is dropped.** Signed in and verified
  (the session says so, or a screen only a verified person reaches is on the back stack:
  onboarding, Today, the archived bond), a verify link has nothing left to do for this
  account. It used to open Verify, whose 410 (an old link is spent) showed the expired layout
  with "Send a new email" and "Use a different address". Now the link is consumed and the
  app comes to the front where the person is; on a cold start that is their own start
  (Today). A verify link for **another** account opened on this phone while signed in to a
  verified one is ignored the same way, so that account is not confirmed from here (it still
  can be with its code, or its link opened signed out). The client cannot tell the two apart
  without sending the token, and sending it is what produced the expired layout. **Owner
  ruling 2026-11-10:** that stays ignored. The same ruling adds a snackbar saying why nothing
  opened: §62.
- **Sign up's email helper names the code.** `register_email_helper`, "We will send a
  verification link.", is now "We will send a 6-digit code to confirm it." (**approved
  2026-11-10**, owner ruling).

### 62. A snackbar: "Your email is already confirmed." (owner ruling 2026-11-10)

- **Why:** a verify link opened by someone signed in whose address is already confirmed opens
  nothing (§61). Without a word the tap looks broken. The owner asked for a snackbar; the
  design system draws none, so this is the smallest one, built from Rosewood tokens only.
- **Built, PROPOSED component** (`MoyiSnackbar`, designsystem `component/MoyiSnackbar.kt`):
  - One line of `body` in `ink` on `surfaceRaised`, bordered by the hairline `outline`,
    radius 12, padding 16 (tokens.md §3: a floating surface is raised, never shadowed). Full
    width inside the gutter, 16 above where a screen's `PrimaryAction` sits (88 above the
    bottom of the safe area), so it never covers the main action. (First built at 24 above
    the safe area; the 2026-11-10 smoke test showed it hiding Today's Write for its 4 s.)
  - **No action button and no close control.** It goes away by itself after 4 seconds
    (`SnackbarShowMillis`, PROPOSED: no token sets how long a note stays), or longer when
    Android's accessibility timeout setting asks for more time
    (`LocalAccessibilityManager.calculateRecommendedTimeoutMillis`).
  - **Announced** through a polite live region, so TalkBack and VoiceOver read it without
    moving focus.
  - **Motion:** fades in over `motion.crossFade` (200 ms) while rising a quarter of its
    height on `motion.push`'s 300 ms `FastOutSlowIn`, and the reverse on the way out. With
    iOS Reduce Motion it only fades (`rememberReduceMotion`); Android's "Remove animations"
    scales both to nothing on its own.
  - Drawn by the shell over every screen and only after the launch splash has gone, so a
    link that opened the app cold is not told about under the splash. It takes no touches.
- **String:** `verify_already_confirmed` "Your email is already confirmed." (the owner's
  words; approved 2026-11-10).
- **When it shows:** only when `accountLinkRouting` answers `LinkRouting.AlreadyVerified`: a
  verify link, someone signed in, and the address confirmed (the session says so, or a screen
  only a verified person reaches is on screen or under it). Never for a reset link, never
  signed out, never at the unverified gate. `LinkRoutingTest` covers each case.
- **Owner rulings 2026-11-10:** (1) the 4-second stay is kept; (2) `MoyiSnackbar` stays
  single-use until a Figma frame draws a snackbar, and then becomes the design system's one;
  (3) `verify_already_confirmed` is **approved 2026-11-10** and goes into `copy.md` (the owner
  adds it in the parent repo); (4) a native VoiceOver announcement
  (`UIAccessibility.post(.announcement)`) is added only if the smoke test shows VoiceOver
  stays silent on the live region.

## The Queued line names the day, and the gate names the code (PR feat/queued-names-the-day)

### 63. Queued on an earlier Bond-day names it; the unverified gate offers the code

- **Why (Queued):** the owner held `today_queued_line_earlier` on 2026-11-08 (strings-proposed.md
  §12) until the backend kept a Bond-day open about 12 h past its end. Backend #154 shipped it
  (`settlesAt`, `LateEntryGrace`; backend-contract-gaps.md row 25, resolved), so an entry written
  offline on an earlier day and sent within 12 h of the Send tap now counts for that day.
- **The rule (`QueuedDay`, `LateEntryGrace` = 12 h, was the 36 h `OfflineWindow`):** if it sends
  now, which day does it count for? Written on the current Bond-day (read from the clock in the
  bond's zone, not from the day Today last showed): that day. Written on an earlier Bond-day and
  at most 12 h ago: that day. Older: the day it sends. `settlesAt` never comes first: the server
  trusts `intendedAt` for 12 h, and the day it falls on settles 12 h after its *end*, which is
  later. A refresh that fails offline works the line out again, so it changes when the grace runs
  out without a connection.
- **Strings (PROPOSED):**
  - `today_queued_line_earlier`: "It'll send when you're back online. It will count for {day}."
    The wording §12 said it would become; it reads exactly like `today_queued_line`, because the
    promise is the same one. `{day}` is `dayInSentence`, locale-aware ("Saturday, 7 November").
  - `today_announce_queued_earlier`: "Written for {day}. It will send when you're back online"
    (the pane title, read by TalkBack and VoiceOver). states.md "Queued offline" asks that an
    earlier day be announced; `today_announce_queued` stays for the current day.
- **Why (gate):** since #64 and #70 the verification email carries a 6-digit code as well as the
  link, and the owner approved "We will send a 6-digit code to confirm it." for sign up. The gate
  still said only "We sent a link to {email}", and offered no way to type a code, so a person
  reading the email on another device (where the link opens the website) had no way forward.
- **Built, PROPOSED placement:** a second centred `TextLink`, "Type the code", above "Send it
  again" in the gate's action block, 8 apart (`spacing.sm`). It pushes the existing verify screen
  for the session's address, signed in (`VerifyRoute(email, signedIn = true)`), the same screen a
  verify link already opens over the gate; a confirmed address returns to the signed-in start,
  and back returns to the gate. Hidden while the address is not known: a code alone names no
  account. Figma 345:46 draws one affordance; this adds a second from existing components only.
- **Strings (PROPOSED):**
  - `gate_line`: "You can sign in, but creating or joining a bond needs a verified address. We
    sent a 6-digit code to {email}. Type the code below, or open the link in the same email."
    (was "... We sent a link to {email}.")
  - `gate_type_code`: "Type the code".
  - `gate_resend` "Send it again" is unchanged: "it" is the email, which carries both.

## Voice note playback speed (PR feat/voice-playback-speed)

### 64. A speed pill on every voice note: 1×, 1.5×, 2× (owner request 2026-11-10)

- **Why:** the owner asked that a voice note can be listened to faster, "maybe 1.5x".
  Rosewood has no speed control, so this one is **PROPOSED, not drawn**.
- **Built, PROPOSED (`SpeedPill`, private to `VoiceNote.kt`):** at the end of the clock line
  under the waveform ("0:12 of 0:42 ... 1×"), on every voice note: the EntryCard's, Write's
  listen-back and Edit's sent voice note, before play too. A tap moves it 1× → 1.5× → 2× → 1×.
  - **Look, tokens only, no new colour:** the `label` style in `ink`, inside a `shapes.full`
    pill with a `hairline` of `outlineStrong` (a control's border, as `Chip`'s), no fill,
    padded `spacing.sm` across and `spacing.xs` down. It is smaller than the clock beside it
    so it reads as the clock's companion, not a second clock.
  - **Target:** a 48 × 48 box (NFR-101) centred on the pill. The layout reports only the
    pill's height, so the clock line does not grow; the target reaches about 11 above and
    below it, over the waveform band's empty bottom edge. A tap there, in the last 48 of the
    waveform's width, changes the speed rather than seeking.
  - **Spoken:** a button, "Playback speed, 1.5 times" ("Playback speed, normal" at 1×). The
    drawn "1.5×" is hidden from screen readers so it is not read twice.
- **Behaviour:**
  - The speed is the **session's**, one for the whole app (`SessionPlaybackSpeed.shared`):
    every pill shows it, the next voice note starts at it, and a tap on any pill changes the
    one playing at once, from where it is, without a restart. Held in memory only, never
    written to disk: a new process starts at 1×.
  - **Pitch is kept:** Android sets `ExoPlayer.setPlaybackSpeed` (PlaybackParameters, pitch 1),
    iOS sets `AVAudioPlayer.enableRate` before `prepareToPlay`, then `rate`. Both stretch time.
  - The waveform and clock still read the player's own position, which is in the voice
    note's time, so at 2× they move twice as fast and end together at the note's length.
- **Strings (PROPOSED):**
  - `ds_voice_speed`: "{speed}×" (the pill).
  - `ds_voice_speed_spoken`: "Playback speed, {speed} times".
  - `ds_voice_speed_spoken_normal`: "Playback speed, normal" ("1 times" does not read).
  - `ds_voice_speed_1`, `ds_voice_speed_1_5`, `ds_voice_speed_2`: "1", "1.5", "2", one string
    each so a locale can write its own decimal separator ("1,5").

## Edit after the reveal (PR fix/edit-after-reveal)

### 65. Write, Edit mode, when the day opened while editing (`409 ENTRY_IMMUTABLE`)

- **Why:** the owner added a photo and a voice note in Edit and tapped Save. The partner had
  written meanwhile, so the day was revealed and the server refused the edit (BR-7: an entry
  is immutable once revealed). The composer said "That didn't save. Your words ... are still
  here" with Try again, which can never work. states.md §5 "Endpoints and states with no
  screen" recorded the gap ("`409 ENTRY_IMMUTABLE` on save: Nothing draws 'it was revealed
  while you were editing'. Needs a state on the composer, and copy"). **That gap is now
  built**, as PROPOSED, with no Figma frame.
- **Built, PROPOSED:** `WriteSubmit.Revealed` (Edit only). The action is no longer Save or Try
  again: it is **See today**, which closes the composer onto Today, already read again, now
  showing the revealed day. Above the action, one centred `body` line in `ink` (neutral, not
  `danger`: nothing went wrong that the person could mend), announced politely. The typed
  words stay in the field to copy; typing is ignored and the photo and voice controls are
  hidden. Leaving by the X, Back or See today never asks to discard, deletes the edit draft,
  and deletes the photo and voice note uploaded for the failed edit (they were never attached).
- **Strings (PROPOSED):**
  - `write_revealed`: "{partner} has written, so today has opened. Your entry stays as it was
    before this edit."
  - `write_revealed_no_name`: "Your partner has written, so today has opened. Your entry stays
    as it was before this edit." (only if the day carried no partner name)
  - `write_see_today`: "See today".
- Other 409s on an edit (`BOND_ARCHIVED`) keep the generic Save failed, as before.

## The composer keeps room for the words (PR fix/composer-keyboard-room)

### 66. With the keyboard up, the words keep three lines and the attachments step back (owner's iPhone, 2026-11-10)

- **Why:** on the owner's iPhone (screenshot IMG_7706, 2026-11-10), in Write with the
  keyboard up and both a photo and a voice note attached, the text field was squeezed to a
  sliver: the typed line "God is" was cut to half its height. The photo (about 100 x 130) and
  the voice row (play, waveform, Record again, Remove, clock, speed pill) kept their full height,
  so the words got whatever was left above the keyboard. On the iPhone 17 Pro simulator, with a
  two-line prompt, they got nothing at all. Nothing in Rosewood or Figma draws a composer with
  attachments and the keyboard up, so the answer below is **PROPOSED, not drawn**.
- **Built, a layout rule (not PROPOSED, a fix):**
  - The words never get fewer than **three lines** of `entry`
    (`MoyiDimens.ComposeField.MinTextLines`), however much is attached.
  - Everything inside the box (prompt, words, attachments, toolbar) **scrolls as one column**
    when it does not fit. The box, its border and the Send button outside it do not move: Send
    stays pinned under the box, above the keyboard, and never covers the words.
  - The line with the cursor is **kept in view** as you type, move the cursor, or the room
    changes (the keyboard rising, the attachments stepping back).
- **Built, PROPOSED (`AttachmentsLayout`, `AttachmentFrame`, the `compact` look of
  `AttachedPhoto`, `SentPhoto`, `AttachedVoice` and `SentVoice`):**
  - **While the keyboard is up and something is attached,** the attachments step back to
    **one row**, side by side, `spacing.sm` apart. The row starts where the words start (the
    text's 12 wrap) and keeps `spacing.xs` above and below, and when the caret is kept in view the
    whole row comes with it if both fit, so the frames are never cut by the box's edge:
    - the photo as a **40 x 40 thumbnail** (`MoyiDimens.ComposeField.CompactThumbnail`,
      Interpreted: the 4 pt half-step above and below inside its 48 line), `shapes.radius`,
      cropped, `spacing.xs` inside its frame, with its remove (or, in Edit, Replace) 48 target beside it;
    - the voice note as a **chip**: play/pause (48), the clock ("0:00 of 0:06", `inkMuted`), a
      `spacing.md` gap, then Remove (48). Remove is never beside play, so one slip does not throw
      a recording away (review). No waveform, no speed pill and **no Record again** (owner ruling
      2026-11-10); Record again returns with the full row.
      While recording: trash, the dot with its clock and the tick, without the live waveform.
    - If the two do not fit across (a very narrow screen, a large font), they stack rather
      than being cut.
  - **When the keyboard goes,** they return to full size. The change of size eases on
    `motion.control` (150 ms); with Reduce Motion (iOS) or Remove animations (Android) it is a cut.
  - **While the keyboard is up, each attachment sits in its own frame** (design review of the
    same screenshot): a `hairline` of the decorative `outline` on `shapes.radius`. The prompt's
    dismiss and the voice note's Remove are the same close glyph a few rows apart; inside its
    frame, each remove control plainly belongs to what it removes. No new icon, no new colour.
    **With the keyboard down there are no frames** and the full-size layout is main's (owner
    ruling 2026-11-10).
  - **Screen readers hear the same names:** "Remove the photo", "Remove the voice note", "The
    photo you are adding", "Play the voice note", "Your voice note, 0:06" with its position as
    the state. In the chip the clock carries the waveform's name and position (it cannot seek).
  - **Targets stay 48** (NFR-101).
  - One layout for both looks, so a voice note being listened to keeps playing when the
    keyboard comes or goes.
- **Edit mode** (§60), photo only or voice only: the same rules. The entry's own photo and voice
  note step back the same way, with Replace and Record again as their one control.
- **Test:** `ComposeFieldRoomTest` (iOS Compose UI) puts both attachments in a 358 x 316 box
  (the screenshot's) and checks the words keep three lines, compact and full.
- **Owner rulings (2026-11-10, PR #77):**
  - The compact row stays where it is, between the words and the toolbar (not in the toolbar's line).
  - No Record again in the compact voice chip.
  - Frames only while the keyboard is up; with it down, main's full-size layout, unframed.
  - A drag inside the composer scrolls it and leaves the keyboard up (it does not dismiss, unlike
    the rest of the screen, D6).
- **Checked, not a bug:** a recording's waveform drawn as flat dots on the iOS simulator is a quiet
  Mac microphone. The levels are sampled by the recorder whatever the layout shows, and a
  recording made while compact keeps its bars when the full row comes back (Android emulator).

## My reaction on the partner's card (PR feat/reaction-pill)

### 67. A pill "You: 🥺 Moved" on the partner's card, and the hint that goes once I have reacted (owner request 2026-11-10)

- **Why:** after reacting, nothing on the partner's card said which reaction was mine except
  the selected chip below it. The owner asked for a small pill on the card itself. Rosewood
  draws no reaction (§45), so this is **PROPOSED, not drawn**.
- **Built, PROPOSED (`EntryCardReaction`, the `reaction` slot of `EntryCard`):** a pill at the
  right of the "From {partner}" label row of a `Partner` card, once I have reacted to it.
  - **Look, tokens only:** the `label` style in `ink`, a `hairline` of `outlineStrong`,
    `shapes.full`, no fill, padded `spacing.sm` across and `spacing.xs` down: the speed pill's
    look (§64). It is not uppercased, so it reads "You: 🥺 Moved".
  - **Display-only:** no target and no click, so no 48 (NFR-101 is about targets).
  - **Spoken:** "You reacted: Moved", as part of the card's one stop (after the words); the
    drawn text is cleared from the tree, so it is never read twice.
  - **When:** the moment a chip is tapped (the chips are optimistic), and gone when it is
    taken back. On Today and in the Archive day view (both draw the chips), wherever
    `canReact`; never on an ended bond, which hides reactions. Not on the reveal turn, which
    settles into Today.
- **Hint:** "React to what {name} wrote." shows only before I have reacted. Once one is chosen
  it is hidden (it carried "Tap another to change it, or the same one to take it back.",
  `reaction_chosen`, now removed); the chips stay for changing or taking back.
- **Strings (PROPOSED):**
  - `reaction_mine_pill`: "You: {emoji} {word}" (emoji and word are the chips' own resources).
  - `reaction_mine_spoken`: "You reacted: {word}".

## The voice note on one line (PR feat/voice-note-one-row)

### 68. Play, the bars, the clock and the speed pill on one line (owner request 2026-11-10)

- **Why:** the player was two lines (the 48 band of the bars, the clock and the speed pill
  under it: 72 tall) under words that are the point of the card. The owner asked for one row,
  with a reviewer's conditions: the play control visibly a button but lighter than an ink disc,
  progress visible at a glance, and nothing clipped at large text sizes. Rosewood draws no
  player (§43), so this is **PROPOSED, not drawn**. It replaces §46's "the row" bullet.
- **Built, PROPOSED (`VoiceRow`, inside `PlayerControls`; the EntryCard player, Edit's
  `SentVoice` and Write's listen-back `AttachedVoice` all use it):**
  - **One 48 line:** play (48), `spacing.xs`, the waveform taking what is left, then `8` and the clock in `inkMuted`
    ("0:04 of 0:11", `body`, tabular figures so the bars never shift as the seconds change),
    `spacing.sm`, the speed pill (§64, unchanged), then any trailing controls. 48 tall, down
    from 72: the touch target is the floor (NFR-101), so it is two thirds of the old height,
    not half.
  - **Wrapping:** when the clock and the pill would leave the bars narrower than 80
    (`MoyiDimens.Waveform.MinWidth`, **Interpreted**: 16 bars, still a comfortable band to tap
    and drag), they go on a second line under the bars, starting where the bars start, the pill
    at the bars' end; if that line cannot hold both, the pill goes under the clock. Text is
    never squeezed or clipped. On an EntryCard this happens at large text sizes (tested at 2×),
    and always in Write's listen-back, where Record again and Remove take 96 of the row.
  - **The play button:** a 40 circle (`MoyiDimens.VoicePlay.Circle`, **Interpreted** from
    `Chip`'s 40 pill in its 48 target) with a `hairline` of `outlineStrong` and no fill, the
    24 play or pause glyph in `ink` inside it. An outline rather than a filled disc: a solid
    `ink` disc is the heaviest mark Rosewood can draw and would outweigh the words; a tinted
    fill would need a colour Rosewood does not have (no accent, tokens.md §1). The outline is
    the border Rosewood gives a control (`outlineStrong`, never the decorative `outline`), the
    same as the speed pill beside it, so the row's two controls read as one family.
  - **Progress:** unchanged from §46: played bars `ink`, the rest `outlineStrong`. At rest
    every bar is `outlineStrong`, which is why the row read as all grey; while it plays the
    split is clear on both themes. `outlineStrong` rather than `inkMuted` for the unplayed
    bars: in dark, `inkMuted` (#CDBBB5) sits too close to `ink` (#F8EFE9) to show the split.
  - **Compact (§66) is unchanged:** with the keyboard up the chip keeps its bare play glyph,
    the clock and Remove, inside its frame (a circle inside the frame's border was one ring
    too many).
- **Accessibility:** unchanged. Play is "Play the voice note" or "Pause the voice note", a
  button; the waveform is one slider node, "Position in the voice note", "0:04 of 0:11"; the
  clock is not read twice; the pill is "Playback speed, normal".
- **Open for the owner:** (1) whether 48 (two thirds) is short enough, or the visible row
  should be shorter with its targets reaching past it, as the speed pill's does; (2) whether
  Write's listen-back should also be one line, which would need Record again and Remove to
  move (for example under the bars, or behind one "More" target).

## The label diet (PR feat/label-diet)

### 69. Tracked capitals only for author lines, section heads and date headers (owner ruling 2026-11-10, option C)

- **Why:** the tracked uppercase `label` style had spread to every small piece of text
  (chips, legends, field captions), so capitals stopped meaning "this names something". The
  owner ruled it back to three jobs. **PROPOSED, not drawn**: Figma draws these chips and
  legends in capitals.
- **Kept as `TextRole.Label`:** (a) author lines on cards ("FROM PAX", "FROM YOU"); (b) section
  heads (Bond settings group heads, the Archive month heading, the Streak window head, the
  onboarding samples head, the screen eyebrow); (c) dates used as headers (Today, Reveal,
  an Archive row's day name).
- **Changed to `TextRole.Tag`:** the same size and weight as `label`, sentence case, no
  tracking (`label.copy(letterSpacing = 0.em)`), `inkMuted` by default. Chips (Archive
  filters, reactions, relationship type), the reaction pill, the speed pill, the Streak
  legend and its "Now" and "Longest" captions, field captions, the back label, and the invite
  code caption and copy label.
- **Strings:** unchanged; they were already sentence case, the uppercasing was in `MoyiText`.
- **Accessibility:** unchanged; Label already spoke its original casing.
- **Today's date header wraps:** the nav row's height was clamped to 48. It is now a minimum,
  so at the largest Dynamic Type the date wraps onto as many lines as it needs and the
  calendar and settings targets stay centred.

## The photo on a card: a height cap, a full-screen viewer, and no jump on open (PR feat/photo-cap-viewer)

### 70. Photo height cap, full-screen viewer, and media sizes in Today's cache (owner approval 2026-11-10)

- **Why:** a tall photo (a 9:16 screenshot) took most of the screen, a panorama wider than 2:1
  was cropped, nothing opened a photo larger (§42), and Today, drawn first from its cache, grew
  when the photo and the voice note arrived from the server, because the cache kept neither.
  The owner approved all three, with a reviewer's condition: a height cap, not a fixed crop.
  Rosewood draws none of it, so it is **PROPOSED, not drawn**.
- **Height cap (`photoFrame`, EntryPhoto.kt):** the card's full inner width at the photo's own
  shape, never taller than **3:4**. A photo taller than that is centre-cropped to 3:4; a camera
  portrait (3:4), a square, a landscape and a panorama are shown whole, never cropped (the old
  2:1 floor is gone). **Interpreted:** 3:4 because it is the shape a phone camera takes, so
  every ordinary portrait shows whole, and on a 306-wide card (iPhone 17 Pro) it is at most
  408 tall, under half the screen, so the words above stay in view. A ratio rather than a
  fixed height, so a wider card (a tablet) never crops a square. The card's 12 radius, on
  `surface` until it loads, as before. With no size from the server, the frame is the cap
  (a placeholder holding the tallest place the photo can take) and the photo is fitted in it,
  never cropped. The composer's previews (§41, §60) keep their own 3:4 to 2:1 clamp.
- **Full-screen viewer (`PhotoViewer`, PROPOSED):**
  - A tap on a shown photo opens it (spoken action PROPOSED `ds_entry_photo_open`, "View the
    photo full screen"). A sensitive photo opens only once uncovered; a photo with no URL yet
    (drawn from the cache) opens nothing.
  - **Layout:** `surface` edge to edge (no black: Rosewood has no black, and `surface` follows
    the theme); a 48 row at the top with the close target at its **start** on both platforms,
    the house `Close` glyph (24, `ink`), spoken "Close" (`ds_close`). Start, because Android's
    full-screen dialogs and iOS's own photo viewers both put the way out at the top left. The
    whole photo under it, fitted, centred; the close target never sits on the photo, so it
    keeps its contrast on any picture.
  - **Closing:** the ✕ and system Back. No tap outside (there is no outside).
  - **Pinch to zoom:** 1× to 4×, a drag moves the zoomed photo; each opening starts at 1×.
  - **Capture protection (R3):** a partner's photo stays protected. Android: the viewer is a
    dialog, a window of its own, so it sets `FLAG_SECURE` on that window
    (`SecureFlagPolicy.SecureOn`); the card's flag on the activity would not cover it. iOS:
    the dialog is a layer of the app's window, and the viewer holds the app-switcher cover
    (`ProtectFromCapture`) as the card does.
  - **Accessibility:** the photo keeps its description ("Photo"); "Close" is a button; the
    dialog takes focus when it opens.
- **No jump on open:** Today's cache (`cached_day`, migration `6.sqm`, version 7) keeps the
  shape of each entry's photo and voice note: the media id, its status, the photo's width and
  height, the voice note's length, and whether it is sensitive. **Never the signed URL**, a
  credential. From the cache a photo draws its frame at its final size, on `surface`; a voice
  note's row is laid out at its final size but not drawn or read (nothing in it could play
  yet). When the server answers, the URL fills them in and nothing moves.
- **Not done here:** the Archive's day cache (`archive_day`) still keeps no media, so an
  Archive day drawn from its cache can still grow; the same columns would fix it.
- **Open for the owner:** (1) `surface` or black behind the viewer; (2) whether a cached voice
  note should show its row (visible, with play disabled until the URL arrives) rather than an
  empty space of the same height, which is what it is offline; (3) the 3:4 cap.

