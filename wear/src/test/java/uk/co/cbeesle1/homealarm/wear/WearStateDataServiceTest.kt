package uk.co.cbeesle1.homealarm.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearProtocol
import uk.co.cbeesle1.homealarm.common.WearResponse

class WearStateDataServiceTest {
    @Test
    fun acceptsOnlyCurrentConfirmedStateOnTheStatePath() {
        val payload = WearProtocol.encodeResponse(
            WearResponse(
                requestId = "notification-1",
                confirmedMode = RemoteAlarmMode.DISARMED,
                freshness = RemoteFreshness.CURRENT,
                requiresPhoneSetup = false,
                message = null,
            ),
        )

        assertEquals(
            RemoteAlarmMode.DISARMED,
            confirmedModeFromStateUpdate(WearProtocol.STATE_PATH, payload),
        )
        assertNull(confirmedModeFromStateUpdate(WearProtocol.RESPONSE_PATH, payload))
    }

    @Test
    fun rejectsStaleOrSetupRequiredState() {
        fun payload(freshness: RemoteFreshness, requiresSetup: Boolean) = WearProtocol.encodeResponse(
            WearResponse(
                requestId = "notification-2",
                confirmedMode = RemoteAlarmMode.AWAY,
                freshness = freshness,
                requiresPhoneSetup = requiresSetup,
                message = null,
            ),
        )

        assertNull(
            confirmedModeFromStateUpdate(
                WearProtocol.STATE_PATH,
                payload(RemoteFreshness.LAST_CONFIRMED, requiresSetup = false),
            ),
        )
        assertNull(
            confirmedModeFromStateUpdate(
                WearProtocol.STATE_PATH,
                payload(RemoteFreshness.CURRENT, requiresSetup = true),
            ),
        )
    }
}
