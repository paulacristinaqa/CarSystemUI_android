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

    fun start() {
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

    override fun close() {
        closed = true
        socket?.close(1000, "CarSystemUI stopped")
        scheduler.shutdownNow()
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            retryAttempt = 0
            onState(TestRunLiveState(TestRunLiveConnection.CONNECTED, testRun = latest))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
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
                        detail = error.message ?: "Invalid live test-run message.",
                    ),
                )
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!closed) reconnect("ATEP closed the stream ($code).")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            reconnect(t.message ?: "ATEP live stream failed.")
        }
    }
}
