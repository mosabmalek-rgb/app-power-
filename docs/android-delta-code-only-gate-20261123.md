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
