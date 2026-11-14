package io.github.cataylor5.ibs

/** One eligible restroom per business, choosing the smallest measured driving detour. */
internal fun matchingBusinesses(
    restrooms: List<BackendRestroom>, seconds: Map<Long, Double>, minutes: Int, freeOnly: Boolean
): List<BackendRestroom> = restrooms
    .filter { it.canUseForRoute(freeOnly) && detourMatches(seconds[it.id], minutes) }
    .sortedWith(compareBy<BackendRestroom> { seconds[it.id] }.thenBy { it.id })
    .distinctBy { it.businessId ?: it.id }
