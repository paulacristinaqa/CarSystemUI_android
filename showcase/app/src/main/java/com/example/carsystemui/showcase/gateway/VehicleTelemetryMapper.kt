package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.VehicleSimulationState
import java.time.Instant
import java.util.UUID

class VehicleTelemetryMapper(
    private val now: () -> String = { Instant.now().toString() },
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {
    fun changedEvents(
        previous: VehicleSimulationState?,
        current: VehicleSimulationState,
    ): List<GatewayTelemetryEvent> = buildList {
        addIfChanged(previous?.powerState?.name, current.powerState.name, "power_state")
        addIfChanged(previous?.gear?.label, current.gear.label, "gear")
        addIfChanged(previous?.speedKmh, current.speedKmh, "vehicle_speed", "km/h")
        addIfChanged(previous?.isDriverDoorOpen, current.isDriverDoorOpen, "driver_door_open")
        addIfChanged(previous?.isSeatbeltFastened, current.isSeatbeltFastened, "seatbelt_fastened")
        addIfChanged(previous?.batteryLevel, current.batteryLevel, "battery_state_of_charge", "percent")
        addIfChanged(previous?.isChargerConnected, current.isChargerConnected, "charger_connected")
    }

    private fun MutableList<GatewayTelemetryEvent>.addIfChanged(
        previous: Any?,
        current: Any,
        property: String,
        unit: String? = null,
    ) {
        if (previous == current) return
        add(
            GatewayTelemetryEvent(
                eventId = nextId(),
                property = property,
                value = current.toString(),
                valueKind = when (current) {
                    is Boolean -> TelemetryValueKind.BOOLEAN
                    is Number -> TelemetryValueKind.NUMBER
                    else -> TelemetryValueKind.STRING
                },
                unit = unit,
                timestamp = now(),
            ),
        )
    }
}
