# Where the app departs from Figma, and the platform look and feel

Figma draws one generic phone. Where it conflicts with how Android or iOS behave, **the
platform convention wins**; Figma stays the source for colour, type, spacing and copy. Each
row says what changes, why, and whether Figma should be redrawn to match (the owner updates
Figma when convenient; the code does not wait for it).

Recorded 2026-10-29 from the owner's review of the running app. Status: **owed** (agreed, not
built), **ruling owed** (needs the owner), **checklist** (applies to a screen not yet built),
**done**.

## Agreed changes to the screens that exist

| # | Change | Why | Figma follows? | Status |
|---|---|---|---|---|
| D1 | **Header spacing, option (b).** Drop the eyebrow on every screen that has a back target; keep it on root screens (sign in while it is the root, the unverified gate, Today). Tighten the gap above the heading. One change in the shared header (`AuthHeader`, the onboarding header) and `MoyiScaffold`. | Back target 48 + 16 + eyebrow + 64 + heading puts the heading at about content y 171, so the top third of a real phone is empty. With a back target, the heading already says where you are. Owner ruling 2026-10-29. | Yes | owed |
| D2 | **Back affordance per platform.** Material arrow-back on Android, chevron on iOS. Today both use `MoyiIcons.ChevronLeft`. | Each platform's users read their own glyph as "back". | Yes (show both, or note it) | owed |
| D3 | **iOS edge swipe back.** Swipe from the left edge pops the screen. | Expected on every iOS stack; Compose Multiplatform navigation does not add it by default. | No (behaviour) | owed |
| D4 | **Android predictive back.** The app targets API 37, so the system animations are already on by default (since targeting API 36) and no manifest flag is needed. Verify the in-app back preview between screens on a device, and build any custom back handling on the Navigation Event APIs: `PredictiveBackHandler` is deprecated from Compose Multiplatform 1.10. | Android 16+ default; the gesture should preview the screen underneath. | No (behaviour) | owed (verify) |
| D5 | **Press feedback per platform.** Ripple on Android; a dim (lowered opacity) on iOS. Set once in the design system's indication. | A ripple reads as Android on an iPhone. | No | owed |
| D6 | **Dismiss the keyboard on iOS** when tapping outside a field or scrolling the content. | iOS has no back key to close it. | No | owed |
| D7 | **Overscroll.** Bounce on iOS, stretch on Android: check both on device. | Compose mostly does this already; not yet verified. | No | owed (verify) |
| D8 | **Legal links open in-app**: Chrome Custom Tabs on Android, SFSafariViewController on iOS. | Neither an external browser nor nothing (today they do nothing; the URLs are still owed). | No | owed (needs the URLs) |
| D9 | **Sign up: consent and legal links scroll with the fields** (already built in PR #6, see `auth-screens-check.md`). | With the name field, the pinned block hid the password and clipped at 200 % font. | Yes | done |

## Rulings owed by the owner

| # | Question | Notes |
|---|---|---|
| R1 | **Consent below the fold on iOS.** Add a scroll cue, or leave it? | On an iPhone 17 at rest the consent row sits just below the fold with no scroll cue (PR #6 device run). The control itself is settled: a checkbox on both platforms, as Figma draws it (owner, 2026-10-29). |
| R2 | **The proposed string** "That password is too long. Use 128 characters or fewer." | From PR #6 (`register_password_error_long`). |
| R3 | **Screen capture on the private screens.** Block screenshots and screen recording, and hide content in the app switcher, on the screens that show entry text (write, Today, reveal)? | On Android, the only switch left at API 37 is `FLAG_SECURE`: `setContentCaptureEnabled(false)` no longer opts out of Content Capture. `FLAG_SECURE` also blocks the person's own screenshots and blanks the recents thumbnail. iOS cannot block screenshots; the equivalent is covering the content when the app leaves the foreground. See P2. |
| R4 | **Assistants and agents (Gemini, Siri).** Expose nothing, or only harmless actions such as "open today's entry" and "remind me to write"? | Both platforms now let assistants call app functions: Android AppFunctions (experimental; Gemini in private preview) and iOS App Intents (Apple's path for Siri and Apple Intelligence; SiriKit is deprecated). Entry text must never be exposed. See P9. |

## Checklist for screens not yet built

Each applies when the screen that needs it is built; listed so no one has to remember it.

| # | Item | Where |
|---|---|---|
| C1 | **Native share sheet** for the invite link, never a custom one. | Pairing (invite) |
| C2 | **Confirmations use the platform's dialog**: alert or action sheet on iOS (its button order), Material dialog on Android. | Sign out, leave bond, delete entry |
| C3 | **Pull to refresh** with each platform's own indicator. | Today |
| C4 | **Haptics**: a light one on iOS when an entry is sent and at the reveal; sparing on Android. | Today, reveal |
| C5 | **Notification permission asked once, at a meaningful moment** (after pairing), not at launch: iOS allows one prompt. | Pairing, Today |
| C6 | **App icon and splash**: Android adaptive icon and the Android 12 splash API; an iOS launch screen. The icon itself does not exist yet and blocks both stores (`missing-components.md` §9). | App shell |

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
| P2 | **Android 17: `setContentCaptureEnabled(false)` no longer opts out of Content Capture**; only `FLAG_SECURE` does. | A journal's entry text could be read by system capture services. Ruling R3. | ruling owed |
| P3 | **`android:allowBackup="true"`** in the manifest (wizard default). | Auto-backup could copy the Keystore-encrypted token file. The key does not restore with it, so a restored install would hold an unreadable session. Either turn backup off or exclude the session file with data extraction rules. | owed (security) |
| P4 | **Android 16: predictive back on by default** for apps targeting API 36 or higher; `onBackPressed` and `KEYCODE_BACK` no longer run. | No legacy back handling exists in the code. D4 is now a verification. | owed (verify) |
| P5 | **Android 16: edge-to-edge is mandatory**; the opt-out is gone. | Already done (`enableEdgeToEdge()`, `MoyiScaffold` on `safeDrawing`). Check 3-button navigation (S1). | done, S1 owed |
| P6 | **Android 17: Certificate Transparency on by default.** | `api.getmoyi.app` uses a publicly logged certificate, so it should pass. Verify once with a release build against production. | owed (verify) |
| P7 | **Android 17: `ACCESS_LOCAL_NETWORK` runtime permission** for LAN access. | Affects only a physical phone reaching a development backend on the LAN. The emulator through `localhost` and `adb reverse` worked in the PR #6 run. Development note only. | note |
| P8 | **iOS 26 Liquid Glass** is drawn by the system only on native navigation components (NavigationStack, TabView). | Moyi has no navigation bar or tab bar by design, so there is little to adopt. The back chevron is drawn by Compose and will not be glass; that is accepted. The edge swipe (D3) still has to come from our navigation. The simulator runs iOS 26; walk the current iOS release too. | accepted, D3 owed |
| P9 | **Assistants calling apps**: Android AppFunctions, iOS App Intents. | Nothing is built. Ruling R4 decides the scope. | ruling owed |

Sources: [Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16),
[Android 17 behaviour changes](https://developer.android.com/about/versions/17/behavior-changes-17),
[Kotlin Multiplatform: iOS Liquid Glass](https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html),
[Android AppFunctions](https://developer.android.com/ai/appfunctions),
[Apple App Intents updates](https://developer.apple.com/documentation/updates/appintents).
