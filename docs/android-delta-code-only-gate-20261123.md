# PowerHub Android Delta — offline auth revocation gate

Scope: code-only CI, test fakes, in-memory Room. No hosted HTTP, device,
Supabase writes, production deployment, real SMS or payment operations.

## Revoked mobile authorization during batch upload
When the reading upload server returns `401` or `403`:
- The entire upload batch ends immediately; it is **not** treated as a
  validation failure for an individual subscriber's reading.
- Subscriber/payment/reading UI cache becomes invisible and its quarantine
  is saved in app preferences, surviving repository recreation.
- No subsequent pending reading is sent in the same batch.
- The current reading's original row, `clientReferenceId` and existing
  state are retained. A future authenticated session must verify scope.
- Network `IOException`, `409` duplicate-in-cycle, `422` validation and
  `5xx` remain outside this auth-revocation category.

## Coroutine cancellation
Login and remote logout propagate `CancellationException` rather than
silently replacing task cancellation with a successful state change. Offline
logout may still clear local credentials on ordinary I/O failure; it never
deletes the Room outbox.

## Tests and boundaries
`OfflineSyncPolicyTest` asserts whole-batch revocation decisions and that
other status codes do not trigger a cache quarantine. Robolectric tests for
`SubscriberCacheTrust` assert the durable cache-denial flag. Existing Room
tests cover preservation of pending operations and idempotency keys.

This is code-gated only. Without real-device/network acceptance, **do not
mark live delivery or release accepted**. PR #17 and PR #359 remain Draft.

## Local writes versus account rotation (follow-up code gate)
The same process-wide `accountScopeMutex` now guards the *complete*
scope-validation/Room-write operation for `saveReading`, `savePayment` and
`requeueReadingForReview`. The check is performed after obtaining the lock:
a writer waiting behind login/logout/Delta must not validate stale credentials
and then write to a newly bound tenant's local outbox.

`FieldReadingAccountWriteRaceTest` uses Robolectric and an in-memory Room
database to hold the production mutex, revoke credentials while a payment or
reading write is queued, and verify the queued operation is denied without
modifying the original pending reading's `clientReferenceId`. These are
deterministic code-side regression tests, not device or live HTTP tests.

Acceptance: only mark this gate PASS after Android CI completes successfully
for the *latest* branch HEAD. A successful run against an older commit
does not establish acceptance for subsequent code changes.

## Room v8 -> v9 disk migration (Robolectric only)
`FieldReadingRoomV8ToV9MigrationTest` creates a file-backed SQLite
database whose v8 tables mirror the pre-Delta Room entities, seeds all five
reading statuses, a payment and a pending sync event, and opens it with
the production `MIGRATION_8_9` object through `Room.databaseBuilder`.

The assertions cover old subscriber cycle fields, original reading and
payment `clientReferenceId`, event `eventKey`, reading retry metadata,
the new empty Delta state/staging tables, survival after database close/reopen,
and preservation of all offline work during a subsequent staged Bootstrap.

A second test injects a migration that creates a table then throws,
verifies SQLite rolls the schema and version back to v8 without losing
legacy records, and retries using the production migration.

This fixture checks legacy schema shape but is not an exported Room v8
schema JSON or real user's database. Test success is bounded by that fixture.
No destructive fallback migration or hosted database change is introduced.

## UI versus WorkManager concurrent sync and cancellation
`FieldReadingSyncConcurrencyTest` exercises the production repository
with two instances sharing the real process-wide upload/account mutexes and
an in-memory Room database. A fake suspend `FieldReadingApi` controls upload
acknowledgements and synthetic Delta responses, without contacting a server.

Scenarios now covered:
- A UI upload and a WorkManager-equivalent upload overlap: only one HTTP
  `createReading` is invoked for the original `clientReferenceId`.
- Cancellation while a reading is in flight leaves the original idempotency
  key intact and retryable; resending uses that same key.
- Logout waits for the active upload and preserves unsent payments and
  `needs_review` readings.
- Cancelling a worker waiting behind `uploadSyncMutex` does not create a
  second upload or block the following sync.
- A synthetic Delta failure after a reading acknowledgement is a failed
  cycle: the reading remains locally `synced` with its original key,
  and the UI does not receive a misleading 100% completion callback.

A successful fake HTTP round-trip verifies in-process sequencing only, not
cross-process locking or live server-side idempotency. The actual post-change
CI result on the latest commit remains the acceptance criterion.

