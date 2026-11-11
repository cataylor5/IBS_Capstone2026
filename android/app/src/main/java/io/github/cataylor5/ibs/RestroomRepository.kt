package io.github.cataylor5.ibs

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException

interface RestroomRepository {
    suspend fun list(): List<BackendRestroom>
    suspend fun details(id: Long): BackendRestroom
}

internal class BackendHttpException(val status: Int) : IOException()
internal class BackendNotConfiguredException : IOException()

internal class HttpRestroomRepository(
    private val baseUrl: String = BuildConfig.RESTROOM_API_BASE_URL
) : RestroomRepository {
    override suspend fun list(): List<BackendRestroom> =
        request("api/restrooms", RestroomJson::list)

    override suspend fun details(id: Long): BackendRestroom {
        require(id > 0)
        return request("api/restrooms/$id", RestroomJson::detail)
    }

    private suspend fun <T> request(path: String, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) throw BackendNotConfiguredException()
            val connection = URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                if (status !in 200..299) throw BackendHttpException(status)
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                ensureActive()
                decode(body)
            } finally {
                connection.disconnect()
            }
        }
}

internal fun restroomErrorMessage(error: Exception): String = when (error) {
    is BackendNotConfiguredException -> "The restroom service isn't configured for this build."
    is BackendHttpException -> if (error.status == 404) "This restroom is no longer available."
        else "IBS couldn't load the data (HTTP ${error.status}). Please try again."
    is SocketTimeoutException -> "IBS took too long to respond. Check your connection and try again."
    is JSONException -> "IBS returned an unexpected response. Please try again."
    else -> "Couldn't reach IBS. Check your connection and that the API server is running, then retry."
}
