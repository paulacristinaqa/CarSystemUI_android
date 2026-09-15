package com.example.carsystemui.showcase.gateway

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

interface TelemetryRetryScheduler {
    fun reconcile(status: GatewaySyncStatus)
}

class WorkManagerTelemetryRetryScheduler(
    context: Context,
    private val workName: String,
) : TelemetryRetryScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun reconcile(status: GatewaySyncStatus) {
        if (TelemetryRetrySchedulingPolicy.shouldSchedule(status)) {
            schedule()
        } else {
            workManager.cancelUniqueWork(workName)
        }
    }

    private fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<TelemetryRetryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_TAG = "atep-telemetry-retry"
        const val BACKOFF_SECONDS = 30L

        fun workName(vehicleId: String): String =
            "atep-telemetry-retry-${vehicleId.ifBlank { "unconfigured" }}"
    }
}

object TelemetryRetrySchedulingPolicy {
    fun shouldSchedule(status: GatewaySyncStatus): Boolean =
        status.pendingCount > 0 &&
            status.state != GatewayConnectionState.DISABLED &&
            !status.retryExhausted
}
