package uk.co.cbeesle1.homealarm.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import uk.co.cbeesle1.homealarm.HomeAlarmApplication
import uk.co.cbeesle1.homealarm.common.WearProtocol
import uk.co.cbeesle1.homealarm.common.WearRequest
import uk.co.cbeesle1.homealarm.domain.AlarmController

class PhoneDataLayerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val application = applicationContext as HomeAlarmApplication
        val processor = PhoneWearRequestProcessor(application.controller)
        BlockingPhoneWearRequestRunner(
            process = processor::process,
            sendResponse = { nodeId, response ->
                Wearable.getMessageClient(applicationContext).sendMessage(
                    nodeId,
                    WearProtocol.RESPONSE_PATH,
                    response,
                ).await()
            },
        ).handle(
            path = messageEvent.path,
            payload = messageEvent.data,
            sourceNodeId = messageEvent.sourceNodeId,
        )
    }
}

/**
 * Keeps the listener callback alive until the Yale operation and reply are complete.
 * WearableListenerService callbacks run on a background thread and the system may
 * unbind the service as soon as the callback returns.
 */
internal class BlockingPhoneWearRequestRunner(
    private val process: suspend (String, ByteArray) -> ByteArray?,
    private val sendResponse: suspend (String, ByteArray) -> Unit,
) {
    fun handle(path: String, payload: ByteArray, sourceNodeId: String) = runBlocking {
        val response = process(path, payload) ?: return@runBlocking
        sendResponse(sourceNodeId, response)
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
