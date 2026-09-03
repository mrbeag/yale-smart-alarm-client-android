package uk.co.cbeesle1.homealarm.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness

class HomeAlarmComplicationStateTest {
    @Test
    fun freshPhoneStateWinsOverCachedState() {
        val snapshot = resolveComplicationSnapshot(
            responseMode = RemoteAlarmMode.AWAY,
            responseFreshness = RemoteFreshness.CURRENT,
            cachedMode = RemoteAlarmMode.HOME,
        )

        assertEquals(RemoteAlarmMode.AWAY, snapshot.mode)
        assertTrue(snapshot.isCurrent)
    }

    @Test
    fun cachedStateIsExplicitlyStaleWhenPhoneCannotBeReached() {
        val snapshot = resolveComplicationSnapshot(
            responseMode = null,
            responseFreshness = null,
            cachedMode = RemoteAlarmMode.DISARMED,
        )

        assertEquals(RemoteAlarmMode.DISARMED, snapshot.mode)
        assertFalse(snapshot.isCurrent)
    }
}
