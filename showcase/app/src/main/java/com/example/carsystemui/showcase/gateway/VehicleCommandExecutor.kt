package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.Gear
import com.example.carsystemui.showcase.VehiclePowerState
import com.example.carsystemui.showcase.VehicleSimulationState

class VehicleCommandExecutor {
    fun execute(
        command: GatewayVehicleCommand,
        current: VehicleSimulationState,
    ): CommandExecutionResult {
        if (command.kind != "set_property") {
            return rejected("unsupported_command", "Unsupported command kind: ${command.kind}")
        }
        val updated = when (command.property) {
            "battery_level" -> {
                val value = command.value.strictIntOrNull()
                    ?: return invalidType(command, "integer")
                if (value !in 0..100) return invalidRange(command, "0..100")
                current.copy(batteryLevel = value)
            }
            "speed_kmh" -> {
                val value = command.value.strictIntOrNull()
                    ?: return invalidType(command, "integer")
                if (value !in 0..120) return invalidRange(command, "0..120")
                if (value > 0 && current.isChargerConnected) {
                    return rejected("unsafe_vehicle_state", "Speed cannot change while charging")
                }
                current.copy(speedKmh = value)
            }
            "driver_door_open" -> current.copy(
                isDriverDoorOpen = command.value.strictBooleanOrNull()
                    ?: return invalidType(command, "boolean"),
            )
            "seatbelt_fastened" -> current.copy(
                isSeatbeltFastened = command.value.strictBooleanOrNull()
                    ?: return invalidType(command, "boolean"),
            )
            "charger_connected" -> {
                val value = command.value.strictBooleanOrNull()
                    ?: return invalidType(command, "boolean")
                if (value && (current.speedKmh != 0 || current.gear != Gear.PARK)) {
                    return rejected(
                        "unsafe_vehicle_state",
                        "Charging requires zero speed and PARK",
                    )
                }
                current.copy(isChargerConnected = value)
            }
            "gear" -> {
                val value = enumValueOrNull<Gear>(command.value)
                    ?: return invalidType(command, "PARK, REVERSE, NEUTRAL, or DRIVE")
                if (current.speedKmh != 0 || current.isChargerConnected) {
                    return rejected(
                        "unsafe_vehicle_state",
                        "Gear changes require zero speed and a disconnected charger",
                    )
                }
                current.copy(gear = value)
            }
            "power_state" -> {
                val value = enumValueOrNull<VehiclePowerState>(command.value)
                    ?: return invalidType(
                        command,
                        "OFF, ACCESSORY, IGNITION_ON, or READY",
                    )
                if (value == VehiclePowerState.OFF &&
                    (current.speedKmh != 0 || current.gear != Gear.PARK)
                ) {
                    return rejected(
                        "unsafe_vehicle_state",
                        "Power-off requires zero speed and PARK",
                    )
                }
                current.copy(powerState = value)
            }
            else -> return rejected(
                "property_not_allowed",
                "Property is not command-enabled: ${command.property}",
            )
        }
        if (updated.speedKmh > 0 &&
            (updated.powerState != VehiclePowerState.READY ||
                updated.gear !in setOf(Gear.DRIVE, Gear.REVERSE) ||
                updated.batteryLevel == 0 || updated.isChargerConnected)
        ) {
            return rejected("unsafe_vehicle_state", "Motion requires READY, a driving gear, battery and no charger")
        }
        return CommandExecutionResult.Applied(command.property, command.value, updated)
    }

    private fun invalidType(
        command: GatewayVehicleCommand,
        expected: String,
    ): CommandExecutionResult.Rejected = rejected(
        "invalid_command_value",
        "${command.property} requires $expected",
    )

    private fun invalidRange(
        command: GatewayVehicleCommand,
        expected: String,
    ): CommandExecutionResult.Rejected = rejected(
        "command_value_out_of_range",
        "${command.property} must be within $expected",
    )

    private fun rejected(code: String, message: String) =
        CommandExecutionResult.Rejected(code, message)

    private fun String.strictIntOrNull(): Int? {
        val number = toDoubleOrNull() ?: return null
        if (!number.isFinite() || number % 1.0 != 0.0) return null
        return number.toInt()
    }

    private fun String.strictBooleanOrNull(): Boolean? = when (this) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value.uppercase() }
}
