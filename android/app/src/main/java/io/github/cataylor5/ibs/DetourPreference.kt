package io.github.cataylor5.ibs

internal val DetourMinuteOptions = listOf(5, 10, 15, 20, 25, 30)

/** Unknown estimates are not presented as matching; comparisons use unrounded seconds. */
internal fun detourMatches(seconds: Double?, minutes: Int): Boolean =
    seconds != null && seconds.isFinite() && seconds >= 0 && seconds <= minutes * 60.0
