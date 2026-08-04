package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.VehicleSimulationState

class VehicleGateway(
    private val config: GatewayConfig,
    private val store: PendingTelemetryStore,
    private val transport: TelemetryTransport,
    private val mapper: VehicleTelemetryMapper = VehicleTelemetryMapper(),
) {
    @Synchronized
    fun publishChanges(
        previous: VehicleSimulationState?,
        current: VehicleSimulationState,
    ): GatewaySyncStatus {
        if (!config.isEnabled) return currentStatus()
        store.enqueue(mapper.changedEvents(previous, current))
        return flush()
    }

    @Synchronized
    fun flush(): GatewaySyncStatus {
        if (!config.isEnabled) return currentStatus()

        var lastError: String? = null
        for (event in store.pending()) {
            when (val result = transport.send(event)) {
                TelemetryDeliveryResult.Delivered -> store.remove(event.eventId)
                is TelemetryDeliveryResult.Rejected -> {
                    store.reject(event, result.reason)
                    lastError = result.reason
                }
                is TelemetryDeliveryResult.RetryableFailure -> {
                    lastError = result.reason
                    break
                }
            }
        }
        if (store.pending().isEmpty()) store.updateRetryState(TelemetryRetryState())
        return currentStatus(lastError)
    }

    @Synchronized
    fun retryPending(): GatewaySyncStatus {
        store.updateRetryState(TelemetryRetryState())
        return flush()
    }

    @Synchronized
    fun retryRejected(eventId: String): GatewaySyncStatus {
        if (!store.retryRejected(eventId)) return currentStatus("Rejected event was not found")
        return flush()
    }

    @Synchronized
    fun discardRejected(eventId: String): GatewaySyncStatus {
        val removed = store.discardRejected(eventId)
        return currentStatus(if (removed) null else "Rejected event was not found")
    }

    fun rejectedEvents(): List<RejectedTelemetryEvent> = store.rejected()

    fun currentStatus(lastError: String? = null): GatewaySyncStatus {
        val pendingCount = store.pending().size
        val rejectedCount = store.rejected().size
        val retryState = store.retryState()
        val state = when {
            !config.isEnabled -> GatewayConnectionState.DISABLED
            rejectedCount > 0 -> GatewayConnectionState.REJECTED
            pendingCount > 0 -> GatewayConnectionState.PENDING
            else -> GatewayConnectionState.SYNCHRONIZED
        }
        return GatewaySyncStatus(
            state = state,
            pendingCount = pendingCount,
            rejectedCount = rejectedCount,
            lastError = lastError,
            retryAttempts = retryState.attempts,
            retryExhausted = retryState.exhausted,
        )
    }
}
