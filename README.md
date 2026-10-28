# Moyi (app)

The Kotlin Multiplatform client for Moyi: Android and iOS from one codebase, with
Compose Multiplatform UI. Package `com.moyi.app`. The rules for working here are in
[CLAUDE.md](./CLAUDE.md); the architecture is `../documents/13-kmp-client-architecture.md`.

## Modules

| Gradle path | Namespace | What it holds |
|---|---|---|
| `:androidApp` | `com.moyi.app` | Android shell: Activity, manifest |
| `iosApp/` (Xcode) | | iOS shell; links the `Shared` framework |
| `:shared` | `com.moyi.app.shared` | Umbrella: `App()`, and the one iOS framework (`Shared`, static) |
| `:shared:core` | `com.moyi.app.core` | `Result`, `DataError`, shared utilities |
| `:shared:domain` | `com.moyi.app.domain` | Models and repository interfaces. **No framework dependencies** |
| `:shared:designsystem` | `com.moyi.app.designsystem` | Theme, tokens and components (Rosewood, PR #5) |
| `:shared:data:api` | `com.moyi.app.data.api` | Generated OpenAPI client only (PR #3) |
| `:shared:data:network` | `com.moyi.app.data.network` | HttpClient, auth, error mapping |
| `:shared:data:session` | `com.moyi.app.data.session` | Token storage and the session |
| `:shared:data:repository` | `com.moyi.app.data.repository` | Repository implementations |
| `:shared:data:testing` | `com.moyi.app.data.testing` | Test fixtures for the data modules' tests only (fake store, MockEngine helpers) |
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
  the Mac. Debug builds allow cleartext HTTP to `10.0.2.2`, `localhost` and `127.0.0.1`
  only (`androidApp/src/debug/res/xml/network_security_config.xml`).
- **iPhone on the same Wi-Fi:** set `moyi.apiBaseUrl=http://<mac-name>.local:8080/api/v1`
  (the name is in System Settings, General, Sharing, Local hostname). Allow incoming
  connections for `java` in the macOS firewall, and accept the Local Network prompt on
  the phone. `Info.plist` allows local networking (`NSAllowsLocalNetworking`) and carries
  the prompt's text (`NSLocalNetworkUsageDescription`).
