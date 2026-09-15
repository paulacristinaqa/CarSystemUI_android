package com.example.carsystemui.showcase.dashboard

enum class DashboardStatus {
    IDLE, CONNECTING, LIVE, STALE, RECONNECTING, COMPLETE, AUTH_REQUIRED, FORBIDDEN, STOPPED,
}

data class DashboardLifecycleState(
    val status: DashboardStatus = DashboardStatus.IDLE,
    val stale: Boolean = true,
    val snapshot: String? = null,
    val retryAtMs: Long? = null,
)

/** Serialized-owner state machine. Times must come from a monotonic clock.
 * The transport owns credentials, parsing and timers; this class never opens a socket.
 */
class DashboardLifecycle {
    var state = DashboardLifecycleState()
        private set
    var generation = 0L
        private set
    private var sequence = 0
    private var attempts = 0
    private var deadline = 0L

    fun start(nowMs: Long): Long {
        attempts = 0
        state = DashboardLifecycleState()
        return connect(nowMs)
    }

    private fun connect(nowMs: Long): Long {
        generation++
        sequence = 0
        deadline = nowMs + 30_000
        state = state.copy(status = DashboardStatus.CONNECTING, stale = true, retryAtMs = null)
        return generation
    }

    /** Consume only a parsed, validated envelope; payload is opaque display data here. */
    fun snapshot(connection: Long, nextSequence: Int, payload: String, nowMs: Long) {
        if (!active(connection)) return
        if (nextSequence != sequence + 1 || nextSequence > DashboardNativeContract.MAX_SNAPSHOTS ||
            payload.length > 300_000
        ) {
            terminate(DashboardStatus.STOPPED)
            return
        }
        sequence = nextSequence
        attempts = 0
        deadline = nowMs + 35_000
        state = DashboardLifecycleState(DashboardStatus.LIVE, stale = false, snapshot = payload)
    }

    /** Call for a transport failure or a close; HTTP denials may be supplied as codes. */
    fun disconnected(connection: Long, code: Int?, nowMs: Long) {
        if (!active(connection)) return
        when (code) {
            401, 4401 -> terminate(DashboardStatus.AUTH_REQUIRED)
            403, 4403 -> terminate(DashboardStatus.FORBIDDEN)
            1002, 1003, 1008, 1009 -> terminate(DashboardStatus.STOPPED)
            1000 -> if (sequence == DashboardNativeContract.MAX_SNAPSHOTS) {
                generation++
                state = state.copy(status = DashboardStatus.COMPLETE, stale = true, retryAtMs = null)
            } else retry(nowMs)
            else -> retry(nowMs)
        }
    }

    /** Returns a new generation only when the owner must create a new connection. */
    fun tick(nowMs: Long): Long? {
        when (state.status) {
            DashboardStatus.CONNECTING -> if (nowMs >= deadline) retry(nowMs)
            DashboardStatus.LIVE -> if (nowMs >= deadline) {
                state = state.copy(status = DashboardStatus.STALE, stale = true)
            }
            DashboardStatus.RECONNECTING -> if (nowMs >= requireNotNull(state.retryAtMs)) {
                return connect(nowMs)
            }
            else -> Unit
        }
        return null
    }

    fun stop() = terminate(DashboardStatus.STOPPED)

    private fun active(connection: Long) = connection == generation &&
        state.status in setOf(DashboardStatus.CONNECTING, DashboardStatus.LIVE, DashboardStatus.STALE)

    private fun retry(nowMs: Long) {
        if (attempts >= 5) {
            terminate(DashboardStatus.STOPPED)
            return
        }
        val delay = minOf(120_000L, 30_000L * (1L shl attempts++))
        generation++ // Invalidate all late callbacks from the discarded socket.
        state = state.copy(status = DashboardStatus.RECONNECTING, stale = true, retryAtMs = nowMs + delay)
    }

    private fun terminate(status: DashboardStatus) {
        generation++
        state = DashboardLifecycleState(status)
    }
}
