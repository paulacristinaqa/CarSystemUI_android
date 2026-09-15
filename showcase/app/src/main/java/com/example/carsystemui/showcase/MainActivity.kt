package com.example.carsystemui.showcase

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carsystemui.showcase.gateway.GatewayConnectionState
import com.example.carsystemui.showcase.gateway.GatewaySyncStatus
import com.example.carsystemui.showcase.gateway.RejectedTelemetryEvent
import com.example.carsystemui.showcase.gateway.TestRunLiveConnection
import com.example.carsystemui.showcase.gateway.TestRunLiveState
import com.example.carsystemui.showcase.vehicle.VehiclePropertySourceMode
import com.example.carsystemui.showcase.vehicle.VehiclePropertySourceStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            requestPermissions(AAOS_READ_PERMISSIONS, AAOS_PERMISSION_REQUEST)
        }
        setContent {
            CarSystemUIShowcaseApp()
        }
    }

    private companion object {
        const val AAOS_PERMISSION_REQUEST = 100
        val AAOS_READ_PERMISSIONS = arrayOf(
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_ENERGY_PORTS",
            "android.car.permission.CAR_POWERTRAIN",
            "android.car.permission.CAR_INFO",
        )
    }
}

@Composable
private fun CarSystemUIShowcaseApp(simulator: VehicleSimulatorViewModel = viewModel()) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B0F14),
        ) {
            VehicleStatusScreen(
                vehicleState = simulator.vehicleState,
                eventHistory = simulator.eventHistory,
                gatewayStatus = simulator.gatewayStatus,
                rejectedTelemetryEvents = simulator.rejectedTelemetryEvents,
                propertySourceStatus = simulator.propertySourceStatus,
                testRunLiveState = simulator.testRunLiveState,
                simulationControlsEnabled = simulator.simulationControlsEnabled,
                onAdvancePowerState = simulator::advancePowerState,
                onPowerOff = simulator::powerOff,
                onGearSelected = simulator::selectGear,
                onAccelerate = simulator::accelerate,
                onBrake = simulator::brake,
                onDriverDoorChanged = simulator::setDriverDoorOpen,
                onSeatbeltChanged = simulator::setSeatbeltFastened,
                onChargerConnectionChanged = simulator::setChargerConnected,
                onChargeAdded = simulator::addCharge,
                onChargeRemoved = simulator::removeCharge,
                onResetSimulation = simulator::resetSimulation,
                onRetryGateway = simulator::retryGateway,
                onRetryRejectedEvent = simulator::retryRejectedEvent,
                onDiscardRejectedEvent = simulator::discardRejectedEvent,
            )
        }
    }
}

