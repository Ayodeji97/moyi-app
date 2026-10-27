# AGENTS.md

Guidance for coding agents working in this repository.

**The rules, sources of truth and pointers are in [`CLAUDE.md`](CLAUDE.md). Read it
first.** It is written for any agent, not only Claude; this file exists so tools that look
for `AGENTS.md` find the same guidance without a second full copy to keep true.

Four of its rules are repeated here, in its own words, for tools that read only this
file. `CLAUDE.md` is the source; if the two ever differ, it is right and this file is stale.

1. Every change goes through a pull request; never commit or push to `main`.
2. Do not merge unless the owner says so.
3. Never modify `../moyi-backend` (or any of its worktrees).
4. The network layer is generated from `contracts/openapi.json`. Hand-written DTOs are
   banned (doc 13 §3).
