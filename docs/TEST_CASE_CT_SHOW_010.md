# CT-SHOW-010 — Authorized Leased Vehicle Command

## Objective

Verify that an authorized ATEP operator can request one idempotent vehicle
property command, that only the targeted capability-authorized gateway can claim
it, and that CarSystemUI applies or safely rejects it before returning terminal
evidence.

## Preconditions

- ATEP database upgraded through migration `0008_vehicle_command_delivery`;
- active vehicle `vehicle-001`;
- gateway module credential declaring `vehicle.commands.consume` and
  `vehicle.telemetry.publish`;
- operator token with `vehicle_commands:write` and `vehicle_commands:read`;
- showcase running with `VEHICLE_PROPERTY_SOURCE=simulator`;
- access to ATEP command, outbox, audit, and telemetry evidence.

## Procedure and expected results

| Step | Action | Expected result |
| --- | --- | --- |
| 1 | Create a `set_property` command for `battery_level=25`, targeting the gateway module and using a new `command_id`. | HTTP 201 returns `pending`; request, audit record, and `atep.vehicle.command.requested.v1` are committed together. |
| 2 | Repeat the exact request with the same `command_id`. | HTTP 200 returns the original command without another command or outbox event. |
| 3 | Reuse that identifier with a different value. | HTTP 409 returns stable code `vehicle_command_conflict`. |
| 4 | Wait for the gateway polling interval. | The targeted module claims the command once; status becomes `claimed`, attempt count increments, and only a hash of the claim token is stored. |
| 5 | Observe CarSystemUI and query ATEP. | Battery becomes 25%, resulting telemetry retains its own idempotent identity, and the command becomes `succeeded` with `atep.vehicle.command.completed.v1`. |
| 6 | Request `battery_level=101`. | Android rejects the command as `command_value_out_of_range`; simulator state is unchanged and ATEP records terminal `rejected` evidence. |
| 7 | Request `charger_connected=true` while speed is nonzero or gear is not PARK. | Android rejects `unsafe_vehicle_state`; no unsafe mutation occurs. |
| 8 | Attempt claim with a module lacking `vehicle.commands.consume`. | HTTP 403 returns `module_capability_required`; the command remains available only to its target. |
| 9 | Interrupt acknowledgement after a successful claim and wait beyond the lease. | The command becomes claimable again with an incremented attempt and a new token; the idempotent property assignment can be replayed safely. |
| 10 | Run the showcase in explicit AAOS mode and request an otherwise valid property change. | The gateway returns `read_only_vehicle_source`; no simulated state replaces AAOS evidence. |

## Pass criteria

Pass when command creation is idempotent, claim is target- and
capability-protected, the raw claim token is never persisted, safe simulator
commands reach terminal success, invalid/unsafe/read-only mutations reach
terminal rejection, and lease recovery creates no duplicate logical command.

## Evidence to retain

- redacted create, duplicate, conflict, claim, and acknowledgement responses;
- command status and attempt count before and after lease recovery;
- matching audit and outbox identifiers;
- CarSystemUI event-history screenshot for applied and rejected commands;
- telemetry receipt for the successfully applied property;
- database evidence showing only a SHA-256 claim-token digest.

## Execution record — 4 August 2026

**Result:** Passed.

The scenario ran on the `ATEP_AAOS_API35` Android Automotive API 35 emulator
against an isolated Docker ATEP stack upgraded through migration `0008`.

| Evidence | Observed result |
| --- | --- |
| Safe command | `battery_level=25` reached `succeeded` on attempt 1. |
| Idempotency | Exact retry returned the original terminal command; changed reuse returned `vehicle_command_conflict`. |
| Range validation | `battery_level=101` reached `rejected` with `command_value_out_of_range`. |
| Safety invariant | `charger_connected=true` at `20 km/h` reached `rejected` with `unsafe_vehicle_state`. |
| Capability isolation | A module without `vehicle.commands.consume` received `module_capability_required`. |
| Lease recovery | An unacknowledged ten-second lease expired; the same logical command succeeded on attempt 2. |
| AAOS isolation | Explicit AAOS mode rejected a valid mutation with `read_only_vehicle_source`; the UI continued to identify Android Automotive / VHAL as its source. |
| Persistence | Seven commands finished: four `succeeded` and three `rejected`; all seven persisted claim-token values were 64-character digests rather than raw tokens. |
| Events | The outbox contained 7 requested, 8 claimed, and 7 completed command events. |
| Telemetry and audit | The run retained 25 telemetry observations and 7 immutable `vehicle.command_requested` audit records. |

Redacted screenshots are retained under `docs/evidence/ct-show-010/`.
