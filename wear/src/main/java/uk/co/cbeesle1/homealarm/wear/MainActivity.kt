package uk.co.cbeesle1.homealarm.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var transport: DataLayerPhoneTransport
    private lateinit var controller: WearAlarmController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transport = DataLayerPhoneTransport(applicationContext)
        val stateStore = WearAlarmStateStore(applicationContext)
        controller = WearAlarmController(
            transport = transport,
            scope = lifecycleScope,
            initialConfirmedMode = stateStore.readConfirmedMode(),
            onConfirmedMode = { mode ->
                stateStore.writeConfirmedMode(mode)
                HomeAlarmComplicationService.requestUpdate(applicationContext)
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WearStateUpdates.confirmedModes.collect(controller::applyPushedConfirmedMode)
            }
        }
        setContent {
            HomeAlarmWearTheme {
                HomeAlarmWearScreen(controller)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.refreshOnSurfaceStart()
    }

    override fun onDestroy() {
        transport.close()
        super.onDestroy()
    }
}

@Composable
private fun HomeAlarmWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun HomeAlarmWearScreen(controller: WearAlarmController) {
    val state by controller.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Home Alarm",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = state.statusText(),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )

        RemoteAlarmMode.entries.forEach { mode ->
            val isCurrent = state.confirmedMode == mode
            val isPending = state.pendingMode == mode
            Button(
                onClick = { controller.requestMode(mode) },
                label = {
                    Text(
                        when {
                            isPending -> "${mode.label}…"
                            isCurrent -> "✓ ${mode.label}"
                            else -> mode.label
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = when {
                            isPending -> "Selecting"
                            isCurrent -> "Current alarm mode"
                            else -> "Not selected"
                        }
                    },
                enabled = state.commandsEnabled && !isCurrent,
            )
        }

        state.message?.let { message ->
            Text(
                text = if (state.requiresPhoneSetup) {
                    "Open Home Alarm on your phone to sign in."
                } else {
                    message
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Button(
            onClick = controller::refresh,
            label = { Text(if (state.isRefreshing) "Checking…" else "Refresh") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isRefreshing && state.pendingMode == null,
        )
    }
}

private fun WearAlarmUiState.statusText(): String = when {
    requiresPhoneSetup -> "Sign in on phone"
    isRefreshing -> "Checking Yale…"
    confirmedMode == null -> "State not yet known"
    freshness == RemoteFreshness.CURRENT -> "${confirmedMode.label} · confirmed"
    else -> "${confirmedMode.label} · last confirmed"
}

private val RemoteAlarmMode.label: String
    get() = when (this) {
        RemoteAlarmMode.AWAY -> "Away"
        RemoteAlarmMode.HOME -> "Home"
        RemoteAlarmMode.DISARMED -> "Disarmed"
    }
