package uk.co.cbeesle1.homealarm.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import uk.co.cbeesle1.homealarm.HomeAlarmApplication
import uk.co.cbeesle1.homealarm.domain.AlarmController
import uk.co.cbeesle1.homealarm.domain.ConfirmationFreshness
import uk.co.cbeesle1.homealarm.wear.DataLayerWearStatePublisher
import uk.co.cbeesle1.homealarm.wear.WearStatePublisher
import uk.co.cbeesle1.homealarm.wear.toWearResponse

internal const val OFFICIAL_YALE_PACKAGE = "com.mobilepeople.yale.yalehome"

internal fun shouldRefreshFromNotification(packageName: String, isOngoing: Boolean): Boolean =
    packageName == OFFICIAL_YALE_PACKAGE && !isOngoing

internal class YaleNotificationRefreshProcessor(
    private val controller: AlarmController,
    private val publisher: WearStatePublisher,
    private val requestId: () -> String = { "notification-${UUID.randomUUID()}" },
) {
    suspend fun process(packageName: String, isOngoing: Boolean): Boolean {
        if (!shouldRefreshFromNotification(packageName, isOngoing)) return false
        if (!controller.refreshNow()) return false

        val state = controller.state.value
        if (state.confirmedMode == null || state.freshness != ConfirmationFreshness.CURRENT) {
            return false
        }

        publisher.publish(state.toWearResponse(requestId()))
        return true
    }
}

class YaleNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationTriggers = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        val application = applicationContext as HomeAlarmApplication
        val processor = YaleNotificationRefreshProcessor(
            controller = application.controller,
            publisher = DataLayerWearStatePublisher(applicationContext),
        )
        serviceScope.launch {
            for (ignored in notificationTriggers) {
                runCatching {
                    processor.process(OFFICIAL_YALE_PACKAGE, isOngoing = false)
                }
            }
        }
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        if (shouldRefreshFromNotification(notification.packageName, notification.isOngoing)) {
            notificationTriggers.trySend(Unit)
        }
    }

    override fun onDestroy() {
        notificationTriggers.close()
        serviceScope.cancel()
        super.onDestroy()
    }
}
