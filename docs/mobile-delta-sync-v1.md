# PowerHub Field Android — Delta Sync + Offline Recovery (E51)

## Status and release boundary

Implementation lives in PR #16 `fix/field-subscriber-safe-refresh`. The server counterpart is PowerHub PR #359, protected source migration `20261116231000_powerhub_mobile_subscriber_delta_journal.sql`. No new migration has been installed in Staging or Production. **Do not enable the new app against a server lacking E51**; the existing v1 offset endpoint is left for older app versions.

## Data and authorization

- The active Room identity is `tenantId:userId:branchId`, persisted as `mobile_active_scope`; a cursor checkpoint is keyed by `scopeKey + scopeVersion`.
- The initial login uses server-issued `MobileUser` tenant/user/branch, and verifies any legacy `field_cache_owner` record before claiming pre-v9 pending rows. Unattributable local operations remain quarantined rather than deleted.
- Every visible subscriber query joins the active Room scope and requires `status='ready'` and `isVisible=true`. A foreign account never sees another account's Room rows. Switching accounts does not destroy unsent readings or payments.
- Pending reading/payment and sync event rows have `scopeKey` columns. Only pending readings from the active scope are eligible for network submission, with a stable `clientReferenceId` reused after transport timeouts. A pending reading with a subscriber no longer visible is flagged for review instead of leaked into an unrelated branch.
- The server resolves tenant/user/branch from a signed mobile access JWT bound to a single-device DB session. HMAC cursors bind tenant/user/session/branch, scopeVersion and highwater.
- Platform capability/session changes and distribution changes trigger Bootstrap when detected by server scope checks. Remote revocations are not knowable while the device is disconnected; reconnect authorization occurs before new Delta/Outbox requests.

## Room 8 → 9 (non-destructive)

`FieldReadingDatabase.MIGRATION_8_9` copies all old subscribers to composite-key `scopeKey,id` entries with legacy/quarantined identity, retains original reading and payment queues, adds `mobile_active_scope` and `mobile_sync_states`, and upgrades pending tables with `scopeKey`. No `fallbackToDestructiveMigration` is used.

`FieldReadingDao.applyMobileDeltaPage` is a Room `@Transaction` updating subscriber upserts, soft-hidden tombstones, bootstrap completion and the **only authoritative nextCursor**. The last successful refresh preference is UI metadata only. A corrupted page, duplicate ID, changed branch, cursor loop or Room crash cannot advance the authoritative cursor.

Bootstrap stages pages under a marker and only exposes subscribers upon receiving the final page. Existing subscribers from a previous scopeVersion remain inaccessible while the new bootstrap is incomplete. A Delta page applies upserts/deletes without deleting local Outbox. On HTTP 409/400 cursor expiry or scope change, the client hides the old scope and begins a new Bootstrap; retried writes keep their original idempotency UUID.

WorkManager already has unique network-required immediate/periodic work and exponential backoff; the repository serializes foreground/background refreshes with one process-wide mutex. A separate schedule/automation service is not added.

## Existing UI, no extra readiness screen

`DashboardScreen`: network state, last successful update, pending counts and data readiness. `DashboardScreen` cycle tab: counts locally and from server-confirmed current-cycle readings. `SyncScreen`: saved locally, sending, retryable, needs review, unconfirmed payments, and synced only when a server acknowledgement was received.

## Test coverage and outstanding tasks

- `MobileDeltaRoomTest.kt` via Robolectric tests actual v8→v9 upgrade with preserved subscriber/reading/payment/event, multi-page bootstrap visibility, scope isolation, denied subscriber tombstone with pending work intact, incomplete page rollback, retry of interrupted `syncing` rows.
- `ExampleRobolectricTest.kt` keeps old legacy snapshot regression tests; `FieldReadingDao.replaceSubscribersSnapshot` remains solely for old behavior and is no longer called by the new refresh.
- Server: `server/mobile-delta-protocol.test.ts`, `server/mobile-api.routes.test.ts`, and disposable PostgreSQL integration `supabase/tests/mobile_subscriber_delta_journal.sql`.
- **Not proven:** real Android-to-Staging E2E, full two-session concurrency under Staging, actual mobile payment submission (not implemented in Mobile API), and a stable on-device performance baseline. These require a protected migration deployment approval and execution on Staging, followed by a real device test. Release is **NOT READY** until verified.
- Retention: `powerhub_mobile_prune_subscriber_changes` can enforce a **minimum 90-day log window**, but a scheduled pruning job has not been provisioned or verified. Never claim automated pruning until it is set up.

## Recovery matrix

| Failure | Outcome |
|---|---|
| Before receiving a page | previous Room cursor retained |
| After receiving before Room | previous Room cursor retained |
| During Room insert/delete | Room transaction rolls back, including cursor |
| After Room commit before next request | next page from committed cursor, harmless duplicate |
| Auth scope changed | server 409 and safe fresh Bootstrap, no cross-user view |
| Technician reassigned / subscriber archived | server emits delete, Room hides but preserves local Outbox |
| Worker process killed mid-reading | `syncing`→`retryable` for same scope, original operation ID |
| A/B account switch | active Room scope changes; other account data hidden |
| No connection | show existing verified Room cache; queues stay intact |
| Cursor expired beyond 90 days | require new Bootstrap, never fabricate a cursor |
