package com.example.carsystemui.showcase.dashboard

import org.junit.Assert.*
import org.junit.Test

class DashboardEnvelopeTest {
    @Test
    fun `valid frame preserves query metadata`() {
        val text = dashboardFrame()
        val frame = DashboardEnvelopeParser.parse(text, DashboardView.OPERATIONS)
        assertEquals(1, frame.sequence)
        assertEquals(text, frame.json)
    }

    @Test
    fun `wrong metadata and cross view responses are rejected`() {
        val original = dashboardFrame()
        val invalid = listOf(
            original.replace("snapshot.v1", "snapshot.v2"),
            original.replace("10:00:30Z", "10:00:31Z"),
            original.replace("not_vehicle_measurement", "vehicle_measurement"),
            original.replace("not_persisted", "persisted"),
            original.replace("operations", "mobility"),
            original.replace("export-v1", "export-v2"),
            original.replace("\"sequence\":1", "\"sequence\":1.5"),
            original.replace("\"data\":{}", "\"data\":[]"),
        )
        invalid.forEach { assertInvalid(it) }
    }

    @Test
    fun `trailing content oversized UTF8 and deep nesting are rejected`() {
        assertInvalid(dashboardFrame() + " {}")
        assertInvalid(dashboardFrame().replace("\"data\":{}", "\"data\":{\"padding\":\"${"é".repeat(150_001)}\"}"))
        assertInvalid(dashboardFrame().replace("\"data\":{}", "\"data\":{\"nested\":${"[".repeat(65)}0${"]".repeat(65)}}"))
        assertInvalid("not json")
    }

    private fun assertInvalid(text: String) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DashboardEnvelopeParser.parse(text, DashboardView.OPERATIONS)
        }
        assertEquals("Invalid dashboard envelope", error.message)
    }
}
