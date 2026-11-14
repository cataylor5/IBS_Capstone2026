package io.github.cataylor5.ibs

import io.github.cataylor5.ibs.ui.theme.IbsBrand
import io.github.cataylor5.ibs.ui.theme.IbsAppearance
import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import kotlinx.coroutines.*

/** A focusable anchored window does not pause its Activity or navigation guidance. */
internal class ChangeStopDropdown(
    private val context: Context,
    private val anchor: View,
    private val scope: CoroutineScope,
    private val position: () -> NavigationTarget?,
    private val currentId: () -> Long?,
    private val destination: () -> TripEndpoint?,
    private val freeOnly: Boolean,
    private val preferences: RestroomPreferences = RestroomPreferences(),
    private val select: (BackendRestroom) -> Unit
) {
    private var window: PopupWindow? = null
    private var panel: LinearLayout? = null
    private var searchView: EditText? = null
    private var job: Job? = null
    private var countdownJob: Job? = null
    private var travelSeconds = emptyMap<Long, Double>()
    private var revision = 0L
    private var rows = emptyList<BackendRestroom>()
    private var detours = emptyMap<Long, Double>()
    private var loading = false
    private var failures = 0
    private var query = ""
    private var sampledAt: NavigationTarget? = null
    private var lastRefresh = 0L
    private var list: LinearLayout? = null
    private var message: TextView? = null
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun open() {
        if (window != null) { close(); return }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = IbsNavigationStyle.card(dp(16).toFloat())
        }
        val search = EditText(context).apply {
            hint = "Search business name"
            setTextColor(IbsAppearance.palette.text)
            setHintTextColor(IbsAppearance.palette.text)
            backgroundTintList = ColorStateList.valueOf(IbsAppearance.palette.actionText)
            contentDescription = "Search replacement business name"
            setSingleLine(true)
            textSize = 15f
        }
        panel = root
        searchView = search
        root.addView(search)
        message = TextView(context).apply { textSize = 13f; setTextColor(IbsAppearance.palette.text) }
        root.addView(message)
        list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        query = ""
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty(); render()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val popup = PopupWindow(root, minOf(dp(360), context.resources.displayMetrics.widthPixels - dp(24)), dp(320), true)
        popup.setBackgroundDrawable(ColorDrawable(IbsAppearance.palette.surface))
        popup.elevation = dp(12).toFloat()
        popup.isOutsideTouchable = true
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        popup.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        popup.setOnDismissListener { revision++; job?.cancel(); countdownJob?.cancel(); window = null; list = null; message = null; panel = null; searchView = null }
        window = popup
        popup.showAsDropDown(anchor, -dp(140), 0)
        refresh()
        countdownJob = scope.launch {
            while (isActive) { delay(15_000); render() }
        }
    }

    fun positionChanged() {
        if (window == null) return
        render() // Uses cached driving estimates and the real wall clock; no search-time network calls.
        val now = android.os.SystemClock.elapsedRealtime()
        val point = position() ?: return
        val previous = sampledAt ?: return
        val moved = straightLineMeters(point, previous)
        if (!loading && moved >= 100 && now - lastRefresh >= 15_000) refresh()
    }

    private fun refresh() {
        val ticket = ++revision
        job?.cancel()
        val point = position()
        if (point == null) { message?.text = "Waiting for current navigation position…"; return }
        sampledAt = point
        lastRefresh = android.os.SystemClock.elapsedRealtime()
        detours = emptyMap()
        travelSeconds = emptyMap()
        failures = 0
        loading = true
        job = scope.launch {
            try {
                rows = HttpRestroomRepository().list()
                render()
                // Bounded sequential route checks; no key or location diagnostics.
                for (row in rows.filter { it.id != currentId() && it.canUseForRoute(freeOnly) && preferences.matches(it) }) {
                    try {
                        val from = position() ?: break
                        val estimate = withTimeout(35_000) {
                            estimateBusinessTrip(TripEndpoint("Current navigation position", from.latitude, from.longitude),
                                destination(), row, freeOnly)
                        }
                        estimate.preferenceSeconds?.let { detours = detours + (row.id to it) }
                        estimate.travelSeconds?.let { travelSeconds = travelSeconds + (row.id to it) }
                    } catch (_: TimeoutCancellationException) { failures++ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failures++ }
                    render()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures++; rows = emptyList() }
            finally { if (ticket == revision) { loading = false; render() } }
        }
        render()
    }

    private fun render() {
        val container = list ?: return
        val point = position()
        val scroll = container.parent as? ScrollView
        val scrollY = scroll?.scrollY ?: 0
        container.removeAllViews()
        if (point == null) { message?.text = "Waiting for current navigation position…"; return }
        val matches = replacementStops(rows, currentId(), freeOnly, null, detours, travelSeconds, query,
            enforceTimeLimit = false, preferences = preferences, showDetour = destination() != null,
            resultLimit = 10)
        message?.text = when {
            loading -> "Checking driving times… ${matches.size} matches"
            failures > 0 -> "${matches.size} matches • Some checks failed; close and reopen to retry."
            matches.isEmpty() -> "No matching replacement stops."
            else -> "${matches.size} matches • Shortest drive first"
        }
        for (entry in matches) {
            val row = Button(context).apply {
                text = listOfNotNull(entry.restroom.name, entry.details, entry.offRouteLabel,
                    businessHoursCountdown(entry.restroom.businessHours)).joinToString("\n")
                textSize = 13f
                setTextColor(IbsAppearance.palette.text)
                backgroundTintList = null
                background = IbsNavigationStyle.card(dp(12).toFloat())
                setPadding(dp(12), dp(10), dp(12), dp(10))
                isAllCaps = false
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setOnClickListener { close(); select(entry.restroom) }
            }
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
        }
        scroll?.post { scroll.scrollTo(0, scrollY) }
    }

    fun applyAppearance() {
        panel?.background = IbsNavigationStyle.card(dp(16).toFloat())
        searchView?.setTextColor(IbsAppearance.palette.text)
        searchView?.setHintTextColor(IbsAppearance.palette.text)
        searchView?.backgroundTintList = ColorStateList.valueOf(IbsAppearance.palette.actionText)
        message?.setTextColor(IbsAppearance.palette.text)
        window?.setBackgroundDrawable(ColorDrawable(IbsAppearance.palette.surface))
        render()
    }

    fun close() { job?.cancel(); countdownJob?.cancel(); window?.dismiss() }
}
