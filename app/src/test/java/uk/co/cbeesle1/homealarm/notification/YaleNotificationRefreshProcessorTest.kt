package uk.co.cbeesle1.homealarm.notification

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearResponse
import uk.co.cbeesle1.homealarm.domain.AlarmController
import uk.co.cbeesle1.homealarm.domain.AlarmGateway
import uk.co.cbeesle1.homealarm.domain.AlarmMode
import uk.co.cbeesle1.homealarm.wear.WearStatePublisher

class YaleNotificationRefreshProcessorTest {
    @Test
    fun ignoresNotificationsFromEveryOtherPackageAndOngoingYaleStatus() = runTest {
        val gateway = RecordingGateway()
        val published = mutableListOf<WearResponse>()
        val processor = processor(gateway, published)

        assertFalse(processor.process("com.example.other", isOngoing = false))
        assertFalse(processor.process(OFFICIAL_YALE_PACKAGE, isOngoing = true))
        assertEquals(0, gateway.statusReads)
        assertTrue(published.isEmpty())
    }

    @Test
    fun yaleEventPublishesOnlyFreshStateReadDirectlyFromYale() = runTest {
        val gateway = RecordingGateway(mode = AlarmMode.AWAY)
        val published = mutableListOf<WearResponse>()

        assertTrue(processor(gateway, published).process(OFFICIAL_YALE_PACKAGE, isOngoing = false))

        assertEquals(1, gateway.statusReads)
        assertEquals(1, published.size)
        assertEquals(RemoteAlarmMode.AWAY, published.single().confirmedMode)
        assertEquals(RemoteFreshness.CURRENT, published.single().freshness)
        assertEquals("notification-test", published.single().requestId)
    }

    @Test
    fun failedYaleReadNeverPublishesCachedStateAsCurrent() = runTest {
        val gateway = RecordingGateway(failRead = true)
        val published = mutableListOf<WearResponse>()

        assertFalse(processor(gateway, published).process(OFFICIAL_YALE_PACKAGE, isOngoing = false))

        assertEquals(1, gateway.statusReads)
        assertTrue(published.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.processor(
        gateway: RecordingGateway,
        published: MutableList<WearResponse>,
    ) = YaleNotificationRefreshProcessor(
        controller = AlarmController(
            initialGateway = gateway,
            scope = backgroundScope,
            delayFunction = {},
            clock = { 123L },
        ),
        publisher = WearStatePublisher { published += it },
        requestId = { "notification-test" },
    )

    private class RecordingGateway(
        private val mode: AlarmMode = AlarmMode.HOME,
        private val failRead: Boolean = false,
    ) : AlarmGateway {
        var statusReads = 0

        override suspend fun currentMode(): AlarmMode {
            statusReads += 1
            if (failRead) error("offline")
            return mode
        }

        override suspend fun requestMode(mode: AlarmMode) = true
    }
}
