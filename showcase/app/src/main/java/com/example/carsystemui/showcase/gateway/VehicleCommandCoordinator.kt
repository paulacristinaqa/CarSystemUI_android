package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.VehicleSimulationState

sealed interface CommandCycleResult {
    data object NoCommand : CommandCycleResult

    data class Completed(
        val commandId: String,
        val execution: CommandExecutionResult,
    ) : CommandCycleResult

    data class Deferred(val reason: String) : CommandCycleResult

    data class GatewayRejected(val reason: String) : CommandCycleResult
}

class VehicleCommandCoordinator(
    private val transport: VehicleCommandTransport,
    private val executor: VehicleCommandExecutor = VehicleCommandExecutor(),
) {
    fun pollOnce(
        current: VehicleSimulationState,
        applyState: (VehicleSimulationState) -> Boolean,
    ): CommandCycleResult = when (val claim = transport.claim()) {
        CommandClaimResult.NoCommand -> CommandCycleResult.NoCommand
        is CommandClaimResult.RetryableFailure -> CommandCycleResult.Deferred(claim.reason)
        is CommandClaimResult.Rejected -> CommandCycleResult.GatewayRejected(claim.reason)
        is CommandClaimResult.Claimed -> executeAndAcknowledge(claim.command, current, applyState)
    }

    private fun executeAndAcknowledge(
        command: GatewayVehicleCommand,
        current: VehicleSimulationState,
        applyState: (VehicleSimulationState) -> Boolean,
    ): CommandCycleResult {
        var execution = executor.execute(command, current)
        if (execution is CommandExecutionResult.Applied && !applyState(execution.state)) {
            execution = CommandExecutionResult.Rejected(
                "read_only_vehicle_source",
                "The active AAOS property source does not permit simulated commands",
            )
        }
        return when (val acknowledgement = transport.acknowledge(command, execution)) {
            CommandAcknowledgementResult.Delivered ->
                CommandCycleResult.Completed(command.commandId, execution)
            is CommandAcknowledgementResult.RetryableFailure ->
                CommandCycleResult.Deferred(acknowledgement.reason)
            is CommandAcknowledgementResult.Rejected ->
                CommandCycleResult.GatewayRejected(acknowledgement.reason)
        }
    }
}
