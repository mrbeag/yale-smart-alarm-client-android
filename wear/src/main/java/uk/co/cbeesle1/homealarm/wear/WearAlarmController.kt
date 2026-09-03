package uk.co.cbeesle1.homealarm.wear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearRequest
import uk.co.cbeesle1.homealarm.common.WearResponse
import java.util.UUID

data class WearAlarmUiState(
    val confirmedMode: RemoteAlarmMode? = null,
    val freshness: RemoteFreshness = RemoteFreshness.UNKNOWN,
    val pendingMode: RemoteAlarmMode? = null,
    val isRefreshing: Boolean = false,
    val requiresPhoneSetup: Boolean = false,
    val message: String? = null,
) {
    val commandsEnabled: Boolean
        get() = confirmedMode != null && pendingMode == null && !isRefreshing && !requiresPhoneSetup
}

class WearAlarmController(
    private val transport: PhoneAlarmTransport,
    private val scope: CoroutineScope,
    initialConfirmedMode: RemoteAlarmMode? = null,
    private val onConfirmedMode: (RemoteAlarmMode) -> Unit = {},
    private val delayFunction: suspend (Long) -> Unit = { delay(it) },
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow(
        WearAlarmUiState(
            confirmedMode = initialConfirmedMode,
            freshness = if (initialConfirmedMode == null) {
                RemoteFreshness.UNKNOWN
            } else {
                RemoteFreshness.LAST_CONFIRMED
            },
        ),
    )
    val state: StateFlow<WearAlarmUiState> = mutableState.asStateFlow()

    fun refresh() {
        scope.launch { refreshNow() }
    }

    fun refreshOnSurfaceStart() {
        scope.launch { refreshOnSurfaceStartNow() }
    }

    internal suspend fun refreshOnSurfaceStartNow() {
        if (!refreshNow() && !mutableState.value.requiresPhoneSetup) {
            delayFunction(OPEN_RETRY_DELAY_MILLIS)
            refreshNow()
        }
    }

    internal suspend fun refreshNow(): Boolean = operationMutex.withLock {
        if (mutableState.value.pendingMode != null) return@withLock false
        mutableState.value = mutableState.value.copy(isRefreshing = true, message = null)
        val result = runCatching {
            transport.request(WearRequest.Status(requestIdFactory()))
        }
        result.fold(
            onSuccess = { response ->
                applyResponse(response, isRefreshing = false)
                response.freshness == RemoteFreshness.CURRENT || response.requiresPhoneSetup
            },
            onFailure = { error ->
                mutableState.value = mutableState.value.copy(
                    isRefreshing = false,
                    freshness = if (mutableState.value.confirmedMode == null) {
                        RemoteFreshness.UNKNOWN
                    } else {
                        RemoteFreshness.LAST_CONFIRMED
                    },
                    message = error.message ?: "Could not reach your phone.",
                )
                false
            },
        )
    }

    fun requestMode(mode: RemoteAlarmMode) {
        scope.launch { requestModeNow(mode) }
    }

    internal fun applyPushedConfirmedMode(mode: RemoteAlarmMode) {
        onConfirmedMode(mode)
        val before = mutableState.value
        mutableState.value = before.copy(
            confirmedMode = mode,
            freshness = RemoteFreshness.CURRENT,
            pendingMode = before.pendingMode?.takeUnless { it == mode },
            requiresPhoneSetup = false,
            message = null,
        )
    }

    internal suspend fun requestModeNow(mode: RemoteAlarmMode) = operationMutex.withLock {
        val before = mutableState.value
        if (!before.commandsEnabled || before.confirmedMode == mode) return@withLock
        mutableState.value = before.copy(pendingMode = mode, message = null)

        runCatching {
            transport.request(WearRequest.SetMode(requestIdFactory(), mode))
        }.fold(
            onSuccess = { response ->
                applyResponse(response, pendingMode = null)
                if (response.confirmedMode != mode && mutableState.value.message == null) {
                    mutableState.value = mutableState.value.copy(
                        message = "Change not confirmed. The previous state is still shown.",
                    )
                }
            },
            onFailure = { error ->
                mutableState.value = before.copy(
                    freshness = if (before.confirmedMode == null) {
                        RemoteFreshness.UNKNOWN
                    } else {
                        RemoteFreshness.LAST_CONFIRMED
                    },
                    message = error.message ?: "Could not reach your phone.",
                )
            },
        )
    }

    private fun applyResponse(
        response: WearResponse,
        pendingMode: RemoteAlarmMode? = mutableState.value.pendingMode,
        isRefreshing: Boolean = mutableState.value.isRefreshing,
    ) {
        response.confirmedMode?.let(onConfirmedMode)
        mutableState.value = mutableState.value.copy(
            confirmedMode = response.confirmedMode,
            freshness = response.freshness,
            pendingMode = pendingMode,
            isRefreshing = isRefreshing,
            requiresPhoneSetup = response.requiresPhoneSetup,
            message = response.message,
        )
    }

    private companion object {
        const val OPEN_RETRY_DELAY_MILLIS = 1_000L
    }
}
