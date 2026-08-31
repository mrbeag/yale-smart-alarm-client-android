package uk.co.cbeesle1.homealarm.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    private lateinit var store: SecureSessionStore

    @Before
    fun setUp() {
        store = SecureSessionStore(ApplicationProvider.getApplicationContext())
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun credentialsAreEncryptedAndRestoredForTheNextLogin() {
        val credentials = YaleCredentials(
            email = "person@example.com",
            password = "correct horse battery staple",
            areaId = 1,
        )

        store.saveCredentials(credentials)

        assertEquals(credentials, store.loadCredentials())
        assertNull(store.load())
        val storedValues = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("encrypted_yale_session", android.content.Context.MODE_PRIVATE)
            .all.values.joinToString()
        assertFalse(storedValues.contains(credentials.email))
        assertFalse(storedValues.contains(credentials.password))
    }

    @Test
    fun clearingOnlyTheSessionPreservesSavedLoginDetails() {
        val credentials = YaleCredentials(
            email = "person@example.com",
            password = "correct horse battery staple",
            areaId = 1,
        )
        store.saveCredentials(credentials)
        store.save(
            YaleSession(
                refreshToken = "refresh-token",
                host = "https://example.com/yapi",
                areaId = 1,
            ),
        )

        store.clearSession()

        assertNull(store.load())
        assertEquals(credentials, store.loadCredentials())
    }
}
