# X-7.8 — Deterministic native dashboard lifecycle

`DashboardLifecycle` is a serialized-owner state machine, not a connected socket
client. An adapter must provide parsed/validated envelopes, monotonic time, timers
and transport events on one owner thread. No credentials are stored in this class.

Start returns a connection generation. Each callback supplies that generation;
disconnect, stop and terminal transitions invalidate old callbacks. `tick` returns
a new generation only when the adapter should open a replacement connection.
The adapter must close old sockets and cancel timers on invalidation/termination.
No adapter is wired by this increment.

- First snapshot deadline: 30 seconds, followed by delayed retry.
- Missing update: retained evidence becomes stale after 35 seconds.
- Consecutive failures: 30/60/120/120/120-second waits, then stop and clear evidence.
  A valid snapshot resets this budget. Jitter remains future adapter work.
- HTTP 401/403 and WebSocket 4401/4403: clear evidence and stop retries. Native
  pre-upgrade 403 can also mean admission rejection; no precise cause is inferred.
- Protocol closures and invalid sequence/oversized payloads: fail closed.
- Ten ordered snapshots plus normal close: complete without automatic restart,
  retaining stale evidence until explicit stop or a new session.

Seven JVM tests cover stale/recovery boundaries, retry/old callbacks, exhaustion,
populated authorization cleanup, completion, invalid input/stop and initial
timeout using virtual time. Payloads are opaque strings: byte limits, JSON/schema
validation, query-time metadata, credential expiry and real socket cancellation
belong to the future adapter, not this evidence claim.

Next: OkHttp/envelope adapter, Android presentation, then real AAOS acceptance.
No emulator, paid services or new dependencies are needed for these tests.

Local validation: 44 JVM tests passed, including seven new lifecycle tests;
lint completed with zero errors and 15 existing warnings; debug APK build passed
in 56 seconds. One worker, two JVM processors and a 1536 MiB heap were used.
Tests and lint analysis executed; no device or live network scenario was run.
