package com.example.carsystemui.showcase.gateway

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class TestRunLiveUpdateClient(
    private val config: TestRunLiveConfig,
    private val onState: (TestRunLiveState) -> Unit,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) : AutoCloseable {
    private val reducer = TestRunLiveReducer()
    @Volatile private var closed = false
    @Volatile private var socket: WebSocket? = null
    private var latest: LiveTestRun? = null
    private var retryAttempt = 0

    @Synchronized
    fun start() {
        if (closed || socket != null) return
        if (!config.isEnabled) {
            onState(
                TestRunLiveState(
                    TestRunLiveConnection.DISABLED,
                    detail = "Configure ATEP_OPERATOR_TOKEN and ATEP_TEST_RUN_ID.",
                ),
            )
            return
        }
        connect(reconnecting = false)
    }

    @Synchronized
    private fun connect(reconnecting: Boolean) {
        if (closed) return
        val url = config.streamUrl()
        if (url == null) {
            onState(TestRunLiveState(TestRunLiveConnection.ERROR, detail = "Invalid ATEP URL."))
            return
        }
        onState(
            TestRunLiveState(
                if (reconnecting) TestRunLiveConnection.RECONNECTING
                else TestRunLiveConnection.CONNECTING,
                testRun = latest,
            ),
        )
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.accessToken}")
            .build()
        socket = httpClient.newWebSocket(request, Listener())
    }

    @Synchronized
    private fun reconnect(reason: String) {
        if (closed) return
        retryAttempt = (retryAttempt + 1).coerceAtMost(6)
        val delaySeconds = (1L shl (retryAttempt - 1)).coerceAtMost(30)
        onState(
            TestRunLiveState(
                TestRunLiveConnection.RECONNECTING,
                testRun = latest,
                detail = "$reason Retrying in ${delaySeconds}s.",
            ),
        )
        scheduler.schedule({ connect(reconnecting = true) }, delaySeconds, TimeUnit.SECONDS)
    }

    @Synchronized
    override fun close() {
        closed = true
        latest = null
        socket?.close(1000, "CarSystemUI stopped")
        scheduler.shutdownNow()
    }

    @Synchronized
    private fun denied() {
        if (closed) return
        close()
        onState(TestRunLiveState(TestRunLiveConnection.ERROR, detail = "ATEP authorization denied. Sign in again."))
    }

    internal inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response): Unit = synchronized(this@TestRunLiveUpdateClient) {
            if (closed) {
                webSocket.cancel()
                return@synchronized
            }
            retryAttempt = 0
            onState(TestRunLiveState(TestRunLiveConnection.CONNECTED, testRun = latest))
        }

        override fun onMessage(webSocket: WebSocket, text: String): Unit = synchronized(this@TestRunLiveUpdateClient) {
            if (closed) return@synchronized
            try {
                when (val message = TestRunUpdateParser.parse(text)) {
                    TestRunStreamMessage.Heartbeat ->
                        onState(TestRunLiveState(TestRunLiveConnection.CONNECTED, testRun = latest))
                    is TestRunStreamMessage.Update -> reducer.accept(message.testRun)?.let { accepted ->
                        latest = accepted
                        onState(
                            TestRunLiveState(
                                TestRunLiveConnection.CONNECTED,
                                testRun = accepted,
                            ),
                        )
                    }
                }
            } catch (error: Exception) {
                onState(
                    TestRunLiveState(
                        TestRunLiveConnection.ERROR,
                        testRun = latest,
                        detail = "Invalid live test-run message.",
                    ),
                )
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code in setOf(1008, 4401, 4403)) denied()
            else reconnect("ATEP closed the stream ($code).")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (response?.code in setOf(401, 403)) denied()
            else reconnect("ATEP live stream failed.")
        }
    }
}
