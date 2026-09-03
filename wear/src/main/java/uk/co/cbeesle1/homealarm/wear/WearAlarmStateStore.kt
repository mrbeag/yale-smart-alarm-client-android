package uk.co.cbeesle1.homealarm.wear

import android.content.Context
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode

class WearAlarmStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun readConfirmedMode(): RemoteAlarmMode? = preferences.getString(KEY_CONFIRMED_MODE, null)
        ?.let { runCatching { RemoteAlarmMode.valueOf(it) }.getOrNull() }

    fun writeConfirmedMode(mode: RemoteAlarmMode) {
        preferences.edit().putString(KEY_CONFIRMED_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "home_alarm_watch_state"
        const val KEY_CONFIRMED_MODE = "confirmed_mode"
    }
}
