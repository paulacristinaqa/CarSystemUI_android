package com.example.carsystemui.showcase.dashboard

import java.io.IOException
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test

internal fun dashboardFrame(sequence: Int = 1) = """{
  "type":"atep.dashboard.snapshot.v1","sequence":$sequence,
  "observed_at":"2026-09-15T10:00:00Z","refresh_not_before":"2026-09-15T10:00:30Z",
  "refresh_interval_seconds":30,"freshness_basis":"server_query_not_vehicle_measurement",
  "snapshot":{"view":"operations","contract_version":"dashboard-export-v1",
  "server_retention":"not_persisted","data":{}}
}"""

class DashboardNativeClientTest {
    private class Socket(private val req: Request) : WebSocket {
        var cancelled = false
        override fun request() = req
        override fun queueSize() = 0L
        override fun send(text: String): Boolean = error("Read-only transport must not send")
        override fun send(bytes: ByteString): Boolean = error("Read-only transport must not send")
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }
    }

    private class Fixture : AutoCloseable {
        var now = 0L
        val states = mutableListOf<DashboardLifecycleState>()
        val sockets = mutableListOf<Socket>()
        val listeners = mutableListOf<WebSocketListener>()
        val client = DashboardNativeClient("https://atep.example", DashboardView.OPERATIONS,
            { states.add(it) }, factory = WebSocket.Factory { request, listener ->
                listeners.add(listener)
                Socket(request).also { sockets.add(it) }
            }, clockMs = { now })
        fun frame(sequence: Int = 1) = listeners.last().onMessage(sockets.last(), dashboardFrame(sequence))
        override fun close() = client.close()
    }

    @Test
    fun `native transport receives a frame and retries without sending messages`() = Fixture().use { f ->
        f.client.start("test-token", 300)
        assertEquals("Bearer test-token", f.sockets[0].request().header("Authorization"))
        assertNull(f.sockets[0].request().header("Origin"))
        f.frame()
        assertEquals(DashboardStatus.LIVE, f.states.last().status)
        f.listeners[0].onFailure(f.sockets[0], IOException("secret detail"), null)
        assertTrue(f.sockets[0].cancelled)
        f.now = 29_999; f.client.poll()
        assertEquals(1, f.sockets.size)
        f.now = 30_000; f.client.poll()
        assertEquals(2, f.sockets.size)
        f.listeners[0].onMessage(f.sockets[0], "invalid old frame")
        f.frame()
        assertEquals(DashboardStatus.LIVE, f.states.last().status)
    }

    @Test
    fun `permission loss clears populated data and cancels transport`() = Fixture().use { f ->
        f.client.start("test-token", 300); f.frame()
        f.listeners[0].onClosing(f.sockets[0], 4403, "private detail")
        assertEquals(DashboardStatus.FORBIDDEN, f.states.last().status)
        assertNull(f.states.last().snapshot)
        assertTrue(f.sockets[0].cancelled)
        f.now = 900_000; f.client.poll()
        assertEquals(1, f.sockets.size)
    }

    @Test
    fun `local expiry clears evidence even during retry`() = Fixture().use { f ->
        f.client.start("test-token", 60); f.frame()
        f.listeners[0].onFailure(f.sockets[0], IOException(), null)
        f.now = 50_000; f.client.poll()
        assertEquals(DashboardStatus.AUTH_REQUIRED, f.states.last().status)
        assertNull(f.states.last().snapshot)
        assertEquals(1, f.sockets.size)
    }

    @Test
    fun `malformed and binary messages fail closed`() {
        for (binary in listOf(false, true)) Fixture().use { f ->
            f.client.start("test-token", 300); f.frame()
            if (binary) f.listeners[0].onMessage(f.sockets[0], ByteString.EMPTY)
            else f.listeners[0].onMessage(f.sockets[0], "invalid")
            assertEquals(DashboardStatus.STOPPED, f.states.last().status)
            assertNull(f.states.last().snapshot)
        }
    }

    @Test
    fun `normal completion cancels socket and does not reconnect`() = Fixture().use { f ->
        f.client.start("test-token", 600)
        for (sequence in 1..10) f.frame(sequence)
        f.listeners[0].onClosing(f.sockets[0], 1000, "done")
        assertEquals(DashboardStatus.COMPLETE, f.states.last().status)
        assertTrue(f.states.last().stale)
        f.now = 400_000; f.client.poll()
        assertEquals(1, f.sockets.size)
    }

    @Test
    fun `disposal ignores late messages and refuses restart`(): Unit = Fixture().use { f ->
        f.client.start("test-token", 300); f.frame(); f.client.close()
        val count = f.states.size
        f.frame(2); f.client.poll()
        assertEquals(count, f.states.size)
        assertNull(f.states.last().snapshot)
        assertThrows(IllegalStateException::class.java) { f.client.start("test-token", 300) }
    }
}
