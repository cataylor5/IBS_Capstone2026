package io.github.cataylor5.ibs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class ReviewSaveException(val userMessage: String) : IOException()

/** Private, durable cookie shared by review requests; identity is issued only by the backend. */
internal class RestroomReviewRepository(context: Context,
    private val baseUrl: String = BuildConfig.RESTROOM_API_BASE_URL) {
    private val cookies = context.applicationContext.getSharedPreferences("ibs_review_session", Context.MODE_PRIVATE)
    private val cookieKey = "cookie_" + baseUrl.hashCode()

    suspend fun context(id: Long): RestroomReviewContext {
        val json = request(id, null)
        require(json.getLong("restroom_id") == id)
        return RestroomReviewContext(json.getString("business_name"), when (json.optString("access_type")) {
            "PUBLIC" -> "Public access"
            "CUSTOMER_ONLY" -> "Patrons only"
            "MEMBERSHIP_REQUIRED" -> "Membership required"
            "EMPLOYEE_ONLY" -> "Employees only"
            else -> "Unknown"
        }, when (json.optString("restroom_type")) {
            "SINGLE_STALL", "FAMILY" -> "Private restroom"
            "MULTI_STALL" -> "Stalls"
            else -> "Unknown"
        })
    }

    suspend fun submit(id: Long, arrivalId: String, demo: Boolean, draft: RestroomReviewDraft) {
        val result = request(id, draft.payload(arrivalId, demo))
        if (!result.optBoolean("saved") || result.getBoolean("demo") != demo)
            throw ReviewSaveException("IBS could not confirm the save. Retry with the same answers.")
    }

    private suspend fun request(id: Long, body: String?): JSONObject = requestLock.withLock {
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) throw ReviewSaveException("The review service is not configured for this build.")
            require(id > 0)
            val connection = URL(baseUrl.trimEnd('/') + "/api/restrooms/$id/arrival-review")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.connectTimeout = 8000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                cookies.getString(cookieKey, null)?.let { connection.setRequestProperty("Cookie", it) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }
                    .flatMap { it.value }.firstOrNull { it.startsWith("connect.sid=") }?.let {
                        // Persist before submitting so a lost response/process recreation retains the same reviewer.
                        cookies.edit().putString(cookieKey, it.substringBefore(';')).commit()
                    }
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                ensureActive()
                if (status !in 200..299) throw ReviewSaveException(when (status) {
                    404 -> "This restroom is no longer available. Return to your trip."
                    409 -> "This arrival was already saved with different answers. Return to your trip."
                    503 -> "Review storage is unavailable. Check the backend review database setup, then retry."
                    401 -> "Your guest session expired. Retry to reconnect."
                    else -> "IBS could not save the review (HTTP $status). Please retry."
                })
                JSONObject(text)
            } finally { connection.disconnect() }
        }
    }
    companion object { private val requestLock = Mutex() }
}
