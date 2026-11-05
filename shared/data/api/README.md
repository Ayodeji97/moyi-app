# shared/data/api

Generated code, plus one hand-written file (below). The OpenAPI client is generated from `contracts/openapi.json` into
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
  decoding, because most response schemas declare no `required` fields and an error body
  would otherwise decode as a response with every field null.
- Error codes are read raw by `ProblemDetailParser`; the generated `ProblemDetail.Code`
  throws on a code it does not know.
- `partnerEntry` on `TodayResponse` and `DayResponse` is a `oneOf` the generator cannot
  produce (`docs/backend-contract-gaps.md`, row 10). `schemaMappings` points it at
  `src/commonMain/.../wire/PartnerEntryWire.kt`, the only hand-written file here: a
  dispatcher that reads `status` and decodes one of the GENERATED models. It has no field
  of its own, so it is not a DTO. Delete it when the backend fixes row 10.
