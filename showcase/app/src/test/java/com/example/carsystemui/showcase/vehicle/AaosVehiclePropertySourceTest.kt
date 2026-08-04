package com.example.carsystemui.showcase.vehicle

import com.example.carsystemui.showcase.Gear
import com.example.carsystemui.showcase.VehiclePowerState
import com.example.carsystemui.showcase.VehicleSimulationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaosVehiclePropertySourceTest {
    @Test
    fun `maps AAOS readings into the shared vehicle state`() {
        val client = FakeCarPropertyClient()
        val source = AaosVehiclePropertySource(client)

        source.start()
        client.emit(CarPropertyReading(CarPropertyKind.IGNITION_STATE, 4))
        client.emit(CarPropertyReading(CarPropertyKind.GEAR_SELECTION, 8))
        client.emit(CarPropertyReading(CarPropertyKind.SPEED_METERS_PER_SECOND, -10f))
        client.emit(CarPropertyReading(CarPropertyKind.EV_BATTERY_CAPACITY_WH, 80_000f))
        client.emit(CarPropertyReading(CarPropertyKind.EV_BATTERY_LEVEL_WH, 60_000f))
        client.emit(CarPropertyReading(CarPropertyKind.CHARGER_CONNECTED, true))

        assertEquals(
            VehicleSimulationState(
                powerState = VehiclePowerState.READY,
                gear = Gear.DRIVE,
                speedKmh = 36,
                batteryLevel = 75,
                isChargerConnected = true,
            ),
            source.state.value,
        )
        assertTrue(source.status.value.connected)
        assertFalse(source.supportsSimulationControls)
    }

    @Test
    fun `calculates state of charge regardless of battery reading order`() {
        val client = FakeCarPropertyClient()
        val source = AaosVehiclePropertySource(client)
        source.start()

        client.emit(CarPropertyReading(CarPropertyKind.EV_BATTERY_LEVEL_WH, 21_000f))
        assertEquals(78, source.state.value.batteryLevel)
        client.emit(CarPropertyReading(CarPropertyKind.EV_BATTERY_CAPACITY_WH, 60_000f))

        assertEquals(35, source.state.value.batteryLevel)
    }

    @Test
    fun `reports unavailable VHAL without enabling simulator controls`() {
        val source = AaosVehiclePropertySource(
            FakeCarPropertyClient(CarPropertySubscriptionResult(0, listOf("permission denied"))),
        )

        source.start()

        assertFalse(source.status.value.connected)
        assertEquals("No accessible VHAL properties", source.status.value.detail)
        assertFalse(source.supportsSimulationControls)
    }

    private class FakeCarPropertyClient(
        private val result: CarPropertySubscriptionResult = CarPropertySubscriptionResult(6, emptyList()),
    ) : CarPropertyClient {
        private var callback: ((CarPropertyReading) -> Unit)? = null

        override fun subscribe(
            onReading: (CarPropertyReading) -> Unit,
        ): CarPropertySubscriptionResult {
            callback = onReading
            return result
        }

        override fun close() {
            callback = null
        }

        fun emit(reading: CarPropertyReading) {
            callback?.invoke(reading)
        }
    }
}
