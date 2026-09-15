package com.example.carsystemui.showcase.gateway

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class HttpTelemetryTransport(
    private val config: GatewayConfig,
) : TelemetryTransport {
    override fun send(event: GatewayTelemetryEvent): TelemetryDeliveryResult {
        val connection = try {
            URI.create(
                "${config.normalizedBaseUrl}/api/v1/vehicles/${config.vehicleId}/telemetry",
            ).toURL().openConnection() as HttpURLConnection
        } catch (error: IllegalArgumentException) {
            return TelemetryDeliveryResult.Rejected("Invalid ATEP URL")
        }

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-ATEP-Module-ID", config.moduleId)
            connection.setRequestProperty("X-ATEP-Module-Token", config.moduleToken)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(event.toRequestJson().toString())
            }

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED ->
                    TelemetryDeliveryResult.Delivered
                HttpURLConnection.HTTP_CONFLICT ->
                    TelemetryDeliveryResult.Rejected("ATEP rejected conflicting event ${event.eventId}")
                HttpURLConnection.HTTP_CLIENT_TIMEOUT, 425, 429 ->
                    TelemetryDeliveryResult.RetryableFailure("ATEP returned HTTP $status")
                in 400..499 -> TelemetryDeliveryResult.Rejected("ATEP returned HTTP $status")
                else -> TelemetryDeliveryResult.RetryableFailure("ATEP returned HTTP $status")
            }
        } catch (error: IOException) {
            TelemetryDeliveryResult.RetryableFailure(error.message ?: "ATEP connection failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun GatewayTelemetryEvent.toRequestJson(): JSONObject = JSONObject()
        .put("event_id", eventId)
        .put("property", property)
        .put("value", typedValue())
        .put("unit", unit ?: JSONObject.NULL)
        .put("timestamp", timestamp)
        .put("source", source)

    private fun GatewayTelemetryEvent.typedValue(): Any = when (valueKind) {
        TelemetryValueKind.STRING -> value
        TelemetryValueKind.BOOLEAN -> value.toBooleanStrict()
        TelemetryValueKind.NUMBER -> value.toLongOrNull() ?: value.toDouble()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
    }
}
