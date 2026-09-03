package uk.co.cbeesle1.homealarm.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmControllerTest {
    @Test
    fun requestedModeStaysSelectingUntilFreshStatusConfirmsIt() = runTest {
        val confirmation = CompletableDeferred<AlarmMode>()
        var reads = 0
        val gateway = object : AlarmGateway {
            override suspend fun currentMode(): AlarmMode = if (reads++ == 0) {
                AlarmMode.DISARMED
            } else {
                confirmation.await()
            }

            override suspend fun requestMode(mode: AlarmMode) = true
        }
        val controller = controller(gateway)
        controller.refreshNow()

        val operation = async { controller.requestModeNow(AlarmMode.AWAY) }
        runCurrent()

        assertEquals(AlarmMode.DISARMED, controller.state.value.confirmedMode)
        assertEquals(AlarmMode.AWAY, controller.state.value.pendingMode)
        assertEquals(ModeButtonState.ACTIVE, controller.state.value.buttonState(AlarmMode.DISARMED))
        assertEquals(ModeButtonState.SELECTING, controller.state.value.buttonState(AlarmMode.AWAY))

        confirmation.complete(AlarmMode.AWAY)
        advanceUntilIdle()
        operation.await()

        assertEquals(AlarmMode.AWAY, controller.state.value.confirmedMode)
        assertNull(controller.state.value.pendingMode)
        assertEquals(ConfirmationFreshness.CURRENT, controller.state.value.freshness)
    }

    @Test
    fun unchangedSuccessfulStatusKeepsPreviousModeCurrent() = runTest {
        val gateway = FixedGateway(AlarmMode.DISARMED)
        val controller = controller(gateway)
        controller.refreshNow()

        controller.requestModeNow(AlarmMode.AWAY)

        assertEquals(AlarmMode.DISARMED, controller.state.value.confirmedMode)
        assertNull(controller.state.value.pendingMode)
        assertEquals(ConfirmationFreshness.CURRENT, controller.state.value.freshness)
        assertTrue(controller.state.value.message.orEmpty().contains("not confirmed"))
    }

    @Test
    fun failedVerificationPreservesPreviousModeAsLastConfirmed() = runTest {
        var reads = 0
        val gateway = object : AlarmGateway {
            override suspend fun currentMode(): AlarmMode {
                if (reads++ == 0) return AlarmMode.HOME
                error("offline")
            }

            override suspend fun requestMode(mode: AlarmMode) = true
        }
        val controller = controller(gateway)
        controller.refreshNow()

        controller.requestModeNow(AlarmMode.AWAY)

        assertEquals(AlarmMode.HOME, controller.state.value.confirmedMode)
        assertEquals(ConfirmationFreshness.LAST_CONFIRMED, controller.state.value.freshness)
        assertNull(controller.state.value.pendingMode)
    }

    @Test
    fun differentReportedModeWinsOverRequestedAndPreviousModes() = runTest {
        var reads = 0
        val gateway = object : AlarmGateway {
            override suspend fun currentMode(): AlarmMode = if (reads++ == 0) {
                AlarmMode.DISARMED
            } else {
                AlarmMode.HOME
            }

            override suspend fun requestMode(mode: AlarmMode) = true
        }
        val controller = controller(gateway)
        controller.refreshNow()

        controller.requestModeNow(AlarmMode.AWAY)

        assertEquals(AlarmMode.HOME, controller.state.value.confirmedMode)
        assertEquals(ConfirmationFreshness.CURRENT, controller.state.value.freshness)
    }

    @Test
    fun rejectedCommandChecksStatusAndKeepsReportedMode() = runTest {
        val gateway = FixedGateway(AlarmMode.DISARMED, requestAccepted = false)
        val controller = controller(gateway)
        controller.refreshNow()

        controller.requestModeNow(AlarmMode.AWAY)

        assertEquals(AlarmMode.DISARMED, controller.state.value.confirmedMode)
        assertNull(controller.state.value.pendingMode)
        assertEquals("Yale did not accept the change.", controller.state.value.message)
    }

    @Test
    fun expiredSessionRequiresPhoneSetupAndPreservesLastConfirmedMode() = runTest {
        var reads = 0
        val gateway = object : AlarmGateway {
            override suspend fun currentMode(): AlarmMode {
                if (reads++ == 0) return AlarmMode.DISARMED
                throw AlarmAuthenticationRequiredException()
            }

            override suspend fun requestMode(mode: AlarmMode) = true
        }
        val controller = controller(gateway)
        controller.refreshNow()

        controller.requestModeNow(AlarmMode.AWAY)

        assertTrue(controller.state.value.requiresSetup)
        assertEquals(AlarmMode.DISARMED, controller.state.value.confirmedMode)
        assertEquals(ConfirmationFreshness.LAST_CONFIRMED, controller.state.value.freshness)
        assertNull(controller.state.value.pendingMode)
    }

    @Test
    fun newConnectionRetriesOneTransientInitialStatusFailure() = runTest {
        var reads = 0
        val gateway = object : AlarmGateway {
            override suspend fun currentMode(): AlarmMode {
                if (reads++ == 0) error("initial read not ready")
                return AlarmMode.DISARMED
            }

            override suspend fun requestMode(mode: AlarmMode) = true
        }
        val controller = AlarmController(
            initialGateway = null,
            scope = backgroundScope,
            verificationDelaysMillis = listOf(0),
            delayFunction = {},
            clock = { 123L },
        )

        controller.replaceGateway(gateway, retryInitialReadOnce = true)

        assertEquals(2, reads)
        assertEquals(AlarmMode.DISARMED, controller.state.value.confirmedMode)
        assertEquals(ConfirmationFreshness.CURRENT, controller.state.value.freshness)
        assertNull(controller.state.value.message)
    }

    @Test
    fun openingSurfaceRetriesOneTransientStatusFailure() = runTest {
        var reads = 0
        val gateway = object : AlarmGateway {
            override suspend fun currentMode(): AlarmMode {
                if (reads++ == 0) error("network not ready")
                return AlarmMode.HOME
            }

            override suspend fun requestMode(mode: AlarmMode) = true
        }
        val controller = AlarmController(
            initialGateway = gateway,
            scope = backgroundScope,
            verificationDelaysMillis = listOf(0),
            delayFunction = {},
            clock = { 123L },
        )

        controller.refreshOnSurfaceStartNow()

        assertEquals(2, reads)
        assertEquals(AlarmMode.HOME, controller.state.value.confirmedMode)
        assertEquals(ConfirmationFreshness.CURRENT, controller.state.value.freshness)
        assertNull(controller.state.value.message)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(gateway: AlarmGateway) = AlarmController(
        initialGateway = gateway,
        scope = backgroundScope,
        verificationDelaysMillis = listOf(0, 0, 0),
        delayFunction = {},
        clock = { 123L },
    )

    private class FixedGateway(
        private val mode: AlarmMode,
        private val requestAccepted: Boolean = true,
    ) : AlarmGateway {
        override suspend fun currentMode() = mode
        override suspend fun requestMode(mode: AlarmMode) = requestAccepted
    }
}
