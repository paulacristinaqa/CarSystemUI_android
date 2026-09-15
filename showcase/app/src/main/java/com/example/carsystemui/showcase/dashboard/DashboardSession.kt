package com.example.carsystemui.showcase.dashboard

interface DashboardConnection : AutoCloseable {
    fun start(accessToken: String, expiresInSeconds: Long)
}

/** All public methods and dispatched callbacks belong to the UI owner thread. */
class DashboardSession(
    private val dispatch: (() -> Unit) -> Unit,
    private val onState: (DashboardLifecycleState) -> Unit,
    private val create: (String, DashboardView, Boolean, (DashboardLifecycleState) -> Unit) -> DashboardConnection =
        { base, view, local, callback -> DashboardNativeClient(base, view, callback, local) },
) : AutoCloseable {
    private var generation = 0L
    private var connection: DashboardConnection? = null
    private var closed = false

    fun connect(base: String, view: DashboardView, local: Boolean, token: String, lifetime: Long) {
        check(!closed) { "Dashboard session is closed" }
        stop()
        val current = generation
        try {
            val next = create(base, view, local) { state ->
                dispatch { if (!closed && generation == current) onState(state) }
            }
            connection = next
            next.start(token, lifetime)
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        generation++
        val old = connection
        connection = null
        runCatching { old?.close() }
        onState(DashboardLifecycleState(DashboardStatus.STOPPED))
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
    }
}
