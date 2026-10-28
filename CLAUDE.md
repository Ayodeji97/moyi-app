# CLAUDE.md

Guidance for Claude Code (and any other agent) working in this repository. It states the
rules that are expensive to get wrong and points at the documents that hold everything
else. **If this file and one of those documents disagree, the document wins and this file
is the bug.**

## What this is

The Kotlin Multiplatform client for **Moyi**, a private gratitude journal for exactly two
people. Each writes one entry a day; neither can read the other's until both have
written. Android and iOS ship together from one codebase (Compose Multiplatform, ~95%
shared UI). Package `com.moyi.app`. The repository is **public**.

The backend lives in the sibling repository `../moyi-backend` (Kotlin, Spring Boot). It is
finished for the daily loop and live at `https://api.getmoyi.app`.

## Sources of truth

Read these before building; they decide, this repository follows.

| For | Read |
|---|---|
| Targets, modules, libraries, UDF pattern, offline-first, auth on the client | `../documents/13-kmp-client-architecture.md` (it says "gratitude-app"; that is this repo) |
| **The API contract** | `../moyi-backend/contracts/openapi.json`, vendored here as `contracts/openapi.json` |
| Endpoints, error envelope, error codes, token lifetimes | `../documents/06-api-specification.md` |
| UX principles, i18n, accessibility | `../documents/14-ux-principles-i18n-and-accessibility.md` |
| Design system (Rosewood): tokens, copy, states | `../.claude/skills/moyi-design-system/` (`tokens.md`, `copy.md`, `states.md`) |
| Every request in the order the app makes them | `../moyi-backend/tools/bruno/` |
| Why the backend does what it does | `../documents/adr/` (auth: ADR-0015, 0018–0025) |
| Where the contract and the docs disagree | `docs/backend-contract-gaps.md` |

## Rules

Each rule carries its reason, so a case it did not foresee can be judged the way it would
have judged it.

**Process**

- **Every change goes through a pull request; never commit or push to `main`.** This is a
  solo project, so review and CI are the only second pair of eyes there is.
- **Do not merge unless the owner says so.** The owner merges; an instruction to merge
  covers the pull requests it names, not the ones after them.
- **Never modify `../moyi-backend`** (or any of its worktrees). Read it; do not write to
  it, run its migrations against a shared database, or push to it. A contract problem
  found from this side is written into `docs/backend-contract-gaps.md` for a backend
  session to fix.
- **Commits follow Conventional Commits, the whole first line at most 88 characters** (type and scope included), checked by CI on
  every commit and on the PR title with its ` (#N)` suffix. Switch the hook on locally:
  `git config core.hooksPath .githooks`.
- **Every PR description has a "Decisions" and a "Concepts worth studying" section.** The
  owner is learning KMP to be hired as a Kotlin engineer; the PR is where each choice is
  explained.

**Code**

- **The network layer is generated from `contracts/openapi.json`. Hand-written DTOs are
  banned** (doc 13 §3). Generated code lives under `build/` and is never committed or
  edited. If a generated type is wrong, the contract is wrong: record it in
  `docs/backend-contract-gaps.md` and map around it in the repository layer.
- **`contracts/openapi.json` is a copy, never hand-edited.** `./gradlew syncContract`
  refreshes it from `../moyi-backend`; changing it is its own PR, because that diff is the
  API changing.
- **`shared/domain` depends on no framework**: no Ktor, SQLDelight, Koin, AndroidX,
  Android or iOS APIs. A Gradle check and a Konsist rule both fail the build if it does.
- **Tokens live in the Keychain (iOS) or an Android-Keystore-encrypted file, never in
  DataStore or preferences.** The access token lives only in memory. Tokens, passwords
  and entry text never reach a log.
- **One refresh at a time.** Access tokens last 15 minutes and refresh tokens rotate
  strictly (reuse signs every session out, ADR-0021). Concurrent 401s share one refresh,
  and the new refresh token is persisted before the new access token is used.
- **The design system wins.** Colours, type, spacing and copy come from the Rosewood
  tokens and `copy.md`; states come from `states.md`. No spinner is invented: waiting is
  shown by the control saying what it is doing (`PrimaryAction` Sending). A component the
  design system lacks is raised with the owner, not improvised.
- **All user-facing text is a string resource.** No concatenation, named placeholders, no
  em or en dashes, no exclamation marks (`copy.md`).

**Configuration**

- **The API base URL is configuration, never hard-coded, and defaults to the LOCAL
  backend**: `http://10.0.2.2:8080/api/v1` from the Android emulator,
  `http://localhost:8080/api/v1` from the iOS simulator. Override it with
  `moyi.apiBaseUrl` in `local.properties`. **Development builds never point at
  production**: real people's data lives there, and the build fails if the base URL's
  host is `getmoyi.app`.

**Reporting**

- **Say what was run and what was only read.** "Verified" means it was executed and seen.
  CI runs on Linux and cannot build iOS, so each PR states whether the iOS framework and
  the Xcode app were built locally.
- **When the spec and the code disagree, say so and stop.** The disagreement is the
  finding.

## Keeping this file true

Change this file in the same PR that changes a rule it states. `AGENTS.md` repeats four
of these rules word for word; when one changes here, change it there in the same commit.
