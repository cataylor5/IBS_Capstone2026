package io.github.cataylor5.ibs

import org.json.JSONObject

internal data class RestroomReviewDraft(
    val overall: Int? = null,
    val comment: String = "",
    val accessChoice: String? = null,
    val privacyChoice: String? = null
) {
    val valid: Boolean get() = overall in 1..5 && comment.length <= 4000 &&
        (accessChoice == null || accessChoice in setOf("PUBLIC", "CUSTOMER_ONLY", "MEMBERSHIP_REQUIRED", "NOT_SURE")) &&
        (privacyChoice == null || privacyChoice in setOf("PRIVATE", "STALLS", "NOT_SURE"))
    fun payload(arrivalId: String, demo: Boolean): String {
        require(valid)
        return JSONObject().put("submissionId", arrivalId).put("demo", demo).put("overall", overall)
            .put("comment", comment.trim().ifBlank { null } ?: JSONObject.NULL)
            .put("accessType", accessChoice?.takeUnless { it == "NOT_SURE" } ?: JSONObject.NULL)
            .put("privacyType", privacyChoice?.takeUnless { it == "NOT_SURE" } ?: JSONObject.NULL)
            .put("accessAnswered", accessChoice != null).put("privacyAnswered", privacyChoice != null).toString()
    }
}

internal data class RestroomReviewContext(val name: String, val access: String, val layout: String)
