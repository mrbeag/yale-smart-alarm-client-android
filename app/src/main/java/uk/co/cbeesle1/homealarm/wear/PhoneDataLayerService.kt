package uk.co.cbeesle1.homealarm.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import uk.co.cbeesle1.homealarm.HomeAlarmApplication
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearProtocol
import uk.co.cbeesle1.homealarm.common.WearRequest
import uk.co.cbeesle1.homealarm.common.WearResponse
import uk.co.cbeesle1.homealarm.domain.AlarmController
import uk.co.cbeesle1.homealarm.domain.AlarmMode
import uk.co.cbeesle1.homealarm.domain.ConfirmationFreshness

class PhoneDataLayerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val application = applicationContext as HomeAlarmApplication
        val processor = PhoneWearRequestProcessor(application.controller)
        application.applicationScope.launch {
            val response = processor.process(messageEvent.path, messageEvent.data) ?: return@launch
            Wearable.getMessageClient(applicationContext).sendMessage(
                messageEvent.sourceNodeId,
                WearProtocol.RESPONSE_PATH,
                response,
            )
        }
    }

}

internal class PhoneWearRequestProcessor(
    private val controller: AlarmController,
) {
    suspend fun process(path: String, payload: ByteArray): ByteArray? {
        val request = WearProtocol.decodeRequest(path, payload) ?: return null
        when (request) {
            is WearRequest.Status -> controller.refreshNow()
            is WearRequest.SetMode -> {
                if (controller.state.value.confirmedMode == null) controller.refreshNow()
                controller.requestModeNow(request.mode.toPhoneMode())
            }
        }
        val state = controller.state.value
        return WearProtocol.encodeResponse(
            WearResponse(
                requestId = request.requestId,
                confirmedMode = state.confirmedMode?.toRemoteMode(),
                freshness = state.freshness.toRemoteFreshness(),
                requiresPhoneSetup = state.requiresSetup,
                message = state.message,
            ),
        )
    }
}

private fun RemoteAlarmMode.toPhoneMode(): AlarmMode = when (this) {
    RemoteAlarmMode.AWAY -> AlarmMode.AWAY
    RemoteAlarmMode.HOME -> AlarmMode.HOME
    RemoteAlarmMode.DISARMED -> AlarmMode.DISARMED
}

private fun AlarmMode.toRemoteMode(): RemoteAlarmMode = when (this) {
    AlarmMode.AWAY -> RemoteAlarmMode.AWAY
    AlarmMode.HOME -> RemoteAlarmMode.HOME
    AlarmMode.DISARMED -> RemoteAlarmMode.DISARMED
}

private fun ConfirmationFreshness.toRemoteFreshness(): RemoteFreshness = when (this) {
    ConfirmationFreshness.CURRENT -> RemoteFreshness.CURRENT
    ConfirmationFreshness.LAST_CONFIRMED -> RemoteFreshness.LAST_CONFIRMED
    ConfirmationFreshness.UNKNOWN -> RemoteFreshness.UNKNOWN
}
