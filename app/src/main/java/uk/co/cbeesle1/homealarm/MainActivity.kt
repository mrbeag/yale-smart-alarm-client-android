package uk.co.cbeesle1.homealarm

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.cbeesle1.homealarm.domain.AlarmMode
import uk.co.cbeesle1.homealarm.domain.AlarmUiState
import uk.co.cbeesle1.homealarm.domain.ConfirmationFreshness
import uk.co.cbeesle1.homealarm.domain.ModeButtonState
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeAlarmTheme {
                HomeAlarmRoot(application as HomeAlarmApplication)
            }
        }
    }
}

@Composable
private fun HomeAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeAlarmRoot(application: HomeAlarmApplication) {
    val state by application.controller.state.collectAsStateWithLifecycle()
    var showSetup by rememberSaveable { mutableStateOf(false) }

    if (state.requiresSetup) {
        SetupScreen(application = application)
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Home Alarm", fontWeight = FontWeight.Bold)
                        Text(
                            "Yale Smart Alarm",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = application.controller::refresh,
                        enabled = state.pendingMode == null && !state.isRefreshing,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh alarm state")
                    }
                    IconButton(onClick = { showSetup = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Connection settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        ControlScreen(
            state = state,
            modifier = Modifier.padding(innerPadding),
            onSelect = application.controller::requestMode,
        )
    }

    if (showSetup) {
        ConnectionDialog(
            application = application,
            onDismiss = { showSetup = false },
        )
    }
}

@Composable
private fun ControlScreen(
    state: AlarmUiState,
    modifier: Modifier = Modifier,
    onSelect: (AlarmMode) -> Unit,
) {
    val context = LocalContext.current
    val checkedAt = state.lastCheckedEpochMillis?.let {
        DateFormat.getTimeFormat(context).format(Date(it))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Choose alarm mode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        AlarmMode.entries.forEach { mode ->
            ModeButton(
                mode = mode,
                visualState = state.buttonState(mode),
                freshness = state.freshness,
                enabled = state.commandsEnabled,
                onClick = { onSelect(mode) },
            )
        }

        state.message?.let {
            StatusBanner(
                title = it,
                detail = "Tap refresh to check again.",
                background = MaterialTheme.colorScheme.errorContainer,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    when {
                        state.isRefreshing -> "Checking Yale…"
                        state.freshness == ConfirmationFreshness.CURRENT -> "State confirmed"
                        state.freshness == ConfirmationFreshness.LAST_CONFIRMED -> "Showing last confirmed state"
                        else -> "State not yet known"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                checkedAt?.let {
                    Text(
                        "Checked $it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state.isRefreshing) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun ModeButton(
    mode: AlarmMode,
    visualState: ModeButtonState,
    freshness: ConfirmationFreshness,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val active = visualState == ModeButtonState.ACTIVE
    val selecting = visualState == ModeButtonState.SELECTING
    val colorScheme = MaterialTheme.colorScheme
    val container = when {
        active -> colorScheme.secondaryContainer
        selecting -> colorScheme.tertiaryContainer
        else -> colorScheme.surface
    }
    val border = when {
        active -> colorScheme.secondary
        selecting -> colorScheme.tertiary
        else -> colorScheme.outlineVariant
    }
    val status = when {
        active && freshness == ConfirmationFreshness.LAST_CONFIRMED -> "Last confirmed"
        active -> "Current"
        selecting -> "Switching"
        else -> "Not active"
    }
    val icon: ImageVector = when (mode) {
        AlarmMode.AWAY -> Icons.Outlined.Lock
        AlarmMode.HOME -> Icons.Outlined.Home
        AlarmMode.DISARMED -> Icons.Outlined.LockOpen
    }

    Card(
        onClick = onClick,
        enabled = enabled && !active && !selecting,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .testTag("mode_${mode.name.lowercase()}")
            .semantics { stateDescription = status },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (active || selecting) 2.dp else 1.dp, border),
        colors = CardDefaults.cardColors(
            containerColor = container,
            disabledContainerColor = container,
            disabledContentColor = colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(
                    colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(mode.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when (mode) {
                        AlarmMode.AWAY -> "All sensors enabled"
                        AlarmMode.HOME -> "Perimeter sensors enabled"
                        AlarmMode.DISARMED -> "Intrusion detection off"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            when {
                selecting -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colorScheme.tertiary,
                    strokeWidth = 3.dp,
                )
                active -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colorScheme.secondary)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    active -> colorScheme.secondary
                    selecting -> colorScheme.tertiary
                    else -> colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatusBanner(title: String, detail: String, background: Color) {
    Surface(color = background, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupScreen(application: HomeAlarmApplication) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Set up Home Alarm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Sign in on this phone before using Android Auto.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            ConnectionForm(application = application, onConnected = {})
        }
    }
}

@Composable
private fun ConnectionDialog(
    application: HomeAlarmApplication,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection settings") },
        text = { ConnectionForm(application, onConnected = onDismiss) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ConnectionForm(application: HomeAlarmApplication, onConnected: () -> Unit) {
    val scope = rememberCoroutineScope()
    val yaleConnectorAvailable = BuildConfig.YALE_BASIC_AUTH.isNotBlank()
    val savedCredentials = remember(application) { application.loadSavedCredentials() }
    var email by rememberSaveable { mutableStateOf(savedCredentials?.email.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(savedCredentials?.password.orEmpty()) }
    var area by rememberSaveable { mutableStateOf(savedCredentials?.areaId?.toString() ?: "1") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Your last successful Yale login is encrypted using this phone's secure keystore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!yaleConnectorAvailable) {
            StatusBanner(
                title = "Yale login disabled in this build",
                detail = "This build cannot contact your alarm.",
                background = MaterialTheme.colorScheme.primaryContainer,
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Yale email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !working && yaleConnectorAvailable,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Yale password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !working && yaleConnectorAvailable,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = area,
            onValueChange = { area = it.filter(Char::isDigit).take(2) },
            label = { Text("Alarm area") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !working && yaleConnectorAvailable,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                working = true
                error = null
                scope.launch {
                    val result = application.connectYale(email, password, area.toIntOrNull() ?: 0)
                    working = false
                    result.onSuccess { onConnected() }
                        .onFailure { error = it.message ?: "Could not connect to Yale." }
                }
            },
            enabled = !working && yaleConnectorAvailable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.size(10.dp))
            }
            Text(if (working) "Connecting…" else "Connect securely")
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF173D68),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEFF),
    secondary = Color(0xFF147A55),
    secondaryContainer = Color(0xFFE7F6EF),
    tertiary = Color(0xFFA76500),
    tertiaryContainer = Color(0xFFFFF4D6),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEAF1F8),
    onSurfaceVariant = Color(0xFF526274),
    outlineVariant = Color(0xFFD5DEE8),
    error = Color(0xFFA52A2A),
    errorContainer = Color(0xFFFFE9E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7CFFF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF16476F),
    secondary = Color(0xFF71DDB0),
    secondaryContainer = Color(0xFF0C513C),
    tertiary = Color(0xFFFFC868),
    tertiaryContainer = Color(0xFF604000),
    background = Color(0xFF0F151C),
    surface = Color(0xFF17212B),
    surfaceVariant = Color(0xFF25313D),
    onSurfaceVariant = Color(0xFFBAC7D4),
    outlineVariant = Color(0xFF3D4A57),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF6B2325),
)
