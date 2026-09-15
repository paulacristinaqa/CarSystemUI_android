# Android showcase consolidation review

## 2026-09-15 — Command safety review

The four stacked showcase PRs are being reviewed before integration into main.
GitHub reports no configured checks on these PRs; local validation is therefore
recorded explicitly and must not be described as passing GitHub CI.

Review found that a speed command could create motion while OFF or in PARK.
The executor now validates the resulting state: motion requires READY, DRIVE or
REVERSE, positive battery charge and a disconnected charger. The same invariant
rejects loss of traction power or battery through another command while moving.
Stopping remains allowed. This is an educational simulator rule, not vehicle
safety certification or authorization to control a real vehicle.

Regression objectives: reject each missing motion prerequisite; accept a valid
driving command; reject power/battery changes that invalidate existing motion;
allow stopping. Existing allowlist, bounds, charging and read-only source tests
remain required.

Validation uses one Gradle worker, disabled parallel execution, a 1536 MiB heap
and two JVM processors. Module/operator tokens are explicitly empty for the APK.
No emulator, paid service or GPU compute workload is started. During the initial
cached baseline check, host CPU was sampled at 76% and GPU at 9%; these are not
peak measurements or usage attributable solely to this build.

Initial baseline build succeeded with cached unit/lint results; it is not fresh
test execution evidence. Corrected-source unit tests, lint and debug APK build
passed in 1m 13s with test and lint tasks actually executed.

## Live test-run client review

The client now serializes closure, connection scheduling and data callbacks to
prevent late callbacks from publishing after disposal. HTTP 401/403 and policy/
authentication WebSocket closures (1008/4401/4403) stop retries and clear the
displayed snapshot. Diagnostic text no longer exposes raw exception messages.
Tests exercise late callbacks, both HTTP denials and all three terminal codes
without network access or an emulator. This client is for test-run updates,
not the forthcoming Volume X dashboard client.

The reviewed PR stack covers an educational simulator and gateway, not an AOSP
system-image build. Real AAOS execution, dashboard acceptance, runtime credential
provisioning instead of development BuildConfig values, broader resilience and
unavailable-property representation remain separate work. No production release
or functional-safety certification is implied by consolidation.

Final stacked-source validation: 32 unit tests passed (zero failures/errors),
lint completed with zero errors and 15 warnings, and the debug APK built in
1m 16s. Test and lint tasks executed rather than returning cached results.
No device tests were run. A sample during validation was host CPU 31%, GPU 3%,
725 MiB GPU memory; unrelated host activity is included. Existing lint warnings
remain tracked limitations, not a clean-lint claim. The PR 3-only validation
contained 26 passing tests and 14 warnings.
