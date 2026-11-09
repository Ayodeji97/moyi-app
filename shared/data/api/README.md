# shared/data/api

Generated code only. The OpenAPI client is generated from `contracts/openapi.json` into
`build/generated/openapi/` by `openApiGenerate` (OpenAPI Generator 7.26.0, `kotlin`
generator, `multiplatform` library, package `com.moyi.app.api`) and is never committed or
edited by hand. Hand-written DTOs are banned (CLAUDE.md, doc 13 §3). Only the generated
`src/commonMain/kotlin` is compiled; every compile task depends on the generation, so a
normal build always regenerates when the contract changes. `check` also runs
`openApiValidate` over the contract.

How to use it, and the traps:

- Build the `*ControllerApi` classes with `ApiConfig.origin` and a client from
  `HttpClientFactory` (`:shared:data:network`). The generated classes never build their
  own client, and we never call their `setBearerToken`.
- Call them through `safeApiCall` / `safeApiCallEmpty`. They check `success` before
  decoding: an error body is a `ProblemDetail`, never the response type. Since the
  2026-11-09 sync the response schemas declare `required`, so a 2xx body missing a field
  fails to decode (`Serialization`) instead of arriving with that field null.
- Error codes are read raw by `ProblemDetailParser`; the generated `ProblemDetail.Code`
  throws on a code it does not know.
- `partnerEntry` on `TodayResponse` and `DayResponse` is the generated sealed class
  `PartnerEntryResponse`: one subclass per `status` (`LockedEntryResponse`,
  `SubmittedPartnerEntryResponse`, `RevealedPartnerEntryResponse`,
  `DeletedPartnerEntryResponse`), chosen while decoding by `@JsonClassDiscriminator("status")`.
  The `status` key is consumed by that choice, so no subclass has a `status` property;
  `when (entry) { is ... }` is how it is read. An unknown status is a decode failure.
