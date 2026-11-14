package io.github.cataylor5.ibs

enum class PrivacyPreference { PRIVATE, STALLS }

/** All dimensions are checked against one restroom before business deduplication. */
data class RestroomPreferences(
    val access: Set<RestroomAccess> = emptySet(),
    val minimumStars: Int? = null,
    val privacy: PrivacyPreference? = null
) {
    fun matches(row: BackendRestroom): Boolean {
        if (!row.canUseForRoute(false) || (access.isNotEmpty() && row.access !in access)) return false
        val minimum = when (minimumStars) { 5 -> 4.6; 4 -> 4.0; 3 -> 3.0; 2 -> 2.0; 1 -> 1.0; else -> null }
        if (minimum != null) {
            // Preserve PostgreSQL numeric precision at boundaries such as 4.6.
            val average = row.exactAverageRating ?: row.averageRating?.takeIf { it.isFinite() }?.toBigDecimal()
            if (row.ratingCount == 0 || average == null || average < minimum.toBigDecimal()) return false
        }
        return when (privacy) {
            PrivacyPreference.PRIVATE -> row.restroomType in setOf("SINGLE_STALL", "FAMILY")
            PrivacyPreference.STALLS -> row.restroomType == "MULTI_STALL"
            null -> true
        }
    }
}
