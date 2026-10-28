# Backend contract gaps

Places where `contracts/openapi.json` (backend `920ff6a`, 2026-10-22) and the documents
disagree, found from the client side. This repository never modifies `../moyi-backend`;
each gap is written here for a backend session to fix, together with how the client copes
until then. Checked with `jq` against the contract, not only read.

| # | Gap | Doc says | Contract says | Client meanwhile |
|---|---|---|---|---|
| 1 | `POST /auth/login` has no `401` | `06` §3.1: every refusal is `401 INVALID_CREDENTIALS` | responses `200, 400, 422, 429, 500` | Decodes `application/problem+json` on **any** non-2xx status, declared or not |
| 2 | `POST /auth/refresh` has no `401` | `06` §3.1: `401 TOKEN_REUSE_DETECTED` / `REFRESH_TOKEN_INVALID` | responses `200, 400, 422, 429, 500` | Same as 1; only these two codes end the session |
| 3 | `POST /auth/verify-email` has no `410` | `06` §3.1: `410 VERIFICATION_TOKEN_EXPIRED` (expired or already used) | responses `200, 400, 422, 429, 500` | Same as 1; switches on `code`, not status |
| 4 | `LoginResponse` declares no `required` | the four fields are always present | `required` absent, so every field is nullable in generated code | Repository mapper treats a missing token as a serialization error; success is checked before decoding the body |
| 5 | `UserResponse` declares no `required` | `id`, `email`, `displayName`, `emailVerified`, `status` always present | `required` absent | Same as 4 |
| 6 | `ProblemDetail` has no `traceId` | `06` §2: every error body carries `traceId` | properties `code, detail, errors, instance, status, title, type` | Not surfaced; logged if present via raw JSON |
| 7 | `servers[0].url` is `http://localhost` and paths carry `/api/v1` | `06` §1: base URL ends in `/api/v1` | server has no path; every path starts `/api/v1` | Config keeps the `…/api/v1` form; one tested function derives the origin for the generated client |
| 8 | `DeviceRequest.platform` is a `pattern`, not an `enum` | `ANDROID \| IOS \| WEAR` | `pattern: "ANDROID\|IOS\|WEAR"` | Generated as `String`; the client sends constants |
| 9 | `RegisterRequest.over18` is optional | sign-up requires the 18+ confirmation | not in `required` | Sent as the consent box's value; the action stays disabled until it is checked |
| 10 | `DayResponse.partnerEntry` / `TodayResponse.partnerEntry` is a `oneOf` with a `status` discriminator whose mapping sends three statuses to `EntryResponse` | `06` §3.4: the partner's entry is locked, revealed or erased, told apart by `status` | OpenAPI Generator 7.26 (`kotlin`/`multiplatform`) emits a sealed class with **no subclasses**, so every non-null `partnerEntry` throws on decode (spike, 2026-10-27, MockEngine). kotlinx polymorphism cannot map several discriminator values to one class either | Not used before M6 (Today and Archive come later), so left as generated. When the Today screen is built: `schemaMappings` `DayResponse_partnerEntry` (the raw inline name, not `DayResponsePartnerEntry`) to a hand-written sealed `PartnerEntry` with a `JsonContentPolymorphicSerializer` that switches on `status` |

Suggested backend fix for 1–3 and 6: declare the 401/410 responses with the
`ProblemDetail` schema on those operations, add `traceId` to `ProblemDetail`, and mark the
always-present response fields `required` (springdoc: non-null Kotlin properties, or
`@Schema(requiredMode = REQUIRED)`). That is a contract change, so it carries the
backend's `breaking-api-change` review.

Suggested backend fix for 10: give each partner-entry variant its own schema and one
discriminator value (`LOCKED`, `REVEALED`, `ERASED`, and so on), so the generated sealed
class has one subclass per value and the client needs no hand-written serializer.
