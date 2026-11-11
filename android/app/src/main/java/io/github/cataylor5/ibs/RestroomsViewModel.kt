package io.github.cataylor5.ibs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RestroomsViewModel internal constructor(
    private val saved: SavedStateHandle,
    private val repository: RestroomRepository
) : ViewModel() {
    constructor(saved: SavedStateHandle) : this(saved, HttpRestroomRepository())

    var restrooms by mutableStateOf<List<BackendRestroom>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    private var listJob: Job? = null

    var selectedId by mutableStateOf(saved.get<Long>("restroom_details_id"))
        private set
    var detail by mutableStateOf<BackendRestroom?>(null)
        private set
    var detailLoading by mutableStateOf(false)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set
    private var detailJob: Job? = null
    private var detailRevision = 0L

    fun ensureLoaded() {
        if (!hasLoaded && !loading) refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        listJob = viewModelScope.launch {
            try {
                restrooms = repository.list()
                hasLoaded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = restroomErrorMessage(failure)
            } finally {
                loading = false
            }
        }
    }

    fun showDetails(id: Long) {
        detailRevision++
        val revision = detailRevision
        detailJob?.cancel()
        selectedId = id
        saved["restroom_details_id"] = id
        detail = null
        detailError = null
        detailLoading = true
        detailJob = viewModelScope.launch {
            try {
                val result = repository.details(id)
                if (revision == detailRevision) detail = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (revision == detailRevision) detailError = restroomErrorMessage(failure)
            } finally {
                if (revision == detailRevision) detailLoading = false
            }
        }
    }

    fun closeDetails() {
        detailRevision++
        detailJob?.cancel()
        selectedId = null
        saved.remove<Long>("restroom_details_id")
        detail = null
        detailLoading = false
        detailError = null
    }
}
