package uk.co.cbeesle1.homealarm.wear

import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearResponse
import uk.co.cbeesle1.homealarm.domain.AlarmMode
import uk.co.cbeesle1.homealarm.domain.AlarmUiState
import uk.co.cbeesle1.homealarm.domain.ConfirmationFreshness

internal fun AlarmUiState.toWearResponse(requestId: String): WearResponse = WearResponse(
    requestId = requestId,
    confirmedMode = confirmedMode?.toRemoteMode(),
    freshness = freshness.toRemoteFreshness(),
    requiresPhoneSetup = requiresSetup,
    message = message,
)

internal fun RemoteAlarmMode.toPhoneMode(): AlarmMode = when (this) {
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
