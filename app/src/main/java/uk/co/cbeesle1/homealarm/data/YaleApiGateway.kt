// Yale API integration adapted into Kotlin from domwillcode/yale-smart-alarm-client.
// Upstream: https://github.com/domwillcode/yale-smart-alarm-client (Apache-2.0).
// Modified for Android HTTP requests and persisted session updates; see NOTICE.md
// and licenses/yale-smart-alarm-client-APACHE-2.0.txt for attribution and licence.
package uk.co.cbeesle1.homealarm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.co.cbeesle1.homealarm.domain.AlarmGateway
import uk.co.cbeesle1.homealarm.domain.AlarmAuthenticationRequiredException
import uk.co.cbeesle1.homealarm.domain.AlarmMode
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class YaleApiGateway private constructor(
    private var session: YaleSession,
    private val clientBasicCredential: String,
    private val onSessionChanged: (YaleSession) -> Unit,
    private var accessToken: String? = null,
) : AlarmGateway {

    override suspend fun currentMode(): AlarmMode = withContext(Dispatchers.IO) {
        val response = authenticatedRequest("GET", "/api/panel/mode/")
        val data = response.optJSONArray("data")
            ?: throw YaleApiException("Yale returned no alarm state")
        if (data.length() == 0) throw YaleApiException("Yale returned no alarm state")
        AlarmMode.fromApiValue(data.getJSONObject(0).getString("mode"))
    }

    override suspend fun requestMode(mode: AlarmMode): Boolean = withContext(Dispatchers.IO) {
        val response = authenticatedRequest(
            method = "POST",
            path = "/api/panel/mode/",
            form = mapOf("area" to session.areaId.toString(), "mode" to mode.apiValue),
        )
        response.optString("code") == "000"
    }

    private fun authenticatedRequest(
        method: String,
        path: String,
        form: Map<String, String>? = null,
        retryAuthentication: Boolean = true,
    ): JSONObject {
        ensureAccessToken()
        return try {
            request(
                method = method,
                url = joinUrl(session.host, path),
                headers = mapOf("Authorization" to "Bearer ${accessToken.orEmpty()}"),
                form = form,
            )
        } catch (error: YaleHttpException) {
            if (retryAuthentication && error.statusCode in listOf(401, 403)) {
                accessToken = null
                refreshAccessToken()
                authenticatedRequest(method, path, form, retryAuthentication = false)
            } else {
                throw error
            }
        }
    }

    private fun ensureAccessToken() {
        if (accessToken == null) refreshAccessToken()
    }

    private fun refreshAccessToken() {
        val tokens = requestTokens(
            host = session.host,
            fields = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to session.refreshToken,
            ),
        )
        updateTokens(tokens)
        updateServiceHost()
    }

    private fun updateServiceHost() {
        val services = authenticatedRequest(
            method = "GET",
            path = "/services/",
            retryAuthentication = false,
        )
        val candidate = services.optString("yapi").trim().trimEnd('/')
        if (candidate.isNotEmpty() && URI(candidate).scheme == "https") {
            session = session.copy(host = candidate)
            onSessionChanged(session)
        }
    }

    private fun requestTokens(host: String, fields: Map<String, String>): JSONObject = try {
        request(
            method = "POST",
            url = joinUrl(host, "/o/token/"),
            headers = mapOf("Authorization" to "Basic $clientBasicCredential"),
            form = fields,
        )
    } catch (error: YaleHttpException) {
        if (error.statusCode in listOf(401, 403)) throw AlarmAuthenticationRequiredException()
        throw error
    }

    private fun updateTokens(tokens: JSONObject) {
        accessToken = tokens.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw YaleAuthenticationException("Yale returned no access token")
        val refreshToken = tokens.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: throw YaleAuthenticationException("Yale returned no refresh token")
        session = session.copy(refreshToken = refreshToken)
        onSessionChanged(session)
    }

    private fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        form: Map<String, String>? = null,
    ): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
            connection.readTimeout = REQUEST_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach(connection::setRequestProperty)
            if (form != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { output ->
                    output.write(encodeForm(form).toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw YaleHttpException(status)
            return JSONObject(body)
        } catch (error: YaleApiException) {
            throw error
        } catch (error: IOException) {
            throw YaleApiException("Could not contact Yale", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeForm(fields: Map<String, String>): String = fields.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, Charsets.UTF_8.name())}=" +
            URLEncoder.encode(it.value, Charsets.UTF_8.name())
    }

    private fun joinUrl(host: String, path: String): String =
        "${host.trimEnd('/')}/${path.trimStart('/')}"

    companion object {
        private const val DEFAULT_HOST = "https://mob.yalehomesystem.co.uk/yapi"
        private const val REQUEST_TIMEOUT_MILLIS = 7_000

        fun fromSession(
            session: YaleSession,
            clientBasicCredential: String,
            onSessionChanged: (YaleSession) -> Unit,
        ): YaleApiGateway = YaleApiGateway(session, clientBasicCredential, onSessionChanged)

        suspend fun signIn(
            email: String,
            password: String,
            areaId: Int,
            clientBasicCredential: String,
            onSessionChanged: (YaleSession) -> Unit,
        ): YaleApiGateway = withContext(Dispatchers.IO) {
            val provisional = YaleSession(refreshToken = "", host = DEFAULT_HOST, areaId = areaId)
            val gateway = YaleApiGateway(provisional, clientBasicCredential, onSessionChanged)
            val tokens = try {
                gateway.requestTokens(
                    host = DEFAULT_HOST,
                    fields = mapOf(
                        "grant_type" to "password",
                        "username" to email,
                        "password" to password,
                    ),
                )
            } catch (_: AlarmAuthenticationRequiredException) {
                throw YaleAuthenticationException("Yale rejected the email or password.")
            }
            gateway.updateTokens(tokens)
            gateway.updateServiceHost()
            gateway
        }
    }
}

open class YaleApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class YaleAuthenticationException(message: String) : YaleApiException(message)
private class YaleHttpException(val statusCode: Int) : YaleApiException(
    if (statusCode in listOf(401, 403)) "Yale sign-in has expired" else "Yale returned HTTP $statusCode",
)
