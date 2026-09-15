# CT-SHOW-008 — Persistent Background Telemetry Retry

## Objective

Verify that pending Vehicle Gateway telemetry survives process termination and
is delivered by one bounded WorkManager job when connectivity returns, without
changing event identity or creating duplicate logical observations.

## Preconditions

- Android emulator or AAOS device with WorkManager enabled;
- ATEP module credentials declaring `vehicle.telemetry.publish`;
- an active canonical vehicle in ATEP;
- access to disable and restore the device network;
- access to ATEP telemetry and outbox evidence.

## Procedure and expected results

| Step | Action | Expected result |
| --- | --- | --- |
| 1 | Start ATEP and launch the showcase with valid gateway configuration. | Gateway reaches synchronized state. |
| 2 | Disable device connectivity and change one vehicle property. | The exact event is persisted and the UI reports a pending queue. |
| 3 | Inspect WorkManager state. | Exactly one uniquely named retry job exists for the configured vehicle and requires connectivity. |
| 4 | Generate additional property changes while offline. | Events append to the same ordered queue; no parallel retry job is created. |
| 5 | Force-stop the application process without clearing application data. | Queue and WorkManager schedule remain persisted. |
| 6 | Restore connectivity and allow scheduled work to run. | The worker sends queued events in order without opening the activity. |
| 7 | Query ATEP receipts and outbox rows. | Each client event ID exists once logically; timestamps and identifiers match the offline evidence. |
| 8 | Relaunch the showcase. | Pending count is zero and no unnecessary retry work remains active. |
| 9 | Repeat with missing gateway credentials. | Disabled mode schedules no background delivery, including when an older queue exists. |

## Pass criteria

Pass when one unique connectivity-constrained job survives process termination,
preserves queue order and event identity, delivers after reconnection, leaves no
duplicate ATEP observation, and remains inactive for disabled configuration.

## Evidence to retain

- application build digest and WorkManager version;
- configured vehicle ID with credentials redacted;
- pending event IDs and timestamps before process termination;
- WorkManager state before and after connectivity restoration;
- matching ATEP observation and outbox identifiers;
- screenshots or logs showing final empty pending queue.
