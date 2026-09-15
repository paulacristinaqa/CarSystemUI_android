package com.example.carsystemui.showcase.vehicle

import android.content.Context
import java.lang.reflect.Proxy

/**
 * Compatibility bridge for the standalone showcase build.
 *
 * The android.car classes are supplied by an AAOS system image and are not present in the normal
 * Android SDK used by this small Gradle application. Reflection keeps the phone build installable
 * while still using CarPropertyManager when the same APK runs on AAOS.
 */
class ReflectiveCarPropertyClient(
    private val context: Context,
) : CarPropertyClient {
    private var car: Any? = null
    private var manager: Any? = null
    private var callback: Any? = null

    override fun subscribe(onReading: (CarPropertyReading) -> Unit): CarPropertySubscriptionResult {
        val carClass = Class.forName(CAR_CLASS)
        val propertyManagerClass = Class.forName(PROPERTY_MANAGER_CLASS)
        val callbackClass = Class.forName(CALLBACK_CLASS)
        val propertyIdsClass = Class.forName(PROPERTY_IDS_CLASS)

        car = carClass.getMethod("createCar", Context::class.java).invoke(null, context)
        manager = carClass.getMethod("getCarManager", String::class.java)
            .invoke(car, PROPERTY_SERVICE)
            ?: error("CarPropertyManager is unavailable")

        callback = Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass),
        ) { _, method, arguments ->
            if (method.name == "onChangeEvent") {
                arguments?.firstOrNull()?.let { propertyValue ->
                    val propertyId = propertyValue.javaClass.getMethod("getPropertyId")
                        .invoke(propertyValue) as Int
                    val value = propertyValue.javaClass.getMethod("getValue").invoke(propertyValue)
                    if (value != null) {
                        specifications.firstOrNull { it.propertyId == propertyId }
                            ?.let { onReading(CarPropertyReading(it.kind, value)) }
                    }
                }
                null
            } else {
                null
            }
        }

        specifications = PROPERTY_SPECS.mapNotNull { spec ->
            runCatching {
                ResolvedProperty(
                    propertyId = propertyIdsClass.getField(spec.fieldName).getInt(null),
                    kind = spec.kind,
                    sampleRateHz = spec.sampleRateHz,
                )
            }.getOrNull()
        }

        val register = propertyManagerClass.methods.firstOrNull { method ->
            method.name == "registerCallback" && method.parameterTypes.size == 3
        } ?: error("Compatible CarPropertyManager.registerCallback API is unavailable")

        val errors = mutableListOf<String>()
        var subscribed = 0
        specifications.forEach { spec ->
            runCatching {
                register.invoke(manager, callback, spec.propertyId, spec.sampleRateHz) as? Boolean
            }.onSuccess { accepted ->
                if (accepted != false) subscribed += 1 else errors += "${spec.kind}: unsupported"
            }.onFailure { error ->
                errors += "${spec.kind}: ${rootCause(error).javaClass.simpleName}"
            }
        }
        return CarPropertySubscriptionResult(subscribed, errors)
    }

    override fun close() {
        val currentManager = manager
        val currentCallback = callback
        if (currentManager != null && currentCallback != null) {
            runCatching {
                currentManager.javaClass.methods.firstOrNull { method ->
                    method.name == "unregisterCallback" && method.parameterTypes.size == 1
                }?.invoke(currentManager, currentCallback)
            }
        }
        car?.let { currentCar ->
            runCatching { currentCar.javaClass.getMethod("disconnect").invoke(currentCar) }
        }
        callback = null
        manager = null
        car = null
        specifications = emptyList()
    }

    private fun rootCause(error: Throwable): Throwable =
        generateSequence(error) { it.cause }.last()

    private data class PropertySpec(
        val fieldName: String,
        val kind: CarPropertyKind,
        val sampleRateHz: Float,
    )

    private data class ResolvedProperty(
        val propertyId: Int,
        val kind: CarPropertyKind,
        val sampleRateHz: Float,
    )

    private var specifications: List<ResolvedProperty> = emptyList()

    private companion object {
        const val CAR_CLASS = "android.car.Car"
        const val PROPERTY_MANAGER_CLASS = "android.car.hardware.property.CarPropertyManager"
        const val CALLBACK_CLASS =
            "android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback"
        const val PROPERTY_IDS_CLASS = "android.car.VehiclePropertyIds"
        const val PROPERTY_SERVICE = "property"
        const val ON_CHANGE_RATE = 0f
        const val NORMAL_RATE = 5f

        val PROPERTY_SPECS = listOf(
            PropertySpec(
                "PERF_VEHICLE_SPEED",
                CarPropertyKind.SPEED_METERS_PER_SECOND,
                NORMAL_RATE,
            ),
            PropertySpec("GEAR_SELECTION", CarPropertyKind.GEAR_SELECTION, ON_CHANGE_RATE),
            PropertySpec("IGNITION_STATE", CarPropertyKind.IGNITION_STATE, ON_CHANGE_RATE),
            PropertySpec(
                "EV_BATTERY_LEVEL",
                CarPropertyKind.EV_BATTERY_LEVEL_WH,
                NORMAL_RATE,
            ),
            PropertySpec(
                "EV_CURRENT_BATTERY_CAPACITY",
                CarPropertyKind.EV_BATTERY_CAPACITY_WH,
                NORMAL_RATE,
            ),
            PropertySpec(
                "INFO_EV_BATTERY_CAPACITY",
                CarPropertyKind.EV_BATTERY_CAPACITY_WH,
                ON_CHANGE_RATE,
            ),
            PropertySpec(
                "EV_CHARGE_PORT_CONNECTED",
                CarPropertyKind.CHARGER_CONNECTED,
                ON_CHANGE_RATE,
            ),
        )
    }
}
