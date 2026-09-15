package com.example.carsystemui.showcase

enum class VehiclePowerState(val title: String, val description: String) {
    OFF("Desligado", "Sistemas de condução indisponíveis."),
    ACCESSORY("Acessórios", "Multimídia disponível; condução indisponível."),
    IGNITION_ON("Ignição ligada", "Sistemas do veículo iniciando e verificando sinais."),
    READY("Pronto para conduzir", "Sistema de tração simulado disponível."),
}

fun VehiclePowerState.next(): VehiclePowerState = when (this) {
    VehiclePowerState.OFF -> VehiclePowerState.ACCESSORY
    VehiclePowerState.ACCESSORY -> VehiclePowerState.IGNITION_ON
    VehiclePowerState.IGNITION_ON -> VehiclePowerState.READY
    VehiclePowerState.READY -> VehiclePowerState.READY
}

enum class Gear(val label: String) { PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D") }

data class VehicleSimulationState(
    val powerState: VehiclePowerState = VehiclePowerState.OFF,
    val gear: Gear = Gear.PARK,
    val speedKmh: Int = 0,
    val isDriverDoorOpen: Boolean = false,
    val isSeatbeltFastened: Boolean = true,
    val batteryLevel: Int = 78,
    val isChargerConnected: Boolean = false,
)

enum class AlertSeverity(val label: String) {
    INFORMATION("INFORMAÇÃO"), WARNING("ATENÇÃO"), CRITICAL("CRÍTICO")
}

data class VehicleAlert(val title: String, val message: String, val severity: AlertSeverity)
data class SimulationEvent(val sequence: Int, val description: String)

fun VehicleSimulationState.activeAlerts(): List<VehicleAlert> = buildList {
    if (batteryLevel <= 20) {
        add(
            VehicleAlert(
                title = if (batteryLevel <= 10) "Bateria de tração crítica" else "Bateria de tração baixa",
                message = if (batteryLevel <= 10) {
                    "Carga em $batteryLevel%. Procure um ponto de recarga."
                } else {
                    "Carga em $batteryLevel%. Planeje uma recarga."
                },
                severity = if (batteryLevel <= 10) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
            ),
        )
    }
    if (isChargerConnected) {
        add(
            VehicleAlert(
                "Carregador conectado",
                "Desconecte o carregador antes de selecionar uma marcha.",
                AlertSeverity.INFORMATION,
            ),
        )
    }
    if (isDriverDoorOpen) {
        add(
            VehicleAlert(
                "Porta do motorista aberta",
                if (speedKmh > 0) "Pare em segurança e feche a porta."
                else "Feche a porta antes de iniciar o movimento.",
                if (speedKmh > 0) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
            ),
        )
    }
    if (!isSeatbeltFastened && powerState == VehiclePowerState.READY) {
        add(
            VehicleAlert(
                "Cinto do motorista desafivelado",
                if (speedKmh > 0) "Afivele o cinto assim que for seguro."
                else "Afivele o cinto antes de iniciar o movimento.",
                if (speedKmh > 0) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
            ),
        )
    }
}.sortedByDescending { it.severity.ordinal }
