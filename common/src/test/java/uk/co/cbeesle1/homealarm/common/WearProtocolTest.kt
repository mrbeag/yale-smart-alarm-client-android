package uk.co.cbeesle1.homealarm.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearProtocolTest {
    @Test
    fun statusRequestRoundTrips() {
        val request = WearRequest.Status("request-1")

        assertEquals(
            request,
            WearProtocol.decodeRequest(WearProtocol.STATUS_REQUEST_PATH, WearProtocol.encodeRequest(request)),
        )
    }

    @Test
    fun modeRequestRoundTrips() {
        val request = WearRequest.SetMode("request-2", RemoteAlarmMode.DISARMED)

        assertEquals(
            request,
            WearProtocol.decodeRequest(WearProtocol.MODE_REQUEST_PATH, WearProtocol.encodeRequest(request)),
        )
    }

    @Test
    fun responseRoundTripsEscapedMessage() {
        val response = WearResponse(
            requestId = "request-3",
            confirmedMode = RemoteAlarmMode.HOME,
            freshness = RemoteFreshness.LAST_CONFIRMED,
            requiresPhoneSetup = false,
            message = "Phone & Yale: temporarily unavailable",
        )

        assertEquals(
            response,
            WearProtocol.decodeResponse(WearProtocol.RESPONSE_PATH, WearProtocol.encodeResponse(response)),
        )
    }

    @Test
    fun unknownPathIsRejected() {
        assertNull(WearProtocol.decodeRequest("/other", WearProtocol.encodeRequest(WearRequest.Status("id"))))
    }
}
