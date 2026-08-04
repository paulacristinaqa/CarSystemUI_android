package com.example.carsystemui.showcase.gateway

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class TelemetryRetryWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result = runCatching {
        val store = VehicleGatewayFactory.store(applicationContext)
        val status = VehicleGatewayFactory.create(applicationContext, store).flush()
        when (val decision = TelemetryRetryPolicy.decide(status, runAttemptCount)) {
            TelemetryRetryDecision.COMPLETE -> {
                store.updateRetryState(TelemetryRetryState())
                Result.success()
            }
            TelemetryRetryDecision.RETRY, TelemetryRetryDecision.EXHAUSTED -> {
                store.updateRetryState(TelemetryRetryPolicy.progress(decision, runAttemptCount))
                if (decision == TelemetryRetryDecision.RETRY) Result.retry() else Result.failure()
            }
        }
    }.getOrElse {
        val store = VehicleGatewayFactory.store(applicationContext)
        val decision = if (runAttemptCount + 1 >= TelemetryRetryPolicy.MAX_ATTEMPTS) {
            TelemetryRetryDecision.EXHAUSTED
        } else {
            TelemetryRetryDecision.RETRY
        }
        store.updateRetryState(TelemetryRetryPolicy.progress(decision, runAttemptCount))
        if (decision == TelemetryRetryDecision.EXHAUSTED) Result.failure() else Result.retry()
    }
}

enum class TelemetryRetryDecision {
    COMPLETE,
    RETRY,
    EXHAUSTED,
}

object TelemetryRetryPolicy {
    const val MAX_ATTEMPTS = 8

    fun decide(status: GatewaySyncStatus, completedAttempts: Int): TelemetryRetryDecision = when {
        status.state == GatewayConnectionState.DISABLED -> TelemetryRetryDecision.COMPLETE
        status.pendingCount == 0 -> TelemetryRetryDecision.COMPLETE
        completedAttempts + 1 >= MAX_ATTEMPTS -> TelemetryRetryDecision.EXHAUSTED
        else -> TelemetryRetryDecision.RETRY
    }

    fun progress(
        decision: TelemetryRetryDecision,
        completedAttempts: Int,
    ): TelemetryRetryState = TelemetryRetryState(
        attempts = (completedAttempts + 1).coerceAtMost(MAX_ATTEMPTS),
        exhausted = decision == TelemetryRetryDecision.EXHAUSTED,
    )
}
