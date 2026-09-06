package uk.co.cbeesle1.homealarm.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness

class HomeAlarmComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val cachedState = WearAlarmStateStore(applicationContext).readState()
        val snapshot = resolveComplicationSnapshot(
            responseMode = null,
            responseFreshness = null,
            cachedMode = cachedState?.mode,
            cachedFreshness = cachedState?.freshness,
        )
        return complicationData(request.complicationType, snapshot)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = complicationData(
        type,
        ComplicationSnapshot(RemoteAlarmMode.HOME, isCurrent = true),
    )

    private fun complicationData(
        type: ComplicationType,
        snapshot: ComplicationSnapshot,
    ): ComplicationData? {
        val modeText = snapshot.mode?.complicationLabel ?: "Alarm"
        val spokenText = when {
            snapshot.mode == null -> "Yale Smart Alarm Client. Open the app to check its state."
            snapshot.isCurrent -> "Yale Smart Alarm Client is ${snapshot.mode.spokenLabel}."
            else -> "Yale Smart Alarm Client was last confirmed as ${snapshot.mode.spokenLabel}."
        }
        val contentDescription = text(spokenText)
        val image = MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_complication),
        ).build()

        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = text(modeText),
                contentDescription = contentDescription,
            )
                .setTitle(text(if (snapshot.isCurrent) "Alarm" else "Last"))
                .setMonochromaticImage(image)
                .setTapAction(openAppAction())
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = text(
                    if (snapshot.isCurrent) {
                        "Yale Smart Alarm Client: $modeText"
                    } else {
                        "Yale Smart Alarm Client: $modeText (last)"
                    },
                ),
                contentDescription = contentDescription,
            )
                .setMonochromaticImage(image)
                .setTapAction(openAppAction())
                .build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = image,
                contentDescription = contentDescription,
            )
                .setTapAction(openAppAction())
                .build()

            else -> null
        }
    }

    private fun openAppAction(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun text(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

    companion object {
        internal fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, HomeAlarmComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}

internal data class ComplicationSnapshot(
    val mode: RemoteAlarmMode?,
    val isCurrent: Boolean,
)

internal fun resolveComplicationSnapshot(
    responseMode: RemoteAlarmMode?,
    responseFreshness: RemoteFreshness?,
    cachedMode: RemoteAlarmMode?,
    cachedFreshness: RemoteFreshness? = null,
): ComplicationSnapshot = when {
    responseMode != null -> ComplicationSnapshot(
        mode = responseMode,
        isCurrent = responseFreshness == RemoteFreshness.CURRENT,
    )

    cachedMode != null -> ComplicationSnapshot(
        cachedMode,
        isCurrent = cachedFreshness == RemoteFreshness.CURRENT,
    )
    else -> ComplicationSnapshot(mode = null, isCurrent = false)
}

private val RemoteAlarmMode.complicationLabel: String
    get() = when (this) {
        RemoteAlarmMode.AWAY -> "Away"
        RemoteAlarmMode.HOME -> "Home"
        RemoteAlarmMode.DISARMED -> "Disarm"
    }

private val RemoteAlarmMode.spokenLabel: String
    get() = when (this) {
        RemoteAlarmMode.AWAY -> "away"
        RemoteAlarmMode.HOME -> "home"
        RemoteAlarmMode.DISARMED -> "disarmed"
    }
