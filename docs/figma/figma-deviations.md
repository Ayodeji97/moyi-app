# Where the app departs from Figma, and the platform look and feel

Figma draws one generic phone. Where it conflicts with how Android or iOS behave, **the
platform convention wins**; Figma stays the source for colour, type, spacing and copy. Each
row says what changes, why, and whether Figma should be redrawn to match (the owner updates
Figma when convenient; the code does not wait for it).

Recorded 2026-10-29 from the owner's review of the running app. Status: **owed** (agreed, not
built), **ruling owed** (needs the owner), **checklist** (applies to a screen not yet built),
**done**.

## When a screen has a back arrow

Following Android's navigation principles and Apple's Human Interface Guidelines (owner, 2026-10-29):

- **Root screens have none**: Promise, Today, sign in while it is the first screen, and any screen after a point of no return (Invite, once the bond exists). On Android, system back exits from the start destination.
- **Pushed screens have one**: Register, Verify, Relationship type and Join code when reached from another screen, Create bond, Join confirm, the "cannot be used" screen.
- **Tasks close instead of going back**: Write (D10). Confirmations are dialogs (D11).
- **Back never crosses a point of no return**: account created, email verified, signed in, bond created. The earlier screens are removed from the stack.
- **The iOS edge swipe (D3) follows the arrow exactly**: it works where the arrow shows and nowhere else.

## Agreed changes to the screens that exist

