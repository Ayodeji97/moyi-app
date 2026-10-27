## What & why

<!-- One or two sentences. The diff shows what; explain why. -->

## Decisions

<!-- Each non-obvious choice, the alternative, and why this one. -->

## Concepts worth studying

<!-- The KMP / Kotlin / Android / iOS ideas this PR uses that an interviewer would ask about. -->

## Verified

<!-- What was RUN (and seen), what was only read. CI is Linux-only: say whether these ran locally. -->

- [ ] `./gradlew build`
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
- [ ] `xcodebuild` of `iosApp` for the simulator
- [ ] Ran on the Android emulator / iOS simulator against the local backend (if it touches a screen or the network)

## Definition of Done

- [ ] No hand-written DTOs; generated client only (doc 13 §3)
- [ ] `shared/domain` still depends on no framework (Gradle guard + Konsist green)
- [ ] No token, password or entry text can reach a log
- [ ] Strings are resources and follow `copy.md` (no dashes, no exclamation marks)
- [ ] Colours, type and spacing come from design-system tokens
- [ ] Error and offline cases handled and tested, not only the happy path
- [ ] Base URL still defaults to the local backend; nothing points at production
- [ ] `CLAUDE.md` updated if a rule it states changed (and `AGENTS.md`, if it is one of the four repeated there)
- [ ] `docs/backend-contract-gaps.md` updated if a contract problem was found
