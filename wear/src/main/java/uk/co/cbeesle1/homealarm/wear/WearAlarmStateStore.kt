package uk.co.cbeesle1.homealarm.wear

import android.content.Context
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness

data class StoredAlarmState(
    val mode: RemoteAlarmMode,
    val freshness: RemoteFreshness,
)

class WearAlarmStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun readConfirmedMode(): RemoteAlarmMode? = preferences.getString(KEY_CONFIRMED_MODE, null)
        ?.let { runCatching { RemoteAlarmMode.valueOf(it) }.getOrNull() }

    fun readState(): StoredAlarmState? {
        val mode = readConfirmedMode() ?: return null
        val freshness = preferences.getString(KEY_FRESHNESS, null)
            ?.let { runCatching { RemoteFreshness.valueOf(it) }.getOrNull() }
            ?: RemoteFreshness.LAST_CONFIRMED
        return StoredAlarmState(mode, freshness)
    }

    fun writeConfirmedMode(
        mode: RemoteAlarmMode,
        freshness: RemoteFreshness = RemoteFreshness.CURRENT,
    ) {
        preferences.edit()
            .putString(KEY_CONFIRMED_MODE, mode.name)
            .putString(KEY_FRESHNESS, freshness.name)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "home_alarm_watch_state"
        const val KEY_CONFIRMED_MODE = "confirmed_mode"
        const val KEY_FRESHNESS = "freshness"
    }
}
