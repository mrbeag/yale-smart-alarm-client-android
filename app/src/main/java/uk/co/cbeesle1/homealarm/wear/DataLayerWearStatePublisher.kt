package uk.co.cbeesle1.homealarm.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import uk.co.cbeesle1.homealarm.common.WearProtocol
import uk.co.cbeesle1.homealarm.common.WearResponse

internal fun interface WearStatePublisher {
    suspend fun publish(response: WearResponse)
}

internal class DataLayerWearStatePublisher(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : WearStatePublisher {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(response: WearResponse) {
        val dataMapRequest = PutDataMapRequest.create(WearProtocol.STATE_PATH).apply {
            dataMap.putByteArray(
                WearProtocol.STATE_PAYLOAD_KEY,
                WearProtocol.encodeResponse(response),
            )
            dataMap.putLong(WearProtocol.STATE_UPDATED_AT_KEY, clock())
        }
        dataClient.putDataItem(dataMapRequest.asPutDataRequest().setUrgent()).await()
    }
}
