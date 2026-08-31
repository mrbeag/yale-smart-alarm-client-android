package uk.co.cbeesle1.homealarm

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.cbeesle1.homealarm.data.SecureSessionStore
import uk.co.cbeesle1.homealarm.data.YaleApiGateway
import uk.co.cbeesle1.homealarm.data.YaleCredentials
import uk.co.cbeesle1.homealarm.data.YaleSession
import uk.co.cbeesle1.homealarm.domain.AlarmController

class HomeAlarmApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessionStore: SecureSessionStore
    lateinit var controller: AlarmController
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SecureSessionStore(this)
        val storedSession = sessionStore.load()
        val realGateway = storedSession
            ?.takeIf { BuildConfig.YALE_BASIC_AUTH.isNotBlank() }
            ?.let(::gatewayFromSession)
        controller = AlarmController(
            initialGateway = realGateway,
            scope = applicationScope,
        )
        controller.refresh()
    }

    suspend fun connectYale(email: String, password: String, areaId: Int): Result<Unit> = runCatching {
        require(BuildConfig.YALE_BASIC_AUTH.isNotBlank()) {
            "Real Yale login is not enabled in this build. Add YALE_BASIC_AUTH when building."
        }
        require(email.isNotBlank()) { "Enter your Yale email address." }
        require(password.isNotBlank()) { "Enter your Yale password." }
        require(areaId > 0) { "Area must be 1 or greater." }

        val gateway = YaleApiGateway.signIn(
            email = email.trim(),
            password = password,
            areaId = areaId,
            clientBasicCredential = BuildConfig.YALE_BASIC_AUTH,
            onSessionChanged = sessionStore::save,
        )
        sessionStore.saveCredentials(
            YaleCredentials(email = email.trim(), password = password, areaId = areaId),
        )
        controller.replaceGateway(gateway, retryInitialReadOnce = true)
    }

    fun loadSavedCredentials(): YaleCredentials? = sessionStore.loadCredentials()

    suspend fun disconnect() {
        sessionStore.clear()
        controller.replaceGateway(null)
    }

    private fun gatewayFromSession(session: YaleSession): YaleApiGateway = YaleApiGateway.fromSession(
        session = session,
        clientBasicCredential = BuildConfig.YALE_BASIC_AUTH,
        onSessionChanged = sessionStore::save,
    )
}
