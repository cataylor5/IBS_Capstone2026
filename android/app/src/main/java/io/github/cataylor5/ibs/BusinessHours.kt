package io.github.cataylor5.ibs

import java.time.*
import kotlin.math.ceil

data class BusinessHours(val isoWeekday: Int, val opensAt: String?, val closesAt: String?)

/** Weekly New York wall-clock schedule. Never uses the Navigation SDK's simulation clock. */
internal fun businessHoursCountdown(hours: List<BusinessHours>, now: Instant = Instant.now()): String {
    val unavailable = "Hours unavailable"
    if (hours.isEmpty()) return unavailable
    val zone = ZoneId.of("America/New_York")
    val today = now.atZone(zone).toLocalDate()
    data class Window(val start: Instant, val end: Instant)
    val windows = mutableListOf<Window>()
    try {
        for (row in hours) {
            if (row.isoWeekday !in 1..7) return unavailable
            if (row.opensAt == null && row.closesAt == null) continue // Explicit closed day.
            if (row.opensAt == null || row.closesAt == null) return unavailable
            val opening = LocalTime.parse(row.opensAt)
            val midnightEnd = row.closesAt.matches(Regex("24:00(?::00(?:\\.0+)?)?"))
            val closing = if (midnightEnd) LocalTime.MIDNIGHT else LocalTime.parse(row.closesAt)
            if (!midnightEnd && opening == closing) return unavailable // Ambiguous 24h/closed.
            for (offset in -1L..14L) {
                val date = today.plusDays(offset)
                if (date.dayOfWeek.value != row.isoWeekday) continue
                val closeDate = if (midnightEnd || closing < opening) date.plusDays(1) else date
                val start = date.atTime(opening).atZone(zone).withEarlierOffsetAtOverlap().toInstant()
                val end = closeDate.atTime(closing).atZone(zone).withLaterOffsetAtOverlap().toInstant()
                if (end <= start) return unavailable
                windows += Window(start, end)
            }
        }
    } catch (_: DateTimeException) { return unavailable }

    // Adjacent/overlapping shifts form one opening; don't announce a false closing between shifts.
    val merged = mutableListOf<Window>()
    for (window in windows.sortedBy { it.start }) {
        val previous = merged.lastOrNull()
        if (previous != null && window.start <= previous.end) {
            merged[merged.lastIndex] = Window(previous.start, maxOf(previous.end, window.end))
        } else merged += window
    }
    val active = merged.firstOrNull { now >= it.start && now < it.end }
    val event = active?.end ?: merged.firstOrNull { it.start > now }?.start ?: return unavailable
    if (active != null && event > now.plus(Duration.ofDays(8))) return unavailable // No weekly closing.
    val minutes = ceil(Duration.between(now, event).toMillis() / 60_000.0).toLong().coerceAtLeast(1)
    val days = minutes / 1440
    val hr = minutes % 1440 / 60
    val min = minutes % 60
    val remaining = listOfNotNull(
        if (days > 0) "$days day${if (days == 1L) "" else "s"}" else null,
        if (hr > 0) "$hr hr" else null,
        if (min > 0) "$min min" else null
    ).joinToString(" ")
    return "${if (active != null) "Closes" else "Opens"} in $remaining"
}
