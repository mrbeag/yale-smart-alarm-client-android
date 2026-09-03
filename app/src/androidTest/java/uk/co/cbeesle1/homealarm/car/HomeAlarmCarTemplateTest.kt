package uk.co.cbeesle1.homealarm.car

import androidx.car.app.model.GridTemplate
import androidx.car.app.model.GridItem
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.cbeesle1.homealarm.domain.AlarmController
import uk.co.cbeesle1.homealarm.domain.AlarmGateway
import uk.co.cbeesle1.homealarm.domain.AlarmMode

@RunWith(AndroidJUnit4::class)
class HomeAlarmCarTemplateTest {
    @Test
    fun productionValidatorIncludesTheSignedAndroidAutoHost() {
        val validator = createHomeAlarmHostValidator(
            context = ApplicationProvider.getApplicationContext(),
            allowUnknownHosts = false,
        )

        assertTrue(
            validator.allowedHosts["com.google.android.projection.gearhead"]
                .orEmpty()
                .isNotEmpty(),
        )
    }

    @Test
    fun gridShowsExactlyThreeModesAndMarksConfirmedMode() {
        val controller = AlarmController(
            initialGateway = object : AlarmGateway {
                override suspend fun currentMode() = AlarmMode.DISARMED
                override suspend fun requestMode(mode: AlarmMode) = true
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
        runBlocking { controller.refreshNow() }
        lateinit var template: GridTemplate
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
            template = HomeAlarmScreen(carContext, controller).onGetTemplate() as GridTemplate
        }

        val items = requireNotNull(template.singleList).items.map { it as GridItem }

        assertEquals(3, items.size)
        assertEquals("Arm Away", items[0].title.toString())
        assertEquals("Arm Home", items[1].title.toString())
        assertEquals("Disarmed", items[2].title.toString())
        assertTrue(items[2].text.toString().contains("Current"))
    }
}