@Composable
private fun VehicleStatusScreen(
    vehicleState: VehicleSimulationState,
    eventHistory: List<SimulationEvent>,
    gatewayStatus: GatewaySyncStatus?,
    rejectedTelemetryEvents: List<RejectedTelemetryEvent>,
    propertySourceStatus: VehiclePropertySourceStatus?,
    testRunLiveState: TestRunLiveState?,
    simulationControlsEnabled: Boolean,
    onAdvancePowerState: () -> Unit,
    onPowerOff: () -> Unit,
    onGearSelected: (Gear) -> Unit,
    onAccelerate: () -> Unit,
    onBrake: () -> Unit,
    onDriverDoorChanged: (Boolean) -> Unit,
    onSeatbeltChanged: (Boolean) -> Unit,
    onChargerConnectionChanged: (Boolean) -> Unit,
    onChargeAdded: () -> Unit,
    onChargeRemoved: () -> Unit,
    onResetSimulation: () -> Unit,
    onRetryGateway: () -> Unit,
    onRetryRejectedEvent: (String) -> Unit,
    onDiscardRejectedEvent: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header()
            PowerStateCard(vehicleState.powerState)
            VehiclePropertySourceCard(propertySourceStatus)
            SimulatedSignals(vehicleState)
            GatewayStatusCard(gatewayStatus, onRetryGateway)
            TestRunLiveCard(testRunLiveState)
            RejectedTelemetryCard(
                events = rejectedTelemetryEvents,
                onRetry = onRetryRejectedEvent,
                onDiscard = onDiscardRejectedEvent,
            )
            AlertCenter(vehicleState)
            if (simulationControlsEnabled) {
                PowerControls(
                    vehicleState = vehicleState,
                    onAdvancePowerState = onAdvancePowerState,
                    onPowerOff = onPowerOff,
                )
                DrivingControls(
                    vehicleState = vehicleState,
                    onGearSelected = onGearSelected,
                    onAccelerate = onAccelerate,
                    onBrake = onBrake,
                )
                ChargingControls(
                    vehicleState = vehicleState,
                    onChargerConnectionChanged = onChargerConnectionChanged,
                    onChargeAdded = onChargeAdded,
                    onChargeRemoved = onChargeRemoved,
                )
                VehicleConditionControls(
                    vehicleState = vehicleState,
                    onDriverDoorChanged = onDriverDoorChanged,
                    onSeatbeltChanged = onSeatbeltChanged,
                )
                EventHistoryCard(eventHistory, onResetSimulation)
            } else {
                ReadOnlySourceNotice()
            }
            Text(
                text = if (simulationControlsEnabled) {
                    "AMBIENTE EDUCACIONAL • DADOS NÃO PROVENIENTES DE UM VEÍCULO"
                } else {
                    "MODO AAOS • PROPRIEDADES RECEBIDAS DO CARSERVICE/VHAL"
                },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF718096),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TestRunLiveCard(state: TestRunLiveState?) {
    val connection = state?.connection
    val color = when (connection) {
        TestRunLiveConnection.CONNECTED -> Color(0xFF55D68B)
        TestRunLiveConnection.CONNECTING, TestRunLiveConnection.RECONNECTING -> Color(0xFFFFC857)
        TestRunLiveConnection.ERROR -> Color(0xFFFF6B6B)
        TestRunLiveConnection.DISABLED, null -> Color(0xFF9FB0C3)
    }
    val label = when (connection) {
        TestRunLiveConnection.CONNECTED -> "CONNECTED"
        TestRunLiveConnection.CONNECTING -> "CONNECTING"
        TestRunLiveConnection.RECONNECTING -> "RECONNECTING"
        TestRunLiveConnection.ERROR -> "ERROR"
        TestRunLiveConnection.DISABLED -> "NOT CONFIGURED"
        null -> "INITIALIZING"
    }
    val run = state?.testRun

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "ATEP Live Test Run",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (run != null) {
                Text(text = run.name, color = Color.White, fontSize = 16.sp)
                Text(
                    text = "${run.suite.uppercase()} • ${run.status.uppercase()} • " +
                        "${run.progressPercent}% • v${run.version}",
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                run.summary?.let { summary ->
                    Text(text = summary, color = Color(0xFFCED8E3), fontSize = 13.sp)
                }
            } else {
                Text(
                    text = state?.detail ?: "Waiting for the configured ATEP test run.",
                    color = Color(0xFF9FB0C3),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun VehiclePropertySourceCard(status: VehiclePropertySourceStatus?) {
    val aaos = status?.mode == VehiclePropertySourceMode.ANDROID_AUTOMOTIVE
    val color = when {
        status == null -> Color(0xFF9FB0C3)
        status.connected -> Color(0xFF55D68B)
        else -> Color(0xFFFFC857)
    }
    val title = if (aaos) "Android Automotive / VHAL" else "Simulador local"

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Origem das propriedades",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = status?.detail ?: "Inicializando origem do veículo",
                color = Color(0xFF9FB0C3),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ReadOnlySourceNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = "Modo somente leitura: os controles locais foram removidos para não alterar " +
                "evidências recebidas do veículo.",
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            color = Color(0xFFFFC857),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun GatewayStatusCard(
    status: GatewaySyncStatus?,
    onRetry: () -> Unit,
) {
    val state = status?.state
    val color = when (state) {
        GatewayConnectionState.SYNCHRONIZED -> Color(0xFF55D68B)
        GatewayConnectionState.PENDING -> Color(0xFFFFC857)
        GatewayConnectionState.REJECTED -> Color(0xFFFF6B6B)
        GatewayConnectionState.DISABLED, null -> Color(0xFF9FB0C3)
    }
    val message = when (state) {
        GatewayConnectionState.SYNCHRONIZED -> "Telemetria sincronizada com o ATEP."
        GatewayConnectionState.PENDING -> if (status.retryExhausted) {
            "Reenvio automático interrompido após ${status.retryAttempts} tentativa(s). " +
                "Os ${status.pendingCount} evento(s) continuam preservados."
        } else {
            "Sem conexão: ${status.pendingCount} evento(s) preservado(s); " +
                "reenvio em segundo plano agendado."
        }
        GatewayConnectionState.REJECTED ->
            "ATEP rejeitou ${status.rejectedCount} evento(s). " +
                if (status.pendingCount > 0) {
                    "Outros ${status.pendingCount} aguardam sincronização."
                } else {
                    "Inspecione cada motivo antes de reenviar ou descartar."
                }
        GatewayConnectionState.DISABLED ->
            "Gateway desativado. Configure ATEP_MODULE_ID e ATEP_MODULE_TOKEN no Gradle."
        null -> "Inicializando o Vehicle Gateway."
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "ATEP Vehicle Gateway",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = message, color = color, fontSize = 14.sp)
            status?.lastError?.let { error ->
                Text(text = error, color = Color(0xFF9FB0C3), fontSize = 12.sp)
            }
            if ((status?.pendingCount ?: 0) > 0) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (status?.retryExhausted == true) {
                            "Retomar e tentar agora"
                        } else {
                            "Tentar sincronizar novamente"
                        },
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun RejectedTelemetryCard(
    events: List<RejectedTelemetryEvent>,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
) {
    if (events.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Eventos rejeitados",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "O event_id original será preservado se o evento for reenviado.",
                color = Color(0xFF9FB0C3),
                fontSize = 13.sp,
            )
            events.take(MAX_VISIBLE_REJECTED_EVENTS).forEach { rejected ->
                val event = rejected.event
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = event.property,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Valor: ${event.value}${event.unit?.let { " $it" } ?: ""}",
                        color = Color(0xFFCED8E3),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "Motivo: ${rejected.reason}",
                        color = Color(0xFFFFC857),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "ID: ${event.eventId} • ${event.timestamp}",
                        color = Color(0xFF9FB0C3),
                        fontSize = 11.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { onRetry(event.eventId) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Reenviar")
                        }
                        OutlinedButton(
                            onClick = { onDiscard(event.eventId) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Descartar", color = Color.White)
                        }
                    }
                }
            }
            if (events.size > MAX_VISIBLE_REJECTED_EVENTS) {
                Text(
                    text = "+ ${events.size - MAX_VISIBLE_REJECTED_EVENTS} evento(s) preservado(s)",
                    color = Color(0xFF9FB0C3),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private const val MAX_VISIBLE_REJECTED_EVENTS = 5

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "CarSystemUI Showcase",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Simulador de estado do veículo",
            color = Color(0xFF9FB0C3),
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun PowerStateCard(powerState: VehiclePowerState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "ESTADO DE ENERGIA",
                color = Color(0xFF9FB0C3),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = powerState.title,
                color = powerState.statusColor(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = powerState.description,
                color = Color.White,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun SimulatedSignals(vehicleState: VehicleSimulationState) {
    val systemsOnline = vehicleState.powerState == VehiclePowerState.IGNITION_ON ||
        vehicleState.powerState == VehiclePowerState.READY

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Sinais simulados",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SignalCard("Velocidade", "${vehicleState.speedKmh} km/h", Modifier.weight(1f))
            SignalCard(
                "Marcha",
                if (systemsOnline) vehicleState.gear.label else "—",
                Modifier.weight(1f),
            )
            SignalCard(
                "Bateria",
                if (systemsOnline || vehicleState.isChargerConnected) {
                    "${vehicleState.batteryLevel}%"
                } else {
                    "—"
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SignalCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, color = Color(0xFF9FB0C3), fontSize = 12.sp)
            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlertCenter(vehicleState: VehicleSimulationState) {
    val alerts = vehicleState.activeAlerts()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Central de alertas",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (alerts.isEmpty()) {
                Text(
                    text = "Nenhum alerta ativo",
                    color = Color(0xFF55D68B),
                    fontSize = 15.sp,
                )
            } else {
                alerts.forEach { alert -> AlertItem(alert) }
            }
        }
    }
}

@Composable
private fun AlertItem(alert: VehicleAlert) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = alert.severity.statusColor().copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = alert.severity.label,
            color = alert.severity.statusColor(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = alert.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = alert.message, color = Color(0xFFCED8E3), fontSize = 14.sp)
    }
}

@Composable
private fun PowerControls(
    vehicleState: VehicleSimulationState,
    onAdvancePowerState: () -> Unit,
    onPowerOff: () -> Unit,
) {
    val powerState = vehicleState.powerState
    val canPowerOff = powerState != VehiclePowerState.OFF &&
        vehicleState.speedKmh == 0 && vehicleState.gear == Gear.PARK

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onAdvancePowerState,
            modifier = Modifier.fillMaxWidth(),
            enabled = powerState != VehiclePowerState.READY,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2878D0),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = when (powerState) {
                    VehiclePowerState.OFF -> "Ligar acessórios"
                    VehiclePowerState.ACCESSORY -> "Ligar ignição"
                    VehiclePowerState.IGNITION_ON -> "Deixar pronto"
                    VehiclePowerState.READY -> "Veículo pronto"
                },
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        OutlinedButton(
            onClick = onPowerOff,
            modifier = Modifier.fillMaxWidth(),
            enabled = canPowerOff,
        ) {
            Text(
                text = "Desligar veículo",
                modifier = Modifier.padding(vertical = 6.dp),
                color = if (!canPowerOff) {
                    Color(0xFF718096)
                } else {
                    Color.White
                },
            )
        }
    }
}

@Composable
private fun DrivingControls(
    vehicleState: VehicleSimulationState,
    onGearSelected: (Gear) -> Unit,
    onAccelerate: () -> Unit,
    onBrake: () -> Unit,
) {
    val isReady = vehicleState.powerState == VehiclePowerState.READY
    val canChangeGear = isReady && vehicleState.speedKmh == 0 &&
        !vehicleState.isChargerConnected
    val canAccelerate = isReady &&
        (vehicleState.gear == Gear.DRIVE || vehicleState.gear == Gear.REVERSE) &&
        vehicleState.speedKmh < 120 && vehicleState.batteryLevel > 0 &&
        !vehicleState.isChargerConnected

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Condução simulada",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    !isReady -> "Disponível quando o veículo estiver pronto."
                    vehicleState.isChargerConnected ->
                        "Desconecte o carregador antes de selecionar uma marcha."
                    vehicleState.speedKmh > 0 -> "Pare o veículo antes de trocar a marcha."
                    vehicleState.batteryLevel == 0 -> "Bateria vazia: recarregue para mover."
                    vehicleState.gear == Gear.PARK -> "Selecione D ou R para simular movimento."
                    vehicleState.gear == Gear.NEUTRAL -> "Em N, a aceleração fica indisponível."
                    else -> "Use +10 km/h para acelerar e Frear para reduzir."
                },
                color = Color(0xFF9FB0C3),
                fontSize = 14.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Gear.entries.forEach { gear ->
                    OutlinedButton(
                        onClick = { onGearSelected(gear) },
                        modifier = Modifier.weight(1f),
                        enabled = canChangeGear && vehicleState.gear != gear,
                    ) {
                        Text(
                            text = gear.label,
                            color = if (vehicleState.gear == gear && isReady) {
                                Color(0xFF55D68B)
                            } else {
                                Color.White
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onAccelerate,
                    modifier = Modifier.weight(1f),
                    enabled = canAccelerate,
                ) {
                    Text("+10 km/h")
                }
                OutlinedButton(
                    onClick = onBrake,
                    modifier = Modifier.weight(1f),
                    enabled = isReady && vehicleState.speedKmh > 0,
                ) {
                    Text("Frear", color = Color.White)
                }
            }
            if (vehicleState.speedKmh > 0 || vehicleState.gear != Gear.PARK) {
                Text(
                    text = "Para desligar: freie até 0 km/h e selecione P.",
                    color = Color(0xFFFFC857),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ChargingControls(
    vehicleState: VehicleSimulationState,
    onChargerConnectionChanged: (Boolean) -> Unit,
    onChargeAdded: () -> Unit,
    onChargeRemoved: () -> Unit,
) {
    val canConnect = vehicleState.speedKmh == 0 && vehicleState.gear == Gear.PARK

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Bateria e recarga EV",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Carga simulada: ${vehicleState.batteryLevel}%",
                color = when {
                    vehicleState.batteryLevel <= 10 -> Color(0xFFFF6B6B)
                    vehicleState.batteryLevel <= 20 -> Color(0xFFFFC857)
                    else -> Color(0xFF55D68B)
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Carregador conectado",
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                )
                Switch(
                    checked = vehicleState.isChargerConnected,
                    onCheckedChange = onChargerConnectionChanged,
                    enabled = canConnect || vehicleState.isChargerConnected,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onChargeRemoved,
                    modifier = Modifier.weight(1f),
                    enabled = vehicleState.speedKmh == 0 && vehicleState.batteryLevel > 0,
                ) {
                    Text("Simular -10%", color = Color.White)
                }
                Button(
                    onClick = onChargeAdded,
                    modifier = Modifier.weight(1f),
                    enabled = vehicleState.isChargerConnected && vehicleState.batteryLevel < 100,
                ) {
                    Text("Recarregar +10%")
                }
            }
            Text(
                text = when {
                    !canConnect && !vehicleState.isChargerConnected ->
                        "Pare e selecione P para conectar o carregador."
                    vehicleState.isChargerConnected ->
                        "Recarga manual ativa; marchas e movimento estão bloqueados."
                    else -> "Conecte o carregador para adicionar carga."
                },
                color = Color(0xFF9FB0C3),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun VehicleConditionControls(
    vehicleState: VehicleSimulationState,
    onDriverDoorChanged: (Boolean) -> Unit,
    onSeatbeltChanged: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Condições para teste",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Altere sinais fictícios e observe a prioridade dos alertas.",
                color = Color(0xFF9FB0C3),
                fontSize = 14.sp,
            )
            ConditionSwitch(
                label = "Porta do motorista aberta",
                checked = vehicleState.isDriverDoorOpen,
                onCheckedChange = onDriverDoorChanged,
            )
            ConditionSwitch(
                label = "Cinto do motorista afivelado",
                checked = vehicleState.isSeatbeltFastened,
                onCheckedChange = onSeatbeltChanged,
            )
        }
    }
}

@Composable
private fun ConditionSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            color = Color.White,
            fontSize = 15.sp,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EventHistoryCard(
    events: List<SimulationEvent>,
    onResetSimulation: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Histórico de eventos",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Últimos ${events.size} de no máximo 8 eventos locais",
                color = Color(0xFF9FB0C3),
                fontSize = 13.sp,
            )
            events.forEach { event ->
                Text(
                    text = "#${event.sequence}  ${event.description}",
                    color = Color(0xFFCED8E3),
                    fontSize = 14.sp,
                )
            }
            OutlinedButton(
                onClick = onResetSimulation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reiniciar simulação", color = Color.White)
            }
        }
    }
}

private fun VehiclePowerState.statusColor(): Color = when (this) {
    VehiclePowerState.OFF -> Color(0xFF9FB0C3)
    VehiclePowerState.ACCESSORY -> Color(0xFFFFC857)
    VehiclePowerState.IGNITION_ON -> Color(0xFF64B5F6)
    VehiclePowerState.READY -> Color(0xFF55D68B)
}

private fun AlertSeverity.statusColor(): Color = when (this) {
    AlertSeverity.INFORMATION -> Color(0xFF64B5F6)
    AlertSeverity.WARNING -> Color(0xFFFFC857)
    AlertSeverity.CRITICAL -> Color(0xFFFF6B6B)
}
