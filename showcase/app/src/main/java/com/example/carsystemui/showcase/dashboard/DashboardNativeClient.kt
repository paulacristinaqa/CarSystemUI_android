package com.example.carsystemui.showcase.dashboard

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Owns its scheduler; callbacks run serialized, not necessarily on the Android UI thread. */
class DashboardNativeClient(
    private val baseUrl: String,
    private val view: DashboardView,
    private val onState: (DashboardLifecycleState) -> Unit,
    private val allowLocalDevelopmentHttp: Boolean = false,
    private val factory: WebSocket.Factory = OkHttpClient(),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : DashboardConnection {
    private val lifecycle = DashboardLifecycle()
    private var token: String? = null
    private var expiresAt = 0L
    private var socket: WebSocket? = null
    private var socketGeneration = -1L
    private var timer: ScheduledFuture<*>? = null
    private var disposed = false
    private var emitted: DashboardLifecycleState? = null

    @Synchronized
    override fun start(accessToken: String, expiresInSeconds: Long) {
        check(!disposed) { "Dashboard client is closed" }
        stop()
        require(expiresInSeconds in 1..86_400) { "Invalid dashboard session lifetime" }
        DashboardNativeContract.request(baseUrl, view, accessToken, allowLocalDevelopmentHttp)
        token = accessToken
        expiresAt = clockMs() + maxOf(1, expiresInSeconds - 10) * 1000
        val generation = lifecycle.start(clockMs())
        timer = scheduler.scheduleWithFixedDelay({ poll() }, 1, 1, TimeUnit.SECONDS)
        connect(generation)
        reconcile()
    }

    private fun connect(generation: Long) {
        val credential = token ?: return
        socketGeneration = generation
        try {
            socket = factory.newWebSocket(
                DashboardNativeContract.request(baseUrl, view, credential, allowLocalDevelopmentHttp),
                Listener(generation),
            )
        } catch (_: Exception) {
            lifecycle.disconnected(generation, null, clockMs())
        }
    }

    @Synchronized
    internal fun poll() {
        if (disposed || token == null) return
        if (clockMs() >= expiresAt) lifecycle.expire()
        else lifecycle.tick(clockMs())?.let { connect(it) }
        reconcile()
    }

    private fun reconcile() {
        if (socketGeneration != lifecycle.generation) {
            socket?.cancel()
            socket = null
            socketGeneration = -1
        }
        if (lifecycle.state.status in setOf(DashboardStatus.STOPPED, DashboardStatus.COMPLETE,
                DashboardStatus.AUTH_REQUIRED, DashboardStatus.FORBIDDEN)) {
            token = null
            timer?.cancel(false)
            timer = null
        }
        if (emitted != lifecycle.state) {
            emitted = lifecycle.state
            onState(lifecycle.state)
        }
    }

    @Synchronized
    fun stop() {
        lifecycle.stop()
        reconcile()
    }

    @Synchronized
    override fun close() {
        if (disposed) return
        disposed = true
        stop()
        scheduler.shutdownNow()
    }

    private inner class Listener(private val generation: Long) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) = synchronized(this@DashboardNativeClient) {
            if (disposed || token == null || generation != lifecycle.generation) return@synchronized
            if (clockMs() >= expiresAt) lifecycle.expire()
            else try {
                val envelope = DashboardEnvelopeParser.parse(text, view)
                lifecycle.snapshot(generation, envelope.sequence, envelope.json, clockMs())
            } catch (_: IllegalArgumentException) {
                lifecycle.disconnected(generation, 1002, clockMs())
            }
            reconcile()
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = ended(1003)
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            ended(code)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = ended(code)
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = ended(response?.code)

        private fun ended(code: Int?) = synchronized(this@DashboardNativeClient) {
            if (disposed || token == null || generation != lifecycle.generation) return@synchronized
            lifecycle.disconnected(generation, code, clockMs())
            reconcile()
        }
    }
}
