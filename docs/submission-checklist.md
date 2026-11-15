# Store submission checklist

What stands between the testing builds (TestFlight 1.0 (4), Play internal testing) and Moyi
in the public App Store and Google Play. Written 2026-11-14 against `main` at `8e74ea5`.

`docs/release-checklist.md` covers the step before this one (a release build, signing,
the first TestFlight and internal-track uploads). This file starts where that one ends and
does not repeat it, except where an item there is still open and blocks submission.

## How to use this checklist

- Work top to bottom. Section 1 is what would get Moyi rejected (or refused at upload)
  today; nothing after it matters until those rows are done.
- Tick a box in the PR that does the work, and change its **Status** in the same PR. Add
  new items here, not in a side list.
- **Who**: **Owner** (accounts, money, legal text, store consoles, the website),
  **Claude PR** (code or docs in this repo, through a PR), **Backend** (a backend session in
  `../moyi-backend`; this repo never writes there, it records the ask in
  `docs/backend-contract-gaps.md`).
- **Status**, and how it was established on 2026-11-14:

  | Mark | Meaning |
  |---|---|
  | **Code** | Verified in code: read in this repository at `8e74ea5` |
  | **Live** | Checked live: `curl` against getmoyi.app or api.getmoyi.app on 2026-11-14 |
  | **Docs** | Read in docs (`../documents/`, `docs/`, an issue or PR), not checked in code |
  | **Web** | The store rule itself, looked up on Apple's or Google's site on 2026-11-14 |
  | **Unknown** | Not checkable from here (a console setting, a device test, an email) |

- The store listing copy in section 5 is **DRAFT**. Nothing in it is approved until the
  owner says so; it follows `../.claude/skills/moyi-design-system/references/copy.md` (no
  em or en dashes, no exclamation marks, "bond" never "space").

---

## 1. Blockers: these would stop Moyi today

Ordered by how early they bite (upload, then review, then the production step).

### Both stores

