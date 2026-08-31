package uk.co.cbeesle1.homealarm.domain

enum class AlarmMode(val apiValue: String, val title: String) {
    AWAY("arm", "Arm Away"),
    HOME("home", "Arm Home"),
    DISARMED("disarm", "Disarmed");

    companion object {
        fun fromApiValue(value: String): AlarmMode = entries.firstOrNull { it.apiValue == value }
            ?: throw IllegalArgumentException("Unsupported alarm mode")
    }
}

enum class ConfirmationFreshness {
    CURRENT,
    LAST_CONFIRMED,
    UNKNOWN,
}

enum class ModeButtonState {
    INACTIVE,
    SELECTING,
    ACTIVE,
}

data class AlarmUiState(
    val confirmedMode: AlarmMode? = null,
    val pendingMode: AlarmMode? = null,
    val freshness: ConfirmationFreshness = ConfirmationFreshness.UNKNOWN,
    val isRefreshing: Boolean = false,
    val requiresSetup: Boolean = false,
    val message: String? = null,
    val lastCheckedEpochMillis: Long? = null,
) {
    fun buttonState(mode: AlarmMode): ModeButtonState = when {
        pendingMode == mode -> ModeButtonState.SELECTING
        confirmedMode == mode -> ModeButtonState.ACTIVE
        else -> ModeButtonState.INACTIVE
    }

    val commandsEnabled: Boolean
        get() = !requiresSetup && !isRefreshing && pendingMode == null && confirmedMode != null
}

interface AlarmGateway {
    suspend fun currentMode(): AlarmMode
    suspend fun requestMode(mode: AlarmMode): Boolean
}

class AlarmAuthenticationRequiredException : Exception("Yale sign-in has expired")
