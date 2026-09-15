package com.example.carsystemui.showcase

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carsystemui.showcase.gateway.GatewaySyncStatus
import com.example.carsystemui.showcase.gateway.CommandCycleResult
import com.example.carsystemui.showcase.gateway.CommandExecutionResult
import com.example.carsystemui.showcase.gateway.RejectedTelemetryEvent
import com.example.carsystemui.showcase.gateway.TestRunLiveState
import com.example.carsystemui.showcase.gateway.VehicleGatewayFactory
import com.example.carsystemui.showcase.vehicle.MutableVehiclePropertySource
import com.example.carsystemui.showcase.vehicle.VehiclePropertySourceFactory
import com.example.carsystemui.showcase.vehicle.VehiclePropertySourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VehicleSimulatorViewModel(application: Application) : AndroidViewModel(application) {
    var vehicleState by mutableStateOf(VehicleSimulationState())
        private set
    var eventHistory by mutableStateOf(listOf(SimulationEvent(1, "Simulação iniciada")))
        private set
    var gatewayStatus by mutableStateOf<GatewaySyncStatus?>(null)
        private set
    var rejectedTelemetryEvents by mutableStateOf<List<RejectedTelemetryEvent>>(emptyList())
        private set
    var propertySourceStatus by mutableStateOf<VehiclePropertySourceStatus?>(null)
        private set
    var testRunLiveState by mutableStateOf<TestRunLiveState?>(null)
        private set

    val simulationControlsEnabled: Boolean
        get() = propertySource.supportsSimulationControls

    private val gatewayStore = VehicleGatewayFactory.store(application)
    private val gateway = VehicleGatewayFactory.create(application, gatewayStore)
    private val retryScheduler = VehicleGatewayFactory.retryScheduler(application)
    private val commandCoordinator = VehicleGatewayFactory.commandCoordinator()
    private val testRunLiveClient = VehicleGatewayFactory.testRunLiveClient { state ->
        viewModelScope.launch { testRunLiveState = state }
    }
    private val propertySource = VehiclePropertySourceFactory.create(application)
    private val gatewayDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var nextSequence = 2

    init {
        testRunLiveClient.start()
        propertySource.start()
        viewModelScope.launch {
            gatewayStore.observeSnapshot().collect { snapshot ->
                rejectedTelemetryEvents = snapshot.rejectedEvents
                val status = gateway.currentStatus(gatewayStatus?.lastError)
                gatewayStatus = status
                retryScheduler.reconcile(status)
            }
        }
        viewModelScope.launch {
            propertySource.status.collect { status -> propertySourceStatus = status }
        }
        viewModelScope.launch {
            var previous: VehicleSimulationState? = null
            propertySource.state.collect { current ->
                vehicleState = current
                if (current != previous) publishChanges(previous, current)
                previous = current
            }
        }
        if (VehicleGatewayFactory.config().isEnabled) {
            viewModelScope.launch(gatewayDispatcher) {
                while (isActive) {
                    val result = commandCoordinator.pollOnce(propertySource.state.value) { updated ->
                        val mutableSource = propertySource as? MutableVehiclePropertySource
                            ?: return@pollOnce false
                        mutableSource.update(updated)
                        true
                    }
                    handleCommandResult(result)
                    delay(COMMAND_POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun advancePowerState() {
        if (vehicleState.powerState == VehiclePowerState.READY) return
        applyState(
            vehicleState.copy(powerState = vehicleState.powerState.next()),
            "Energia: ${vehicleState.powerState.next().title}",
        )
    }

    fun powerOff() {
        if (vehicleState.powerState == VehiclePowerState.OFF ||
            vehicleState.speedKmh != 0 || vehicleState.gear != Gear.PARK
        ) return
        applyState(
            vehicleState.copy(powerState = VehiclePowerState.OFF, gear = Gear.PARK, speedKmh = 0),
            "Veículo desligado",
        )
    }

    fun selectGear(gear: Gear) {
        if (vehicleState.powerState != VehiclePowerState.READY ||
            vehicleState.speedKmh != 0 || vehicleState.isChargerConnected ||
            vehicleState.gear == gear
        ) return
        applyState(vehicleState.copy(gear = gear), "Marcha selecionada: ${gear.label}")
    }

    fun accelerate() {
        if (vehicleState.powerState != VehiclePowerState.READY ||
            vehicleState.gear !in setOf(Gear.DRIVE, Gear.REVERSE) ||
            vehicleState.speedKmh >= 120 || vehicleState.batteryLevel <= 0 ||
            vehicleState.isChargerConnected
        ) return
        val updated = vehicleState.copy(
            speedKmh = (vehicleState.speedKmh + 10).coerceAtMost(120),
            batteryLevel = (vehicleState.batteryLevel - 1).coerceAtLeast(0),
        )
        applyState(updated, "Aceleração: ${updated.speedKmh} km/h")
    }

    fun brake() {
        if (vehicleState.powerState != VehiclePowerState.READY || vehicleState.speedKmh <= 0) return
        val updated = vehicleState.copy(speedKmh = (vehicleState.speedKmh - 10).coerceAtLeast(0))
        applyState(updated, "Frenagem: ${updated.speedKmh} km/h")
    }

    fun setDriverDoorOpen(value: Boolean) {
        if (vehicleState.isDriverDoorOpen == value) return
        applyState(
            vehicleState.copy(isDriverDoorOpen = value),
            if (value) "Porta do motorista aberta" else "Porta do motorista fechada",
        )
    }

    fun setSeatbeltFastened(value: Boolean) {
        if (vehicleState.isSeatbeltFastened == value) return
        applyState(
            vehicleState.copy(isSeatbeltFastened = value),
            if (value) "Cinto afivelado" else "Cinto desafivelado",
        )
    }

    fun setChargerConnected(value: Boolean) {
        if (vehicleState.isChargerConnected == value) return
        if (value && (vehicleState.speedKmh != 0 || vehicleState.gear != Gear.PARK)) return
        applyState(
            vehicleState.copy(isChargerConnected = value),
            if (value) "Carregador conectado" else "Carregador desconectado",
        )
    }

    fun addCharge() {
        if (!vehicleState.isChargerConnected || vehicleState.batteryLevel >= 100) return
        val updated = vehicleState.copy(batteryLevel = (vehicleState.batteryLevel + 10).coerceAtMost(100))
        applyState(updated, "Recarga: ${updated.batteryLevel}%")
    }

    fun removeCharge() {
        if (vehicleState.speedKmh != 0 || vehicleState.batteryLevel <= 0) return
        val updated = vehicleState.copy(batteryLevel = (vehicleState.batteryLevel - 10).coerceAtLeast(0))
        applyState(updated, "Carga preparada: ${updated.batteryLevel}%")
    }

    fun resetSimulation() {
        val mutableSource = propertySource as? MutableVehiclePropertySource ?: return
        val initialState = VehicleSimulationState()
        mutableSource.update(initialState)
        vehicleState = initialState
        eventHistory = emptyList()
        nextSequence = 1
        record("Simulação reiniciada")
    }

    fun retryGateway() {
        viewModelScope.launch(gatewayDispatcher) {
            updateGatewayStatus(gateway.retryPending())
        }
    }

    fun retryRejectedEvent(eventId: String) {
        viewModelScope.launch(gatewayDispatcher) {
            updateGatewayStatus(gateway.retryRejected(eventId))
        }
    }

    fun discardRejectedEvent(eventId: String) {
        viewModelScope.launch(gatewayDispatcher) {
            updateGatewayStatus(gateway.discardRejected(eventId))
        }
    }

    private fun applyState(updated: VehicleSimulationState, description: String) {
        val mutableSource = propertySource as? MutableVehiclePropertySource ?: return
        if (updated == mutableSource.state.value) return
        mutableSource.update(updated)
        vehicleState = updated
        record(description)
    }

    private fun publishChanges(
        previous: VehicleSimulationState?,
        current: VehicleSimulationState,
    ) {
        viewModelScope.launch(gatewayDispatcher) {
            updateGatewayStatus(gateway.publishChanges(previous, current))
        }
    }

    private fun updateGatewayStatus(status: GatewaySyncStatus) {
        gatewayStatus = status
        retryScheduler.reconcile(status)
    }

    private fun record(description: String) {
        eventHistory = (listOf(SimulationEvent(nextSequence++, description)) + eventHistory).take(8)
    }

    private suspend fun handleCommandResult(result: CommandCycleResult) {
        val description = when (result) {
            CommandCycleResult.NoCommand -> return
            is CommandCycleResult.Deferred -> "Comando ATEP adiado: ${result.reason}"
            is CommandCycleResult.GatewayRejected -> "Comando ATEP recusado: ${result.reason}"
            is CommandCycleResult.Completed -> when (val execution = result.execution) {
                is CommandExecutionResult.Applied ->
                    "Comando ATEP aplicado: ${execution.property}=${execution.value}"
                is CommandExecutionResult.Rejected ->
                    "Comando ATEP rejeitado: ${execution.errorCode}"
            }
        }
        withContext(Dispatchers.Main) { record(description) }
    }

    override fun onCleared() {
        testRunLiveClient.close()
        propertySource.stop()
        super.onCleared()
    }

    private companion object {
        const val COMMAND_POLL_INTERVAL_MS = 5_000L
    }
}
