# ATEP Vehicle Gateway — Android and AAOS Property Integration

## Purpose

The showcase acts as the Android-side adapter between vehicle properties and the
Automotive Test Engineering Platform (ATEP). `VehiclePropertySource` now isolates
the UI and gateway from the origin of those properties. The application can use
either the deterministic local simulator or a read-only Android Automotive
`CarPropertyManager` bridge backed by CarService and VHAL.

The Android application communicates only with the public ATEP API. It never
connects directly to PostgreSQL, Redis, or RabbitMQ.

## Runtime flow

1. The selected property source creates a new immutable `VehicleSimulationState`.
2. `VehicleTelemetryMapper` compares the previous and current states.
3. Only changed properties receive a new client-generated `event_id` and UTC timestamp.
4. `VehicleGateway` stores those events before attempting delivery.
5. `HttpTelemetryTransport` sends each event to ATEP in queue order.
6. HTTP 200 and 202 remove the event from the pending queue.
7. Network and server failures preserve the exact event for a later retry.
8. Permanent 4xx rejection moves the event to a bounded local rejected-event store.
9. A remaining pending queue schedules one unique WorkManager job requiring connectivity.
10. The worker retries with exponential backoff and stops after eight bounded attempts.

The persisted event is reused without changing its identifier or timestamp. This
matches ATEP's idempotency contract and makes reconnection safe.

## Background retry

The foreground gateway still attempts immediate ordered delivery. When telemetry
remains pending, `WorkManagerTelemetryRetryScheduler` enqueues one unique job per
vehicle using `ExistingWorkPolicy.KEEP`. A connected network is required, so the
operating system defers work while offline. `TelemetryRetryWorker` opens the same
persistent queue, flushes it in order, and returns retry while records remain.

Retry uses a 30-second exponential backoff and is bounded to eight worker
attempts. Attempt count and exhaustion state are stored beside the queue. An
exhausted job is not started again silently: the UI retains the evidence and
requires an explicit **Resume and retry now** action. Disabled configuration
never schedules background work, even if an older queue exists, and successful
or manual delivery cancels unnecessary pending work. WorkManager persists its
schedule and can resume it after process termination or device reboot.

## Rejected-event operations

Permanent client-side rejection remains separate from transient delivery
failure. The gateway exposes a bounded list of rejected events containing the
property, serialized value, unit, timestamp, original `event_id`, and a
non-sensitive rejection reason. The operator can act on one record at a time:

- **Retry** atomically moves the selected event back to the pending queue and
  retains its identifier and timestamp before immediate delivery;
- **Discard** removes only the selected local rejected record;
- events not selected remain unchanged, and no bulk-delete operation is exposed.

`SharedPreferences.OnSharedPreferenceChangeListener` feeds queue and retry-state
changes back to the `ViewModel`, including changes made by WorkManager while the
activity is open. A process restart reconstructs the same inspection state from
the persistent store.

## ATEP contract

```http
POST /api/v1/vehicles/{vehicle_id}/telemetry
Content-Type: application/json
X-ATEP-Module-ID: <registered module UUID>
X-ATEP-Module-Token: <raw-once module credential>
```

The registered ATEP module must declare the capability
`vehicle.telemetry.publish`.

Example body:

```json
{
  "event_id": "d43df7a1-ef48-4721-bb4e-f27ff48f806e",
  "property": "battery_state_of_charge",
  "value": 77,
  "unit": "percent",
  "timestamp": "2026-08-04T12:00:00Z",
  "source": "android-automotive-showcase"
}
```

Mapped properties are `power_state`, `gear`, `vehicle_speed`,
`driver_door_open`, `seatbelt_fastened`, `battery_state_of_charge`, and
`charger_connected`.

## Vehicle property sources

`VEHICLE_PROPERTY_SOURCE` accepts three values:

- `simulator` (default): uses deterministic local controls on any Android device;
- `aaos`: requires Android Automotive and never falls back to simulated data;
- `auto`: selects AAOS on an automotive system image and the simulator elsewhere.

The AAOS source subscribes to `PERF_VEHICLE_SPEED`, `GEAR_SELECTION`,
`IGNITION_STATE`, `EV_BATTERY_LEVEL`, `EV_CURRENT_BATTERY_CAPACITY` (or nominal
capacity), and `EV_CHARGE_PORT_CONNECTED`. Speed is converted from m/s to km/h.
Battery state of charge is calculated from energy level divided by usable
capacity. Unsupported or unauthorized properties are isolated so available
signals can continue, and the UI reports partial or unavailable connectivity.

The standalone showcase uses a compatibility bridge because `android.car` is
provided by an AAOS system image rather than the standard phone SDK. The bridge
still calls `Car.createCar`, `CarPropertyManager.registerCallback`, and
`CarPropertyValue` at runtime. The production AOSP build should replace this
bridge with direct typed platform APIs and the current subscription API.

## Local configuration

Do not commit module credentials. Add these values to the user-level Gradle file
`%USERPROFILE%\.gradle\gradle.properties`:

```properties
ATEP_BASE_URL=http://10.0.2.2:8000
ATEP_VEHICLE_ID=vehicle-001
ATEP_MODULE_ID=<module UUID returned by ATEP>
ATEP_MODULE_TOKEN=<raw credential returned once by ATEP>
VEHICLE_PROPERTY_SOURCE=simulator
```

`10.0.2.2` reaches the Windows host from the standard Android Emulator. A
physical device must use the host's reachable LAN address. Cleartext HTTP is
enabled only in the debug manifest; production builds must use HTTPS.

When either workload credential is absent, the gateway stays disabled and no
event is queued or transmitted. The UI reports the current gateway state and
offers a retry action while events remain pending.

## Verification

Run from `showcase/` after Gradle has downloaded the declared dependencies:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

The unit suite verifies property mapping, changed-value filtering, offline queue
retention, stable identifiers during retry, rejected-event inspection, selective
discard, manual recovery after exhaustion, fail-safe behavior when credentials
are absent, simulator-source mutation, AAOS property conversion, state-of-charge
calculation, unavailable-VHAL behavior, and bounded background-retry decisions.

The Windows verification run resolved WorkManager `2.11.2`, assembled the debug
APK, passed all 18 unit tests with no failures or skipped tests, and completed
`lintDebug` with zero errors. Fourteen non-blocking warnings remain documented:
the AAOS reflection compatibility bridge, KTX suggestions, available dependency
updates, target level, Android backup rules, and the missing showcase icon.

## Current limitations

- the development shared secret is embedded in the debug APK and is not a
  production workload-identity mechanism;
- certificate pinning, mTLS, OAuth workload identity, and secret-manager delivery
  remain production-hardening work;
- an AAOS emulator/system image is still required for live CarService/VHAL evidence;
- door and seatbelt signals remain simulator-only until area-aware property mapping is added;
- the compatibility bridge uses the deprecated cross-version callback registration API;
- runtime permission denial is reported by the source status and never replaced with simulated data.
- a live process-death/reboot WorkManager run still requires emulator evidence;
- rejected-event retry and discard are local operator actions and do not yet
  create a dedicated ATEP administrative audit record.

## Next increment

Introduce an area-aware property catalogue for doors and seats, migrate the
platform build to typed `subscribePropertyEvents`, and attach immutable evidence
to operator retry/discard decisions.
