package uk.co.cbeesle1.homealarm.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class YaleSession(
    val refreshToken: String,
    val host: String,
    val areaId: Int,
)

data class YaleCredentials(
    val email: String,
    val password: String,
    val areaId: Int,
)

class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(session: YaleSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = JSONObject()
            .put("refreshToken", session.refreshToken)
            .put("host", session.host)
            .put("areaId", session.areaId)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)

        preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): YaleSession? {
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val encodedIv = preferences.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val json = JSONObject(
                cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
                    .toString(Charsets.UTF_8),
            )
            YaleSession(
                refreshToken = json.getString("refreshToken"),
                host = json.getString("host"),
                areaId = json.getInt("areaId"),
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun saveCredentials(credentials: YaleCredentials) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = JSONObject()
            .put("email", credentials.email)
            .put("password", credentials.password)
            .put("areaId", credentials.areaId)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)

        preferences.edit()
            .putString(CREDENTIALS_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(CREDENTIALS_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadCredentials(): YaleCredentials? {
        val encodedCiphertext = preferences.getString(CREDENTIALS_CIPHERTEXT, null) ?: return null
        val encodedIv = preferences.getString(CREDENTIALS_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val json = JSONObject(
                cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
                    .toString(Charsets.UTF_8),
            )
            YaleCredentials(
                email = json.getString("email"),
                password = json.getString("password"),
                areaId = json.getInt("areaId"),
            )
        }.getOrElse {
            preferences.edit()
                .remove(CREDENTIALS_CIPHERTEXT)
                .remove(CREDENTIALS_IV)
                .apply()
            null
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "encrypted_yale_session"
        const val KEY_CIPHERTEXT = "session_ciphertext"
        const val KEY_IV = "session_iv"
        const val CREDENTIALS_CIPHERTEXT = "credentials_ciphertext"
        const val CREDENTIALS_IV = "credentials_iv"
        const val KEY_ALIAS = "home_alarm_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
