package com.example.carsystemui.showcase

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class VehicleSimulatorViewModel : ViewModel() {
    var vehicleState by mutableStateOf(VehicleSimulationState())
        private set
    var eventHistory by mutableStateOf(listOf(SimulationEvent(1, "Simulação iniciada")))
        private set
    private var nextSequence = 2

    fun advancePowerState() {
        if (vehicleState.powerState == VehiclePowerState.READY) return
        vehicleState = vehicleState.copy(powerState = vehicleState.powerState.next())
        record("Energia: ${vehicleState.powerState.title}")
    }

    fun powerOff() {
        if (vehicleState.powerState == VehiclePowerState.OFF || vehicleState.speedKmh != 0 || vehicleState.gear != Gear.PARK) return
        vehicleState = vehicleState.copy(powerState = VehiclePowerState.OFF, gear = Gear.PARK, speedKmh = 0)
        record("Veículo desligado")
    }

    fun selectGear(gear: Gear) {
        if (vehicleState.powerState != VehiclePowerState.READY || vehicleState.speedKmh != 0 || vehicleState.isChargerConnected || vehicleState.gear == gear) return
        vehicleState = vehicleState.copy(gear = gear)
        record("Marcha selecionada: ${gear.label}")
    }

    fun accelerate() {
        if (vehicleState.powerState != VehiclePowerState.READY || vehicleState.gear !in setOf(Gear.DRIVE, Gear.REVERSE) || vehicleState.speedKmh >= 120 || vehicleState.batteryLevel <= 0 || vehicleState.isChargerConnected) return
        vehicleState = vehicleState.copy(
            speedKmh = (vehicleState.speedKmh + 10).coerceAtMost(120),
            batteryLevel = (vehicleState.batteryLevel - 1).coerceAtLeast(0),
        )
        record("Aceleração: ${vehicleState.speedKmh} km/h")
    }

    fun brake() {
        if (vehicleState.powerState != VehiclePowerState.READY || vehicleState.speedKmh <= 0) return
        vehicleState = vehicleState.copy(speedKmh = (vehicleState.speedKmh - 10).coerceAtLeast(0))
        record("Frenagem: ${vehicleState.speedKmh} km/h")
    }

    fun setDriverDoorOpen(value: Boolean) {
        if (vehicleState.isDriverDoorOpen == value) return
        vehicleState = vehicleState.copy(isDriverDoorOpen = value)
        record(if (value) "Porta do motorista aberta" else "Porta do motorista fechada")
    }

    fun setSeatbeltFastened(value: Boolean) {
        if (vehicleState.isSeatbeltFastened == value) return
        vehicleState = vehicleState.copy(isSeatbeltFastened = value)
        record(if (value) "Cinto afivelado" else "Cinto desafivelado")
    }

    fun setChargerConnected(value: Boolean) {
        if (vehicleState.isChargerConnected == value) return
        if (value && (vehicleState.speedKmh != 0 || vehicleState.gear != Gear.PARK)) return
        vehicleState = vehicleState.copy(isChargerConnected = value)
        record(if (value) "Carregador conectado" else "Carregador desconectado")
    }

    fun addCharge() {
        if (!vehicleState.isChargerConnected || vehicleState.batteryLevel >= 100) return
        vehicleState = vehicleState.copy(batteryLevel = (vehicleState.batteryLevel + 10).coerceAtMost(100))
        record("Recarga: ${vehicleState.batteryLevel}%")
    }

    fun removeCharge() {
        if (vehicleState.speedKmh != 0 || vehicleState.batteryLevel <= 0) return
        vehicleState = vehicleState.copy(batteryLevel = (vehicleState.batteryLevel - 10).coerceAtLeast(0))
        record("Carga preparada: ${vehicleState.batteryLevel}%")
    }

    fun resetSimulation() {
        vehicleState = VehicleSimulationState()
        eventHistory = emptyList()
        nextSequence = 1
        record("Simulação reiniciada")
    }

    private fun record(description: String) {
        eventHistory = (listOf(SimulationEvent(nextSequence++, description)) + eventHistory).take(8)
    }
}
