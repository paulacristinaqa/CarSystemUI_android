package com.example.carsystemui.showcase.gateway

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class HttpVehicleCommandTransport(
    private val config: GatewayConfig,
) : VehicleCommandTransport {
    override fun claim(): CommandClaimResult {
        if (!config.isEnabled) return CommandClaimResult.Rejected("ATEP gateway is disabled")
        val connection = open("commands/claim")
            ?: return CommandClaimResult.Rejected("Invalid ATEP URL")
        return try {
            configure(connection)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(JSONObject().put("lease_seconds", LEASE_SECONDS).toString())
            }
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NO_CONTENT -> CommandClaimResult.NoCommand
                HttpURLConnection.HTTP_OK -> parseCommand(connection.inputStream.bufferedReader().readText())
                HttpURLConnection.HTTP_CLIENT_TIMEOUT, 425, 429 ->
                    CommandClaimResult.RetryableFailure("ATEP returned HTTP $status")
                in 400..499 -> CommandClaimResult.Rejected("ATEP returned HTTP $status")
                else -> CommandClaimResult.RetryableFailure("ATEP returned HTTP $status")
            }
        } catch (error: IOException) {
            CommandClaimResult.RetryableFailure(error.message ?: "ATEP command claim failed")
        } catch (error: IllegalArgumentException) {
            CommandClaimResult.Rejected(error.message ?: "ATEP returned an invalid command")
        } finally {
            connection.disconnect()
        }
    }

    override fun acknowledge(
        command: GatewayVehicleCommand,
        execution: CommandExecutionResult,
    ): CommandAcknowledgementResult {
        val connection = open("commands/${command.commandId}/acknowledgement")
            ?: return CommandAcknowledgementResult.Rejected("Invalid ATEP URL")
        return try {
            configure(connection)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(execution.toAcknowledgementJson(command.claimToken).toString())
            }
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> CommandAcknowledgementResult.Delivered
                HttpURLConnection.HTTP_CLIENT_TIMEOUT, 425, 429 ->
                    CommandAcknowledgementResult.RetryableFailure("ATEP returned HTTP $status")
                in 400..499 -> CommandAcknowledgementResult.Rejected("ATEP returned HTTP $status")
                else -> CommandAcknowledgementResult.RetryableFailure("ATEP returned HTTP $status")
            }
        } catch (error: IOException) {
            CommandAcknowledgementResult.RetryableFailure(
                error.message ?: "ATEP command acknowledgement failed",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String): HttpURLConnection? = try {
        URI.create(
            "${config.normalizedBaseUrl}/api/v1/vehicles/${config.vehicleId}/$path",
        ).toURL().openConnection() as HttpURLConnection
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun configure(connection: HttpURLConnection) {
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-ATEP-Module-ID", config.moduleId)
        connection.setRequestProperty("X-ATEP-Module-Token", config.moduleToken)
    }

    private fun parseCommand(body: String): CommandClaimResult {
        val json = JSONObject(body)
        val parameters = json.getJSONObject("parameters")
        val value = parameters.get("value")
        val valueKind = when (value) {
            is Boolean -> TelemetryValueKind.BOOLEAN
            is Number -> TelemetryValueKind.NUMBER
            else -> TelemetryValueKind.STRING
        }
        return CommandClaimResult.Claimed(
            GatewayVehicleCommand(
                commandId = json.getString("command_id"),
                claimToken = json.getString("claim_token"),
                kind = json.getString("kind"),
                property = parameters.getString("property"),
                value = value.toString(),
                valueKind = valueKind,
            ),
        )
    }

    private fun CommandExecutionResult.toAcknowledgementJson(claimToken: String): JSONObject =
        when (this) {
            is CommandExecutionResult.Applied -> JSONObject()
                .put("claim_token", claimToken)
                .put("outcome", "succeeded")
                .put(
                    "result",
                    JSONObject().put("property", property).put("applied_value", value),
                )
            is CommandExecutionResult.Rejected -> JSONObject()
                .put("claim_token", claimToken)
                .put("outcome", "rejected")
                .put("error_code", errorCode)
                .put("error_message", errorMessage)
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val LEASE_SECONDS = 60
    }
}
