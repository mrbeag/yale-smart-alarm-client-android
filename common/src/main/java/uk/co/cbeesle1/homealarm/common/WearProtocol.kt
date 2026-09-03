package uk.co.cbeesle1.homealarm.common

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class RemoteAlarmMode {
    AWAY,
    HOME,
    DISARMED,
}

enum class RemoteFreshness {
    CURRENT,
    LAST_CONFIRMED,
    UNKNOWN,
}

sealed interface WearRequest {
    val requestId: String

    data class Status(override val requestId: String) : WearRequest

    data class SetMode(
        override val requestId: String,
        val mode: RemoteAlarmMode,
    ) : WearRequest
}

data class WearResponse(
    val requestId: String,
    val confirmedMode: RemoteAlarmMode?,
    val freshness: RemoteFreshness,
    val requiresPhoneSetup: Boolean,
    val message: String?,
)

object WearProtocol {
    const val STATUS_REQUEST_PATH = "/home-alarm/v1/request/status"
    const val MODE_REQUEST_PATH = "/home-alarm/v1/request/mode"
    const val RESPONSE_PATH = "/home-alarm/v1/response"

    fun encodeRequest(request: WearRequest): ByteArray = when (request) {
        is WearRequest.Status -> fields(
            "requestId" to request.requestId,
        )

        is WearRequest.SetMode -> fields(
            "requestId" to request.requestId,
            "mode" to request.mode.name,
        )
    }.toByteArray(StandardCharsets.UTF_8)

    fun decodeRequest(path: String, payload: ByteArray): WearRequest? {
        val values = parse(payload)
        val requestId = values["requestId"]?.takeIf(String::isNotBlank) ?: return null
        return when (path) {
            STATUS_REQUEST_PATH -> WearRequest.Status(requestId)
            MODE_REQUEST_PATH -> values["mode"]
                ?.let { runCatching { RemoteAlarmMode.valueOf(it) }.getOrNull() }
                ?.let { WearRequest.SetMode(requestId, it) }

            else -> null
        }
    }

    fun encodeResponse(response: WearResponse): ByteArray = fields(
        "requestId" to response.requestId,
        "confirmedMode" to response.confirmedMode?.name.orEmpty(),
        "freshness" to response.freshness.name,
        "requiresPhoneSetup" to response.requiresPhoneSetup.toString(),
        "message" to response.message.orEmpty(),
    ).toByteArray(StandardCharsets.UTF_8)

    fun decodeResponse(path: String, payload: ByteArray): WearResponse? {
        if (path != RESPONSE_PATH) return null
        val values = parse(payload)
        val requestId = values["requestId"]?.takeIf(String::isNotBlank) ?: return null
        val mode = values["confirmedMode"]
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { RemoteAlarmMode.valueOf(it) }.getOrNull() }
        val freshness = values["freshness"]
            ?.let { runCatching { RemoteFreshness.valueOf(it) }.getOrNull() }
            ?: return null
        return WearResponse(
            requestId = requestId,
            confirmedMode = mode,
            freshness = freshness,
            requiresPhoneSetup = values["requiresPhoneSetup"] == "true",
            message = values["message"]?.takeIf(String::isNotBlank),
        )
    }

    private fun fields(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }

    private fun parse(payload: ByteArray): Map<String, String> = payload
        .toString(StandardCharsets.UTF_8)
        .split('&')
        .mapNotNull { field ->
            val separator = field.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            decode(field.substring(0, separator)) to decode(field.substring(separator + 1))
        }
        .toMap()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
