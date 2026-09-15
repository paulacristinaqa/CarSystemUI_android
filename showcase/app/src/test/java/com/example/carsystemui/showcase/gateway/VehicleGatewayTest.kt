package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.VehicleSimulationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleGatewayTest {
    private val enabledConfig = GatewayConfig(
        baseUrl = "http://10.0.2.2:8000",
        vehicleId = "vehicle-001",
        moduleId = "00000000-0000-0000-0000-000000000001",
        moduleToken = "a".repeat(48),
    )

    @Test
    fun `network failure preserves event ids and retry drains queue`() {
        val store = FakeStore()
        val sentIds = mutableListOf<String>()
        var firstAttempt = true
        val transport = TelemetryTransport { event ->
            sentIds += event.eventId
            if (firstAttempt) {
                firstAttempt = false
                TelemetryDeliveryResult.RetryableFailure("offline")
            } else {
                TelemetryDeliveryResult.Delivered
            }
        }
        val gateway = VehicleGateway(enabledConfig, store, transport, deterministicMapper())

        val offlineStatus = gateway.publishChanges(null, VehicleSimulationState())
        val originalFirstId = store.pending().first().eventId
        val recoveredStatus = gateway.flush()

        assertEquals(GatewayConnectionState.PENDING, offlineStatus.state)
        assertEquals(7, offlineStatus.pendingCount)
        assertEquals(GatewayConnectionState.SYNCHRONIZED, recoveredStatus.state)
        assertTrue(store.pending().isEmpty())
        assertEquals(listOf(originalFirstId, originalFirstId), sentIds.take(2))
    }

    @Test
    fun `permanent rejection moves event out of retry queue`() {
        val store = FakeStore()
        val gateway = VehicleGateway(
            enabledConfig,
            store,
            TelemetryTransport { TelemetryDeliveryResult.Rejected("contract conflict") },
            deterministicMapper(),
        )

        val status = gateway.publishChanges(
            VehicleSimulationState(),
            VehicleSimulationState(speedKmh = 10),
        )

        assertEquals(GatewayConnectionState.REJECTED, status.state)
        assertEquals(0, status.pendingCount)
        assertEquals(1, status.rejectedCount)
        assertEquals("contract conflict", status.lastError)
        assertEquals("event-1", store.rejected().single().event.eventId)
        assertEquals("contract conflict", store.rejected().single().reason)
    }

    @Test
    fun `retrying a rejected event preserves its identity and removes the rejection`() {
        val store = FakeStore()
        val sentIds = mutableListOf<String>()
        var reject = true
        val gateway = VehicleGateway(
            enabledConfig,
            store,
            TelemetryTransport { event ->
                sentIds += event.eventId
                if (reject) {
                    TelemetryDeliveryResult.Rejected("contract conflict")
                } else {
                    TelemetryDeliveryResult.Delivered
                }
            },
            deterministicMapper(),
        )

        gateway.publishChanges(VehicleSimulationState(), VehicleSimulationState(speedKmh = 10))
        val originalId = store.rejected().single().event.eventId
        reject = false
        val status = gateway.retryRejected(originalId)

        assertEquals(GatewayConnectionState.SYNCHRONIZED, status.state)
        assertEquals(listOf(originalId, originalId), sentIds)
        assertTrue(store.rejected().isEmpty())
    }

    @Test
    fun `discard removes only the selected rejected event`() {
        val store = FakeStore()
        val first = testEvent("event-1", "speed")
        val second = testEvent("event-2", "battery")
        store.reject(first, "invalid speed")
        store.reject(second, "invalid battery")
        val gateway = VehicleGateway(
            enabledConfig,
            store,
            TelemetryTransport { TelemetryDeliveryResult.Delivered },
        )

        val status = gateway.discardRejected(first.eventId)

        assertEquals(1, status.rejectedCount)
        assertEquals(second.eventId, store.rejected().single().event.eventId)
    }

    @Test
    fun `manual retry clears exhausted background state while retaining failed events`() {
        val store = FakeStore()
        store.enqueue(listOf(testEvent("event-1", "speed")))
        store.updateRetryState(TelemetryRetryState(attempts = 8, exhausted = true))
        val gateway = VehicleGateway(
            enabledConfig,
            store,
            TelemetryTransport { TelemetryDeliveryResult.RetryableFailure("still offline") },
        )

        val status = gateway.retryPending()

        assertEquals(GatewayConnectionState.PENDING, status.state)
        assertEquals(1, status.pendingCount)
        assertEquals(0, status.retryAttempts)
        assertEquals(false, status.retryExhausted)
    }

    @Test
    fun `missing workload credentials disables publication`() {
        val store = FakeStore()
        var calls = 0
        val gateway = VehicleGateway(
            enabledConfig.copy(moduleToken = ""),
            store,
            TelemetryTransport {
                calls++
                TelemetryDeliveryResult.Delivered
            },
            deterministicMapper(),
        )

        val status = gateway.publishChanges(null, VehicleSimulationState())

        assertEquals(GatewayConnectionState.DISABLED, status.state)
        assertTrue(store.pending().isEmpty())
        assertEquals(0, calls)
    }

    private fun deterministicMapper(): VehicleTelemetryMapper {
        var id = 0
        return VehicleTelemetryMapper(
            now = { "2026-08-04T12:00:00Z" },
            nextId = { "event-${++id}" },
        )
    }

    private fun testEvent(eventId: String, property: String) = GatewayTelemetryEvent(
        eventId = eventId,
        property = property,
        value = "10",
        valueKind = TelemetryValueKind.NUMBER,
        unit = null,
        timestamp = "2026-08-04T12:00:00Z",
    )

    private class FakeStore : PendingTelemetryStore {
        private val queued = mutableListOf<GatewayTelemetryEvent>()
        private val rejectedItems = mutableListOf<RejectedTelemetryEvent>()
        private var backgroundRetryState = TelemetryRetryState()

        override fun enqueue(events: List<GatewayTelemetryEvent>) {
            val known = queued.mapTo(mutableSetOf()) { it.eventId }
            queued += events.filterNot { it.eventId in known }
        }

        override fun pending(): List<GatewayTelemetryEvent> = queued.toList()

        override fun remove(eventId: String) {
            queued.removeAll { it.eventId == eventId }
        }

        override fun reject(event: GatewayTelemetryEvent, reason: String) {
            remove(event.eventId)
            rejectedItems += RejectedTelemetryEvent(event, reason)
        }

        override fun rejected(): List<RejectedTelemetryEvent> = rejectedItems.toList()

        override fun retryRejected(eventId: String): Boolean {
            val selected = rejectedItems.firstOrNull { it.event.eventId == eventId } ?: return false
            enqueue(listOf(selected.event))
            rejectedItems.removeAll { it.event.eventId == eventId }
            backgroundRetryState = TelemetryRetryState()
            return true
        }

        override fun discardRejected(eventId: String): Boolean =
            rejectedItems.removeAll { it.event.eventId == eventId }

        override fun retryState(): TelemetryRetryState = backgroundRetryState

        override fun updateRetryState(state: TelemetryRetryState) {
            backgroundRetryState = state
        }
    }
}
