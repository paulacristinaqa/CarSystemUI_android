package com.example.carsystemui.showcase.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carsystemui.showcase.BuildConfig
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    var baseUrl by mutableStateOf(BuildConfig.ATEP_BASE_URL)
    var tokenDraft by mutableStateOf("")
    var lifetimeDraft by mutableStateOf("300")
    var view by mutableStateOf(DashboardView.OPERATIONS)
    var allowLocalHttp by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
        private set
    var state by mutableStateOf(DashboardLifecycleState())
        private set
    private val session = DashboardSession(
        dispatch = { task -> viewModelScope.launch { task() } },
        onState = { state = it },
    )

    fun connect() {
        val credential = tokenDraft
        tokenDraft = ""
        session.stop()
        error = null
        val lifetime = lifetimeDraft.toLongOrNull()
        try {
            require(lifetime != null && lifetime in 1..86_400)
            DashboardNativeContract.request(baseUrl, view, credential, BuildConfig.DEBUG && allowLocalHttp)
            session.connect(baseUrl, view, BuildConfig.DEBUG && allowLocalHttp, credential, lifetime)
        } catch (_: IllegalArgumentException) {
            error = "Check the server origin, token and remaining lifetime (1–86400 seconds)."
        }
    }

    fun stop() {
        tokenDraft = ""
        error = null
        session.stop()
    }

    override fun onCleared() {
        tokenDraft = ""
        session.close()
    }
}
