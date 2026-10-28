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
| `:shared:feature:auth` | `com.moyi.app.feature.auth` | Register, verify, sign in |
| `:architecture-tests` | | JVM-only Konsist rules over `shared/**` |
| `build-logic/` | | Convention plugins: `moyi.kmp.library`, `moyi.kmp.compose`, `moyi.dependency.guard` |

Dependencies point inwards: feature to domain, designsystem and core; repository to
domain and the data modules; domain to core only; `:shared` to everything.

Domain stays framework-free two ways. `checkDependencyGuard` (part of `check`) fails if
`:shared:domain` declares anything but `:shared:core`, the Kotlin standard library,
kotlin-test or kotlinx-coroutines. The Konsist rules in `:architecture-tests` fail if a
domain file imports Ktor, SQLDelight, Koin, kotlinx.serialization, Android, AndroidX,
iOS platform APIs or the data layer, or if a feature file imports the data layer.

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
