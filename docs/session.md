# The session on the client

How the app keeps someone signed in, and why it is built this way. The rules are in
CLAUDE.md (Code); the server's side is ADR-0019 to ADR-0021 and doc 06 §3.1.

## Who holds what

| What | Where | Lifetime |
|---|---|---|
| Access token (15 minutes) | `SessionManager`, in memory only | until the process dies or the session ends |
| Refresh token (30 days, rotates on every use) | `SecureTokenStore` | until sign-out, revocation or reinstall |
| The signed-in user (id, email, name) | stored next to the refresh token | the same |

`SecureTokenStore` has two implementations:

- **iOS:** one Keychain generic-password item (service `com.moyi.app.session`), readable
  after the first unlock, never backed up or moved to another device. The Keychain
  survives deleting the app, so the first launch after an install (no `moyi.installed`
  flag in UserDefaults) wipes it.
- **Android:** a file in `noBackupFilesDir`, AES-256-GCM encrypted under a non-exportable
  Android Keystore key, written through `AtomicFile`. If it ever fails to decrypt (key
  invalidated, file tampered or restored) the file and key are deleted and the person is
  signed out.

## One refresh at a time

Refresh tokens rotate strictly: presenting one that was already used revokes the whole
session family and emails the person (ADR-0021, no grace window). Two refreshes of the
same token from this app would sign the person out everywhere. So:

1. Every refresh goes through `SessionManager.refresh(staleAccess)`, behind a `Mutex`.
2. Inside the lock, if the current access token is no longer `staleAccess`, someone ahead
   in the queue already refreshed; their token is returned and nothing is sent.
3. Otherwise one `POST /auth/refresh` goes out on the **public** client.
4. On success the new refresh token is **written to the store first**, then the new access
   token is published. Nothing can use the new access token while the only copy of the
   new refresh token is in memory.
5. Only `401 TOKEN_REUSE_DETECTED` and `401 REFRESH_TOKEN_INVALID` end the session (store
   and memory wiped, `SessionEvent.Ended` emitted). Offline, a timeout or a 5xx keeps
   everything; the request that needed the refresh fails with that error.

The authed `HttpClient` uses Ktor's bearer plugin with `cacheTokens = false` (the session
holds the only copy of the access token, so no Ktor cache can go stale at sign-out) and
reads the stale token from the refused request's own `Authorization` header. Ktor also
coalesces concurrent refreshes itself; the `Mutex` is what covers cold start, which does
not go through Ktor, and makes the guarantee ours and tested.

Login, register, verify, resend, refresh and logout use the public client, which has no
Auth plugin: a wrong password's `401` must never spend the refresh token.

## The crash window (accepted)

Between the server rotating the refresh token and the app writing the new one, there is a
moment where only the server knows the new token. If the app dies exactly then (killed,
crashed, phone off), the next launch presents the old token, which the server reads as
reuse: that session family is revoked and the person is emailed and signed out.

This is accepted, not fixed:

- ADR-0021 chose strict rotation with no grace window; a window would weaken reuse
  detection exactly where it matters.
- The window is a few milliseconds per refresh, at most once every 15 minutes of use.
- The client shrinks it where it can: the refresh and the write run in a `NonCancellable`
  block (closing a screen cannot abandon a refresh that is on the wire), and a write that
  fails ends the session locally instead of keeping a token that would trip reuse later.

If people report being signed out for no reason, measure how often this happens before
asking the backend for a window (ADR-0021, "Revisit when").
