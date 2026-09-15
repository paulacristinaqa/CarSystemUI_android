# CT-SHOW-007 — AAOS Vehicle Property Source

## Objective

Verify that the CarSystemUI showcase reads supported vehicle properties from an
Android Automotive system image, exposes an honest source status, disables local
simulation controls, and forwards canonical changes to ATEP without direct
infrastructure access.

## Preconditions

- an AAOS emulator or test device with CarService and a VHAL implementation;
- the showcase built with `VEHICLE_PROPERTY_SOURCE=aaos`;
- required car read permissions granted where the system image permits them;
- optional ATEP module credentials with `vehicle.telemetry.publish` for the
  telemetry portion of the test;
- ADB access for observing and changing reference-VHAL properties.

## Procedure and expected results

| Step | Action | Expected result |
| --- | --- | --- |
| 1 | Launch the showcase on AAOS. | The source card identifies Android Automotive / VHAL; simulator controls are absent. |
| 2 | Deny or remove one property permission, then relaunch. | The source reports partial or unavailable access and does not substitute simulated values. |
| 3 | Restore permissions and publish a speed value through the reference VHAL. | The UI displays the converted non-negative speed in km/h. |
| 4 | Publish park, reverse, neutral, and drive gear values. | The UI maps supported values to P, R, N, and D. |
| 5 | Publish battery energy and usable capacity values in Wh. | The UI calculates state of charge as `(level / capacity) * 100`, bounded to 0–100. |
| 6 | Change the charge-port-connected property. | The charger state changes without a local UI command. |
| 7 | Inspect ATEP telemetry when credentials are configured. | Changed canonical properties arrive with stable event IDs and no direct PostgreSQL, Redis, or RabbitMQ connection. |
| 8 | Stop the application. | Car property callbacks are unregistered and the Car connection is closed. |

## Pass criteria

Pass when the AAOS source is clearly identified, only accessible VHAL signals are
consumed, local simulation controls remain unavailable, conversions match the
expected values, permission failures remain visible, and any ATEP delivery follows
the public authenticated telemetry contract.

## Evidence to retain

- AAOS image/build identifier;
- application build digest and property-source configuration;
- permission state;
- VHAL injection commands and timestamps;
- UI screenshots;
- matching ATEP telemetry receipts and correlation data;
- log excerpt showing callback cleanup.
