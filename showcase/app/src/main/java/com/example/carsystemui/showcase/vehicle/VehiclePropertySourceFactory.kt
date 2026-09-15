package com.example.carsystemui.showcase.vehicle

import android.content.Context
import android.content.pm.PackageManager
import com.example.carsystemui.showcase.BuildConfig

object VehiclePropertySourceFactory {
    fun create(context: Context): VehiclePropertySource {
        val configuredMode = BuildConfig.VEHICLE_PROPERTY_SOURCE.trim().lowercase()
        val automotiveDevice = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE,
        )
        return when (configuredMode) {
            "aaos", "android-automotive" -> aaos(context)
            "auto" -> if (automotiveDevice) aaos(context) else SimulatedVehiclePropertySource()
            else -> SimulatedVehiclePropertySource()
        }
    }

    private fun aaos(context: Context): VehiclePropertySource =
        AaosVehiclePropertySource(ReflectiveCarPropertyClient(context.applicationContext))
}
