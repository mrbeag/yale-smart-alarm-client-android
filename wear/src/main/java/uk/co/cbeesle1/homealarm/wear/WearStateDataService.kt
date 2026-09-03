package uk.co.cbeesle1.homealarm.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearProtocol

internal fun confirmedModeFromStateUpdate(path: String, payload: ByteArray): RemoteAlarmMode? {
    val response = WearProtocol.decodeState(path, payload) ?: return null
    if (response.freshness != RemoteFreshness.CURRENT || response.requiresPhoneSetup) return null
    return response.confirmedMode
}

internal object WearStateUpdates {
    private val mutableConfirmedModes = MutableSharedFlow<RemoteAlarmMode>(extraBufferCapacity = 1)
    val confirmedModes = mutableConfirmedModes.asSharedFlow()

    fun publish(mode: RemoteAlarmMode) {
        mutableConfirmedModes.tryEmit(mode)
    }
}

class WearStateDataService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var changed = false
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            val payload = DataMapItem.fromDataItem(item).dataMap
                .getByteArray(WearProtocol.STATE_PAYLOAD_KEY)
                ?: continue
            val mode = confirmedModeFromStateUpdate(item.uri.path.orEmpty(), payload) ?: continue
            WearAlarmStateStore(applicationContext).writeConfirmedMode(
                mode = mode,
                freshness = RemoteFreshness.CURRENT,
            )
            WearStateUpdates.publish(mode)
            changed = true
        }
        if (changed) HomeAlarmComplicationService.requestUpdate(applicationContext)
    }
}
