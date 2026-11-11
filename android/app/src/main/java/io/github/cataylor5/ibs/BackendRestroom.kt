package io.github.cataylor5.ibs

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class RestroomAccess { PUBLIC, CUSTOMER_ONLY, EMPLOYEE_ONLY, UNKNOWN }

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
    val ratingCount: Int
) {
    // Unknown fees and purchase requirements must never be treated as confirmed free.
    val isConfirmedFreePublic: Boolean
        get() = access == RestroomAccess.PUBLIC && feeRequired == false && requiresPurchase == false

    val hasCoordinates: Boolean get() =
        latitude != null && latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude != null && longitude.isFinite() && longitude in -180.0..180.0

    fun canUseForRoute(freePublicOnly: Boolean): Boolean =
        hasCoordinates && access in listOf(RestroomAccess.PUBLIC, RestroomAccess.CUSTOMER_ONLY) &&
            (!freePublicOnly || isConfirmedFreePublic)

    val accessLabel: String get() = when (access) {
        RestroomAccess.PUBLIC -> "Public access"
        RestroomAccess.CUSTOMER_ONLY -> "Customers only"
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
                "EMPLOYEE_ONLY" -> RestroomAccess.EMPLOYEE_ONLY
                else -> RestroomAccess.UNKNOWN
            },
            json.value("fee_required") as? Boolean,
            json.value("requires_purchase") as? Boolean,
            json.value("requires_code") as? Boolean,
            json.value("accessible") as? Boolean,
            json.value("verified") as? Boolean,
            json.number("average_rating")?.takeIf { it in 1.0..5.0 },
            json.number("rating_count")?.toInt()?.coerceAtLeast(0) ?: 0
        )
    }

    private fun JSONObject.value(key: String): Any? = opt(key).takeUnless { it == JSONObject.NULL }
    private fun JSONObject.number(key: String): Double? =
        value(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
