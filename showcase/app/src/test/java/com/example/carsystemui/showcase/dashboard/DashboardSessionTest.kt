package com.example.carsystemui.showcase.dashboard

import org.junit.Assert.*
import org.junit.Test

class DashboardSessionTest {
    private class Connection(val callback: (DashboardLifecycleState) -> Unit) : DashboardConnection {
        var closed = false
        var failStart = false
        var lifetime = 0L
        override fun start(accessToken: String, expiresInSeconds: Long) {
            lifetime = expiresInSeconds
            if (failStart) error("private detail")
        }
        override fun close() { closed = true }
        fun emit() = callback(DashboardLifecycleState(DashboardStatus.LIVE, false, "evidence"))
    }

    private class Fixture {
        val queue = mutableListOf<() -> Unit>()
        val states = mutableListOf<DashboardLifecycleState>()
        val connections = mutableListOf<Connection>()
        var fail = false
        val session = DashboardSession({ queue.add(it) }, { states.add(it) },
            { _, _, _, callback -> Connection(callback).also { it.failStart = fail; connections.add(it) } })
        fun connect() = session.connect("https://atep.example", DashboardView.OPERATIONS, false, "test-token", 60)
        fun drain() { queue.toList().also { queue.clear() }.forEach { it() } }
    }

    @Test
    fun `queued callbacks cannot restore data after background stop`() {
        val f = Fixture(); f.connect(); f.connections[0].emit()
        f.session.stop(); f.drain()
        assertTrue(f.connections[0].closed)
        assertNull(f.states.last().snapshot)
    }

    @Test
    fun `switching sessions closes old transport and rejects old callbacks`() {
        val f = Fixture(); f.connect(); f.connections[0].emit(); f.connect(); f.drain()
        assertTrue(f.connections[0].closed)
        assertNull(f.states.last().snapshot)
        f.connections[1].emit(); f.drain()
        assertEquals("evidence", f.states.last().snapshot)
        assertEquals(60L, f.connections[1].lifetime)
        f.session.close()
    }

    @Test
    fun `failed start closes partial transport and leaves no evidence`() {
        val f = Fixture(); f.fail = true; f.connect()
        assertTrue(f.connections[0].closed)
        assertEquals(DashboardStatus.STOPPED, f.states.last().status)
        assertNull(f.states.last().snapshot)
    }

    @Test
    fun `final disposal rejects restart and late callback`() {
        val f = Fixture(); f.connect(); f.session.close(); f.connections[0].emit(); f.drain()
        assertNull(f.states.last().snapshot)
        assertThrows(IllegalStateException::class.java) { f.connect() }
    }
}
