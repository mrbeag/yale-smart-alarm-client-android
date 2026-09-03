package uk.co.cbeesle1.homealarm.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import uk.co.cbeesle1.homealarm.HomeAlarmApplication
import uk.co.cbeesle1.homealarm.common.WearProtocol
import uk.co.cbeesle1.homealarm.common.WearRequest
import uk.co.cbeesle1.homealarm.domain.AlarmController

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
        return WearProtocol.encodeResponse(state.toWearResponse(request.requestId))
    }
}
