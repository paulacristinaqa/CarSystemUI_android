# CT-SHOW-009 — Rejected Telemetry and Retry Exhaustion Operations

## Objective

Verify that CarSystemUI presents actionable, persistent evidence for permanently
rejected telemetry and exhausted background delivery, while preserving event
identity and preventing unintended bulk deletion or automatic retry loops.

## Preconditions

- showcase configured with an active ATEP vehicle and module credential;
- a controllable ATEP response or test proxy able to return HTTP 4xx and 5xx;
- access to WorkManager state and ATEP telemetry receipts;
- application data retained throughout the scenario.

## Procedure and expected results

| Step | Action | Expected result |
| --- | --- | --- |
| 1 | Configure one telemetry request to receive a permanent HTTP 4xx response. | The event leaves the pending queue and appears once in the rejected-event card. |
| 2 | Inspect the rejected item. | Property, value, unit when present, timestamp, original `event_id`, and a non-sensitive reason are visible. No credential is displayed. |
| 3 | Restart the application process without clearing data. | The same rejected event remains visible with unchanged identity and evidence. |
| 4 | Correct the rejecting condition and select **Retry** for that item. | The exact event moves atomically to pending and is sent with its original identifier and timestamp. |
| 5 | Query ATEP telemetry and outbox evidence. | One logical observation exists for the original `event_id`; no duplicate event is created. |
| 6 | Create two new rejected events and select **Discard** on only one. | Only the selected item disappears; the second rejection remains unchanged. |
| 7 | Force retryable failure for a pending event through all eight WorkManager attempts. | The job becomes exhausted, attempt count is persisted, and the UI reports that automatic retry stopped while retaining the event. |
| 8 | Wait or generate another UI recomposition without operator action. | No fresh background retry job is silently scheduled. |
| 9 | Restore the service and select **Resume and retry now**. | Exhaustion state clears, delivery is attempted, and successful delivery empties the pending queue. |

## Pass criteria

Pass when rejected evidence survives process restart, retry preserves event
identity, discard affects only the selected item, eight failures stop automatic
work, and explicit recovery delivers without duplicate logical observations.

## Evidence to retain

- screenshots of the rejected-event detail and exhausted-retry message;
- redacted WorkManager attempt and terminal-state evidence;
- event identifiers before rejection, after retry, and in ATEP;
- rejected-list contents before and after selective discard;
- final pending/rejected counts and matching ATEP outbox evidence.
