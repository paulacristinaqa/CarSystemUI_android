package com.example.carsystemui.showcase.dashboard

import org.junit.Assert.*
import org.junit.Test

class DashboardLifecycleTest {
    @Test
    fun `freshness becomes stale after 35 seconds and valid snapshot restores it`() {
        val client = DashboardLifecycle()
        val connection = client.start(0)
        client.snapshot(connection, 1, "first", 100)
        client.tick(35_099)
        assertFalse(client.state.stale)
        client.tick(35_100)
        assertEquals(DashboardStatus.STALE, client.state.status)
        assertEquals("first", client.state.snapshot)
        client.snapshot(connection, 2, "second", 36_000)
        assertFalse(client.state.stale)
    }

    @Test
    fun `retry waits at least 30 seconds and ignores old callbacks`() {
        val client = DashboardLifecycle()
        val old = client.start(0)
        client.snapshot(old, 1, "first", 1)
        client.disconnected(old, null, 2)
        client.snapshot(old, 2, "late", 3)
        client.disconnected(old, 403, 3)
        assertEquals("first", client.state.snapshot)
        assertEquals(DashboardStatus.RECONNECTING, client.state.status)
        assertNull(client.tick(30_001))
        val next = requireNotNull(client.tick(30_002))
        client.snapshot(next, 1, "new connection", 30_003)
        assertEquals("new connection", client.state.snapshot)
    }

    @Test
    fun `five consecutive retries are capped and evidence cleared`() {
        val client = DashboardLifecycle()
        var now = 0L
        var connection = client.start(now)
        for (delay in listOf(30_000L, 60_000L, 120_000L, 120_000L, 120_000L)) {
            client.disconnected(connection, 1013, now)
            assertEquals(now + delay, client.state.retryAtMs)
            now += delay
            connection = requireNotNull(client.tick(now))
        }
        client.disconnected(connection, null, now)
        assertEquals(DashboardStatus.STOPPED, client.state.status)
        assertNull(client.state.snapshot)
        assertNull(client.tick(now + 999_999))
    }

    @Test
    fun `authorization and protocol denials clear populated evidence`() {
        for ((code, expected) in listOf(401 to DashboardStatus.AUTH_REQUIRED,
            4401 to DashboardStatus.AUTH_REQUIRED, 403 to DashboardStatus.FORBIDDEN,
            4403 to DashboardStatus.FORBIDDEN, 1008 to DashboardStatus.STOPPED)) {
            val client = DashboardLifecycle()
            val connection = client.start(0)
            client.snapshot(connection, 1, "private evidence", 1)
            client.disconnected(connection, code, 2)
            assertEquals(expected, client.state.status)
            assertNull(client.state.snapshot)
            assertNull(client.tick(999_999))
        }
    }

    @Test
    fun `ten snapshots complete normally without automatic restart`() {
        val client = DashboardLifecycle()
        val connection = client.start(0)
        for (sequence in 1..10) client.snapshot(connection, sequence, "snapshot", sequence * 30_000L)
        client.disconnected(connection, 1000, 300_001)
        assertEquals(DashboardStatus.COMPLETE, client.state.status)
        assertTrue(client.state.stale)
        assertNull(client.tick(999_999))
        client.stop()
        assertNull(client.state.snapshot)
    }

    @Test
    fun `invalid sequence oversized payload and stop fail closed`() {
        for ((sequence, payload) in listOf(2 to "gap", 1 to "x".repeat(300_001))) {
            val client = DashboardLifecycle()
            val connection = client.start(0)
            client.snapshot(connection, sequence, payload, 1)
            assertEquals(DashboardStatus.STOPPED, client.state.status)
        }
        val client = DashboardLifecycle()
        val connection = client.start(0)
        client.stop()
        client.snapshot(connection, 1, "late", 1)
        assertNull(client.state.snapshot)
    }

    @Test
    fun `first snapshot timeout schedules bounded retry`() {
        val client = DashboardLifecycle()
        client.start(0)
        client.tick(29_999)
        assertEquals(DashboardStatus.CONNECTING, client.state.status)
        client.tick(30_000)
        assertEquals(60_000L, client.state.retryAtMs)
    }
}
