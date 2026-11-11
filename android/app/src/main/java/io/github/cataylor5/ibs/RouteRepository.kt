package io.github.cataylor5.ibs

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal class RouteRequestException(
    val status: Int, val code: String?,
    val extraDrivingSeconds: Double? = null, val detourLimitMinutes: Int? = null
) : IOException()

internal class RouteRepository(
    private val baseUrl: String = BuildConfig.RESTROOM_API_BASE_URL
) {
    suspend fun compute(
        origin: TripEndpoint, destination: TripEndpoint,
        restroomId: Long? = null, maxDetourMinutes: Int = 10, freePublicOnly: Boolean = false
    ): DrivingRoute =
        withContext(Dispatchers.IO) {
            require(origin.isResolved && destination.isResolved)
            if (baseUrl.isBlank()) throw BackendNotConfiguredException()
            fun point(endpoint: TripEndpoint) = JSONObject()
                .put("latitude", endpoint.latitude).put("longitude", endpoint.longitude)
            val request = JSONObject().put("origin", point(origin)).put("destination", point(destination))
            if (restroomId != null) {
                require(restroomId > 0 && maxDetourMinutes in listOf(5, 10, 15))
                request.put("restroomId", restroomId).put("maxDetourMinutes", maxDetourMinutes)
                    .put("freePublicOnly", freePublicOnly)
            }
            val payload = request.toString().toByteArray(Charsets.UTF_8)
            val connection = URL(baseUrl.trimEnd('/') + "/api/routes").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 24_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    // Read only a known code; never show or log raw upstream bodies.
                    val failure = try {
                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { JSONObject(it.readText()) }
                    } catch (_: Exception) { null }
                    val code = failure?.optString("code")?.takeIf { value ->
                        value in setOf("INVALID_ENDPOINTS", "ROUTES_NOT_CONFIGURED", "ROUTES_ACCESS_DENIED",
                            "ROUTES_UPSTREAM_FAILED", "NO_ROUTE", "INVALID_ROUTE_RESPONSE",
                            "ROUTES_TIMEOUT", "ROUTES_UNAVAILABLE", "RESTROOM_LOOKUP_FAILED", "RESTROOM_NOT_FOUND",
                            "RESTROOM_COORDINATES_UNAVAILABLE", "RESTROOM_NOT_ELIGIBLE",
                            "INVALID_DETOUR_PREFERENCES", "DETOUR_LIMIT_EXCEEDED")
                    }
                    val extra = (failure?.opt("extraDrivingSeconds") as? Number)?.toDouble()
                        ?.takeIf { it.isFinite() && it >= 0 }
                    val limit = (failure?.opt("detourLimitMinutes") as? Number)?.toInt()
                        ?.takeIf { it in listOf(5, 10, 15) }
                    throw RouteRequestException(status, code, extra, limit)
                }
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                ensureActive()
                val result = DrivingRouteJson.decode(body, origin, destination)
                if (result.stop?.id != restroomId || (restroomId != null && result.detourLimitMinutes != maxDetourMinutes)) {
                    throw JSONException("Unexpected restroom stop")
                }
                result
            } finally {
                connection.disconnect()
            }
        }
}

internal fun routeErrorMessage(error: Exception): String = when (error) {
    is BackendNotConfiguredException -> "The route service isn't configured for this build."
    is RouteRequestException -> when (error.code) {
        "ROUTES_NOT_CONFIGURED" -> "Route calculation isn't configured yet. Set up the backend Routes API key, then retry."
        "ROUTES_ACCESS_DENIED" -> "Google denied route access. Check that Routes API is enabled and the backend key's restrictions and billing are correct."
        "NO_ROUTE" -> "No driving route was found. Choose another starting location or destination."
        "INVALID_ENDPOINTS" -> "Select a valid starting location and destination before creating a route."
        "ROUTES_TIMEOUT" -> "Route calculation timed out. Please try again."
        "RESTROOM_NOT_FOUND" -> "This restroom is no longer available. Refresh the list and choose another."
        "RESTROOM_LOOKUP_FAILED" -> "Couldn't look up this restroom. Please try again."
        "RESTROOM_COORDINATES_UNAVAILABLE" -> "This restroom has no usable location. Choose another stop."
        "RESTROOM_NOT_ELIGIBLE" -> "This restroom doesn't meet your access and fee preference. Choose another stop."
        "INVALID_DETOUR_PREFERENCES" -> "Choose a 5, 10 or 15 minute detour allowance and try again."
        "DETOUR_LIMIT_EXCEEDED" -> {
            val extra = error.extraDrivingSeconds
            val limit = error.detourLimitMinutes
            if (extra != null && limit != null)
                "This stop adds about ${kotlin.math.ceil(extra / 60).toLong()} minutes of driving, exceeding your $limit-minute limit. Choose another stop or increase the allowance."
            else "This stop exceeds your detour allowance. Choose another stop or increase the allowance."
        }
        else -> "Couldn't calculate the route (HTTP ${error.status}). Please try again."
    }
    is SocketTimeoutException -> "Route calculation timed out. Check your connection and try again."
    is JSONException -> "The route service returned incomplete route data. Please try again."
    else -> "Couldn't reach the route service. Check your connection and that the API server is running, then retry."
}