| # | Change | Why | Figma follows? | Status |
|---|---|---|---|---|
| D1 | **Header spacing, option (b).** Drop the eyebrow on every screen that has a back target; keep it on root screens (sign in while it is the root, the unverified gate, Today). Tighten the gap above the heading. One change in the shared header (`AuthHeader`, the onboarding header) and `MoyiScaffold`. | Back target 48 + 16 + eyebrow + 64 + heading puts the heading at about content y 171, so the top third of a real phone is empty. With a back target, the heading already says where you are. Owner ruling 2026-10-29. | Yes | done (#10): heading 24 below the back target; owner may ask for 16 |
| D2 | **Back affordance per platform.** Material arrow-back on Android, chevron on iOS. Today both use `MoyiIcons.ChevronLeft`. | Each platform's users read their own glyph as "back". | Yes (show both, or note it) | done (#10) |
| D3 | **iOS edge swipe back.** Swipe from the left edge pops the screen. | Expected on every iOS stack. Compose Multiplatform 1.12.2 with navigation 2.10.0 already provides it (checked in #10). | No (behaviour) | done (#10): Compose Multiplatform already provides it; verified on the simulator |
| D4 | **Android predictive back.** The app targets API 37, so the system animations are already on by default (since targeting API 36) and no manifest flag is needed. Verify the in-app back preview between screens on a device, and build any custom back handling on the Navigation Event APIs: `PredictiveBackHandler` is deprecated from Compose Multiplatform 1.10. | Android 16+ default; the gesture should preview the screen underneath. | No (behaviour) | done (#10): verified on an API 36 emulator, no flag |
| D5 | **Press feedback per platform.** Ripple on Android; a dim (lowered opacity) on iOS. Set once in the design system's indication. | A ripple reads as Android on an iPhone. | No | done (#10): iOS dim 0.4 awaits owner approval |
| D6 | **Dismiss the keyboard on iOS** when tapping outside a field or scrolling the content. | iOS has no back key to close it. | No | done (#10) |
| D7 | **Overscroll.** Bounce on iOS, stretch on Android: check both on device. | Compose mostly does this already; not yet verified. | No | owed (verify by hand on a device) |
| D8 | **Legal links open in-app**: Chrome Custom Tabs on Android, SFSafariViewController on iOS. | Neither an external browser nor nothing (today they do nothing; the URLs are still owed). | No | mechanism done (#10); URLs owed (gap 12) |
| D9 | **Sign up: consent and legal links scroll with the fields** (already built in PR #6, see `auth-screens-check.md`). | With the name field, the pinned block hid the password and clipped at 200 % font. | Yes | done |
| D10 | **Write is a task, not a pushed screen.** Close it with a close (X) control on Android (full-screen dialog style) and Cancel on iOS (sheet style), not a back arrow. If text has been typed, closing asks to discard it first, in the platform's own dialog. | Both platforms present a self-contained task as dismissible; Apple reserves Back for steps in a flow. Today back drops the typed words without asking. Owner approval 2026-10-29. | Yes (181:3 draws a back arrow) | owed |
| D11 | **Deleting an entry confirms in the platform's dialog**: an alert with a destructive Delete on iOS, a Material dialog on Android, instead of the full Delete screen. Applies C2. | A destructive confirmation is a dialog on both platforms. Owner approval 2026-10-29. | Yes (420:64 draws a full screen) | owed |
| D12 | **Back during the reveal settles to Today.** The reveal replaces Today in the back stack, so Android back during it currently closes the app. | Back must never exit from a moment the person is inside. iOS already settles to Today on its own. Owner approval 2026-10-29. | No (behaviour) | owed |
| D13 | **The promise, variant C** (owner ruling 2026-10-30). The block is centred in the room above the actions (top-aligned and scrolling when too tall); two sample `EntryCard`s (Partner) under "An example" in place of one; the heading is "Write before you read." alone; "I already have an account" is sentence case. | On a tall phone the top-aligned block left the middle empty; two entries show the exchange, not one diary line. | Yes (358:45 draws one card, top-aligned, label-case link) | done (first-impression PR); sentence-case links everywhere: D14 |
| D14 | **Every secondary link is sentence case** (owner ruling 2026-10-30). `TextLink` has one text setting, `body` (16 / 24, system sans), underlined, `ink` (`danger` when destructive), in the words as written: "I forgot my password", "Send it again", "Use a different address", "I have an invite code", "Not now", "Cancel" and the rest. The uppercase `label` setting (`TextLinkStyle.Label`) and the style parameter are removed, so a link cannot drift back to uppercase. Targets stay at least 48. Applies to auth, onboarding (join too), Today, and the design system previews. | Uppercase letters read as a shout and are slower to read than the sentence they spell; the promise screen (D13) showed the calmer form, and one style everywhere is simpler than two. Eyebrows and card author labels keep `label` (uppercase): they are labels, not actions. | Yes (Figma draws label-metric, uppercase links on every screen; `auth-placement-spec.md` D2) | done (sentence-case-links PR) |

## Rulings owed by the owner

| # | Question | Notes |
|---|---|---|
| R2 | **The proposed string** "That password is too long. Use 128 characters or fewer." | From PR #6 (`register_password_error_long`). |

## Rulings made

| # | Ruling | Notes |
|---|---|---|
| R1 | **Add a scroll cue when content continues below the pinned action block** (owner, 2026-10-29). The consent control stays a checkbox on both platforms, as Figma draws it. | Found on sign up: on an iPhone 17 at rest the consent row sits just below the fold with nothing to say the content scrolls (PR #6 device run). Proposed form: a short fade at the bottom edge of the scrolling content, in `surface`, shown only while more content is below, built once in `MoyiScaffold` so every scrolling screen gets it. It is a small addition to the design system, so its exact look is confirmed with the owner before it ships. Not built yet. |
| R3 | **Screen capture: blocked only where the partner's words appear** (owner, 2026-10-29; replaces "not blocked, for now" from earlier the same day). | Protected: the reveal, the partner's entry on Today, and the partner's entries in the Archive. Android: `FLAG_SECURE` set when one of those screens opens and cleared when it closes (blocks screenshots and recording, blanks the recents card, and is the only Content Capture opt-out at API 37). iOS: cannot block screenshots; cover the content with `surface` while the app is in the background. Everything else, including the person's own entry and the write screen, can be screenshotted. Sharing for publicity goes through a share card instead (C7). Not built yet. |
| R4 | **Assistants and agents: only harmless actions** (owner, 2026-10-29). | Actions such as "open today's entry" and "remind me to write" may be exposed later through Android AppFunctions and iOS App Intents. Entry text, a partner's words and bond details are never exposed. Nothing is built yet (P9). |
| R5 | **No navigation bar, tab bar or rail**, at any width (existing decision; the owner asked about it 2026-10-29 and the recommendation is to keep it). | Already decided in `states.md` §3 and the design system's notes: Today is home, with Archive and Bond settings one tap away from its `nav` row. A persistent bar would advertise destinations for an app whose point is one entry a day. Liquid Glass (P8) is not a reason to add one. |

## Checklist for screens not yet built

Each applies when the screen that needs it is built; listed so no one has to remember it.

| # | Item | Where |
|---|---|---|
| C1 | **Native share sheet** for the invite link, never a custom one. | Pairing (invite) |
| C2 | **Confirmations use the platform's dialog**: alert or action sheet on iOS (its button order), Material dialog on Android. | Sign out, leave bond, delete entry |
| C3 | **Pull to refresh** with each platform's own indicator. | Today |
| C4 | **Haptics**: a light one on iOS when an entry is sent and at the reveal; sparing on Android. | Today, reveal |
| C5 | **Notification permission asked once, at a meaningful moment** (after pairing), not at launch: iOS allows one prompt. | Pairing, Today |
| C6 | **App icon and splash**: Android adaptive icon and the Android 12 splash API; an iOS launch screen. Done (first-impression PR). The icon is the o-dot mark, V1 "open gap" (owner, 2026-10-30; `tools/icon/README.md`). iOS draws the wordmark on `surface` (`UILaunchScreen`). Android draws the mark on `surface`. | App shell |
| C7 | **Share card** (R3): a "Share" action that renders an entry as a designed card (Rosewood type and colour, the Moyi name and getmoyi.app at the foot, sized for a status) through the native share sheet. Your own entry: one tap. Your partner's words: only if they allow it, a per-person "let my partner share my entries" switch, off by default. Copy and card design need the owner. | Reveal, Archive |

## System details (one pass)

| # | Item | Notes |
|---|---|---|
| S1 | **Status and navigation bars**: light icons on the dark ground; Android's navigation bar transparent with gesture and 3-button navigation. | Check both navigation modes. |
| S2 | **Fonts and text scaling**: body text is the system face, Roboto on Android and SF on iOS (SF is wider, which is why Terms and Privacy differ in width). Verify iOS Dynamic Type scales the way Android's font scale does. | Only Android was walked at 200 % (PR #6). |
| S3 | **Password autofill**: Google and iCloud Keychain should offer to save. The iOS Strong Password suggestion needs an associated domain (`apple-app-site-association` on getmoyi.app). | Site or backend work, not this repository: recorded here for that session. |

## Platform changes checked 2026-10-29

Read from the current platform documentation against this app's settings (targetSdk 37,
compileSdk 37, Compose Multiplatform 1.12.2, navigation 2.10.0). Nothing here was run on a
device yet unless it says so.

| # | Change | What it means for Moyi | Status |
|---|---|---|---|
| P1 | **Android 17: no opting out of orientation and resizability on large screens** (smallest width 600 dp and up) for apps targeting API 37. | Every screen must lay out in landscape and on tablets and foldables. Only portrait phones have been walked so far. The Figma Tablet section is the reference. | owed (walk a tablet and landscape) |
| P2 | **Android 17: `setContentCaptureEnabled(false)` no longer opts out of Content Capture**; only `FLAG_SECURE` does. | A journal's entry text could be read by system capture services. Ruled in R3: `FLAG_SECURE` on the screens that show the partner's words only. | ruled, owed |
| P3 | **`android:allowBackup="true"`** in the manifest (wizard default). | Auto-backup could copy the Keystore-encrypted token file. The key does not restore with it, so a restored install would hold an unreadable session. Either turn backup off or exclude the session file with data extraction rules. | done (#10): allowBackup false plus data extraction rules |
| P4 | **Android 16: predictive back on by default** for apps targeting API 36 or higher; `onBackPressed` and `KEYCODE_BACK` no longer run. | No legacy back handling exists in the code. D4 is now a verification. | done (#10): no legacy back handling; D4 verified |
| P5 | **Android 16: edge-to-edge is mandatory**; the opt-out is gone. | Already done (`enableEdgeToEdge()`, `MoyiScaffold` on `safeDrawing`). Check 3-button navigation (S1). | done, S1 owed |
| P6 | **Android 17: Certificate Transparency on by default.** | `api.getmoyi.app` uses a publicly logged certificate, so it should pass. Verify once with a release build against production. | owed (verify) |
| P7 | **Android 17: `ACCESS_LOCAL_NETWORK` runtime permission** for LAN access. | Affects only a physical phone reaching a development backend on the LAN. The emulator through `localhost` and `adb reverse` worked in the PR #6 run. Development note only. | note |
| P8 | **iOS 26 Liquid Glass** is drawn by the system only on native navigation components (NavigationStack, TabView). | Moyi has no navigation bar or tab bar by design, so there is little to adopt. The back chevron is drawn by Compose and will not be glass; that is accepted. The edge swipe (D3) still has to come from our navigation. The simulator runs iOS 26; walk the current iOS release too. | accepted, D3 owed |
| P9 | **Assistants calling apps**: Android AppFunctions, iOS App Intents. | Nothing is built. Scope ruled in R4: harmless actions only. | ruled, not built |

Sources: [Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16),
[Android 17 behaviour changes](https://developer.android.com/about/versions/17/behavior-changes-17),
[Kotlin Multiplatform: iOS Liquid Glass](https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html),
[Android AppFunctions](https://developer.android.com/ai/appfunctions),
[Apple App Intents updates](https://developer.apple.com/documentation/updates/appintents).
