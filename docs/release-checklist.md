# Release checklist

What stands between `main` and the first build in people's hands, in the order to do it.
Written 2026-11-11, after the daily loop and its polish were merged (#85 to #92). Tick items
in the PR that does them; add items here rather than in a side list.

Owner = only Daniel can do it (accounts, keys, money, signatures). Claude = code or docs, done
through a PR as usual.

## 1. Blockers: no store build is possible yet

- [ ] **Claude: a release build that talks to production.** Every build today is forced onto the
  local backend: `shared/data/network/build.gradle.kts` defaults to `localhost` / `10.0.2.2`
  and fails the build for any `getmoyi.app` host ("There is no release flavor yet, so the
  guard holds for every build; store builds will opt out explicitly"). Add that explicit
  opt-out:
  - release builds only (Android `release` build type, iOS `Release` configuration) use
    `https://api.getmoyi.app/api/v1`;
  - debug builds keep the guard exactly as it is, and CI checks that a debug build can never
    carry the production host;
  - cleartext stays debug-only.
  - CLAUDE.md "Configuration" changes in the same PR.
- [ ] **Owner: a production Firebase project (`moyi-prod`).** Both platforms already refuse
  the development project (`moyi-65b68`) in a release build:
  - Android: `androidApp/build.gradle.kts`, which reads `src/release/google-services.json`;
  - iOS: the build phase reading `iosApp/Firebase/Release/GoogleService-Info.plist`.

  Create `moyi-prod`, add the Android and iOS apps `app.getmoyi.moyi`, upload the APNs key,
  and put both files in those git-ignored paths. The backend needs the same project's
  service account for push.
- [ ] **Owner: finish issue #49** (Android upload key and fingerprints). Check what is still
  unticked there.

## 2. Store accounts and signing (owner)

- [ ] **Google Play Console:**
  - create the app `app.getmoyi.moyi`;
  - enrol in Play App Signing;
  - add the **Play signing key's SHA-256** to `assetlinks.json` next to the upload key's
    (moyi-backend #157). Without it, app links fail on store installs.
- [ ] **App Store Connect:**
  - create the app `app.getmoyi.moyi`;
  - set `TEAM_ID` for release signing (git-ignored `Local.xcconfig`, #13);
  - create the APNs key for `moyi-prod`.
- [ ] **TestFlight:** first archive and upload. Claude can script the archive. The upload
  needs your Apple ID.
- [ ] **Play internal testing track:** first `bundleRelease` upload.

## 3. Links that open the app

- [ ] **Site:** the live AASA lists no paths (`docs/backend-contract-gaps.md` row 34). Add
  `/verify`, `/reset-password` and `/i/*` once the app routes them on both platforms.
- [ ] **Claude:** verify on a phone that tapping a verify, reset and invite link opens the app
  on the right screen, cold and warm.

## 4. Real-phone checks (owner holds the phone, Claude writes the steps)

The simulator and emulator cannot cover these. Several have never been run on Android at all.

- [ ] **Android device or emulator, every screen from #87 to #92.** None of them was run on
  Android: two-step reset, time-zone date, Bond settings cards, More menu, compact Archive,
  Sign out confirmation.
- [ ] **Android notification small icon** (`ic_stat_moyi`, #92): seen in the status bar and
  the shade, in light and dark. It was only rendered off-device.
- [ ] **A real push tapped on each platform** opens the right day.
  - **Blocked by the backend:** a push names no day (gaps row 27, backend #146), so the app
    opens Today for now.
- [ ] **iPhone microphone permission prompt** on first recording (owed since #73).
- [ ] **Voice playback on a real iPhone:** an AAC note (#86) and an old Ogg note.
- [ ] **Screen readers:** VoiceOver and TalkBack through the daily loop. Check the new rows
  (Bond settings cards, Archive rows, More menu) read as one element.
- [ ] **Large text (200%):** Today, Write, Archive rows, Bond settings.
- [ ] **Screen capture:** the partner's words are protected on Today and in the Archive day
  view, but **not** in the Archive list (`TODO(#17)` in `ArchiveScreen.kt`). Decide before
  release: protect the list, or accept the gap.

## 5. Store listing (owner, with Claude drafting)

- [ ] Name, subtitle, description and keywords. Claude drafts them in the copy.md voice.
- [ ] Screenshots: iPhone 6.9" and 6.5", Android phone. Claude can capture them from the
  simulator and emulator with seeded demo data, never real accounts.
- [ ] App icon at store sizes. Check the source artwork is final.
- [ ] Privacy:
  - Apple privacy nutrition labels;
  - Google Play Data safety form;
  - the privacy policy URL, the same document `/meta/legal` serves.
- [ ] Age rating questionnaires. User-generated content between two people: say how report
  and block work (#58).
- [ ] Support URL and contact email.

## 6. Paperwork in this repo and the parent repo (Claude)

- [ ] **Design-system amendments.** `docs/missing-components.md` §71 to §80 hold PROPOSED
  strings and components that the owner has approved:
  - the grouped card and chevron rows;
  - the More menu;
  - "You both wrote today.";
  - the offline line;
  - the sign-out dialog;
  - the notification icon.

  Write them into the parent repo's `copy.md`, `tokens.md`, `states.md` and `SKILL.md`, then
  mark the sections done here.
- [ ] **Version numbers:** `versionCode` / `versionName` (Android) and
  `CURRENT_PROJECT_VERSION` / `MARKETING_VERSION` (iOS) are 1 / 1.0. Decide on a scheme, and
  bump them per upload.
- [ ] **Stale branches:** delete `feat/push-registration` and `spike/first-impression` with
  their worktrees.

## 7. Backend-side items the app is waiting on

None of these blocks a first release. The app already handles each one's absence.

- Row 27 / backend #146: a push names no day.
- Rows 16, 17, 32: account deletion and export doc lag. Both are built in the app (#63).
- Rows 36, 37: docs disagree with the contract. The contract was followed.
