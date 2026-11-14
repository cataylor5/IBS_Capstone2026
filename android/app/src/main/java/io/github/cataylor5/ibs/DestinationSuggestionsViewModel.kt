package io.github.cataylor5.ibs

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import kotlinx.coroutines.*

internal data class DestinationPrediction(val id: String, val text: String)

internal data class DestinationPredictions(
    val revision: Long = 0, val query: String = "",
    val rows: List<DestinationPrediction> = emptyList(),
    val loading: Boolean = false, val error: String? = null
) {
    fun begin(text: String) = DestinationPredictions(revision + 1, text, loading = text.trim().length >= 2)
    fun complete(ticket: Long, rows: List<DestinationPrediction>) =
        if (ticket != revision) this else copy(rows = rows, loading = false, error = null)
    fun fail(ticket: Long, message: String) =
        if (ticket != revision) this else copy(rows = emptyList(), loading = false, error = message)
}

class DestinationSuggestionsViewModel(application: Application) : AndroidViewModel(application) {
    internal var state by mutableStateOf(DestinationPredictions())
        private set
    private val service = EndpointLocationService(application)
    private var job: Job? = null
    private var session: AutocompleteSessionToken? = null

    internal fun search(text: String, retry: Boolean = false) {
        if (state.query == text && !retry) return
        state = state.begin(text)
        job?.cancel()
        if (!state.loading) { session = null; return }
        val ticket = state.revision
        job = viewModelScope.launch {
            try {
                delay(300)
                val token = session ?: AutocompleteSessionToken.newInstance().also { session = it }
                val rows = withTimeout(15_000) { service.predictions(text.trim(), token) }
                state = state.complete(ticket, rows)
            } catch (_: TimeoutCancellationException) {
                state = state.fail(ticket, "Destination search timed out. Retry.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { state = state.fail(ticket, placesFailureMessage(failure)) }
        }
    }

    internal fun select(row: DestinationPrediction): AutocompleteSessionToken? {
        if (row !in state.rows || state.loading) return null
        val token = session
        clear()
        return token
    }

    internal fun clear() {
        state = state.begin("")
        job?.cancel()
        session = null
    }
}
