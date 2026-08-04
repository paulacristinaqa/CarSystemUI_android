package com.example.carsystemui.showcase.vehicle

import com.example.carsystemui.showcase.VehicleSimulationState
import kotlinx.coroutines.flow.StateFlow

enum class VehiclePropertySourceMode {
    SIMULATOR,
    ANDROID_AUTOMOTIVE,
}

data class VehiclePropertySourceStatus(
    val mode: VehiclePropertySourceMode,
    val connected: Boolean,
    val detail: String,
)

interface VehiclePropertySource {
    val state: StateFlow<VehicleSimulationState>
    val status: StateFlow<VehiclePropertySourceStatus>
    val supportsSimulationControls: Boolean

    fun start()

    fun stop()
}

interface MutableVehiclePropertySource : VehiclePropertySource {
    fun update(state: VehicleSimulationState)
}

enum class CarPropertyKind {
    SPEED_METERS_PER_SECOND,
    GEAR_SELECTION,
    IGNITION_STATE,
    EV_BATTERY_LEVEL_WH,
    EV_BATTERY_CAPACITY_WH,
    CHARGER_CONNECTED,
}

data class CarPropertyReading(
    val kind: CarPropertyKind,
    val value: Any,
)

data class CarPropertySubscriptionResult(
    val subscribedProperties: Int,
    val errors: List<String>,
)

interface CarPropertyClient {
    fun subscribe(onReading: (CarPropertyReading) -> Unit): CarPropertySubscriptionResult

    fun close()
}
