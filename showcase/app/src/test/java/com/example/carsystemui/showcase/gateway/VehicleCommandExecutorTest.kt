package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.Gear
import com.example.carsystemui.showcase.VehiclePowerState
import com.example.carsystemui.showcase.VehicleSimulationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCommandExecutorTest {
    private val executor = VehicleCommandExecutor()

    @Test
    fun `allowed property command returns a bounded updated state`() {
        val result = executor.execute(command("battery_level", "12"), VehicleSimulationState())

        assertTrue(result is CommandExecutionResult.Applied)
        assertEquals(12, (result as CommandExecutionResult.Applied).state.batteryLevel)
    }

    @Test
    fun `out of range and unknown properties are rejected without mutation`() {
        val outOfRange = executor.execute(
            command("battery_level", "101"),
            VehicleSimulationState(),
        )
        val unknown = executor.execute(
            command("brake_pressure", "80"),
            VehicleSimulationState(),
        )

        assertEquals(
            "command_value_out_of_range",
            (outOfRange as CommandExecutionResult.Rejected).errorCode,
        )
        assertEquals(
            "property_not_allowed",
            (unknown as CommandExecutionResult.Rejected).errorCode,
        )
    }

    @Test
    fun `unsafe charging and gear combinations are rejected`() {
        val moving = VehicleSimulationState(speedKmh = 10, gear = Gear.DRIVE)
        val result = executor.execute(command("charger_connected", "true"), moving)

        assertEquals(
            "unsafe_vehicle_state",
            (result as CommandExecutionResult.Rejected).errorCode,
        )
    }

    @Test
    fun `commands cannot create motion without driving prerequisites`() {
        val ready = VehicleSimulationState(powerState = VehiclePowerState.READY, gear = Gear.DRIVE)
        val invalidStates = listOf(
            VehicleSimulationState(),
            ready.copy(powerState = VehiclePowerState.IGNITION_ON),
            ready.copy(gear = Gear.PARK),
            ready.copy(gear = Gear.NEUTRAL),
            ready.copy(batteryLevel = 0),
            ready.copy(isChargerConnected = true),
        )
        invalidStates.forEach { state ->
            val result = executor.execute(command("speed_kmh", "10"), state)
            assertEquals("unsafe_vehicle_state", (result as CommandExecutionResult.Rejected).errorCode)
        }
        assertTrue(executor.execute(command("speed_kmh", "10"), ready) is CommandExecutionResult.Applied)
    }

    @Test
    fun `moving vehicle cannot lose power or battery through a command`() {
        val moving = VehicleSimulationState(
            powerState = VehiclePowerState.READY, gear = Gear.DRIVE, speedKmh = 10,
        )
        listOf("power_state" to "ACCESSORY", "battery_level" to "0").forEach { (property, value) ->
            val result = executor.execute(command(property, value), moving)
            assertEquals("unsafe_vehicle_state", (result as CommandExecutionResult.Rejected).errorCode)
        }
        assertTrue(executor.execute(command("speed_kmh", "0"), moving) is CommandExecutionResult.Applied)
    }

    private fun command(property: String, value: String) = GatewayVehicleCommand(
        commandId = "command-001",
        claimToken = "c".repeat(48),
        kind = "set_property",
        property = property,
        value = value,
        valueKind = TelemetryValueKind.NUMBER,
    )
}
