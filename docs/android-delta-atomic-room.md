# Android Subscriber Delta — atomic Room protocol (PR review only)

Server source: PowerHub PR #359, E51 route `GET /api/mobile/v1/subscribers/delta`.
Android base: `9ab3ad5c256920338f8f277c51036e7df8a347bb`.

Storage v9 adds `subscriber_delta_state` (single active opaque cursor and
scopeVersion) plus `subscriber_delta_stage` (incomplete bootstrap pages).
No migration deletes existing subscribers, readings, payments or sync events.

The authoritative cursor and subscriber writes are committed in one Room
transaction. A bootstrap is staged until its final page; active subscribers
are published once, atomically. Delta pages modify only named subscriber
rows and atomically advance the cursor. Failures roll back both mutations
and cursor changes, preventing partial progress. Offline outbox rows are
never cleared by Delta operations.

The Retrofit DTO mirrors server camelCase JSON fields but this is only
source-level integration. Session/scope, cursor error handling, bootstrap
resume and live behavior are validated in separate gates. Do not distribute
this branch's APK until CI, Robolectric and field acceptance are green.
