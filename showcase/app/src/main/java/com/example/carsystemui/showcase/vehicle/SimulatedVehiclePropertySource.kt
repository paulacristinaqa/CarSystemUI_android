package com.example.carsystemui.showcase.vehicle

import com.example.carsystemui.showcase.VehicleSimulationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimulatedVehiclePropertySource(
    initialState: VehicleSimulationState = VehicleSimulationState(),
) : MutableVehiclePropertySource {
    private val mutableState = MutableStateFlow(initialState)
    private val mutableStatus = MutableStateFlow(
        VehiclePropertySourceStatus(
            mode = VehiclePropertySourceMode.SIMULATOR,
            connected = true,
            detail = "Local deterministic simulator",
        ),
    )

    override val state: StateFlow<VehicleSimulationState> = mutableState.asStateFlow()
    override val status: StateFlow<VehiclePropertySourceStatus> = mutableStatus.asStateFlow()
    override val supportsSimulationControls: Boolean = true

    override fun start() = Unit

    override fun stop() = Unit

    override fun update(state: VehicleSimulationState) {
        mutableState.value = state
    }
}
