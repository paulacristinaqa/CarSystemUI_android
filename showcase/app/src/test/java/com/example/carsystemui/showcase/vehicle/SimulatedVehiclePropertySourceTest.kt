package com.example.carsystemui.showcase.vehicle

import com.example.carsystemui.showcase.VehicleSimulationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedVehiclePropertySourceTest {
    @Test
    fun `publishes deterministic mutable simulator state`() {
        val source = SimulatedVehiclePropertySource()
        val updated = VehicleSimulationState(speedKmh = 40, batteryLevel = 70)

        source.update(updated)

        assertEquals(updated, source.state.value)
        assertTrue(source.status.value.connected)
        assertTrue(source.supportsSimulationControls)
    }
}
