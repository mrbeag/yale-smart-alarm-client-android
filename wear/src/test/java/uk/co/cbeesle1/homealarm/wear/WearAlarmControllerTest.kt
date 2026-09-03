package uk.co.cbeesle1.homealarm.wear

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.cbeesle1.homealarm.common.RemoteAlarmMode
import uk.co.cbeesle1.homealarm.common.RemoteFreshness
import uk.co.cbeesle1.homealarm.common.WearRequest
import uk.co.cbeesle1.homealarm.common.WearResponse

class WearAlarmControllerTest {
    @Test
    fun openingSurfaceRetriesOneTransientFailure() = runTest {
        var calls = 0
        var delays = 0
        val controller = WearAlarmController(
            transport = object : PhoneAlarmTransport {
                override suspend fun request(request: WearRequest): WearResponse {
                    calls++
                    if (calls == 1) error("Phone temporarily unavailable")
                    return response(request, RemoteAlarmMode.HOME)
                }
            },
            scope = backgroundScope,
            delayFunction = { delays++ },
            requestIdFactory = { "request-$calls" },
        )

        controller.refreshOnSurfaceStartNow()

        assertEquals(2, calls)
        assertEquals(1, delays)
        assertEquals(RemoteAlarmMode.HOME, controller.state.value.confirmedMode)
        assertEquals(RemoteFreshness.CURRENT, controller.state.value.freshness)
        assertNull(controller.state.value.message)
    }

    @Test
    fun pendingModeDoesNotReplaceConfirmedMode() = runTest {
        val modeResponse = CompletableDeferred<WearResponse>()
        var requestCount = 0
        val controller = WearAlarmController(
            transport = object : PhoneAlarmTransport {
                override suspend fun request(request: WearRequest): WearResponse {
                    requestCount++
                    return if (request is WearRequest.Status) {
                        response(request, RemoteAlarmMode.HOME)
                    } else {
                        modeResponse.await()
                    }
                }
            },
            scope = backgroundScope,
            delayFunction = {},
            requestIdFactory = { "request-${requestCount + 1}" },
        )
        controller.refreshNow()

        val operation = async { controller.requestModeNow(RemoteAlarmMode.DISARMED) }
        testScheduler.runCurrent()

        assertEquals(RemoteAlarmMode.HOME, controller.state.value.confirmedMode)
        assertEquals(RemoteAlarmMode.DISARMED, controller.state.value.pendingMode)
        assertFalse(operation.isCompleted)

        modeResponse.complete(
            WearResponse(
                requestId = "request-2",
                confirmedMode = RemoteAlarmMode.DISARMED,
                freshness = RemoteFreshness.CURRENT,
                requiresPhoneSetup = false,
                message = null,
            ),
        )
        operation.await()

        assertEquals(RemoteAlarmMode.DISARMED, controller.state.value.confirmedMode)
        assertNull(controller.state.value.pendingMode)
    }

    @Test
    fun phoneSetupResponseIsNotRetried() = runTest {
        var calls = 0
        val controller = WearAlarmController(
            transport = object : PhoneAlarmTransport {
                override suspend fun request(request: WearRequest): WearResponse {
                    calls++
                    return WearResponse(
                        requestId = request.requestId,
                        confirmedMode = null,
                        freshness = RemoteFreshness.UNKNOWN,
                        requiresPhoneSetup = true,
                        message = "Reconnect on your phone.",
                    )
                }
            },
            scope = backgroundScope,
            delayFunction = { error("Setup response must not be retried") },
        )

        controller.refreshOnSurfaceStartNow()

        assertEquals(1, calls)
        assertTrue(controller.state.value.requiresPhoneSetup)
    }

    private fun response(request: WearRequest, mode: RemoteAlarmMode) = WearResponse(
        requestId = request.requestId,
        confirmedMode = mode,
        freshness = RemoteFreshness.CURRENT,
        requiresPhoneSetup = false,
        message = null,
    )
}
