package uk.co.cbeesle1.homealarm.wear

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import uk.co.cbeesle1.homealarm.common.WearProtocol
import uk.co.cbeesle1.homealarm.common.WearRequest
import uk.co.cbeesle1.homealarm.common.WearResponse
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

interface PhoneAlarmTransport {
    suspend fun request(request: WearRequest): WearResponse
}

class DataLayerPhoneTransport(
    context: Context,
    private val responseTimeoutMillis: Long = DEFAULT_RESPONSE_TIMEOUT_MILLIS,
) :
    PhoneAlarmTransport,
    MessageClient.OnMessageReceivedListener,
    Closeable {
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val listenerRegistration = messageClient.addListener(this)
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<WearResponse>>()

    override suspend fun request(request: WearRequest): WearResponse {
        listenerRegistration.await()
        val node = nodeClient.connectedNodes.await()
            .sortedByDescending { it.isNearby }
            .firstOrNull()
            ?: error("Your phone is not connected.")
        val response = CompletableDeferred<WearResponse>()
        check(pendingResponses.putIfAbsent(request.requestId, response) == null)

        return try {
            messageClient.sendMessage(
                node.id,
                request.path,
                WearProtocol.encodeRequest(request),
            ).await()
            withTimeout(responseTimeoutMillis) { response.await() }
        } finally {
            pendingResponses.remove(request.requestId, response)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val response = WearProtocol.decodeResponse(event.path, event.data) ?: return
        pendingResponses.remove(response.requestId)?.complete(response)
    }

    override fun close() {
        messageClient.removeListener(this)
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()
    }

    private val WearRequest.path: String
        get() = when (this) {
            is WearRequest.Status -> WearProtocol.STATUS_REQUEST_PATH
            is WearRequest.SetMode -> WearProtocol.MODE_REQUEST_PATH
        }

    private companion object {
        const val DEFAULT_RESPONSE_TIMEOUT_MILLIS = 30_000L
    }
}
