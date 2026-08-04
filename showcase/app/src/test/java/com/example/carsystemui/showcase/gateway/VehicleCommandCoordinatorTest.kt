package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.VehicleSimulationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCommandCoordinatorTest {
    @Test
    fun `claimed command mutates simulator and is acknowledged`() {
        val transport = FakeCommandTransport(command("battery_level", "25"))
        val coordinator = VehicleCommandCoordinator(transport)
        var applied: VehicleSimulationState? = null

        val result = coordinator.pollOnce(VehicleSimulationState()) { state ->
            applied = state
            true
        }

        assertTrue(result is CommandCycleResult.Completed)
        assertEquals(25, applied?.batteryLevel)
        assertTrue(transport.execution is CommandExecutionResult.Applied)
    }

    @Test
    fun `read only property source rejects command and acknowledges evidence`() {
        val transport = FakeCommandTransport(command("battery_level", "25"))
        val coordinator = VehicleCommandCoordinator(transport)

        val result = coordinator.pollOnce(VehicleSimulationState()) { false }

        assertTrue(result is CommandCycleResult.Completed)
        val execution = transport.execution as CommandExecutionResult.Rejected
        assertEquals("read_only_vehicle_source", execution.errorCode)
    }

    @Test
    fun `acknowledgement outage defers completion for lease based replay`() {
        val transport = FakeCommandTransport(
            command("battery_level", "25"),
            acknowledgement = CommandAcknowledgementResult.RetryableFailure("offline"),
        )
        val coordinator = VehicleCommandCoordinator(transport)

        val result = coordinator.pollOnce(VehicleSimulationState()) { true }

        assertEquals(CommandCycleResult.Deferred("offline"), result)
    }

    private fun command(property: String, value: String) = GatewayVehicleCommand(
        commandId = "command-001",
        claimToken = "c".repeat(48),
        kind = "set_property",
        property = property,
        value = value,
        valueKind = TelemetryValueKind.NUMBER,
    )

    private class FakeCommandTransport(
        private val command: GatewayVehicleCommand,
        private val acknowledgement: CommandAcknowledgementResult =
            CommandAcknowledgementResult.Delivered,
    ) : VehicleCommandTransport {
        var execution: CommandExecutionResult? = null

        override fun claim(): CommandClaimResult = CommandClaimResult.Claimed(command)

        override fun acknowledge(
            command: GatewayVehicleCommand,
            execution: CommandExecutionResult,
        ): CommandAcknowledgementResult {
            this.execution = execution
            return acknowledgement
        }
    }
}
