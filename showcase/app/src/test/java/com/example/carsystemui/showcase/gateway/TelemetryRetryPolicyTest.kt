package com.example.carsystemui.showcase.gateway

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryRetryPolicyTest {
    @Test
    fun `does not retry a disabled gateway even if an older queue exists`() {
        val status = GatewaySyncStatus(
            state = GatewayConnectionState.DISABLED,
            pendingCount = 2,
            rejectedCount = 0,
        )

        assertEquals(
            TelemetryRetryDecision.COMPLETE,
            TelemetryRetryPolicy.decide(status, completedAttempts = 0),
        )
    }

    @Test
    fun `completes when no pending telemetry remains even if rejections exist`() {
        val status = GatewaySyncStatus(
            state = GatewayConnectionState.REJECTED,
            pendingCount = 0,
            rejectedCount = 2,
        )

        assertEquals(
            TelemetryRetryDecision.COMPLETE,
            TelemetryRetryPolicy.decide(status, completedAttempts = 0),
        )
    }

    @Test
    fun `retries pending telemetry before attempt limit`() {
        val status = GatewaySyncStatus(
            state = GatewayConnectionState.PENDING,
            pendingCount = 3,
            rejectedCount = 0,
        )

        assertEquals(
            TelemetryRetryDecision.RETRY,
            TelemetryRetryPolicy.decide(status, completedAttempts = 6),
        )
    }

    @Test
    fun `exhausts bounded work after eighth attempt`() {
        val status = GatewaySyncStatus(
            state = GatewayConnectionState.PENDING,
            pendingCount = 1,
            rejectedCount = 0,
        )

        assertEquals(
            TelemetryRetryDecision.EXHAUSTED,
            TelemetryRetryPolicy.decide(status, completedAttempts = 7),
        )
        assertEquals(
            TelemetryRetryState(attempts = 8, exhausted = true),
            TelemetryRetryPolicy.progress(
                TelemetryRetryDecision.EXHAUSTED,
                completedAttempts = 7,
            ),
        )
    }

    @Test
    fun `schedules persistent work while pending events remain`() {
        val status = GatewaySyncStatus(
            state = GatewayConnectionState.PENDING,
            pendingCount = 2,
            rejectedCount = 0,
            retryAttempts = 3,
        )

        assertEquals(true, TelemetryRetrySchedulingPolicy.shouldSchedule(status))
    }

    @Test
    fun `does not automatically restart exhausted work`() {
        val status = GatewaySyncStatus(
            state = GatewayConnectionState.PENDING,
            pendingCount = 2,
            rejectedCount = 0,
            retryAttempts = 8,
            retryExhausted = true,
        )

        assertEquals(false, TelemetryRetrySchedulingPolicy.shouldSchedule(status))
    }
}
