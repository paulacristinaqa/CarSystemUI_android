# X-7.9 — Native transport and envelope adapter

`DashboardNativeClient` connects the request contract and lifecycle to OkHttp.
Call `start(accessToken, expiresInSeconds)` with the remaining login lifetime;
the client applies a ten-second conservative margin (minimum one second).
Local expiry and server authorization denial clear evidence and cancel the socket.
The token is held only for the active session; there is no storage, logging,
refresh, logout HTTP request or claim of secure memory erasure.

Events and a one-second monotonic timer are synchronized through the client.
The client owns its supplied scheduler and shuts it down on close. Observers must
return promptly without throwing and dispatch presentation work to the UI thread.
Connection generations invalidate late callbacks; discarded sockets are cancelled.
No application messages are sent: native authentication is exclusively a header.

The parser bounds text to 300,000 UTF-8 bytes and nesting to 64, checks the frame
type, integral sequence, 30-second interval, query-time freshness basis, timestamps,
requested view, export version, retention label and object-shaped data. It retains
the full envelope so server query time cannot be confused with measurement time.
Detailed per-view aggregate schema validation is not implemented by this parser.
OkHttp receives the message before the parser checks size; this is not a network
allocation cap. JSON platform compatibility remains a device acceptance gate.

## Test objectives

Nine new JVM tests cover valid metadata; invalid metadata/cross-view data;
trailing/oversized/deep JSON; header-only receipt/retry/obsolete callbacks;
populated permission cleanup; expiry during retry; malformed/binary rejection;
normal completion; and disposal/restart rejection. Transport tests use an injected
WebSocket factory, not a live backend. Time is advanced explicitly, without sleep.

Production uses the existing Android JSON API and OkHttp. JVM tests add only
`org.json:json:20260814`, whose release is recorded in the upstream
[release history](https://github.com/stleary/JSON-java/releases/tag/20260814).
This test implementation is not proof of identical Android parser behavior.

Next: wire Android presentation/session ownership, then run actual backend/AAOS
acceptance. Reconnect jitter, full view schemas and multi-client capacity remain
explicit limitations. No emulator, paid services or production dependencies added.

Local evidence: 53 JVM tests passed (nine new); lint and debug APK build passed in
52 seconds using one worker, two JVM processors and a 1536 MiB heap. An initial
JUnit initialization failure was corrected by declaring a test's Unit return type.
A host sample was CPU 27%, GPU 11%, GPU memory 715 MiB; not peak or project-only use.

Final production validation after lint review passed in 1m 4s. Fixed-delay polling
replaced fixed-rate scheduling to avoid catch-up bursts after Android suspension;
the test JSON library was updated to 20260814. Lint returned to zero errors and
15 pre-existing warnings. Size/depth tests were strengthened with otherwise valid
envelopes and rerun separately; no production change followed this build.
