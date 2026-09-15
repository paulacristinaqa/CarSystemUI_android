package com.example.carsystemui.showcase.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestRunLiveModelsTest {
    @Test
    fun `stream URL maps HTTP schemes to WebSocket schemes`() {
        assertEquals(
            "ws://10.0.2.2:8000/api/v1/test-runs/run-0001/stream",
            config("http://10.0.2.2:8000/").streamUrl(),
        )
        assertEquals(
            "wss://atep.example/api/v1/test-runs/run-0001/stream",
            config("https://atep.example").streamUrl(),
        )
        assertNull(config("ftp://atep.example").streamUrl())
    }

    @Test
    fun `reducer ignores duplicates and out of order messages`() {
        val reducer = TestRunLiveReducer()
        val versionTwo = run(version = 2, progress = 25)
        assertEquals(versionTwo, reducer.accept(versionTwo))
        assertNull(reducer.accept(versionTwo))
        assertNull(reducer.accept(run(version = 1, progress = 10)))
        val versionThree = run(version = 3, progress = 100)
        assertEquals(versionThree, reducer.accept(versionThree))
    }

    @Test
    fun `live subscription requires run and operator credentials`() {
        assertEquals(true, config("http://localhost:8000").isEnabled)
        assertEquals(false, config("http://localhost:8000").copy(accessToken = "").isEnabled)
        assertEquals(false, config("http://localhost:8000").copy(runId = "").isEnabled)
    }

    private fun config(baseUrl: String) = TestRunLiveConfig(
        baseUrl = baseUrl,
        runId = "run-0001",
        accessToken = "operator-token",
    )

    private fun run(version: Int, progress: Int) = LiveTestRun(
        runId = "run-0001",
        name = "Battery thermal test",
        suite = "smoke",
        status = if (progress == 100) "passed" else "running",
        progressPercent = progress,
        version = version,
        summary = null,
    )
}
