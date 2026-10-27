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
  flag in UserDefaults) wipes it. Before the first unlock after a reboot the Keychain
  answers `errSecInteractionNotAllowed` (-25308); that is read as "unavailable", not as
  "no session".
- **Android:** a file in `noBackupFilesDir`, AES-256-GCM encrypted under a non-exportable
  Android Keystore key, written through `AtomicFile`. Only a failure that proves the key
  or the payload is gone (`AEADBadTagException`, `KeyPermanentlyInvalidatedException`,
  `UnrecoverableKeyException`, a missing key, a payload that fails its format checks or
  does not decode; see `shouldWipe`) deletes the file and key and signs the person out.
  Anything else (`ProviderException`, `KeyStoreException`, `IOException`) may be
  transient and is read as "unavailable".

"Unavailable" is `TokenStoreUnavailableException`. It never signs anyone out:

- `SessionManager.restore()` leaves the state `Unknown` and sends nothing. `restore()` is
  idempotent (a decided state is returned without work; an `Unknown` one tries again),
  so the app shell calls `AuthRepository.restoreSession()` again when the app returns to
  the foreground while the state is `Unknown`.
- A refresh during a request fails that request (`DataError.Network.Unknown`) and keeps
  the session.
- Sign-out still wipes this device; the server is not told, and the family expires.

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
   everything; the request that needed the refresh fails with that error. Keeping the
   old refresh token is the only move available, but it is not always harmless: see
   "The lost response" below.
6. A `401` for an access token that was not issued in the current session (a request
   sent as the account that just signed out) gets no token: it is not retried as the new
   account and spends no refresh.

The authed `HttpClient` uses Ktor's bearer plugin with `cacheTokens = false` (the session
holds the only copy of the access token, so no Ktor cache can go stale at sign-out) and
reads the stale token from the refused request's own `Authorization` header. Ktor also
coalesces concurrent refreshes itself; the `Mutex` is what covers cold start, which does
not go through Ktor, and makes the guarantee ours and tested.

Login, register, verify, resend, refresh and logout use the public client, which has no
Auth plugin: a wrong password's `401` must never spend the refresh token.

## The lost response (accepted for now, measured)

Once `POST /auth/refresh` has left the device, the server may rotate the refresh token
(R1 to R2) at any moment until its answer arrives. If the app does not receive that answer,
the next refresh presents R1, which the server reads as reuse: the whole session family is
revoked, the person is emailed and signed out everywhere (ADR-0021, no grace window).

The window is **the whole time the refresh response is in flight**, not a few
milliseconds. It opens in two ways:

- **The app dies** between the server rotating and the app storing R2 (killed, crashed,
  phone off).
- **The answer is lost on the network.** A request or socket (read) timeout, or a
  connection reset after the request was sent, is ambiguous: the server may have rotated.
  This is the common case on a weak mobile connection, so "a bad network never signs
  anyone out" is false.

What the client does:

- The refresh and the write run in a `NonCancellable` block (closing a screen cannot
  abandon a refresh that is on the wire), and a write that fails ends the session locally
  instead of keeping a token that would trip reuse later.
- Each failed refresh is classified (`RefreshFailureKind`):
  - **Safe**: the request never left. Connect timeout, DNS failure or unresolved host,
    connection refused, no route; on iOS `NSURLErrorCannotFindHost`,
    `DNSLookupFailed`, `CannotConnectToHost`, `NotConnectedToInternet`. Also a problem
    answer from the app itself (it handled the request and refused it).
  - **Ambiguous**: everything else. Request and socket timeouts, a reset or lost
    connection, a 2xx whose body could not be read (the server certainly rotated), a 5xx
    page from a proxy (a gateway timeout can arrive after the app committed), and
    anything unrecognised.
- Behaviour is the same for both: keep R1, return no token. An ambiguous failure logs one
  Kermit warning under the tag `SessionRefreshAmbiguous`, with the exception's class name
  only, never a token. A `TOKEN_REUSE_DETECTED` that follows an ambiguous failure in the
  same process logs a second line under the same tag. Counting those pairs is how
  ADR-0021's "Revisit when" (legitimate reuse trips in the field) gets measured.

Where the classification is uncertain:

- **iOS** has no separate connect timeout: NSURLSession reports a timeout during connect
  the same way as one after sending, so every iOS timeout counts as ambiguous, some of
  them wrongly. Whether `NSURLErrorNotConnectedToInternet` can arrive for a connection
  that dropped mid-request is undocumented; it is treated as safe until the measurement
  says otherwise.
- **Android**: OkHttp retries some failed requests on its own (`retryOnConnectionFailure`,
  on by default), and a POST whose body was already written can be resent on a fresh
  connection: a second presentation of R1 the client never sees. It is **turned off** on
  the shared engine (`platformEngine()` on Android); the app retries on its own terms.

The real fix needs the backend (docs/backend-contract-gaps.md, row 11): an idempotent
refresh (an `Idempotency-Key` that replays the same R2 for a retried R1 for a few
seconds), a same-device grace window, or an explicit decision to accept the risk. It needs
an owner ruling and an amendment to ADR-0021.
