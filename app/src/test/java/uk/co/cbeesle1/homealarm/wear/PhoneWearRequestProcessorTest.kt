package uk.co.cbeesle1.homealarm.wear

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearProtocol
import uk.co.cbeesle1.homealarm.common.WearRequest
import uk.co.cbeesle1.homealarm.domain.AlarmController
import uk.co.cbeesle1.homealarm.domain.AlarmGateway
import uk.co.cbeesle1.homealarm.domain.AlarmMode

class PhoneWearRequestProcessorTest {
    @Test
    fun statusRequestReturnsFreshPhoneState() = runTest {
        val controller = AlarmController(
            initialGateway = FixedGateway(AlarmMode.HOME),
            scope = backgroundScope,
            delayFunction = {},
            clock = { 123L },
        )
        val request = WearRequest.Status("status-1")

        val payload = PhoneWearRequestProcessor(controller).process(
            WearProtocol.STATUS_REQUEST_PATH,
            WearProtocol.encodeRequest(request),
        )
        val response = requireNotNull(WearProtocol.decodeResponse(WearProtocol.RESPONSE_PATH, requireNotNull(payload)))

        assertEquals("status-1", response.requestId)
        assertEquals(RemoteAlarmMode.HOME, response.confirmedMode)
        assertEquals(RemoteFreshness.CURRENT, response.freshness)
        assertFalse(response.requiresPhoneSetup)
    }

    @Test
    fun modeRequestUsesPhoneControllerAndReturnsConfirmedResult() = runTest {
        val gateway = RecordingGateway(AlarmMode.HOME)
        val controller = AlarmController(
            initialGateway = gateway,
            scope = backgroundScope,
            verificationDelaysMillis = listOf(0),
            delayFunction = {},
            clock = { 123L },
        )
        controller.refreshNow()
        val request = WearRequest.SetMode("mode-1", RemoteAlarmMode.DISARMED)

        val payload = PhoneWearRequestProcessor(controller).process(
            WearProtocol.MODE_REQUEST_PATH,
            WearProtocol.encodeRequest(request),
        )
        val response = requireNotNull(WearProtocol.decodeResponse(WearProtocol.RESPONSE_PATH, requireNotNull(payload)))

        assertEquals(AlarmMode.DISARMED, gateway.requestedMode)
        assertEquals(RemoteAlarmMode.DISARMED, response.confirmedMode)
        assertEquals(RemoteFreshness.CURRENT, response.freshness)
    }

    private class FixedGateway(private val mode: AlarmMode) : AlarmGateway {
        override suspend fun currentMode() = mode
        override suspend fun requestMode(mode: AlarmMode) = true
    }

    private class RecordingGateway(initialMode: AlarmMode) : AlarmGateway {
        private var mode = initialMode
        var requestedMode: AlarmMode? = null

        override suspend fun currentMode() = mode

        override suspend fun requestMode(mode: AlarmMode): Boolean {
            requestedMode = mode
            this.mode = mode
            return true
        }
    }
}
