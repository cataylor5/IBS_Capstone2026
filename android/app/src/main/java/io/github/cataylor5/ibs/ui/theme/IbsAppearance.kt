package io.github.cataylor5.ibs.ui.theme

import android.content.Context
import androidx.compose.runtime.*

internal data class IbsModeState(
    val systemNight: Boolean? = null,
    val night: Boolean? = null,
    val mapCount: Int = 0
) {
    fun systemChanged(value: Boolean) = copy(systemNight = value,
        night = if (night == null || (systemNight != null && systemNight != value && mapCount == 0)) value else night)
    fun mapOpened() = copy(mapCount = mapCount + 1)
    fun mapChanged(value: Boolean) = copy(night = value)
    fun mapClosed() = copy(mapCount = (mapCount - 1).coerceAtLeast(0))
}

internal data class IbsSurfacePalette(val background: Int, val surface: Int, val text: Int,
    val border: Int, val selected: Int, val selectedText: Int, val actionText: Int)

internal fun ibsSurfacePalette(night: Boolean) = if (night)
    IbsSurfacePalette(0xFF1D2835.toInt(), 0xFF263442.toInt(), 0xFFECF3FA.toInt(),
        0xFF63788C.toInt(), 0xFF234D57.toInt(), IbsBrand.AquaArgb, IbsBrand.AquaArgb)
else IbsSurfacePalette(IbsBrand.BackgroundArgb, -1, IbsBrand.NavyArgb,
    IbsBrand.BorderArgb, IbsBrand.PaleAquaArgb, IbsBrand.NavyArgb, IbsBrand.CobaltArgb)

/** Persists only appearance; effective SDK mode wins while any navigation map is visible. */
internal object IbsAppearance {
    var state by mutableStateOf(IbsModeState())
        private set
    private var initialized = false
    val palette get() = ibsSurfacePalette(state.night == true)

    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences("ibs_appearance", Context.MODE_PRIVATE)
        if (prefs.contains("night")) state = state.copy(night = prefs.getBoolean("night", false))
        initialized = true
        val app = context.applicationContext
        app.registerComponentCallbacks(object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(configuration: android.content.res.Configuration) {
                systemChanged(app, configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
            }
            override fun onLowMemory() {}
        })
    }
    fun systemChanged(context: Context, value: Boolean) {
        initialize(context)
        val next = state.systemChanged(value)
        if (next != state) { state = next; persist(context) }
    }
    fun mapOpened(context: Context) { initialize(context); state = state.mapOpened() }
    fun mapChanged(context: Context, value: Boolean) {
        state = state.mapChanged(value)
        persist(context)
    }
    fun mapClosed() { state = state.mapClosed() }
    private fun persist(context: Context) {
        state.night?.let { context.applicationContext.getSharedPreferences("ibs_appearance", Context.MODE_PRIVATE)
            .edit().putBoolean("night", it).apply() }
    }
}