## Manual Delta versus WorkManager upload — serialized code gate
Public `refreshSubscribers()` now takes `uploadSyncMutex` before
`accountScopeMutex`, matching login/logout. An internal
`refreshSubscribersWithinUploadLock()` is used only from
`syncAllData()`, which already owns the upload mutex. This prevents
deadlock through recursive acquisition while keeping branch/profile changes
serialized against active reading uploads.

`FieldReadingSyncConcurrencyTest` additionally checks:
- A manual refresh waits for the worker's unacknowledged reading upload.
  The manual and worker Delta requests run sequentially, with one reading post.
- Cancellation of a waiting manual refresh sends no Delta request and does
  not block the active worker.
- HTTP 403 during manual Delta persists cache quarantine, hides subscriber/
  reading/payment flows immediately and after repository recreation, and
  preserves local subscriber rows and pending reading/payment reference keys.

These are in-process Robolectric / synthetic API tests, not hosted acceptance.
The code-side gate is accepted only on a successful latest-HEAD Android CI.

## Account profile and delayed token refresh safety
- A successful HTTP 200 mobile profile no longer implicitly authorizes a
  previously pinned branch. A removed branch is durably quarantined before
  any attempt to adopt another branch. A profile with zero authorized branches
  fails closed; cached rows and unsent offline operations remain on disk.
- A profile whose tenant/user does not match the currently stored session
  fails before mutating the cache scope, branch preference or Room snapshot.
- The coroutine-based token refresh uses a compare-and-swap keyed on its
  original refresh token. A response arriving after logout, fresh login or
  HTTP-authenticator token rotation cannot overwrite newer credentials or
  resurrect cleared credentials.

Tests: `MobileSessionRefreshRaceTest` covers stale response/login/logout/
authenticator races and preservation of the local PIN. The additional
`FieldReadingSyncConcurrencyTest` scenarios cover a 200 profile with zero
branches, a foreign tenant, and reassignment to a different branch with
unsent offline work. All use only synthetic local data.

## Cross-repository cache quarantine and logout hardening (final code gate)
An active UI repository must not keep showing stale subscriber, reading or
payment flows if another repository instance (e.g. WorkManager) receives a
403 or otherwise quarantines local data. The persistent
`subscriber_cache_requires_validation` flag is the authority; a process-wide
revision flow notifies *already active* collectors after every successful
persisted quarantine/release. Each cache-backed UI flow rechecks the persisted
flag, matching tenant/user/branch scope and local visibility before emission.
The companion Robolectric regression starts collectors before a worker 403,
then asserts all three flows go empty while the original offline reading and
payment keys and subscriber records remain on disk. A newly created repository
must continue to deny access.

Mobile credential clearing is performed under the same refresh monitor as
successful login, token rotation and revocation; it uses a committed change
instead of asynchronously applying the clear. The original pending/outbox
tables are untouched.

## Combined code-only acceptance inventory (PR #17 / PR #359)
- Android: `mosabmalek-rgb/app-power-` PR #17,
  branch `feat/android-mobile-delta-atomic-sync`. Verify **latest HEAD** and
  Android CI `Test and build debug APK` success *after* these final edits.
- Platform: `mosabmalek-rgb/powerhub-platform` PR #359,
  branch `feat/auth-local-first-single-pr`. At the last direct check, HEAD
  `1e51ed52892e17a9fc6dd6776d35ef8b50f68f32` had six completed
  successful PR workflows (PowerHub CI, Security, Admin Guard, Reading Cycle
  PostgreSQL Integration, B5 Synthetic Financial Load and Docker Shadow).
  These are historical checks, not a claim of a new platform deployment.
- Local-only Android tests cover Room v8->v9 file migration and rollback,
  bootstrap cursor/snapshot atomicity, idempotent Outbox recovery, UI vs
  worker upload and Delta serialization, 401/403 cache quarantine, session
  refresh/login/logout races, and multi-instance cache visibility.
- No hosted HTTP, real device/emulator acceptance, live SMS/payment,
  staging/production migration, release or merge was performed.
- An Android CI green check only establishes the code-side gate. Both PRs
  must remain Draft unless an explicit separate merge/release decision is made.
  Cross-system server-side idempotency and real-network/device behavior remain
  outside this acceptance scope.
