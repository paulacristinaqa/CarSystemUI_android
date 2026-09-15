package com.example.carsystemui.showcase.dashboard

import java.time.Instant
import org.json.JSONObject
import org.json.JSONTokener

data class DashboardEnvelope(val sequence: Int, val json: String)

object DashboardEnvelopeParser {
    fun parse(text: String, view: DashboardView): DashboardEnvelope {
        try {
            require(text.length <= 300_000 && text.toByteArray(Charsets.UTF_8).size <= 300_000)
            var depth = 0
            var quoted = false
            var escaped = false
            for (char in text) {
                if (quoted) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 64) }
                    '}', ']' -> depth--
                }
            }
            val tokener = JSONTokener(text)
            val frame = tokener.nextValue() as? JSONObject ?: error("Invalid frame")
            require(tokener.nextClean() == '\u0000')
            require(frame.get("type") == "atep.dashboard.snapshot.v1")
            val sequence = frame.get("sequence")
            require(sequence is Int && sequence in 1..10)
            require(frame.get("refresh_interval_seconds") == 30)
            require(frame.get("freshness_basis") == "server_query_not_vehicle_measurement")
            val observed = Instant.parse(frame.getString("observed_at"))
            require(Instant.parse(frame.getString("refresh_not_before")) == observed.plusSeconds(30))
            val snapshot = frame.getJSONObject("snapshot")
            require(snapshot.get("view") == view.path)
            require(snapshot.get("contract_version") == "dashboard-export-v1")
            require(snapshot.get("server_retention") == "not_persisted")
            snapshot.getJSONObject("data")
            return DashboardEnvelope(sequence, text)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid dashboard envelope")
        }
    }
}
