package io.github.cataylor5.ibs

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

internal object ArrivalReviewReceipt {
    fun saved(context: Context, arrivalId: String): Boolean =
        context.getSharedPreferences("ibs_review_drafts", Context.MODE_PRIVATE).getBoolean(arrivalId + "_saved", false)
}

internal class RestroomReviewViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val id = saved.get<Long>("restroom_id") ?: 0L
    private val arrivalId = saved.get<String>("arrival_id").orEmpty()
    val demo = saved.get<Boolean>("demo") ?: false
    val businessName = saved.get<String>("business_name").orEmpty()
    private val preferences = application.getSharedPreferences("ibs_review_drafts", Context.MODE_PRIVATE)
    private val repository = RestroomReviewRepository(application)
    var draft by mutableStateOf(loadDraft())
        private set
    var context by mutableStateOf<RestroomReviewContext?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var complete by mutableStateOf(ArrivalReviewReceipt.saved(application, arrivalId))
        private set

    init { if (!complete) load() }

    private fun loadDraft(): RestroomReviewDraft = try {
        val json = JSONObject(saved.get<String>("draft") ?: preferences.getString(arrivalId + "_draft", null) ?: "{}")
        RestroomReviewDraft(json.optInt("overall").takeIf { it in 1..5 }, json.optString("comment"),
            json.optString("access").takeIf { it.isNotBlank() }, json.optString("privacy").takeIf { it.isNotBlank() })
    } catch (_: Exception) { RestroomReviewDraft() }

    fun edit(value: RestroomReviewDraft) {
        if (saving || complete) return
        draft = value
        val json = JSONObject().put("overall", value.overall ?: JSONObject.NULL).put("comment", value.comment)
            .put("access", value.accessChoice ?: "").put("privacy", value.privacyChoice ?: "").toString()
        saved["draft"] = json
        preferences.edit().putString(arrivalId + "_draft", json).apply()
    }

    fun load() {
        if (loading || saving || complete) return
        loading = true
        error = null
        viewModelScope.launch {
            try { context = repository.context(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = (failure as? ReviewSaveException)?.userMessage
                    ?: "Could not load this restroom. Check your connection and retry."
            } finally { loading = false }
        }
    }

    fun submit() {
        if (saving || loading || complete || context == null || !draft.valid) return
        saving = true
        error = null
        val submitted = draft
        viewModelScope.launch {
            try {
                repository.submit(id, arrivalId, demo, submitted)
                // Durable receipt also handles recreation between the HTTP response and activity result.
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    preferences.edit().putBoolean(arrivalId + "_saved", true).remove(arrivalId + "_draft").commit()
                }
                complete = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = (failure as? ReviewSaveException)?.userMessage
                    ?: "Couldn't save your review. Your draft is kept; check your connection and retry."
            } finally { saving = false }
        }
    }
}
