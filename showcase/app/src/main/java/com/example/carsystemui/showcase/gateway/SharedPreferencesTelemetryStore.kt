package com.example.carsystemui.showcase.gateway

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesTelemetryStore(
    private val preferences: SharedPreferences,
) : PendingTelemetryStore {
    private val lock = Any()

    override fun enqueue(events: List<GatewayTelemetryEvent>) = synchronized(lock) {
        if (events.isEmpty()) return@synchronized
        val knownIds = readEvents(PENDING_KEY).mapTo(mutableSetOf()) { it.eventId }
        val merged = readEvents(PENDING_KEY).toMutableList()
        events.filterNot { it.eventId in knownIds }.forEach(merged::add)
        writeEvents(PENDING_KEY, merged)
    }

    override fun pending(): List<GatewayTelemetryEvent> = synchronized(lock) {
        readEvents(PENDING_KEY)
    }

    override fun remove(eventId: String) = synchronized(lock) {
        writeEvents(PENDING_KEY, readEvents(PENDING_KEY).filterNot { it.eventId == eventId })
    }

    override fun reject(event: GatewayTelemetryEvent, reason: String) = synchronized(lock) {
        val pending = readEvents(PENDING_KEY).filterNot { it.eventId == event.eventId }
        val rejected = readRejected().toMutableList()
        rejected += RejectedTelemetryEvent(event, reason)
        writeQueues(pending, rejected.takeLast(MAX_REJECTED_EVENTS))
    }

    override fun rejected(): List<RejectedTelemetryEvent> = synchronized(lock) {
        readRejected()
    }

    override fun retryRejected(eventId: String): Boolean = synchronized(lock) {
        val rejected = readRejected()
        val selected = rejected.firstOrNull { it.event.eventId == eventId } ?: return@synchronized false
        val pending = readEvents(PENDING_KEY).toMutableList()
        if (pending.none { it.eventId == eventId }) pending += selected.event
        val remaining = rejected.filterNot { it.event.eventId == eventId }
        writeQueues(
            pending = pending,
            rejected = remaining,
            retryState = TelemetryRetryState(),
        )
        true
    }

    override fun discardRejected(eventId: String): Boolean = synchronized(lock) {
        val rejected = readRejected()
        if (rejected.none { it.event.eventId == eventId }) return@synchronized false
        writeRejected(rejected.filterNot { it.event.eventId == eventId })
        true
    }

    override fun retryState(): TelemetryRetryState = synchronized(lock) {
        TelemetryRetryState(
            attempts = preferences.getInt(RETRY_ATTEMPTS_KEY, 0),
            exhausted = preferences.getBoolean(RETRY_EXHAUSTED_KEY, false),
        )
    }

    override fun updateRetryState(state: TelemetryRetryState) = synchronized(lock) {
        check(
            preferences.edit()
                .putInt(RETRY_ATTEMPTS_KEY, state.attempts)
                .putBoolean(RETRY_EXHAUSTED_KEY, state.exhausted)
                .commit(),
        ) { "Unable to persist telemetry retry state" }
    }

    fun snapshot(): TelemetryQueueSnapshot = synchronized(lock) {
        TelemetryQueueSnapshot(
            pendingCount = readEvents(PENDING_KEY).size,
            rejectedEvents = readRejected(),
            retryState = retryState(),
        )
    }

    fun observeSnapshot(): Flow<TelemetryQueueSnapshot> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key in OBSERVED_KEYS) trySend(snapshot())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun readEvents(key: String): List<GatewayTelemetryEvent> {
        val array = JSONArray(preferences.getString(key, "[]"))
        return List(array.length()) { index -> array.getJSONObject(index).toTelemetryEvent() }
    }

    private fun writeEvents(key: String, events: List<GatewayTelemetryEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        check(preferences.edit().putString(key, array.toString()).commit()) {
            "Unable to persist pending telemetry"
        }
    }

    private fun readRejected(): List<RejectedTelemetryEvent> {
        val array = JSONArray(preferences.getString(REJECTED_KEY, "[]"))
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RejectedTelemetryEvent(
                event = item.getJSONObject("event").toTelemetryEvent(),
                reason = item.getString("reason"),
            )
        }
    }

    private fun writeRejected(events: List<RejectedTelemetryEvent>) {
        val serialized = rejectedToJson(events)
        check(preferences.edit().putString(REJECTED_KEY, serialized).commit()) {
            "Unable to persist rejected telemetry"
        }
    }

    private fun writeQueues(
        pending: List<GatewayTelemetryEvent>,
        rejected: List<RejectedTelemetryEvent>,
        retryState: TelemetryRetryState? = null,
    ) {
        val editor = preferences.edit()
            .putString(PENDING_KEY, eventsToJson(pending))
            .putString(REJECTED_KEY, rejectedToJson(rejected))
        retryState?.let {
            editor.putInt(RETRY_ATTEMPTS_KEY, it.attempts)
                .putBoolean(RETRY_EXHAUSTED_KEY, it.exhausted)
        }
        check(editor.commit()) { "Unable to persist telemetry queues" }
    }

    private fun eventsToJson(events: List<GatewayTelemetryEvent>): String {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    private fun rejectedToJson(events: List<RejectedTelemetryEvent>): String {
        val array = JSONArray()
        events.forEach { rejected ->
            array.put(
                JSONObject()
                    .put("event", rejected.event.toJson())
                    .put("reason", rejected.reason),
            )
        }
        return array.toString()
    }

    private fun GatewayTelemetryEvent.toJson(): JSONObject = JSONObject()
        .put("event_id", eventId)
        .put("property", property)
        .put("value", value)
        .put("value_kind", valueKind.name)
        .put("unit", unit ?: JSONObject.NULL)
        .put("timestamp", timestamp)
        .put("source", source)

    private fun JSONObject.toTelemetryEvent(): GatewayTelemetryEvent = GatewayTelemetryEvent(
        eventId = getString("event_id"),
        property = getString("property"),
        value = getString("value"),
        valueKind = TelemetryValueKind.valueOf(getString("value_kind")),
        unit = if (isNull("unit")) null else getString("unit"),
        timestamp = getString("timestamp"),
        source = getString("source"),
    )

    private companion object {
        const val PENDING_KEY = "pending_telemetry"
        const val REJECTED_KEY = "rejected_telemetry"
        const val RETRY_ATTEMPTS_KEY = "background_retry_attempts"
        const val RETRY_EXHAUSTED_KEY = "background_retry_exhausted"
        const val MAX_REJECTED_EVENTS = 100
        val OBSERVED_KEYS = setOf(
            PENDING_KEY,
            REJECTED_KEY,
            RETRY_ATTEMPTS_KEY,
            RETRY_EXHAUSTED_KEY,
        )
    }
}
