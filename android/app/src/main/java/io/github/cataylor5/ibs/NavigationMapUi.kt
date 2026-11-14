package io.github.cataylor5.ibs

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageButton
import com.google.android.libraries.navigation.NavigationView
import io.github.cataylor5.ibs.ui.theme.IbsAppearance
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.libraries.navigation.NavigationFragment
import com.google.android.libraries.navigation.Navigator
import io.github.cataylor5.ibs.ui.theme.IbsBrand
import java.time.Instant
import kotlinx.coroutines.*

/** Presentation only: observes SDK estimates/geometry; never stops guidance or the simulator. */
internal class NavigationMapUi(
    private val activity: FragmentActivity,
    private val plan: () -> NavigationPlan,
    private val progress: () -> NavigationProgress,
    private val routeReady: () -> Boolean,
    private val finalIsRestroom: () -> Boolean,
    private val onModeChanged: () -> Unit = {}
) {
    private val fragment = activity.supportFragmentManager.findFragmentById(R.id.navigation_map) as NavigationFragment
    private val area = activity.findViewById<View>(R.id.navigation_map_area)
    private val card = activity.findViewById<View>(R.id.navigation_eta)
    private val time = activity.findViewById<TextView>(R.id.navigation_eta_time)
    private val details = activity.findViewById<TextView>(R.id.navigation_eta_details)
    private val toggle = activity.findViewById<ImageButton>(R.id.navigation_overview)
    private var navigator: Navigator? = null
    private var map: GoogleMap? = null
    private val markers = mutableListOf<Marker>()
    private var pinIcon: BitmapDescriptor? = null
    private var pins = emptyList<NavigationMapPin>()
    private var closed = false
    private var active = false
    private var iconGeneration = 0L
    var overview = false
        private set
    private var arrivalAt: Instant? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { if (active && !closed) { update(); handler.postDelayed(this, 15_000) } }
    }
    private val nightListener = NavigationView.OnNightModeChangedListener { event -> main {
        IbsAppearance.mapChanged(activity, event.inNightMode())
        IbsNavigationStyle.applyAppearance(activity)
        onModeChanged()
        updateOverviewArtwork()
    } }
    private val estimates = Navigator.RemainingTimeOrDistanceChangedListener { main { update() } }
    private val routes = Navigator.RouteChangedListener { main {
        update()
        if (overview) area.post { if (!closed && overview) frameRoute() }
    } }

    init {
        IbsAppearance.mapOpened(activity)
        fragment.addOnNightModeChangedListener(nightListener)
        fragment.setEtaCardEnabled(false)
        // Preserve the native green duration and gray secondary line, with contrast on cobalt.
        time.setTextColor(Color.rgb(0x81, 0xC9, 0x95))
        details.setTextColor(Color.rgb(0xDA, 0xDC, 0xE0))
        area.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (overview && !closed && (r - l != oldR - oldL || b - t != oldB - oldT)) {
                area.post { if (overview && !closed) frameRoute() }
            }
        }
        card.background = GradientDrawable().apply {
            setColor(IbsBrand.CobaltArgb)
            cornerRadius = dp(24).toFloat()
        }
        updateOverviewArtwork()
        toggle.setOnClickListener {
            overview = !overview
            applyCamera()
        }
        fragment.getMapAsync {
            if (!closed) {
                map = it
                activity.lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        SplashArtwork.load(activity.applicationContext)?.first?.let {
                            val cropped = trimPin(it)
                            val height = dp(52)
                            Bitmap.createScaledBitmap(cropped,
                                (cropped.width * height.toFloat() / cropped.height).toInt().coerceAtLeast(1), height, true)
                        }
                    }
                    if (closed) return@launch
                    if (bitmap != null) pinIcon = BitmapDescriptorFactory.fromBitmap(bitmap)
                    renderPins(force = true)
                }
                update()
                if (routeReady()) applyCamera()
            }
        }
    }

    private fun updateOverviewArtwork() {
        val ticket = ++iconGeneration
        val night = IbsAppearance.state.night == true
        activity.lifecycleScope.launch {
            val icon = withContext(Dispatchers.IO) { BrandAssets.loadOverview(activity.applicationContext, night) }
            if (!closed && ticket == iconGeneration && icon != null) toggle.setImageBitmap(icon)
        }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun main(action: () -> Unit) {
        activity.runOnUiThread { if (!closed) action() }
    }

    fun attach(value: Navigator) {
        detach()
        navigator = value
        value.addRemainingTimeOrDistanceChangedListener(1, 1, estimates)
        value.addRouteChangedListener(routes)
    }

    fun start() { active = true; handler.removeCallbacks(tick); handler.post(tick) }
    fun stop() { active = false; handler.removeCallbacks(tick) }

    fun restoreOverview(value: Boolean) { overview = value }
    fun onRouteReady() { update(); applyCamera() }

    fun update() {
        if (closed) return
        val state = progress()
        val hasRoute = plan().destination != null
        val show = hasRoute && (routeReady() || state.atRestroom || state.arrived)
        toggle.visibility = if (hasRoute && !state.arrived && (routeReady() || state.atRestroom)) View.VISIBLE else View.GONE
        toggle.contentDescription = if (overview) "Resume following vehicle" else "Show route overview"
        fragment.setRecenterButtonEnabled(!overview)
        val eta = if (show) {
            val stopped = state.atRestroom || state.arrived
            if (stopped && arrivalAt == null) arrivalAt = Instant.now()
            if (!stopped) arrivalAt = null
            val next = navigator?.currentTimeAndDistance
            if (stopped) tripEta(0, 0, arrivalAt!!, use24HourClock = DateFormat.is24HourFormat(activity))
            else next?.let { tripEta(it.seconds.toLong(), it.meters.toLong(), Instant.now(),
                use24HourClock = DateFormat.is24HourFormat(activity)) }
        } else null
        card.visibility = if (show) View.VISIBLE else View.GONE
        time.text = eta?.duration ?: "—"
        details.text = eta?.distanceAndArrival ?: "Updating trip estimate…"
        renderPins()
    }

    private fun applyCamera() {
        toggle.contentDescription = if (overview) "Resume following vehicle" else "Show route overview"
        fragment.setRecenterButtonEnabled(!overview)
        if (overview) area.post { if (overview && !closed) frameRoute() }
        else map?.followMyLocation(GoogleMap.CameraPerspective.TILTED)
    }

    private fun frameRoute() {
        val value = navigator ?: return
        val googleMap = map ?: return
        if (area.width <= 0 || area.height <= 0 || (!routeReady() && !progress().atRestroom)) return
        val segments = value.routeSegments.orEmpty()
        val finalSeconds = value.timeAndDistanceList?.lastOrNull()?.seconds?.toLong()
        if (!needsFullRouteBounds(segments.size, finalSeconds)) {
            fragment.showRouteOverview()
            return
        }
        // Replace the first leg with the SDK's live, road-snapped remaining geometry.
        val points = (value.currentRouteSegment?.latLngs.orEmpty() +
            segments.drop(1).flatMap { it.latLngs.orEmpty() } +
            plan().remainingTargets(progress()).map { LatLng(it.latitude, it.longitude) }).distinct()
        if (points.isEmpty()) { fragment.showRouteOverview(); return }
        val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
        googleMap.moveCamera(if (points.size == 1) CameraUpdateFactory.newLatLngZoom(points.first(), 15f)
            else CameraUpdateFactory.newLatLngBounds(bounds, dp(64)))
    }

    private fun renderPins(force: Boolean = false) {
        val googleMap = map ?: return
        val requested = navigationMapPins(plan(), finalIsRestroom())
        if (!force && pins == requested) return
        markers.forEach { it.remove() }
        markers.clear()
        pins = requested
        for (pin in requested) {
            // Wait for actual IBS artwork instead of briefly adding a duplicate default restroom pin.
            if (pin.restroom && pinIcon == null) continue
            val options = MarkerOptions().position(LatLng(pin.target.latitude, pin.target.longitude))
                .title(pin.target.name).anchor(0.5f, 1f)
            if (pin.restroom) options.icon(pinIcon).zIndex(2f)
            googleMap.addMarker(options)?.let(markers::add)
        }
    }

    private fun trimPin(bitmap: Bitmap): Bitmap {
        var left = bitmap.width; var right = -1; var top = bitmap.height; var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if ((bitmap.getPixel(x, y) ushr 24) != 0) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        return if (right >= left && bottom >= top) Bitmap.createBitmap(bitmap, left, top, right-left+1, bottom-top+1)
            else bitmap
    }

    private fun detach() {
        navigator?.removeRemainingTimeOrDistanceChangedListener(estimates)
        navigator?.removeRouteChangedListener(routes)
    }
    fun close() {
        stop(); closed = true; detach()
        fragment.removeOnNightModeChangedListener(nightListener)
        fragment.setRecenterButtonEnabled(true)
        IbsAppearance.mapClosed()
        markers.forEach { it.remove() }; markers.clear()
        navigator = null; map = null
    }
}
