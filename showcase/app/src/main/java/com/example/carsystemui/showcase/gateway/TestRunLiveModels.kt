package com.example.carsystemui.showcase.gateway

data class TestRunLiveConfig(
    val baseUrl: String,
    val runId: String,
    val accessToken: String,
) {
    val isEnabled: Boolean
        get() = baseUrl.isNotBlank() && runId.isNotBlank() && accessToken.isNotBlank()

    fun streamUrl(): String? {
        val normalized = baseUrl.trimEnd('/')
        val socketBase = when {
            normalized.startsWith("https://") -> "wss://${normalized.removePrefix("https://")}" 
            normalized.startsWith("http://") -> "ws://${normalized.removePrefix("http://")}" 
            else -> return null
        }
        return "$socketBase/api/v1/test-runs/$runId/stream"
    }
}

enum class TestRunLiveConnection {
    DISABLED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}

data class LiveTestRun(
    val runId: String,
    val name: String,
    val suite: String,
    val status: String,
    val progressPercent: Int,
    val version: Int,
    val summary: String?,
)

data class TestRunLiveState(
    val connection: TestRunLiveConnection,
    val testRun: LiveTestRun? = null,
    val detail: String? = null,
)

sealed interface TestRunStreamMessage {
    data class Update(val testRun: LiveTestRun) : TestRunStreamMessage

    data object Heartbeat : TestRunStreamMessage
}

class TestRunLiveReducer {
    private var latestVersion = 0

    fun accept(candidate: LiveTestRun): LiveTestRun? {
        if (candidate.version <= latestVersion) return null
        latestVersion = candidate.version
        return candidate
    }
}
