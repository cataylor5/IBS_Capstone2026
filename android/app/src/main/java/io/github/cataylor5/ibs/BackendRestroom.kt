package io.github.cataylor5.ibs

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class RestroomAccess { PUBLIC, CUSTOMER_ONLY, MEMBERSHIP_REQUIRED, EMPLOYEE_ONLY, UNKNOWN }

data class BackendRestroom(
    val id: Long,
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val access: RestroomAccess,
    val feeRequired: Boolean?,
    val requiresPurchase: Boolean?,
    val requiresCode: Boolean?,
    val accessible: Boolean?,
    val verified: Boolean?,
    val averageRating: Double?,
    val ratingCount: Int,
    val businessId: Long? = null,
    val businessHours: List<BusinessHours> = emptyList(),
    val restroomType: String? = null,
    val exactAverageRating: java.math.BigDecimal? = null
) {
    // Unknown fees and purchase requirements must never be treated as confirmed free.
    val isConfirmedFreePublic: Boolean
        get() = access == RestroomAccess.PUBLIC && feeRequired == false && requiresPurchase == false

    val hasCoordinates: Boolean get() =
        latitude != null && latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude != null && longitude.isFinite() && longitude in -180.0..180.0

    fun canUseForRoute(freePublicOnly: Boolean): Boolean =
        hasCoordinates && access in listOf(RestroomAccess.PUBLIC, RestroomAccess.CUSTOMER_ONLY, RestroomAccess.MEMBERSHIP_REQUIRED) &&
            (!freePublicOnly || isConfirmedFreePublic)

    val privacyLabel: String get() = when (restroomType) {
        "SINGLE_STALL", "FAMILY" -> "Private"
        "MULTI_STALL" -> "Stalls"
        else -> "Privacy unknown"
    }

    val accessLabel: String get() = when (access) {
        RestroomAccess.PUBLIC -> "Public access"
        RestroomAccess.CUSTOMER_ONLY -> "Customers only"
        RestroomAccess.MEMBERSHIP_REQUIRED -> "Membership required"
        RestroomAccess.EMPLOYEE_ONLY -> "Employees only"
        RestroomAccess.UNKNOWN -> "Access unknown"
    }
    val feeLabel: String get() = when (feeRequired) {
        true -> "Restroom fee required"
        false -> "No separate restroom fee"
        null -> "Restroom fee unknown"
    }
}

/** Matches Express's bare-array responses and PostgreSQL numeric-string fields. */
internal object RestroomJson {
    fun list(body: String): List<BackendRestroom> {
        val array = JSONArray(body)
        return List(array.length()) { index -> decode(array.getJSONObject(index)) }
    }

    fun detail(body: String): BackendRestroom = decode(JSONObject(body))

    private fun decode(json: JSONObject): BackendRestroom {
        val id = json.value("restroom_id")?.toString()?.toLongOrNull()
            ?.takeIf { it > 0 } ?: throw JSONException("Invalid restroom ID")
        val name = json.value("business_name") as? String
        if (name.isNullOrBlank()) throw JSONException("Missing business name")
        val latitude = json.number("latitude")?.takeIf { it in -90.0..90.0 }
        val longitude = json.number("longitude")?.takeIf { it in -180.0..180.0 }
        return BackendRestroom(
            id, name, (json.value("address") as? String)?.takeIf { it.isNotBlank() },
            latitude, longitude,
            when (json.value("access_type")) {
                "PUBLIC" -> RestroomAccess.PUBLIC
                "CUSTOMER_ONLY" -> RestroomAccess.CUSTOMER_ONLY
                "MEMBERSHIP_REQUIRED" -> RestroomAccess.MEMBERSHIP_REQUIRED
                "EMPLOYEE_ONLY" -> RestroomAccess.EMPLOYEE_ONLY
                else -> RestroomAccess.UNKNOWN
            },
            json.value("fee_required") as? Boolean,
            json.value("requires_purchase") as? Boolean,
            json.value("requires_code") as? Boolean,
            json.value("accessible") as? Boolean,
            json.value("verified") as? Boolean,
            json.number("average_rating")?.takeIf { it in 1.0..5.0 },
            json.number("rating_count")?.toInt()?.coerceAtLeast(0) ?: 0,
            json.value("business_id")?.toString()?.toLongOrNull(),
            json.optJSONArray("business_hours")?.let { hours ->
                List(hours.length()) { index ->
                    val item = hours.optJSONObject(index)
                    BusinessHours((item?.opt("iso_weekday") as? Number)?.toInt() ?: 0,
                        item?.value("opens_at") as? String, item?.value("closes_at") as? String)
                }
            } ?: emptyList(),
            json.value("restroom_type") as? String,
            json.value("average_rating")?.toString()?.toBigDecimalOrNull()?.takeIf {
                it >= java.math.BigDecimal.ONE && it <= java.math.BigDecimal("5")
            }
        )
    }

    private fun JSONObject.value(key: String): Any? = opt(key).takeUnless { it == JSONObject.NULL }
    private fun JSONObject.number(key: String): Double? =
        value(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
