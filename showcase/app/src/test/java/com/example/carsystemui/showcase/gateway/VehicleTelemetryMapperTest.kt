package com.example.carsystemui.showcase.gateway

import com.example.carsystemui.showcase.VehicleSimulationState
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleTelemetryMapperTest {
    @Test
    fun `initial snapshot maps every supported property`() {
        var id = 0
        val mapper = VehicleTelemetryMapper(
            now = { "2026-08-04T12:00:00Z" },
            nextId = { "event-${++id}" },
        )

        val events = mapper.changedEvents(null, VehicleSimulationState())

        assertEquals(7, events.size)
        assertEquals(
            setOf(
                "power_state",
                "gear",
                "vehicle_speed",
                "driver_door_open",
                "seatbelt_fastened",
                "battery_state_of_charge",
                "charger_connected",
            ),
            events.map { it.property }.toSet(),
        )
        assertEquals(listOf("event-1", "event-2", "event-3", "event-4", "event-5", "event-6", "event-7"), events.map { it.eventId })
        assertEquals(setOf("2026-08-04T12:00:00Z"), events.map { it.timestamp }.toSet())
    }

    @Test
    fun `state transition emits only changed values`() {
        val mapper = VehicleTelemetryMapper(
            now = { "2026-08-04T12:00:00Z" },
            nextId = { "stable-id" },
        )
        val previous = VehicleSimulationState()
        val current = previous.copy(speedKmh = 10, batteryLevel = 77)

        val events = mapper.changedEvents(previous, current)

        assertEquals(listOf("vehicle_speed", "battery_state_of_charge"), events.map { it.property })
        assertEquals(listOf("10", "77"), events.map { it.value })
        assertEquals(listOf("km/h", "percent"), events.map { it.unit })
    }
}
