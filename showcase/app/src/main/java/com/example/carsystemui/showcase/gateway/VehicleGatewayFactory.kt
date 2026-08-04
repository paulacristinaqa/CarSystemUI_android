package com.example.carsystemui.showcase.gateway

import android.content.Context
import com.example.carsystemui.showcase.BuildConfig

object VehicleGatewayFactory {
    fun config(): GatewayConfig = GatewayConfig(
        baseUrl = BuildConfig.ATEP_BASE_URL,
        vehicleId = BuildConfig.ATEP_VEHICLE_ID,
        moduleId = BuildConfig.ATEP_MODULE_ID,
        moduleToken = BuildConfig.ATEP_MODULE_TOKEN,
    )

    fun store(context: Context): SharedPreferencesTelemetryStore =
        SharedPreferencesTelemetryStore(
            context.getSharedPreferences("atep_vehicle_gateway", Context.MODE_PRIVATE),
        )

    fun create(
        context: Context,
        store: PendingTelemetryStore = store(context),
    ): VehicleGateway {
        val config = config()
        return VehicleGateway(config, store, HttpTelemetryTransport(config))
    }

    fun retryScheduler(context: Context): TelemetryRetryScheduler =
        WorkManagerTelemetryRetryScheduler(
            context,
            WorkManagerTelemetryRetryScheduler.workName(config().vehicleId),
        )
}
