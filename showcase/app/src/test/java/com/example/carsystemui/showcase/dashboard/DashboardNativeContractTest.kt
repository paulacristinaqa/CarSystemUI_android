package com.example.carsystemui.showcase.dashboard

import org.junit.Assert.*
import org.junit.Test

class DashboardNativeContractTest {
    @Test
    fun `views use native route with header authentication and no Origin`() {
        DashboardView.entries.forEach { view ->
            val request = DashboardNativeContract.request("https://atep.example:8443", view, "test-token")
            assertEquals("/api/v1/dashboard/stream/${view.path}", request.url.encodedPath)
            assertEquals("Bearer test-token", request.header("Authorization"))
            assertNull(request.header("Origin"))
            assertNull(request.url.query)
            assertFalse(request.url.toString().contains("test-token"))
            assertEquals("GET", request.method)
        }
    }

    @Test
    fun `plaintext needs explicit local development opt in`() {
        listOf("localhost", "127.0.0.1", "[::1]", "10.0.2.2").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                DashboardNativeContract.request("http://$host:8000", DashboardView.OPERATIONS, "token")
            }
            DashboardNativeContract.request("http://$host:8000", DashboardView.OPERATIONS, "token", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DashboardNativeContract.request("http://192.168.1.50:8000", DashboardView.OPERATIONS, "token", true)
        }
    }

    @Test
    fun `URL credentials paths queries fragments and unsupported schemes are rejected`() {
        listOf("https://user:secret@atep.example", "https://atep.example/api",
            "https://atep.example?token=secret", "https://atep.example#secret",
            "file:///tmp", "wss://atep.example", "not a URL").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                DashboardNativeContract.request(url, DashboardView.OPERATIONS, "token")
            }
        }
    }

    @Test
    fun `invalid tokens are rejected without echoing credentials`() {
        listOf("", "a b", "token\r\nX-Header: value", "a\t", "é", "x".repeat(4097)).forEach { token ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                DashboardNativeContract.request("https://atep.example", DashboardView.OPERATIONS, token)
            }
            assertEquals("Invalid dashboard access token", error.message)
        }
        DashboardNativeContract.request("https://atep.example", DashboardView.OPERATIONS, "x".repeat(4096))
    }

    @Test
    fun `constants match the bounded backend contract`() {
        assertEquals(30, DashboardNativeContract.REFRESH_SECONDS)
        assertEquals(10, DashboardNativeContract.MAX_SNAPSHOTS)
    }
}
