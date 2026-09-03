package uk.co.cbeesle1.homealarm.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AlarmController(
    initialGateway: AlarmGateway?,
    private val scope: CoroutineScope,
    private val verificationDelaysMillis: List<Long> = listOf(0, 1_000, 2_000, 3_000, 5_000, 8_000),
    private val delayFunction: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val operationMutex = Mutex()
    private var gateway: AlarmGateway? = initialGateway
    private val mutableState = MutableStateFlow(
        AlarmUiState(requiresSetup = initialGateway == null),
    )

    val state: StateFlow<AlarmUiState> = mutableState.asStateFlow()

    fun refresh() {
        scope.launch { refreshNow() }
    }

    fun refreshOnSurfaceStart() {
        scope.launch { refreshOnSurfaceStartNow() }
    }

    internal suspend fun refreshOnSurfaceStartNow() {
        val firstReadSucceeded = refreshNow()
        if (!firstReadSucceeded && !mutableState.value.requiresSetup) {
            delayFunction(INITIAL_READ_RETRY_DELAY_MILLIS)
            refreshNow()
        }
    }

    suspend fun refreshNow(): Boolean = operationMutex.withLock {
        val activeGateway = gateway ?: run {
            mutableState.value = mutableState.value.copy(
                requiresSetup = true,
                isRefreshing = false,
                freshness = ConfirmationFreshness.UNKNOWN,
            )
            return@withLock false
        }
        if (mutableState.value.pendingMode != null) return@withLock false

        mutableState.value = mutableState.value.copy(isRefreshing = true, message = null)
        runCatching { activeGateway.currentMode() }.fold(
            onSuccess = { reported ->
                mutableState.value = mutableState.value.copy(
                    confirmedMode = reported,
                    freshness = ConfirmationFreshness.CURRENT,
                    isRefreshing = false,
                    requiresSetup = false,
                    lastCheckedEpochMillis = clock(),
                )
                true
            },
            onFailure = {
                handleReadFailure(it)
                false
            },
        )
    }

    fun requestMode(mode: AlarmMode) {
        scope.launch { requestModeNow(mode) }
    }

    suspend fun requestModeNow(mode: AlarmMode) = operationMutex.withLock {
        val activeGateway = gateway ?: return@withLock
        val before = mutableState.value
        if (before.pendingMode != null || before.confirmedMode == mode || before.confirmedMode == null) {
            return@withLock
        }

        mutableState.value = before.copy(pendingMode = mode, message = null)
        val commandResult = runCatching { activeGateway.requestMode(mode) }
        if (commandResult.exceptionOrNull() is AlarmAuthenticationRequiredException) {
            requireReconnect()
            return@withLock
        }
        val commandAccepted = commandResult.getOrDefault(false)

        if (!commandAccepted) {
            verifyOnceAfterRejectedCommand(activeGateway)
            return@withLock
        }

        var latestReported: AlarmMode? = null
        var hadSuccessfulRead = false

        for (waitMillis in verificationDelaysMillis) {
            delayFunction(waitMillis)
            val statusResult = runCatching { activeGateway.currentMode() }
            if (statusResult.exceptionOrNull() is AlarmAuthenticationRequiredException) {
                requireReconnect()
                return@withLock
            }
            statusResult
                .onSuccess { reported ->
                    hadSuccessfulRead = true
                    latestReported = reported
                    mutableState.value = mutableState.value.copy(
                        confirmedMode = reported,
                        freshness = ConfirmationFreshness.CURRENT,
                        lastCheckedEpochMillis = clock(),
                    )
                }

            if (latestReported == mode) {
                mutableState.value = mutableState.value.copy(
                    confirmedMode = mode,
                    pendingMode = null,
                    freshness = ConfirmationFreshness.CURRENT,
                    message = null,
                    lastCheckedEpochMillis = clock(),
                )
                return@withLock
            }
        }

        mutableState.value = mutableState.value.copy(
            pendingMode = null,
            freshness = if (hadSuccessfulRead) {
                ConfirmationFreshness.CURRENT
            } else {
                ConfirmationFreshness.LAST_CONFIRMED
            },
            message = "Change not confirmed. The previous state is still shown.",
        )
    }

    private suspend fun verifyOnceAfterRejectedCommand(activeGateway: AlarmGateway) {
        val result = runCatching { activeGateway.currentMode() }
        if (result.exceptionOrNull() is AlarmAuthenticationRequiredException) {
            requireReconnect()
            return
        }
        mutableState.value = result.fold(
            onSuccess = { reported ->
                mutableState.value.copy(
                    confirmedMode = reported,
                    pendingMode = null,
                    freshness = ConfirmationFreshness.CURRENT,
                    message = "Yale did not accept the change.",
                    lastCheckedEpochMillis = clock(),
                )
            },
            onFailure = {
                mutableState.value.copy(
                    pendingMode = null,
                    freshness = ConfirmationFreshness.LAST_CONFIRMED,
                    message = "Yale did not accept the change. Current state could not be checked.",
                )
            },
        )
    }

    private fun handleReadFailure(error: Throwable) {
        if (error is AlarmAuthenticationRequiredException) {
            requireReconnect()
            return
        }
        mutableState.value = mutableState.value.copy(
            freshness = if (mutableState.value.confirmedMode == null) {
                ConfirmationFreshness.UNKNOWN
            } else {
                ConfirmationFreshness.LAST_CONFIRMED
            },
            isRefreshing = false,
            message = "Could not check the current alarm state.",
        )
    }

    private fun requireReconnect() {
        mutableState.value = mutableState.value.copy(
            pendingMode = null,
            isRefreshing = false,
            requiresSetup = true,
            freshness = if (mutableState.value.confirmedMode == null) {
                ConfirmationFreshness.UNKNOWN
            } else {
                ConfirmationFreshness.LAST_CONFIRMED
            },
            message = "Yale sign-in has expired. Reconnect on your phone.",
        )
    }

    suspend fun replaceGateway(
        newGateway: AlarmGateway?,
        retryInitialReadOnce: Boolean = false,
    ) = operationMutex.withLock {
        gateway = newGateway
        mutableState.value = AlarmUiState(
            requiresSetup = newGateway == null,
        )
    }.also {
        refreshNow()
        if (
            retryInitialReadOnce &&
            mutableState.value.confirmedMode == null &&
            !mutableState.value.requiresSetup
        ) {
            delayFunction(INITIAL_READ_RETRY_DELAY_MILLIS)
            refreshNow()
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    private companion object {
        const val INITIAL_READ_RETRY_DELAY_MILLIS = 1_000L
    }
}
