package uk.co.cbeesle1.homealarm.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uk.co.cbeesle1.homealarm.BuildConfig
import uk.co.cbeesle1.homealarm.HomeAlarmApplication
import uk.co.cbeesle1.homealarm.R
import uk.co.cbeesle1.homealarm.domain.AlarmController
import uk.co.cbeesle1.homealarm.domain.AlarmMode
import uk.co.cbeesle1.homealarm.domain.AlarmUiState
import uk.co.cbeesle1.homealarm.domain.ConfirmationFreshness
import uk.co.cbeesle1.homealarm.domain.ModeButtonState

class HomeAlarmCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = if (BuildConfig.DEBUG) {
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    } else {
        HostValidator.Builder(applicationContext)
            .addAllowedHosts(R.array.hosts_allowlist)
            .build()
    }

    override fun onCreateSession(): Session = HomeAlarmSession()
}

private class HomeAlarmSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val application = carContext.applicationContext as HomeAlarmApplication
        return HomeAlarmScreen(carContext, application.controller)
    }
}

internal class HomeAlarmScreen(
    carContext: CarContext,
    private val controller: AlarmController,
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch {
            controller.state.collectLatest { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val state = controller.state.value
        if (state.requiresSetup) {
            return MessageTemplate.Builder("Open Home Alarm on your phone and finish secure Yale setup.")
                .setTitle("Finish setup on phone")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        if (state.confirmedMode == null) {
            return MessageTemplate.Builder(
                if (state.isRefreshing) "Checking the current alarm mode…" else "Current alarm state is unavailable.",
            )
                .setTitle("Home Alarm")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setOnClickListener(controller::refresh)
                        .build(),
                )
                .build()
        }

        val items = ItemList.Builder()
        AlarmMode.entries.forEach { mode -> items.addItem(modeGridItem(mode, state)) }
        return GridTemplate.Builder()
            .setTitle("Home Alarm")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .build()
    }

    private fun modeGridItem(mode: AlarmMode, state: AlarmUiState): GridItem {
        val visualState = state.buttonState(mode)
        val status = when {
            visualState == ModeButtonState.SELECTING -> "Switching…"
            visualState == ModeButtonState.ACTIVE &&
                state.freshness == ConfirmationFreshness.LAST_CONFIRMED -> "✓ Last confirmed"
            visualState == ModeButtonState.ACTIVE -> "✓ Current"
            else -> "Not active"
        }
        val iconResource = when (mode) {
            AlarmMode.AWAY -> R.drawable.ic_away
            AlarmMode.HOME -> R.drawable.ic_home
            AlarmMode.DISARMED -> R.drawable.ic_disarmed
        }
        val builder = GridItem.Builder()
            .setTitle(mode.title)
            .setText(status)
            .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, iconResource)).build())

        if (state.commandsEnabled && visualState == ModeButtonState.INACTIVE) {
            builder.setOnClickListener { controller.requestMode(mode) }
        }
        return builder.build()
    }
}
