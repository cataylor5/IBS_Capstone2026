package io.github.cataylor5.ibs

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

internal data class TripEta(val duration: String, val distanceAndArrival: String)

internal fun tripEta(seconds: Long, meters: Long, now: Instant,
    zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault(),
    use24HourClock: Boolean = false): TripEta? {
    if (seconds < 0 || meters < 0) return null
    val minutes = ceil(seconds / 60.0).toLong()
    val duration = if (minutes < 60) "$minutes min" else
        "${minutes / 60} hr" + if (minutes % 60 == 0L) "" else " ${minutes % 60} min"
    val miles = meters / 1609.344
    val imperial = locale.country in setOf("US", "GB", "LR", "MM")
    val distance = if (imperial) {
        if (miles < 0.1) "${(meters * 3.28084).toInt()} ft"
        else String.format(locale, if (miles < 9.95) "%.1f mi" else "%.0f mi", miles)
    } else {
        if (meters < 1000) "$meters m" else String.format(locale, "%.1f km", meters / 1000.0)
    }
    val arrival = DateTimeFormatter.ofPattern(if (use24HourClock) "HH:mm" else "h:mm a", locale)
        .withZone(zone).format(now.plusSeconds(seconds))
    return TripEta(duration, "$distance • $arrival")
}

internal data class NavigationHeader(val finalDestination: String?, val restroomStop: String?)

/** A business-only trip still routes to its endpoint, but belongs under Restroom stop in the header. */
internal fun navigationHeader(plan: NavigationPlan, finalIsRestroom: Boolean): NavigationHeader =
    NavigationHeader(
        finalDestination = plan.destination?.name.takeUnless { finalIsRestroom },
        restroomStop = (plan.restroom ?: plan.destination.takeIf { finalIsRestroom })?.name
    )

internal data class ArrivedRestroom(val id: Long, val name: String)

internal fun arrivedRestroom(plan: NavigationPlan, progress: NavigationProgress,
    stopId: Long?, finalRestroomId: Long?): ArrivedRestroom? = when {
    progress.atRestroom -> stopId?.takeIf { it > 0 }?.let { id ->
        plan.restroom?.let { ArrivedRestroom(id, it.name) }
    }
    progress.arrived -> finalRestroomId?.takeIf { it > 0 }?.let { id ->
        plan.destination?.let { ArrivedRestroom(id, it.name) }
    }
    else -> null
}

internal data class NavigationMapPin(val target: NavigationTarget, val restroom: Boolean)

internal fun navigationMapPins(plan: NavigationPlan, finalIsRestroom: Boolean): List<NavigationMapPin> =
    listOfNotNull(plan.restroom?.let { NavigationMapPin(it, true) },
        plan.destination?.let { NavigationMapPin(it, finalIsRestroom) })
        .groupBy { it.target.latitude to it.target.longitude }
        .values.map { pins -> pins.firstOrNull { it.restroom } ?: pins.first() }

internal fun needsFullRouteBounds(segmentCount: Int, finalSeconds: Long?) =
    segmentCount > 1 || finalSeconds == null || finalSeconds > 45 * 60
