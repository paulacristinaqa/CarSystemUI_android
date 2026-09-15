package com.example.carsystemui.showcase.dashboard

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

enum class DashboardView(val path: String) {
    OPERATIONS("operations"), MOBILITY("mobility"), EVIDENCE_READINESS("evidence-readiness"),
}

/** Request construction only: no connection, credential storage or vehicle commands. */
object DashboardNativeContract {
    const val REFRESH_SECONDS = 30
    const val MAX_SNAPSHOTS = 10

    fun request(
        baseUrl: String,
        view: DashboardView,
        accessToken: String,
        allowLocalDevelopmentHttp: Boolean = false,
    ): Request {
        val base = baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid dashboard base URL")
        require(base.username.isEmpty() && base.password.isEmpty() &&
            base.encodedPath == "/" && base.query == null && base.fragment == null
        ) { "Dashboard base URL must contain only scheme, host and port" }
        require(base.isHttps || (allowLocalDevelopmentHttp &&
            base.host in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2"))
        ) { "Dashboard requires HTTPS except explicitly enabled local development" }
        require(accessToken.isNotEmpty() && accessToken.length <= 4096 &&
            accessToken.all { it in '!'..'~' }
        ) { "Invalid dashboard access token" }
        val url = base.newBuilder().addPathSegments("api/v1/dashboard/stream")
            .addPathSegment(view.path).build()
        // OkHttp uses an HTTP(S) upgrade request for newWebSocket. Never add Origin.
        return Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
    }
}
