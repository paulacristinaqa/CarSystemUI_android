package com.example.carsystemui.showcase.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carsystemui.showcase.BuildConfig

@Composable
fun DashboardPanel(model: DashboardViewModel = viewModel()) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ATEP dashboard — engineering preview")
            Text("Read-only server aggregates, not live vehicle measurements. Use only while parked in a test environment.")
            OutlinedTextField(model.baseUrl, { model.stop(); model.baseUrl = it }, label = { Text("Server origin") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            DashboardView.entries.forEach { view ->
                OutlinedButton(onClick = { model.stop(); model.view = view }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (model.view == view) "Selected: ${view.path}" else view.path)
                }
            }
            OutlinedTextField(model.tokenDraft, { model.tokenDraft = it.take(4097) },
                label = { Text("Temporary access token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model.lifetimeDraft, { model.lifetimeDraft = it.take(6) },
                label = { Text("Remaining token lifetime (seconds)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth())
            if (BuildConfig.DEBUG) {
                Text("Allow local development HTTP (loopback / 10.0.2.2 only)")
                Checkbox(model.allowLocalHttp, { model.stop(); model.allowLocalHttp = it },
                    modifier = Modifier.semantics { contentDescription = "Allow local development HTTP" })
            }
            Button(onClick = model::connect, modifier = Modifier.fillMaxWidth()) { Text("Connect") }
            OutlinedButton(onClick = model::stop, modifier = Modifier.fillMaxWidth()) { Text("Disconnect and clear") }
            Text("${model.state.status.name} · ${if (model.state.stale) "STALE / NOT LIVE" else "CURRENT SERVER QUERY"}",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            model.error?.let { Text(it) }
            Text("Token is cleared from the form on connect. Leaving this app disconnects and clears evidence. No automatic login or restart.")
            model.state.snapshot?.let { snapshot ->
                Text("Envelope preview (first 12,000 characters; observation timestamps are server query times):")
                Text(snapshot.take(12_000))
                if (snapshot.length > 12_000) Text("Preview truncated; this is not a complete export.")
            }
        }
    }
}
