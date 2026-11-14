package io.github.cataylor5.ibs

import kotlin.math.*
import java.util.Locale

/** Haversine metres, used unchanged for both ranking and display. */
internal fun straightLineMeters(from: NavigationTarget, to: BackendRestroom): Double {
    require(from.valid && to.hasCoordinates)
    return straightLineMeters(from, NavigationTarget(to.name.ifBlank { "Location" }, to.latitude!!, to.longitude!!))
}

internal fun straightLineMeters(from: NavigationTarget, to: NavigationTarget): Double {
    require(from.valid && to.valid)
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val delta = Math.toRadians(to.longitude - from.longitude)
    val a = (sin((lat2 - lat1) / 2).pow(2) +
        cos(lat1) * cos(lat2) * sin(delta / 2).pow(2)).coerceIn(0.0, 1.0)
    return 6_371_000 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Arrival duration and added detour duration are deliberately separate measurements. */
internal data class ReplacementStop(
    val restroom: BackendRestroom, val travelSeconds: Double?, val extraDrivingSeconds: Double? = null
) {
    val offRouteLabel: String? get() = extraDrivingSeconds?.takeIf { it.isFinite() && it >= 0 }?.let {
        val minutes = ceil(it / 60).toLong()
        "$minutes ${if (minutes == 1L) "minute" else "minutes"} off route"
    }
    val details: String get() {
        val travel = travelSeconds?.takeIf { it.isFinite() && it >= 0 }?.let {
            "${ceil(it / 60).toLong()} min away"
        } ?: "Travel time unavailable"
        val rating = if (restroom.ratingCount > 0 && restroom.averageRating != null)
            String.format(Locale.US, "%.1f/5", restroom.averageRating) else "No ratings yet"
        return "$travel • $rating"
    }
}

/** Home always applies a limit: added detour with a destination, arrival drive time without one. */
internal fun homeStops(
    rows: List<BackendRestroom>, minutes: Int, detours: Map<Long, Double>, travelSeconds: Map<Long, Double>,
    hasDestination: Boolean, preferences: RestroomPreferences
): List<ReplacementStop> = replacementStops(rows, null, false, minutes,
    if (hasDestination) detours else travelSeconds, travelSeconds, "",
    preferences = preferences, showDetour = hasDestination)

internal fun replacementStops(
    rows: List<BackendRestroom>, currentId: Long?, freeOnly: Boolean, minutes: Int?,
    detours: Map<Long, Double>, travelSeconds: Map<Long, Double>, query: String,
    enforceTimeLimit: Boolean = true,
    preferences: RestroomPreferences = RestroomPreferences(),
    showDetour: Boolean = enforceTimeLimit,
    resultLimit: Int = Int.MAX_VALUE
): List<ReplacementStop> {
    val currentBusiness = rows.firstOrNull { it.id == currentId }?.businessId
    return rows.asSequence()
        .filter { it.id != currentId && (currentBusiness == null || it.businessId != currentBusiness) &&
            it.canUseForRoute(freeOnly) && preferences.matches(it) && (!enforceTimeLimit || minutes == null || detourMatches(detours[it.id], minutes)) &&
            it.name.contains(query.trim(), ignoreCase = true) }
        .map { ReplacementStop(it, travelSeconds[it.id]?.takeIf { seconds -> seconds.isFinite() && seconds >= 0 },
            detours[it.id].takeIf { showDetour }) }
        .sortedWith(compareBy<ReplacementStop> { it.travelSeconds ?: Double.POSITIVE_INFINITY }
            .thenBy { it.restroom.id })
        .distinctBy { it.restroom.businessId ?: it.restroom.id }
        .take(resultLimit)
        .toList()
}
