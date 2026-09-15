# X-7.10 — Android engineering panel and session ownership

The showcase now includes a Compose dashboard panel, separate from vehicle
controls and the existing test-run gateway. It offers server origin, three views,
a masked temporary access token, remaining token lifetime, explicit connect and
disconnect/clear. Local HTTP opt-in is visible only in debug builds and remains
limited to loopback/10.0.2.2 by the native contract.

This is an engineering token-entry panel, not an end-user login screen. Obtain a
temporary token through the existing authorized backend workflow and enter its
actual remaining lifetime. Do not embed production tokens in build configuration.
The token draft is cleared on submission, backgrounding and disposal. No saved
state, preferences or disk storage is introduced. Immutable JVM strings cannot be
securely erased, and keyboard/OS behavior is outside this in-memory guarantee.

`DashboardSession` owns one transport and filters queued UI callbacks by session
generation. `DashboardViewModel` dispatches callbacks to its UI scope. Activity
onStop disconnects and clears evidence, including during rotation; no automatic
reconnect occurs on return. Changing origin/view/local HTTP policy stops the old
session. Destruction closes the session and its scheduler. Explicit disconnect
is local cleanup, not server-side token revocation or HTTP logout.

The panel labels stale/current query state and presents at most 12,000 characters
of the envelope, with a truncation notice. Query timestamps are not measurements.
This is not a finished KPI dashboard, full export, distraction-optimized screen,
functional-safety claim or a UI suitable for use while driving. Use only parked
in a controlled test environment.

Four JVM session tests cover queued callbacks after stop, connection replacement,
partial-start cleanup and final disposal. Compose compilation/lint do not prove
visual accessibility or that Activity lifecycle behavior was exercised on-device.
Next: real backend/AAOS acceptance, including form interaction, background/return,
permission loss, expiry, stale data, normal completion and narrow-screen inspection.
No emulator or paid service is required for this code-validation slice.

Local evidence: 57 JVM tests passed (four new owner tests), lint completed with
zero errors and 15 existing warnings, and debug APK build passed in 1m 2s. One
worker, two JVM processors and a 1536 MiB heap were used. Host sample: CPU 23%,
GPU 7%, GPU memory 629 MiB; not peak/project-only usage. No visual/device test run.
