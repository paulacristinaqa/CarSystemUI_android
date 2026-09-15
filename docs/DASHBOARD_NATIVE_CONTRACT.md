# X-7.7 — Native dashboard request foundation

This prerequisite introduces request construction only, not a connected dashboard
or completed AAOS acceptance. The existing test-run stream is a separate feature.

`DashboardNativeContract.request` accepts an HTTPS base origin, an allowlisted
view and a caller-supplied access token. It returns an OkHttp HTTP upgrade request
for `/api/v1/dashboard/stream/{view}` with Authorization and without Origin.
The caller must not log the request or persist its headers. This helper does not
store credentials, open sockets, refresh tokens or issue vehicle commands.

HTTP is denied by default. Explicit development opt-in permits only localhost,
loopback literals and the Android emulator host alias 10.0.2.2. A physical device
requires HTTPS under this contract. URL credentials, paths, queries and fragments
are rejected; tokens must be bounded printable ASCII without spaces. Error text
does not reproduce input credentials. Existing Android network policy is unchanged.

## Verification objectives

| Scenario | Objective |
|---|---|
| All three views | Native route, GET upgrade, Authorization header, absent Origin and no URL token |
| HTTP default and opt-in | Reject unencrypted remote access and require deliberate local development |
| Invalid base URLs | Prevent credentials or arbitrary path/query/fragment configuration |
| Invalid tokens and 4096/4097 boundary | Prevent header injection and unbounded credentials |
| Protocol constants | Record 30-second refresh and ten-snapshot session contract |

The constants document the backend contract; they do not yet schedule or enforce
client behavior. Tests are JVM unit tests without a server. The next increment
must implement bounded socket lifecycle, stale state, terminal permission loss,
normal session completion and cleanup with deterministic tests. Then connect the
Android UI and run real AAOS acceptance, including unavailable VHAL properties.
The native client must not reuse browser first-message authentication.

No new dependencies, paid service, emulator or GPU workload is introduced.

Local evidence: 37 unit tests passed, including five new contract tests; lint
analysis and debug APK build passed in 54 seconds. Lint retains 15 warnings and
zero errors. One Gradle worker, 1536 MiB JVM heap and two JVM processors were used.
A post-run host sample was CPU 22%, GPU 7%, GPU memory 687 MiB; not peak or
project-exclusive usage. No socket/server/device acceptance was performed.
