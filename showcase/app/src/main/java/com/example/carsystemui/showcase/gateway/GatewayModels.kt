package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.VehicleSimulationState

data class GatewayConfig(
    val baseUrl: String,
    val vehicleId: String,
    val moduleId: String,
    val moduleToken: String,
) {
    val isEnabled: Boolean
        get() = baseUrl.isNotBlank() && vehicleId.isNotBlank() &&
            moduleId.isNotBlank() && moduleToken.isNotBlank()

    val normalizedBaseUrl: String
        get() = baseUrl.trimEnd('/')
}

enum class TelemetryValueKind {
    STRING,
    NUMBER,
    BOOLEAN,
}

data class GatewayTelemetryEvent(
    val eventId: String,
    val property: String,
    val value: String,
    val valueKind: TelemetryValueKind,
    val unit: String?,
    val timestamp: String,
    val source: String = "android-automotive-showcase",
)

data class RejectedTelemetryEvent(
    val event: GatewayTelemetryEvent,
    val reason: String,
)

data class TelemetryRetryState(
    val attempts: Int = 0,
    val exhausted: Boolean = false,
)

data class TelemetryQueueSnapshot(
    val pendingCount: Int,
    val rejectedEvents: List<RejectedTelemetryEvent>,
    val retryState: TelemetryRetryState,
)

enum class GatewayConnectionState {
    DISABLED,
    SYNCHRONIZED,
    PENDING,
    REJECTED,
}

data class GatewaySyncStatus(
    val state: GatewayConnectionState,
    val pendingCount: Int,
    val rejectedCount: Int,
    val lastError: String? = null,
    val retryAttempts: Int = 0,
    val retryExhausted: Boolean = false,
)

sealed interface TelemetryDeliveryResult {
    data object Delivered : TelemetryDeliveryResult

    data class RetryableFailure(val reason: String) : TelemetryDeliveryResult

    data class Rejected(val reason: String) : TelemetryDeliveryResult
}

interface PendingTelemetryStore {
    fun enqueue(events: List<GatewayTelemetryEvent>)

    fun pending(): List<GatewayTelemetryEvent>

    fun remove(eventId: String)

    fun reject(event: GatewayTelemetryEvent, reason: String)

    fun rejected(): List<RejectedTelemetryEvent>

    fun retryRejected(eventId: String): Boolean

    fun discardRejected(eventId: String): Boolean

    fun retryState(): TelemetryRetryState

    fun updateRetryState(state: TelemetryRetryState)
}

fun interface TelemetryTransport {
    fun send(event: GatewayTelemetryEvent): TelemetryDeliveryResult
}

data class GatewayVehicleCommand(
    val commandId: String,
    val claimToken: String,
    val kind: String,
    val property: String,
    val value: String,
    val valueKind: TelemetryValueKind,
)

sealed interface CommandClaimResult {
    data object NoCommand : CommandClaimResult

    data class Claimed(val command: GatewayVehicleCommand) : CommandClaimResult

    data class RetryableFailure(val reason: String) : CommandClaimResult

    data class Rejected(val reason: String) : CommandClaimResult
}

sealed interface CommandExecutionResult {
    data class Applied(
        val property: String,
        val value: String,
        val state: VehicleSimulationState,
    ) : CommandExecutionResult

    data class Rejected(
        val errorCode: String,
        val errorMessage: String,
    ) : CommandExecutionResult
}

sealed interface CommandAcknowledgementResult {
    data object Delivered : CommandAcknowledgementResult

    data class RetryableFailure(val reason: String) : CommandAcknowledgementResult

    data class Rejected(val reason: String) : CommandAcknowledgementResult
}

interface VehicleCommandTransport {
    fun claim(): CommandClaimResult

    fun acknowledge(
        command: GatewayVehicleCommand,
        execution: CommandExecutionResult,
    ): CommandAcknowledgementResult
}
