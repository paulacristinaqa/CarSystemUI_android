package com.example.carsystemui.showcase.gateway

import org.json.JSONObject

object TestRunUpdateParser {
    fun parse(body: String): TestRunStreamMessage {
        val root = JSONObject(body)
        val type = root.getString("type")
        if (type == "atep.test_run.heartbeat.v1") return TestRunStreamMessage.Heartbeat
        if (type !in setOf(
                "atep.test_run.snapshot.v1",
                "atep.test_run.created.v1",
                "atep.test_run.updated.v1",
            )
        ) {
            throw IllegalArgumentException("Unsupported test-run event: $type")
        }
        val value = root.getJSONObject("test_run")
        return TestRunStreamMessage.Update(
            LiveTestRun(
                runId = value.getString("run_id"),
                name = value.getString("name"),
                suite = value.getString("suite"),
                status = value.getString("status"),
                progressPercent = value.getInt("progress_percent"),
                version = value.getInt("version"),
                summary = value.optString("summary").takeUnless { value.isNull("summary") },
            ),
        )
    }
}
