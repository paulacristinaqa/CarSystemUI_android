package com.example.carsystemui.showcase.gateway

import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestRunLiveUpdateClientTest {
    private val socket = Proxy.newProxyInstance(
        WebSocket::class.java.classLoader, arrayOf(WebSocket::class.java),
    ) { _, method, _ -> if (method.returnType == Boolean::class.javaPrimitiveType) true else null } as WebSocket

    @Test
    fun `late callbacks after close do not publish or schedule work`() {
        val states = mutableListOf<TestRunLiveState>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val client = TestRunLiveUpdateClient(TestRunLiveConfig("", "", ""), { states.add(it) }, scheduler = scheduler)
        client.close()
        val listener = client.Listener()
        listener.onOpen(socket, response(101))
        listener.onMessage(socket, "not JSON")
        listener.onClosed(socket, 1006, "disconnected")
        listener.onFailure(socket, IOException("private detail"), null)
        client.start()
        assertTrue(states.isEmpty())
        assertTrue(scheduler.isShutdown)
    }

    @Test
    fun `authorization rejection stops retries and clears displayed evidence`() {
        for (code in listOf(401, 403)) {
            val states = mutableListOf<TestRunLiveState>()
            val scheduler = Executors.newSingleThreadScheduledExecutor()
            val client = TestRunLiveUpdateClient(TestRunLiveConfig("", "", ""), { states.add(it) }, scheduler = scheduler)
            try {
                client.Listener().onFailure(socket, IOException("private detail"), response(code))
                assertEquals(1, states.size)
                assertEquals(TestRunLiveConnection.ERROR, states.single().connection)
                assertNull(states.single().testRun)
                assertTrue(scheduler.isShutdown)
            } finally { client.close() }
        }
    }

    @Test
    fun `authorization close frames are terminal`() {
        for (code in listOf(1008, 4401, 4403)) {
            val states = mutableListOf<TestRunLiveState>()
            val client = TestRunLiveUpdateClient(TestRunLiveConfig("", "", ""), { states.add(it) })
            try {
                client.Listener().onClosed(socket, code, "denied")
                client.Listener().onFailure(socket, IOException(), null)
                assertEquals(1, states.size)
                assertNull(states.single().testRun)
            } finally { client.close() }
        }
    }

    private fun response(code: Int) = Response.Builder()
        .request(Request.Builder().url("http://localhost/").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("test").build()
}
