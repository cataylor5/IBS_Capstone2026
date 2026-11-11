package io.github.cataylor5.ibs

import org.json.JSONException
import org.json.JSONObject

data class RoutePoint(val latitude: Double, val longitude: Double)

data class RouteStop(val id: Long, val name: String, val address: String?, val latitude: Double, val longitude: Double)

data class DrivingRoute(
    val origin: TripEndpoint,
    val destination: TripEndpoint,
    val distanceMeters: Long,
    val durationSeconds: Double,
    val encodedPolyline: String,
    val points: List<RoutePoint>,
    val stop: RouteStop? = null,
    val baseline: DrivingRoute? = null,
    val extraDrivingSeconds: Double? = null,
    val detourLimitMinutes: Int? = null
)

internal object DrivingRouteJson {
    fun decode(body: String, origin: TripEndpoint, destination: TripEndpoint): DrivingRoute {
        val json = JSONObject(body)
        val route = direct(json, origin, destination)
        if (!json.has("stop") || json.isNull("stop")) return route
        val stop = json.getJSONObject("stop")
        val id = stop.getLong("id")
        val name = stop.getString("name")
        val latitude = stop.getDouble("latitude")
        val longitude = stop.getDouble("longitude")
        val baselineJson = json.getJSONObject("baseline")
        if (baselineJson.has("stop")) throw JSONException("Nested stop baseline")
        val baseline = direct(baselineJson, origin, destination)
        val extra = json.getDouble("extraDrivingSeconds")
        val limit = json.getDouble("detourLimitMinutes")
        val expectedExtra = kotlin.math.max(0.0, route.durationSeconds - baseline.durationSeconds)
        if (id <= 0 || name.isBlank() || !latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0 ||
            !extra.isFinite() || extra < 0 || kotlin.math.abs(extra - expectedExtra) > 0.001 ||
            limit !in listOf(5.0, 10.0, 15.0) || extra > limit * 60) {
            throw JSONException("Invalid restroom detour")
        }
        return route.copy(
            stop = RouteStop(id, name, stop.opt("address").takeIf { it is String } as? String, latitude, longitude),
            baseline = baseline, extraDrivingSeconds = extra, detourLimitMinutes = limit.toInt()
        )
    }

    private fun direct(json: JSONObject, origin: TripEndpoint, destination: TripEndpoint): DrivingRoute {
        val distance = (json.opt("distanceMeters") as? Number)?.toDouble()
        val seconds = (json.opt("durationSeconds") as? Number)?.toDouble()
        val encoded = json.opt("encodedPolyline") as? String
        if (distance == null || !distance.isFinite() || distance < 0 || distance > Int.MAX_VALUE ||
            distance % 1.0 != 0.0 || seconds == null || !seconds.isFinite() || seconds < 0 ||
            encoded == null || encoded.isEmpty() || encoded.length > 200_000) {
            throw JSONException("Incomplete driving route")
        }
        val points = try { decodeRoutePolyline(encoded) }
            catch (_: IllegalArgumentException) { throw JSONException("Invalid route geometry") }
        if (points.size < 2) throw JSONException("Missing route geometry")
        return DrivingRoute(origin, destination, distance.toLong(), seconds, encoded, points)
    }
}

/** Google's encoded polyline format, five decimal places. Reject truncated or invalid geometry. */
internal fun decodeRoutePolyline(encoded: String): List<RoutePoint> {
    require(encoded.length <= 200_000)
    var index = 0
    var latitude = 0L
    var longitude = 0L
    val points = mutableListOf<RoutePoint>()
    fun delta(): Long {
        var value = 0L
        var shift = 0
        while (true) {
            require(index < encoded.length && shift <= 25)
            val chunk = encoded[index++].code - 63
            require(chunk in 0..63)
            value = value or ((chunk and 31).toLong() shl shift)
            if (chunk and 32 == 0) break
            shift += 5
        }
        return if (value and 1L == 0L) value shr 1 else (value shr 1).inv()
    }
    while (index < encoded.length) {
        latitude += delta()
        longitude += delta()
        require(latitude in -9_000_000L..9_000_000L && longitude in -18_000_000L..18_000_000L)
        points.add(RoutePoint(latitude / 100_000.0, longitude / 100_000.0))
    }
    return points
}

/** A late request may only complete the generation that started it. */
data class RouteState(
    val revision: Long = 0,
    val route: DrivingRoute? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val requestedStopId: Long? = null
) {
    fun begin(stopId: Long? = null, keepRoute: Boolean = false) = RouteState(
        revision = revision + 1, route = if (keepRoute) route else null,
        loading = true, requestedStopId = stopId
    )
    fun withoutStop(): RouteState =
        route?.baseline?.let { RouteState(revision = revision + 1, route = it) } ?: this
    fun clear() = RouteState(revision = revision + 1)
    fun complete(ticket: Long, result: DrivingRoute) =
        if (ticket != revision) this else copy(route = result, loading = false, error = null, requestedStopId = null)
    fun fail(ticket: Long, message: String) =
        if (ticket != revision) this else copy(loading = false, error = message, requestedStopId = null)
}
