# Moyi (app)

The Kotlin Multiplatform client for Moyi: Android and iOS from one codebase, with
Compose Multiplatform UI. Package `com.moyi.app`. The rules for working here are in
[CLAUDE.md](./CLAUDE.md); the architecture is `../documents/13-kmp-client-architecture.md`.

## Modules

| Gradle path | Namespace | What it holds |
|---|---|---|
| `:androidApp` | `com.moyi.app` | Android shell: Activity, manifest, FCM service |
| `iosApp/` (Xcode) | | iOS shell; links the `Shared` framework, SQLCipher (the `SQLCipher.swift` package) and FirebaseMessaging (`firebase-ios-sdk`) |
| `:shared` | `com.moyi.app.shared` | Umbrella: `App()`, and the one iOS framework (`Shared`, static) |
| `:shared:core` | `com.moyi.app.core` | `Result`, `DataError`, shared utilities |
| `:shared:domain` | `com.moyi.app.domain` | Models and repository interfaces. **No framework dependencies** |
| `:shared:designsystem` | `com.moyi.app.designsystem` | Theme, tokens and components (Rosewood, PR #5) |
| `:shared:data:api` | `com.moyi.app.data.api` | Generated OpenAPI client only (PR #3) |
| `:shared:data:network` | `com.moyi.app.data.network` | HttpClient, auth, error mapping |
| `:shared:data:session` | `com.moyi.app.data.session` | Token storage and the session |
| `:shared:data:database` | `com.moyi.app.data.database` | SQLDelight 2.x local database (Today's cache, drafts, outbox), encrypted at rest with SQLCipher; wiped at every session change |
| `:shared:data:repository` | `com.moyi.app.data.repository` | Repository implementations |
| `:shared:data:testing` | `com.moyi.app.data.testing` | Test fixtures for the data modules' tests only (fake store, MockEngine helpers, in-memory database) |
| `:shared:feature:auth` | `com.moyi.app.feature.auth` | Register, verify, sign in |
| `:shared:feature:onboarding` | `com.moyi.app.feature.onboarding` | The promise, relationship type, create bond, invite handoff, join (UI on fake state, PR #7) |
| `:architecture-tests` | | JVM-only Konsist rules over `shared/**` |
| `build-logic/` | | Convention plugins: `moyi.kmp.library`, `moyi.kmp.compose`, `moyi.dependency.guard` |

Dependencies point inwards: feature to domain, designsystem and core; repository to
domain and the data modules; domain to core only; `:shared` to everything.

Domain stays framework-free two ways. `checkDependencyGuard` (part of `check`) fails if
`:shared:domain` declares anything but `:shared:core`, the Kotlin standard library,
kotlin-test or kotlinx-coroutines. The Konsist rules in `:architecture-tests` fail if a
domain file imports Ktor, SQLDelight, Koin, kotlinx.serialization, Android, AndroidX,
iOS platform APIs or the data layer, or if a feature file imports the data layer.

## The session

The access token lives only in memory; the refresh token sits in the iOS Keychain or an
Android-Keystore-encrypted file. `SessionManager` (`:shared:data:session`) is the one place
that refreshes: concurrent `401`s share one refresh behind a `Mutex`, the new refresh token
is stored before the new access token is used, and only `TOKEN_REUSE_DETECTED` or
`REFRESH_TOKEN_INVALID` signs the person out. Auth endpoints use a public `HttpClient`
without the bearer plugin; everything else uses the authed one. The design, and the
accepted crash window between a rotation and its write, are in
[docs/session.md](./docs/session.md).

## Commands

```sh
./gradlew build                                  # everything, both platforms' Kotlin
./gradlew check                                  # tests, architecture rules, dependency guard, lint
./gradlew :androidApp:assembleDebug              # Android debug APK
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # iOS framework for the simulator

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
```

CI runs `./gradlew check` on Linux, which cannot link iOS; each PR says whether the iOS
framework and the Xcode app were built locally.

The local database is encrypted with SQLCipher on both platforms. Android gets it from
`net.zetetic:sqlcipher-android`; the Xcode app from the `SQLCipher.swift` Swift package
(Xcode resolves it on the first build, so that build needs the network); the iOS test
executables from the same xcframework, which `./gradlew` downloads and checks by SHA-256
(`:sqlcipherXcframework`). Nothing links the system SQLite (`-lsqlite3`). FirebaseMessaging calls
the `sqlite3_*` API too and links no SQLite of its own, so in the app those calls also bind
to SQLCipher (`nm -m` on `Moyi.debug.dylib` shows every `_sqlite3_*` "from SQLCipher"). The Android
encryption tests need a device or emulator:
`./gradlew :shared:data:database:connectedAndroidDeviceTest`.

### UI preview (debug only)

The onboarding and daily-loop screens run on fake state, so they can be clicked through
with no backend and no session. Build with `-PmoyiUiPreview=onboarding` and the app opens on
a preview index (`shared/src/commonMain/kotlin/com/moyi/app/debug/UiPreviewIndex.kt`)
instead of deciding from the session; build with `-PmoyiUiPreview=today` and it opens on
Today with its debug gestures (`docs/figma/today-check.md`, "Reaching the screens"):

```sh
./gradlew :androidApp:installDebug -PmoyiUiPreview=onboarding
./gradlew :androidApp:installDebug -PmoyiUiPreview=today
```

The switch is one string, `UiPreviewConfig.UI_PREVIEW` in `:shared`. Its allowed values
are one list in `shared/build.gradle.kts` (`""`, off, `onboarding` and `today`); any other value
fails the build. Only the command line counts: a value in `gradle.properties`,
`ORG_GRADLE_PROJECT_moyiUiPreview` or `local.properties` is ignored, because those persist
and would ride along into the next build.

With it set, the build fails unless it is plainly a debug build: every requested task
must contain `Debug`, or the only task must be Xcode's `embedAndSignAppleFrameworkForXcode`
with `CONFIGURATION=Debug`. So `assemble`, `build`, `assembleRelease`, an Xcode Release
build, and any build with `CI` set all fail at configuration.

**On iOS**, Xcode cannot pass the switch through the environment. Locally, add
`-PmoyiUiPreview=onboarding` (or `=today`) to the `./gradlew` line of the iosApp target's
"Compile Kotlin Framework" build phase, and build the Debug configuration. **Never commit
that change**: the build phase is shared by every build of the app.

In the join flow, the code `ZZZZZZ` plays "cannot be used" and any other well-formed code
resolves. Build again without the switch to get the normal start back.

## The API contract

`contracts/openapi.json` is a byte copy of `../moyi-backend/contracts/openapi.json`, so
CI and fresh clones build without the backend. The network client in `:shared:data:api`
is generated from it on every build.

```sh
./gradlew syncContract          # copy the backend's contract over the vendored one
./gradlew checkContractInSync   # fail if they differ (part of check; skipped if the backend is absent)
```

A contract change is its own PR: that diff is the API changing. The backend is read, never
written. A git worktree of this repo does not sit next to `moyi-backend`; point the tasks
at it with `moyi.backendDir=/path/to/moyi-backend` in `local.properties` or `-P`.

## Which backend the app talks to

Development builds talk to a **local** backend:

| Running on | Default base URL |
|---|---|
| Android emulator | `http://10.0.2.2:8080/api/v1` (the emulator's name for the Mac) |
| iOS simulator | `http://localhost:8080/api/v1` |

Override it for both platforms in `local.properties` (not committed), or once with
`-Pmoyi.apiBaseUrl=...`:

```properties
moyi.apiBaseUrl=http://localhost:8080/api/v1
```

The value keeps the `/api/v1` ending, the same as Bruno. **The build fails if the host is
`getmoyi.app` or one of its subdomains**: real people's data lives there.

Real devices:

- **Android phone over USB:** run `adb reverse tcp:8080 tcp:8080`, then set
  `moyi.apiBaseUrl=http://localhost:8080/api/v1`. The phone's port 8080 is forwarded to
  the Mac.
- **iPhone on the same Wi-Fi:** set `moyi.apiBaseUrl=http://<mac-name>.local:8080/api/v1`
  (the name is in System Settings, General, Sharing, Local hostname). Allow incoming
  connections for `java` in the macOS firewall, and accept the Local Network prompt on
  the phone. `Info.plist` allows local networking (`NSAllowsLocalNetworking`) and carries
  the prompt's text (`NSLocalNetworkUsageDescription`).

**Cleartext on Android (debug only).** The local backend speaks plain HTTP, and so does the
MinIO it signs photo and voice URLs for (`http://<the Mac's LAN address>:9000`). Android
refuses cleartext unless the host is listed, so the debug build generates its
`network_security_config.xml` (task `:androidApp:generateDebugNetworkSecurityConfig`) and
allows plain HTTP to these hosts and no others:

- `10.0.2.2`, `localhost` and `127.0.0.1` (the emulator's Mac, and `adb reverse`);
- the host of `moyi.apiBaseUrl`, so a phone on the same Wi-Fi reaches the Mac;
- `moyi.devMediaHosts`, comma-separated, for a media host the base URL does not name. On
  the emulator's default base URL, MinIO still signs for the LAN address:

```properties
moyi.devMediaHosts=192.168.1.70
```

A host that is `getmoyi.app`, one of its subdomains or production's address fails the
build, like the base URL check. Release builds have no such file: Android's default, HTTPS
only. If photos or voice notes stay empty and logcat says `CleartextNotPermittedException`,
the host in the message is missing from this list.

**Signing for a real iPhone.** Create `iosApp/Configuration/Local.xcconfig` (git-ignored)
with one line, your Team ID from developer.apple.com, Account, Membership details (or
Xcode, Settings, Accounts):

```
TEAM_ID=ABCDE12345
```

`Config.xcconfig` includes it if present, so the ID never enters the repository. The bundle
identifier is `app.getmoyi.moyi`, registered to the owner's team; on another team, override
it in `Local.xcconfig` (`PRODUCT_BUNDLE_IDENTIFIER=...`) to run on your own phone. Without the file, simulator
builds still work with ad hoc signing (`docs/session.md`).

The app carries the Associated Domains entitlement `applinks:getmoyi.app`
(`iosApp/iosApp/iosApp.entitlements`), so the App ID must have the Associated Domains
capability: automatic signing turns it on for an ID on your own paid team, and a free
Personal Team cannot sign it at all. To run on a phone from such a team, also set
`CODE_SIGN_ENTITLEMENTS=` (empty) in `Local.xcconfig`.

**Links that open the app.** `https://getmoyi.app/verify?token=…`, `/reset-password?token=…`
and `/i/<code>` open their screens (`DeepLink.parse`, `LinkRouting`): on Android through the
`autoVerify` intent filter in `AndroidManifest.xml`, verified against the site's
`assetlinks.json`; on iOS through the entitlement above and the site's AASA, which must list
the three paths (moyi-backend#157). Neither verification passes for a debug build (debug
key, no AASA paths yet), so test them by hand:

```sh
# Android: name the package, so the unverified link still reaches the app
adb shell am start -a android.intent.action.VIEW -d "https://getmoyi.app/i/7KQ4MZ" app.getmoyi.moyi
# iOS simulator, debug builds only: a cold launch with the link handed over
xcrun simctl launch --terminate-running-process booted app.getmoyi.moyi -moyiOpenLink "https://getmoyi.app/i/7KQ4MZ"
```

**Android release signing.** The release build is signed with the upload key only where
these four Gradle properties exist, which is the owner's `~/.gradle/gradle.properties`,
never the repository (issue #49). Without them, as on CI, `assembleRelease` still builds and
the APK is simply unsigned.

```properties
MOYI_UPLOAD_STORE_FILE=/absolute/path/to/moyi-upload.jks
MOYI_UPLOAD_STORE_PASSWORD=...
MOYI_UPLOAD_KEY_ALIAS=moyi-upload
MOYI_UPLOAD_KEY_PASSWORD=...
```

## Push notifications

Push goes through Firebase Cloud Messaging (FCM) on both platforms; on iOS, FCM delivers
through APNs. The app registers its FCM token with the server
(`PUT /me/devices/current/push-token`, `PushTokenRegistrar`) whenever someone is signed in.

**Firebase's config files are never committed**: the repository is public, and
`.gitignore` ignores both names everywhere. Each checkout that should receive push needs its
own copies, **one per build type**, so a release build can never talk to the development
Firebase project:

| File | Debug (Firebase project `moyi-65b68`) | Release (Firebase project `moyi-prod`) |
|---|---|---|
| Android `google-services.json` | `androidApp/src/debug/google-services.json` | `androidApp/src/release/google-services.json` |
| iOS `GoogleService-Info.plist` | `iosApp/Firebase/Debug/GoogleService-Info.plist` | `iosApp/Firebase/Release/GoogleService-Info.plist` |

Where to get them: the Firebase console, the project, Project settings, General, Your apps.
The Android app is `app.getmoyi.moyi` (download `google-services.json`); the iOS app is the
bundle ID `app.getmoyi.moyi` (download `GoogleService-Info.plist`). The `moyi-prod` project
does not exist yet, so release builds have no push for now.

**Without the files the app still builds and runs, without push.** That is how CI builds.

- Android: the google-services Gradle plugin is applied always, with
  `missingGoogleServicesStrategy = WARN` (`androidApp/build.gradle.kts`): a variant with no file
  gets a warning and no Firebase resources, Firebase never starts (`FirebasePush`), and
  `MoyiMessagingService` is never called. The plugin looks in `src/<buildType>/` and then in
  `androidApp/` itself; a file left in `androidApp/` would be used by release too, so a
  release build fails if the file it would use is `moyi-65b68`'s.
- iOS: the "Copy Firebase Config" build phase copies the configuration's plist into the app,
  or removes it when there is none. `AppDelegate` (`NotificationDelegate.swift`) starts
  Firebase only when the plist is in the bundle. A Release build fails if its plist is
  `moyi-65b68`'s. Firebase itself is the `firebase-ios-sdk` Swift package (product
  `FirebaseMessaging`), resolved by Xcode on the first build like `SQLCipher.swift`.

The iOS app carries the `aps-environment` entitlement (Push Notifications), so the App ID
needs that capability: automatic signing turns it on for an ID on your own paid team. On a
free Personal Team, set `CODE_SIGN_ENTITLEMENTS=` in `Local.xcconfig` as above.

**Sending a real push from the local backend.** The local backend's push provider is `log`:
it writes `Push NOT sent (provider=log): category=… bond=…` instead of sending. To send for
real, run it with a service account of `moyi-65b68` (Firebase console, Project settings,
Service accounts; never commit the key):
`MOYI_NOTIFICATION_PUSH_PROVIDER=fcm`, `MOYI_NOTIFICATION_PUSH_FCM_PROJECT_ID=moyi-65b68`,
`MOYI_NOTIFICATION_PUSH_FCM_CLIENT_EMAIL` and `MOYI_NOTIFICATION_PUSH_FCM_PRIVATE_KEY_PEM`.
A real iPhone and an Android emulator with a Google Play image can receive it; an iOS
simulator receives APNs pushes too (Apple silicon), but only through APNs' sandbox.

## Legal page URLs

Sign up's **Terms** and **Privacy Policy** links, and the Terms and Privacy screen (from Bond
settings, and the re-consent step over Today), open the URLs `GET /meta/legal` names, in the
in-app browser (Chrome Custom Tabs on Android, `SFSafariViewController` on iOS). Nothing is
configured on the app side: a deployment states its documents in the backend's
`moyi.legal.*` settings (ADR-0056). The local backend names `http://localhost:3000/...`
pages, which nothing serves, so on a simulator or emulator the browser opens and shows that
the page cannot be reached.