- [ ] **B1. A live privacy policy at `https://getmoyi.app/privacy`.**
  Who: Owner (text and site). Status: **Live**, missing. The URL returns `404`, and
  `GET https://api.getmoyi.app/api/v1/meta/legal` reports Terms, Privacy and the
  data-processing summary as `"version": "unpublished"`.
  Both stores require a privacy policy URL in the listing, and Apple also requires it
  inside the app (5.1.1(i)). The app already links it from sign-up and from Bond settings ›
  Terms and Privacy (**Code**: `register_privacy`, `settings_row_legal`), so those links
  open a 404 today. The policy must name every processor: doc 09 §"Subprocessors" lists
  Hetzner, Cloudflare, Firebase, Resend, Sentry and Grafana Cloud, but **not AWS
  Rekognition**, which moderates photos (**Docs**). Tracked in issue #96.
  Links: [App Review 5.1.1](https://developer.apple.com/app-store/review/guidelines/#data-collection-and-storage),
  [Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- [ ] **B2. Live Terms at `https://getmoyi.app/terms`** (and `/data-processing`, which
  `/meta/legal` names). Who: Owner. Status: **Live**, `/terms` returns `404`. Sign-up asks
  people to agree to them ("I am 18 or over, and I agree to the terms below", **Code**), so a
  reviewer who taps the link sees a dead page.
- [ ] **B3. A demo bond for the reviewers.** Who: Owner (creates the accounts on
  production), Claude PR (can write the review-notes text, section 5.4). Status:
  **Unknown**, none exists that this repo knows of (issue #96 lists it, **Docs**).
  Moyi cannot be tested alone: a reviewer who signs up sees an empty bond waiting for an
  invite. Doc 20 §4 calls this "the single most likely cause of rejection". Needed: two
  accounts, email verified, already in one `ACTIVE` bond, with a few revealed days in the
  Archive (a photo and a voice note among them). Give the reviewer **both** sets of
  credentials, so they can write as each person and see a reveal on the same day without
  waiting for the owner. Apple 2.1(a) requires it; Play calls it "App access".
  Links: [App Review 2.1](https://developer.apple.com/app-store/review/guidelines/#app-completeness),
  [Play app access](https://support.google.com/googleplay/android-developer/answer/15748846).

### App Store

- [ ] **B4. A privacy manifest (`PrivacyInfo.xcprivacy`) in the app target.** Who: Claude
  PR. Status: **Code**, missing: there is no `PrivacyInfo.xcprivacy` anywhere in the repo,
  and the app calls a required-reason API itself: `NSUserDefaults` in
  `KeychainTokenStore.kt` (the reinstall check) and `UserDefaultsNotificationAskMemory.kt`
  (#103). That needs `NSPrivacyAccessedAPICategoryUserDefaults` with reason `CA92.1`
  (data only this app reads and writes). Apple reports a missing declaration as
  `ITMS-91053`; reports differ on whether it is still a warning email or a refused upload,
  so treat it as required. **Owner check:** did the App Store Connect processing email for
  build 1.0 (4) list `ITMS-91053`? The Kotlin/Native runtime and Compose Multiplatform may
  call other required-reason APIs (file timestamps, system boot time): the PR should
  generate Xcode's Privacy Report from an archive (Organizer › Generate Privacy Report) and
  declare what it shows. Third-party SDKs: firebase-ios-sdk `13.1.1` (FirebaseMessaging)
  ships its own manifests and signatures (**Web**, Firebase added them in 10.22.0);
  SQLCipher.swift `4.20.0` is **Unknown**, check it appears in the same report.
  Links: [Describing use of required reason API](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api),
  [Third-party SDK requirements](https://developer.apple.com/support/third-party-SDK-requirements/),
  [Kotlin: privacy manifest for iOS apps](https://kotlinlang.org/docs/apple-privacy-manifest.html).
- [ ] **B5. iPad: support it properly, or drop it.** Who: Owner decides, Claude PR does
  either. Status: **Code**, `TARGETED_DEVICE_FAMILY = "1,2"` in `iosApp.xcodeproj`, so the
  app is offered on iPad. Apple then requires iPad 13" screenshots and reviews the app on an
  iPad; no screen has been checked at iPad sizes (**Unknown**). Either set the family to `1`
  (iPhone only, which is what the design covers) or add an iPad pass and screenshots.
  Link: [Screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/screenshot-specifications).
- [ ] **B6. EU trader status (Digital Services Act).** Who: Owner. Status: **Unknown**. The
  owner is in Portugal and the app will be on EU storefronts; App Store Connect refuses EU
  distribution until the account declares whether it is a trader. A trader's address,
  phone and email are shown on the listing. This ties to doc 20 §1 "Decide the controller
  entity". Link: [Manage EU DSA trader requirements](https://developer.apple.com/help/app-store-connect/manage-compliance-information/manage-european-union-digital-services-act-trader-requirements).

### Google Play

- [ ] **B7. A web page for account deletion.** Who: Owner (site), Backend if the page should
  start the deletion itself. Status: **Live**, none: `https://getmoyi.app/delete-account`
  returns `404`, and nothing else on the site offers it. Play requires a web link, entered
  in the Data safety form, where someone who has uninstalled the app can ask for their
  account and data to be deleted; the page must name "Moyi" as on the listing. The in-app
  path already exists (B-OK1 below). The page can be instructions plus a request route (for
  example: open the app › Account › Delete your account, or email support from the account's
  address); it does not need its own API. Link:
  [Understanding Google Play's app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111).
- [ ] **B8. Closed testing: 12 testers for 14 days, if the Play account is personal.** Who:
  Owner. Status: **Unknown** (account type). A personal developer account created after
  13 November 2023 must run a **closed** test (internal testing does not count) with at
  least 12 testers opted in continuously for the 14 days before applying for production
  access. An organisation account (D-U-N-S number) is exempt. PR #109's lane uploads to the
  internal track only (**Docs**). Recruit testers now: this is the longest lead time in
  the file. Link: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465).

### Not blockers, though they look like ones

- **B-OK1. In-app account deletion: present.** **Code**: Account › "Delete your account"
  (`AccountViewModel`, `AccountDeletionRepositoryImpl`) calls
  `POST /api/v1/me/deletion-request` (`requestAccountDeletion` in `contracts/openapi.json`),
  confirmed with the password, cancellable with `DELETE`. Deletion happens 14 days later and
  the screen says so ("Nothing is deleted today. Your account is deleted 14 days from now");
  Apple accepts a delay when the app states it. Reachable without a bond (the person glyph
  opens Account, `strings.xml` note §57). Satisfies Apple 5.1.1(v) and Play's in-app path.
  Link: [Offering account deletion in your app](https://developer.apple.com/support/offering-account-deletion-in-your-app/).
- **B-OK2. Sign in with Apple: not needed.** Guideline 4.8 applies only to third-party or
  social login. Moyi has email and password only; no Google, Apple or other sign-in SDK is
  in `gradle/libs.versions.toml` or the Xcode packages (**Code**). Revisit if FR-010 (Google
  sign-in) ever ships (doc 20 §4).
- **B-OK3. Export compliance: answered.** `ITSAppUsesNonExemptEncryption = false` in
  `Info.plist`, with the reason (HTTPS and SQLCipher, not the app's purpose) (**Code**).
- **B-OK4. Target API level: above the bar.** `android-targetSdk = "37"` (**Code**); Play
  requires 36 for new apps and updates from 31 August 2026 (**Web**,
  [target API level requirements](https://developer.android.com/google/play/requirements/target-sdk)).
- **B-OK5. Xcode 26 SDK: met.** Uploads from 28 April 2026 must be built with Xcode 26
  (**Web**, [upcoming requirements](https://developer.apple.com/news/upcoming-requirements/));
  App Store Connect accepted build 1.0 (4), so the toolchain passes (**Docs**).

---

## 2. Both stores

| ✓ | Item | Who | Status | Link |
|---|---|---|---|---|
| [ ] | **User-generated content and safety** (Apple 1.2; Play UGC policy). Required: filter objectionable content, report, block, published contact. Report an entry and report the other member: **Code** (`ReportViewModel`, `reportEntry`, `reportMember`). Block and leave: **Code** (`blockBond`, "Block and leave"). Contact: in-app support (`sendSupportMessage`, **Code**), plus the support URL (row below). Filter: photos are moderated by AWS Rekognition on the backend (**Docs**, FR-101 / doc 26); **text and voice are not filtered** (**Docs**, doc 26 scopes moderation to images). Moyi's case: a bond is two consenting adults who invited each other, with nothing public. Say so in the review notes (5.4) rather than wait for a rejection to ask | Owner (review notes), Claude PR (draft) | Partly **Code** | [1.2](https://developer.apple.com/app-store/review/guidelines/#user-generated-content), [Play UGC](https://support.google.com/googleplay/android-developer/answer/9876937) |
| [ ] | **Support URL and contact email** shown on both listings | Owner | **Live**: `https://getmoyi.app/support` is `404` | [Apple app information](https://developer.apple.com/help/app-store-connect/reference/app-information) |
| [ ] | **Production backend is on `30f5873` or later** (the commit the contract was synced to), and its rate limits do not lock out reviewers signing in from Apple's or Google's networks (ADR-0023) | Backend | **Unknown** (`/actuator/info` needs auth). Noted in `docs/backend-contract-gaps.md` | `docs/backend-contract-gaps.md` |
| [ ] | **Production Firebase (`moyi-prod`) and push on store builds.** Release builds refuse the development project. Check that TestFlight 1.0 (4) actually receives a push; if it was built without `moyi-prod`, push is inert in it | Owner | **Unknown**; open in `docs/release-checklist.md` §1 | `docs/release-checklist.md` |
| [ ] | **Links open the app**: the live AASA lists no paths (`"components": []`) and no `webcredentials`; `assetlinks.json` needs the Play signing SHA-256 after the first Play upload. Not a rejection reason (links fall back to the browser), but invite links are the way a second person arrives | Owner (site) | **Docs** (gaps rows 34 and 43); AASA and assetlinks return `200` (**Live**) | moyi-backend#157, issue #96 |
| [ ] | **Age: 18+ in the listing as in the app.** Sign-up requires "I am 18 or over" (**Code**). Answer both questionnaires consistently with that (sections 3 and 4) | Owner | **Code** | |
| [ ] | **Screenshots from seeded demo data, never real accounts.** iPhone 6.9" (and iPad 13" if B5 keeps iPad); Play phone screenshots (2 to 8) | Claude PR (capture), Owner (approve) | **Unknown** | |
| [ ] | **App icon final** at 1024 × 1024 (Apple, no transparency) and 512 × 512 (Play) | Owner | **Unknown** (`docs/release-checklist.md` §5) | |
| [ ] | **Version numbers**: 1 / 1.0 on both (`versionCode`, `CURRENT_PROJECT_VERSION`). Each upload needs a higher build number | Claude PR | **Code** | `docs/release-checklist.md` §6 |
| [ ] | **The name on the store.** Doc 19 decided the marked form `Mọyì` for "logo, app name, marketing"; the brief for this file and the installed app (`app_name`, `PRODUCT_NAME`) say `Moyi` (**Code**). Pick one for both listings | Owner | **Docs** vs **Code** disagree | `../documents/19-naming-and-brand.md` |

## 3. App Store

In App Store Connect, in the order the submission page asks.

| ✓ | Item | Who | Status | Link |
|---|---|---|---|---|
| [ ] | **App Privacy (nutrition label).** Proposed answers, all "linked to the user", none "used for tracking", purpose App Functionality: Contact Info (Name: the display name; Email Address); User Content (Photos; Audio Data: voice notes; Other User Content: entries, reactions, reports; Customer Support: support messages); Identifiers (User ID; Device ID if the FCM registration token is counted as one, see Firebase's own disclosure). No crash or analytics SDK in the app (**Code**: none in `libs.versions.toml` or the Xcode packages); the backend uses Sentry for its own errors (**Docs**). The device row sends app and OS version (**Code**, `IosDeviceInfoProvider`): owner decides if that is "Other Diagnostic Data" | Owner (submits), Claude PR (can verify each row against the API) | **Code** for what the app sends; answers not filled | [App privacy details](https://developer.apple.com/app-store/app-privacy-details/), [Firebase data disclosure](https://firebase.google.com/docs/ios/app-store-data-collection) |
| [ ] | **Privacy manifest** | Claude PR | Blocker B4 | |
| [ ] | **Age rating questionnaire** (the 2025 system: 4+, 9+, 13+, 16+, 18+). Answers to expect: user-generated content yes, messaging between users yes (the bond), no unrestricted web, no gambling. Pick **18+** to match the Terms and the sign-up gate | Owner | **Unknown** | [Age ratings values and definitions](https://developer.apple.com/help/app-store-connect/reference/age-ratings-values-and-definitions) |
| [ ] | **App Review information**: both demo credentials (B3), the review notes (5.4), a contact name, phone and email | Owner | **Unknown** | [Submit for review](https://developer.apple.com/help/app-store-connect/manage-submissions-to-app-review/submit-for-review) |
| [ ] | **Permission strings are specific** (Apple rejects vague ones, 5.1.1(ii)). Camera and microphone strings present and say when they are used (**Code**). No photo-library string needed: `PHPickerViewController` (**Code**, `PhotoPicker.ios.kt`) | Claude PR if reworded | **Code** | [5.1.1](https://developer.apple.com/app-store/review/guidelines/#data-collection-and-storage) |
| [ ] | **Background mode `fetch`** is declared for the outbox (`BGTaskSchedulerPermittedIdentifiers`, **Code**). Mention it in review notes if asked; it is a real use | Owner | **Code** | |
| [ ] | **Push entitlement**: `aps-environment` is `development` in the file; the App Store export re-signs it as `production` (**Code**, comment in `iosApp.entitlements`). Confirm in the uploaded build's entitlements (`fastlane ios check_ipa`) | Claude PR | **Code**; upload **Unknown** | `fastlane/README.md` |
| [ ] | **Listing fields**: name, subtitle, promotional text, description, keywords, category, support URL, marketing URL, privacy URL, copyright (draft in section 5) | Owner approves | **DRAFT** | [App information](https://developer.apple.com/help/app-store-connect/reference/app-information) |
| [ ] | **Price and availability**: Free; choose countries. No paid-apps agreement or tax forms needed for a free app without in-app purchases | Owner | **Unknown** | |
| [ ] | **Release option**: manual release after approval, so the website and backend are checked first | Owner | **Unknown** | |
| [ ] | **External TestFlight first** (optional): the public link in issue #96 goes through the same beta review, a cheap rehearsal of B1 to B3 | Owner | **Docs** (#96) | |

## 4. Google Play

In Play Console › App content (Policy), then the store listing, then the tracks.

| ✓ | Item | Who | Status | Link |
|---|---|---|---|---|
| [ ] | **Account type and testing route** | Owner | Blocker B8 | |
| [ ] | **Privacy policy URL** | Owner | Blocker B1 | |
| [ ] | **App access**: "All or some functionality is restricted", with both demo accounts and steps (5.4) | Owner | Blocker B3 | [App access](https://support.google.com/googleplay/android-developer/answer/15748846) |
| [ ] | **Ads**: "No, my app does not contain ads" | Owner | **Code** (no ad SDK) | |
| [ ] | **Content rating (IARC questionnaire)**: category "Social or communication" is the closest; users interact and share content (text, photos, audio) with one other user; no location sharing; no purchases | Owner | **Unknown** | [Content ratings](https://support.google.com/googleplay/android-developer/answer/9898843) |
| [ ] | **Target audience and content**: 18 and over only. Keeps Moyi out of the Families policy | Owner | **Code** (18+ gate) | [Target audience](https://support.google.com/googleplay/android-developer/answer/9867159) |
| [ ] | **Data safety form.** Proposed: collected, not shared (processors acting for Moyi are not "sharing"), encrypted in transit (HTTPS; Release has no cleartext, **Code**), deletion can be requested (in app, and B7's URL). Types: Personal info (Name, Email address, User IDs); Photos and videos (Photos); Audio (Voice or sound recordings); Messages (Other in-app messages: entries and reactions) or App activity (Other user-generated content), owner picks; Device or other IDs (FCM token); App info and performance only if the app version counts (as in the Apple row) | Owner | **Unknown** | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) |
| [ ] | **Account deletion URL** in the Data safety form | Owner | Blocker B7 | |
| [ ] | **Other declarations**: News app no; Government app no; Financial features none; Health none; Advertising ID: no (app does not use it). Check the merged manifest has no `AD_ID` permission from a library | Owner; Claude PR for the manifest check | **Unknown** (merged manifest not built for this file) | [Advertising ID](https://support.google.com/googleplay/android-developer/answer/6048248) |
| [ ] | **Permissions**: `POST_NOTIFICATIONS` (asked after the first entry, #103), `RECORD_AUDIO` (asked at the tap on "Record a voice note"), `INTERNET`, `ACCESS_NETWORK_STATE`. **No** `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `CAMERA`: photos come from the Photo Picker (`PickVisualMedia`) and the system camera app via `FileProvider` (**Code**, `AndroidManifest.xml`, `PhotoPicker.android.kt`), so the Photo and Video Permissions declaration does not apply. Microphone needs no separate declaration, only the Data safety audio row. Confirm the **merged** release manifest adds nothing (a library can) | Claude PR (merged-manifest check) | **Code** for the source manifest | [Photo and video permissions](https://support.google.com/googleplay/android-developer/answer/14115180) |
| [ ] | **16 KB page sizes**: apps targeting Android 15+ must ship native libraries aligned for 16 KB pages. Moyi bundles SQLCipher's native library (`sqlcipher-android 4.20.0`). Check in Play Console › App bundle explorer, or `zipalign -c -P 16` on the release APK | Claude PR | **Unknown** | [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) |
| [ ] | **Play App Signing SHA-256 into `assetlinks.json`** after the first upload | Owner (site) | **Docs** (moyi-backend#157) | |
| [ ] | **Store listing**: title, short description, full description, app icon 512 × 512, **feature graphic 1024 × 500** (required), phone screenshots, category, contact email, privacy URL | Owner approves | **DRAFT** (section 5) | [Store listing assets](https://support.google.com/googleplay/android-developer/answer/9866151) |
| [ ] | **Developer contact details** shown publicly (email always; address for a monetised or organisation account) | Owner | **Unknown** | |
| [ ] | **Release lane**: PR #109 (open) adds `fastlane android beta` for the internal track. Production then goes closed → production with a staged rollout (doc 20 §5: 10%, 50%, 100%) | Claude PR (lanes), Owner (rollout) | **Docs** (#109 not merged) | |

---

## 5. Store listing: DRAFT for owner approval

Nothing below is approved. Lengths are counted; limits are the stores' own. Claims are
kept to what the app does today: no "end-to-end encrypted" (ADR-0009: not in v1), and no
claim that nobody else can ever read an entry (reports and support exist).

### 5.1 Fields

| Field | Store | Limit | DRAFT |
|---|---|---|---|
| Name | Both | 30 | `Moyi` (or `Mọyì`, owner decision in §2) |
| Subtitle | Apple | 30 | `Daily appreciation for two` (26) |
| Short description | Play | 80 | `One thing you appreciate, each day. You read theirs once you have both written.` (79) |
| Promotional text | Apple | 170 | `Moyi is for two people. Each of you writes one thing a day you appreciate about the other, and you read theirs once you have both written.` |
| Keywords | Apple | 100 | `gratitude,journal,couple,partner,diary,thankful,relationship,private,habit,friend,family,love,thanks` (owner checks the count after edits; words in the name and subtitle are indexed already and are left out) |
| Primary category | Both | | Lifestyle (Apple and Play) |
| Secondary category | Apple | | Health & Fitness, or none (owner decision) |
| Support URL | Both | | `https://getmoyi.app/support` (404 today, B1/§2) |
| Marketing URL | Apple | | `https://getmoyi.app` |
| Privacy URL | Both | | `https://getmoyi.app/privacy` (404 today, B1) |
| Account deletion URL | Play | | `https://getmoyi.app/delete-account` (proposed path, 404 today, B7) |
| Copyright | Apple | | `2026 <the controller entity>` (doc 20 §1) |

### 5.2 Full description (both stores, 4000 characters max)

```
Moyi is a gratitude journal for exactly two people.

Each day, you write one thing you appreciate about the other person. They do the same. Neither of you can read the other's entry until you have both written, so what you write is yours, not an answer to theirs.

Then your day opens, and you read what they noticed about you.

Write it your way
A few words, a photo, or a voice note. One entry a day is enough.

Keep what you have both written
Every day you both write is kept in an archive you share. Look back at a week, a month, or this day last year.

For any two people
Partners, friends, family, a parent and a grown child. You invite one person, and the bond is just the two of you.

Gentle by design
Reminders ask, they do not nag. A missed day is a missed day, not a failure. Nothing is public, and there is no feed.

Yours to take or close
Export your archive at any time. Leave a bond, or delete your account, from inside the app.

Moyi is for people aged 18 and over.

The name comes from the Yoruba "mo mọ ìyì rẹ": I know your worth.
```

Owner checks before approving: "Look back at ... this day last year" relies on On this day
being live; "Reminders" relies on push working on store builds (§2, `moyi-prod`);
"Export your archive" relies on export being deployed (gaps rows 17 and 32).

### 5.3 What's new (version 1.0)

```
The first version of Moyi.
```

### 5.4 Review notes (Apple "Notes" and Play "App access" instructions)

```
Moyi is a private journal for two people who have invited each other into one "bond".
Each writes one entry a day, and neither can read the other's until both have written.
The app cannot be used alone, so we have created two accounts that are already in one bond.

Account A: <email> / <password>
Account B: <email> / <password>

To see the daily loop:
1. Sign in as Account A. Today shows that A has not written yet.
2. Write an entry (text, and optionally a photo or a voice note) and send it.
   Today now says A is waiting for B.
3. Sign out (Today, then Bond settings, then Sign out) and sign in as Account B.
4. Write B's entry. The day opens and both entries can be read.
5. The Archive holds earlier days we have written.

Safety: there is nothing public in Moyi. A bond is two adults who invited each other.
Either person can report an entry or the other person, block them (which ends the bond),
leave the bond, or delete their account (the account button at the top right, then Delete
your account; deletion takes
effect after 14 days and can be called off until then). Photos are checked by automated
moderation before the other person sees them. Support is in the app (Bond settings, then
Get help).

Sign-up requires confirming the person is 18 or over.
```

The paths above were read from the string resources (`today_nav_settings`,
`settings_row_sign_out`, `settings_row_help`, `account_row_delete`), not walked on a device.
Owner checks: the demo bond's time zone gives the reviewer a fresh day when they start, and
the photo-moderation sentence matches what production does.

---

## 6. Owner decisions this file needs

1. Store name: `Moyi` or `Mọyì` (§2).
2. iPad: drop it (`TARGETED_DEVICE_FAMILY = 1`) or support it with screenshots (B5).
3. Controller entity and EU trader status (B6, doc 20 §1).
4. Play account type: personal (12 testers for 14 days) or organisation (D-U-N-S) (B8).
5. Age rating: 18+ on both, matching the Terms (§3, §4).
6. Data label edge cases: is the FCM token a "Device ID", is the app version "diagnostic
   data", are entries "messages" or "other user-generated content" (§3, §4).
7. Account deletion web page: instructions only, or a form that starts the deletion
   (B7; the second needs a backend session).
8. Categories and the DRAFT copy in section 5.
