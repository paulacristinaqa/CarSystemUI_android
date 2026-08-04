package com.example.carsystemui.showcase.vehicle

import com.example.carsystemui.showcase.Gear
import com.example.carsystemui.showcase.VehiclePowerState
import com.example.carsystemui.showcase.VehicleSimulationState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AaosVehiclePropertySource(
    private val client: CarPropertyClient,
) : VehiclePropertySource {
    private val mutableState = MutableStateFlow(VehicleSimulationState())
    private val mutableStatus = MutableStateFlow(
        VehiclePropertySourceStatus(
            mode = VehiclePropertySourceMode.ANDROID_AUTOMOTIVE,
            connected = false,
            detail = "Waiting for CarPropertyManager",
        ),
    )
    private var batteryLevelWh: Float? = null
    private var batteryCapacityWh: Float? = null

    override val state: StateFlow<VehicleSimulationState> = mutableState.asStateFlow()
    override val status: StateFlow<VehiclePropertySourceStatus> = mutableStatus.asStateFlow()
    override val supportsSimulationControls: Boolean = false

    override fun start() {
        val result = runCatching { client.subscribe(::accept) }.getOrElse { error ->
            mutableStatus.value = VehiclePropertySourceStatus(
                mode = VehiclePropertySourceMode.ANDROID_AUTOMOTIVE,
                connected = false,
                detail = error.message ?: error.javaClass.simpleName,
            )
            return
        }
        mutableStatus.value = VehiclePropertySourceStatus(
            mode = VehiclePropertySourceMode.ANDROID_AUTOMOTIVE,
            connected = result.subscribedProperties > 0,
            detail = when {
                result.subscribedProperties == 0 -> "No accessible VHAL properties"
                result.errors.isEmpty() -> "Receiving CarPropertyManager events"
                else -> "${result.subscribedProperties} properties active; ${result.errors.size} unavailable"
            },
        )
    }

    override fun stop() = client.close()

    internal fun accept(reading: CarPropertyReading) {
        when (reading.kind) {
            CarPropertyKind.SPEED_METERS_PER_SECOND -> updateNumber(reading.value) { state, value ->
                state.copy(speedKmh = (abs(value) * METERS_PER_SECOND_TO_KMH).roundToInt())
            }
            CarPropertyKind.GEAR_SELECTION -> updateNumber(reading.value) { state, value ->
                state.copy(gear = gearFor(value.toInt()))
            }
            CarPropertyKind.IGNITION_STATE -> updateNumber(reading.value) { state, value ->
                state.copy(powerState = powerStateFor(value.toInt()))
            }
            CarPropertyKind.EV_BATTERY_LEVEL_WH -> {
                batteryLevelWh = (reading.value as? Number)?.toFloat()
                updateBatteryPercentage()
            }
            CarPropertyKind.EV_BATTERY_CAPACITY_WH -> {
                batteryCapacityWh = (reading.value as? Number)?.toFloat()
                updateBatteryPercentage()
            }
            CarPropertyKind.CHARGER_CONNECTED -> {
                val connected = reading.value as? Boolean ?: return
                mutableState.value = mutableState.value.copy(isChargerConnected = connected)
            }
        }
    }

    private fun updateNumber(
        raw: Any,
        transform: (VehicleSimulationState, Float) -> VehicleSimulationState,
    ) {
        val value = (raw as? Number)?.toFloat() ?: return
        mutableState.value = transform(mutableState.value, value)
    }

    private fun updateBatteryPercentage() {
        val level = batteryLevelWh ?: return
        val capacity = batteryCapacityWh?.takeIf { it > 0f } ?: return
        mutableState.value = mutableState.value.copy(
            batteryLevel = ((level / capacity) * 100f).roundToInt().coerceIn(0, 100),
        )
    }

    private fun gearFor(value: Int): Gear = when (value) {
        GEAR_REVERSE -> Gear.REVERSE
        GEAR_NEUTRAL -> Gear.NEUTRAL
        GEAR_DRIVE -> Gear.DRIVE
        else -> Gear.PARK
    }

    private fun powerStateFor(value: Int): VehiclePowerState = when (value) {
        IGNITION_ACCESSORY -> VehiclePowerState.ACCESSORY
        IGNITION_ON -> VehiclePowerState.READY
        else -> VehiclePowerState.OFF
    }

    private companion object {
        const val METERS_PER_SECOND_TO_KMH = 3.6f
        const val GEAR_NEUTRAL = 1
        const val GEAR_REVERSE = 2
        const val GEAR_DRIVE = 8
        const val IGNITION_ACCESSORY = 3
        const val IGNITION_ON = 4
    }
}
