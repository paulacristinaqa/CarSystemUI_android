# CT-SHOW-006 — ATEP Offline Queue and Idempotent Resend

## Objective

Verify that simulator changes become ATEP telemetry, remain locally durable
during a network outage, and are resent without creating a second logical event.

**Priority:** P0  
**Type:** Integration, resilience, and API contract  
**Area:** Vehicle Gateway / ATEP

## Preconditions

- ATEP is running and contains `vehicle-001`.
- A registered module declares `vehicle.telemetry.publish`.
- Valid `ATEP_*` Gradle properties configure the showcase.
- The application is installed on an Android Emulator.

## Procedure and expected results

1. Open the application while ATEP is available.
   - The ATEP Vehicle Gateway card reports synchronized telemetry.
2. Advance the vehicle to `READY`.
   - ATEP receives `power_state` changes; unchanged values create no event.
3. Select `D` and accelerate once.
   - ATEP receives `gear`, `vehicle_speed`, and
     `battery_state_of_charge` with the expected types and units.
4. Stop the ATEP API and change door, seatbelt, and battery conditions.
   - The card reports pending events, which survive application restart.
5. Restore ATEP and select **Tentar sincronizar novamente**.
   - The queue reaches zero and every event appears once in PostgreSQL.
6. Inspect one retried observation.
   - Its `event_id` and timestamp are identical across attempts. ATEP returns
     HTTP 202 for a new event or HTTP 200 with `duplicate: true` for an exact retry.
7. Remove `vehicle.telemetry.publish` temporarily and generate a property change.
   - ATEP returns HTTP 403; the gateway records a permanent rejection and does
     not retry it forever.
8. Remove the workload credentials and reinstall the application.
   - The gateway reports disabled and makes no delivery attempt.

## Evidence

- screenshots of synchronized and pending gateway states;
- ATEP telemetry query response;
- database row counts before and after resend;
- logs containing status codes but no module token.

## Automated coverage

- `VehicleTelemetryMapperTest`: full snapshot and changed-property filtering;
- `VehicleGatewayTest`: temporary outage, stable-ID retry, permanent rejection,
  and disabled behavior without credentials.

**Automation status:** Passed locally  
**Manual end-to-end status:** Pending
