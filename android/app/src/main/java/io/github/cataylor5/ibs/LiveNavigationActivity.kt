package io.github.cataylor5.ibs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import com.google.android.libraries.navigation.RoadSnappedLocationProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.navigation.DisplayOptions
import com.google.android.libraries.navigation.AudioGuidanceSettings
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationFragment
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.SimulationOptions
import com.google.android.libraries.navigation.Waypoint

/** A foreground navigation session. Leaving the screen stops location/audio guidance. */
class LiveNavigationActivity : FragmentActivity() {
    private lateinit var plan: NavigationPlan
    private lateinit var status: TextView
    private lateinit var action: Button
    private var navigator: Navigator? = null
    private lateinit var mapUi: NavigationMapUi
    private var finalIsRestroom = false
    private var finalRestroomId: Long? = null
    private var arrivalReviewId: String? = null
    private var reviewPageOpen = false
    private val reviewPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        reviewPageOpen = false
        if (progress.atRestroom || progress.arrived) renderProgress()
    }
    private var progress = NavigationProgress()
    private var busy = false
    private var ready = false
    private var foreground = false
    private var disposed = false
    private var generation = 0
    private var demo = false
    private var permissionAsked = false
    private var simulationStarted = false
    private lateinit var changeButton: Button
    private lateinit var dropdown: ChangeStopDropdown
    private var positionProvider: RoadSnappedLocationProvider? = null
    private var lastPosition: NavigationTarget? = null
    private var positionTime = 0L
    private val deviceLocations by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var deviceUpdates = false
    private var mapCentered = false
    private val deviceCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (disposed || !foreground || plan.destination != null) return
            val location = result.lastLocation ?: return
            val point = NavigationTarget("Current position", location.latitude, location.longitude)
            if (!point.valid) return
            lastPosition = point
            positionTime = android.os.SystemClock.elapsedRealtime()
            centerDeviceMap(point)
            dropdown.positionChanged()
        }
    }
    private var stopId: Long? = null
    private var freeOnly = false
    private var preferences = RestroomPreferences()
    private var changing = false
    private var changeJob: Job? = null
    private val replacementTransaction = RouteReplacementTransaction<Pair<NavigationPlan, NavigationProgress>> { target, done ->
        installTargets(target.first, target.second, done)
    }
    private val locationListener = RoadSnappedLocationProvider.LocationListener { location ->
        if (!disposed && foreground && plan.destination != null) {
            val point = NavigationTarget("Current navigation position", location.latitude, location.longitude)
            if (point.valid) {
                lastPosition = point
                positionTime = android.os.SystemClock.elapsedRealtime()
                if (::dropdown.isInitialized) dropdown.positionChanged()
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        generation++
        busy = false
        ready = false
        stopGuidance()
        show("Navigation timed out. Check internet, device location and Cloud configuration, then retry.")
    }
    private val arrivalListener = Navigator.ArrivalListener { event ->
        if (!disposed && ready) {
            changeJob?.cancel()
            changing = false
            dropdown.close()
            progress = progress.arrive(event.isFinalDestination)
            arrivalReviewId = java.util.UUID.randomUUID().toString()
            ready = false
            stopGuidance()
            renderProgress()
        }
    }
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasPreciseLocation()) initialize()
        else show("Precise location is required for live guidance. Allow precise location in app settings, then retry.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decoded = decodePlan(intent)
        if (decoded == null) { finish(); return }
        plan = decoded
        if (savedInstanceState?.getBoolean("active_plan") == true) {
            plan = plan.copy(destination = decode(savedInstanceState.getBundle("active_destination")),
                restroom = decode(savedInstanceState.getBundle("active_stop")))
        }
        decode(savedInstanceState?.getBundle("active_position"))?.let { plan = plan.copy(origin = it) }
        stopId = (savedInstanceState?.getLong("active_stop_id") ?: intent.getLongExtra("stop_id", 0)).takeIf { it > 0 }
        finalIsRestroom = savedInstanceState?.getBoolean("final_is_restroom")
            ?: intent.getBooleanExtra("final_is_restroom", false)
        finalRestroomId = (savedInstanceState?.getLong("final_restroom_id")
            ?: intent.getLongExtra("final_restroom_id", if (finalIsRestroom) stopId ?: 0L else 0L)).takeIf { it > 0 }
        arrivalReviewId = savedInstanceState?.getString("arrival_review_id")
        freeOnly = intent.getBooleanExtra("free_only", false)
        preferences = decodePreferences(intent)
        demo = intent.getBooleanExtra("demo", false)
        permissionAsked = savedInstanceState?.getBoolean("permissionAsked") ?: false
        progress = NavigationProgress(
            savedInstanceState?.getBoolean("stopVisited") ?: false,
            savedInstanceState?.getBoolean("atRestroom") ?: false,
            savedInstanceState?.getBoolean("arrived") ?: false
        )
        setContentView(R.layout.activity_live_navigation)
        IbsNavigationStyle.apply(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navigation_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = findViewById(R.id.navigation_status)
        action = findViewById(R.id.navigation_action)
        changeButton = findViewById(R.id.navigation_change)
        dropdown = ChangeStopDropdown(this, changeButton, lifecycleScope,
            position = ::currentPosition, currentId = { stopId },
            destination = { finalEndpoint() }, freeOnly = freeOnly,
            preferences = preferences, select = ::replaceStop)
        changeButton.setOnClickListener {
            if (ready && !changing && !progress.arrived && currentPosition() != null) dropdown.open()
            else brief("Wait for an active navigation position before changing stops.")
        }
        mapUi = NavigationMapUi(this, { plan }, { progress }, { ready && !busy }, { finalIsRestroom },
            { dropdown.applyAppearance() })
        mapUi.restoreOverview(savedInstanceState?.getBoolean("route_overview") ?: false)
        renderHeader()
        findViewById<Button>(R.id.navigation_stop).setOnClickListener { finish() }
        findViewById<Button>(R.id.navigation_review).setOnClickListener {
            if (reviewPageOpen) return@setOnClickListener
            val target = arrivedRestroom(plan, progress, stopId, finalRestroomId) ?: return@setOnClickListener
            val reviewId = arrivalReviewId ?: java.util.UUID.randomUUID().toString().also { arrivalReviewId = it }
            reviewPageOpen = true
            findViewById<Button>(R.id.navigation_review).isEnabled = false
            reviewPage.launch(Intent(this, RestroomReviewActivity::class.java)
                .putExtra("restroom_id", target.id).putExtra("business_name", target.name)
                .putExtra("arrival_id", reviewId).putExtra("demo", demo))
        }
        action.setOnClickListener {
            when {
                progress.arrived -> finish()
                progress.atRestroom -> {
                    progress = progress.continueTrip()
                    arrivalReviewId = null
                    initialize()
                }
                else -> requestLocation()
            }
        }
        if (progress.atRestroom || progress.arrived) renderProgress()
        else if (savedInstanceState == null || plan.destination == null) requestLocation()
        else show("Navigation paused after screen recreation. Tap Resume guidance when ready.")
    }

    private fun hasPreciseLocation() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (busy || progress.arrived) return
        if (hasPreciseLocation()) { initialize(); return }
        if (permissionAsked && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            show("Precise location is off. Enable it in app settings, then return and resume guidance.")
            action.text = "Open app settings"
            action.setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${packageName}")))
                action.setOnClickListener { requestLocation() }
                action.text = "Resume guidance"
            }
        } else launchPermission()
    }

    private fun launchPermission() {
        permissionAsked = true
        permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun initialize() {
        if (busy || disposed || progress.arrived || progress.atRestroom) return
        if (!hasPreciseLocation()) { requestLocation(); return }
        busy = true
        ready = false
        val ticket = ++generation
        show("Connecting to navigation…")
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 45_000)
        try {
            NavigationApi.getNavigator(this, object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(value: Navigator) {
                    if (disposed || ticket != generation) return
                    navigator = value
                    mapUi.attach(value)
                    positionProvider?.removeLocationListener(locationListener)
                    positionProvider = NavigationApi.getRoadSnappedLocationProvider(application)
                    if (foreground) positionProvider?.addLocationListener(locationListener)
                    value.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE)
                    value.removeArrivalListener(arrivalListener)
                    value.addArrivalListener(arrivalListener)
                    if (plan.destination == null) showMapOnly(value) else calculate(value, ticket)
                }
                override fun onError(errorCode: Int) {
                    if (disposed || ticket != generation) return
                    fail(when (errorCode) {
                        NavigationApi.ErrorCode.NOT_AUTHORIZED -> "Navigation NOT_AUTHORIZED. Enable Navigation SDK for Android and allow it on the Android key; check billing, package and SHA-1."
                        NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> "Navigation terms were not accepted. Tap Resume guidance to try again."
                        NavigationApi.ErrorCode.NETWORK_ERROR -> "Navigation NETWORK_ERROR. Check internet and retry."
                        NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> "Navigation needs precise location permission."
                        else -> "Navigation initialization failed (code ${errorCode}). Retry."
                    })
                }
            })
        } catch (_: SecurityException) {
            fail("Location permission is unavailable. Allow precise location, then retry.")
        } catch (_: Exception) {
            fail("Navigation could not initialize. Check device and Cloud configuration.")
        }
    }

    private fun showMapOnly(value: Navigator) {
        handler.removeCallbacks(timeout)
        value.stopGuidance()
        value.clearDestinations()
        value.simulator.unsetUserLocation()
        simulationStarted = false
        busy = false
        ready = true
        val point = lastPosition ?: plan.origin
        lastPosition = point
        positionTime = android.os.SystemClock.elapsedRealtime()
        centerDeviceMap(point)
        startDeviceUpdates()
        show()
    }

    private fun centerDeviceMap(point: NavigationTarget) {
        val fragment = supportFragmentManager.findFragmentById(R.id.navigation_map) as NavigationFragment
        fragment.getMapAsync { map ->
            if (!disposed && plan.destination == null && hasPreciseLocation()) {
                try {
                    map.isMyLocationEnabled = true
                    if (!mapCentered) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16f))
                        mapCentered = true
                    }
                } catch (_: SecurityException) {
                    show("Location permission changed. Enable precise location in Settings.")
                }
            }
        }
    }

    private fun startDeviceUpdates() {
        if (deviceUpdates || !foreground || disposed || plan.destination != null || !hasPreciseLocation()) return
        try {
            deviceLocations.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000).build(),
                deviceCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    deviceUpdates = false
                    if (!disposed && plan.destination == null) {
                        show("Location updates unavailable. Check device location and reopen navigation.")
                    }
                }
            deviceUpdates = true
        } catch (_: SecurityException) {
            show("Location permission is unavailable. Enable precise location in Settings.")
        }
    }

    private fun stopDeviceUpdates() {
        if (deviceUpdates) deviceLocations.removeLocationUpdates(deviceCallback)
        deviceUpdates = false
    }

    private fun calculate(value: Navigator, ticket: Int) {
        try {
            value.stopGuidance()
            value.clearDestinations()
            simulationStarted = false
            if (demo) {
                val start = if (progress.stopVisited) plan.restroom?.let {
                    LatLng(it.latitude, it.longitude)
                } else null
                value.simulator.setUserLocation(start ?: LatLng(plan.origin.latitude, plan.origin.longitude))
            } else value.simulator.unsetUserLocation()
            val waypoints = plan.remainingTargets(progress).map {
                Waypoint.builder().setLatLng(it.latitude, it.longitude).setTitle(it.name).build()
            }
            show("Calculating route…")
            value.setDestinations(waypoints, RoutingOptions().travelMode(RoutingOptions.TravelMode.DRIVING),
                DisplayOptions().hideDestinationMarkers(true)) { description ->
                if (disposed || ticket != generation) return@setDestinations
                handler.removeCallbacks(timeout)
                busy = false
                if (description.routeStatus == Navigator.RouteStatus.OK) {
                    ready = true
                    value.setAudioGuidanceSettings(AudioGuidanceSettings.builder()
                        .setGuidanceMode(AudioGuidanceSettings.GuidanceMode.VOICE_ALERTS_AND_GUIDANCE).build())
                    if (foreground) resumeGuidance()
                    mapUi.onRouteReady()
                    if (ready) show()
                } else {
                    fail("Route calculation failed: ${description.routeStatus.name}. Check location, internet and Navigation SDK Cloud/key settings, then retry.")
                }
            }
        } catch (_: SecurityException) {
            fail("Precise location permission was lost. Allow it and retry.")
        } catch (_: Exception) {
            fail("Could not calculate navigation. Check device location and retry.")
        }
    }

    private fun renderHeader() {
        val header = navigationHeader(plan, finalIsRestroom)
        status.text = header.finalDestination.orEmpty()
        findViewById<TextView>(R.id.navigation_restroom_stop).text = header.restroomStop.orEmpty()
        findViewById<View>(R.id.navigation_final_section).visibility =
            if (header.finalDestination == null) View.GONE else View.VISIBLE
        findViewById<View>(R.id.navigation_restroom_section).visibility =
            if (header.restroomStop == null) View.GONE else View.VISIBLE
        findViewById<View>(R.id.navigation_header).visibility =
            if (header.finalDestination == null && header.restroomStop == null) View.GONE else View.VISIBLE
    }

    private fun currentPosition(): NavigationTarget? =
        lastPosition?.takeIf { android.os.SystemClock.elapsedRealtime() - positionTime <= 15_000 }

    private fun finalEndpoint(): TripEndpoint? = plan.destination?.let {
        TripEndpoint(it.name, it.latitude, it.longitude)
    }

    private fun brief(message: String) {
        if (!disposed) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun replaceStop(row: BackendRestroom) {
        if (changing || !ready || progress.arrived) return
        if (!preferences.matches(row)) { brief("This restroom no longer matches your preferences."); return }
        val point = currentPosition() ?: run { brief("Current position unavailable. Try again."); return }
        changing = true
        changeButton.isEnabled = false
        changeButton.text = "Checking…"
        val previousPlan = plan
        val previousProgress = progress
        changeJob = lifecycleScope.launch {
            try {
                // Guidance continues while the server revalidates access, fees and driving detour.
                withTimeout(35_000) {
                    estimateBusinessTrip(TripEndpoint(point.name, point.latitude, point.longitude),
                        finalEndpoint(), row, freeOnly)
                }
                if (!ready || progress != previousProgress || disposed || !foreground) return@launch
                val latest = currentPosition() ?: run { brief("Current position unavailable. Your route is unchanged."); return@launch }
                val replacement = previousPlan.withBusiness(latest, row)
                changing = true
                busy = true
                ready = false
                stopGuidance()
                if (demo) navigator?.simulator?.setUserLocation(LatLng(latest.latitude, latest.longitude))
                show("Changing stop…")
                val rollbackPlan = previousPlan.copy(origin = latest)
                replacementTransaction.replace(rollbackPlan to previousProgress, replacement to NavigationProgress()) { outcome ->
                    if (outcome == ReplacementOutcome.ACCEPTED) {
                        if (previousPlan.destination == null) {
                            finalIsRestroom = true
                            finalRestroomId = row.id
                        }
                        arrivalReviewId = null
                        plan = replacement
                        stopId = row.id
                        progress = NavigationProgress()
                        changing = false
                        finishRouteChange()
                    } else {
                        changing = false
                        plan = rollbackPlan
                        if (outcome == ReplacementOutcome.RESTORED) {
                            finishRouteChange()
                            brief("Couldn't change stop. Previous route restored.")
                        } else {
                            busy = false
                            ready = false
                            show("Couldn't restore guidance. Tap Resume guidance to retry your previous stop.")
                            brief("Stop change failed. Previous stop is still selected.")
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                brief("Stop lookup timed out. Your route is unchanged.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                brief("Couldn't check that stop. Your route is unchanged.")
            } finally {
                // SDK transaction owns the button until it commits or restores.
                if (!busy) {
                    changing = false
                    if (!disposed) {
                        changeButton.isEnabled = ready && !progress.arrived
                        changeButton.text = "Change Stop"
                    }
                }
            }
        }
    }

    private fun installTargets(target: NavigationPlan, state: NavigationProgress, done: (Boolean) -> Unit) {
        val value = navigator ?: run { done(false); return }
        if (target.destination == null) {
            try {
                value.stopGuidance()
                value.clearDestinations()
                value.simulator.unsetUserLocation()
                done(true)
            } catch (_: Exception) { done(false) }
            return
        }
        val ticket = ++generation
        val watchdog = Runnable {
            if (!disposed && ticket == generation) { generation++; done(false) }
        }
        handler.postDelayed(watchdog, 30_000)
        try {
            val waypoints = target.remainingTargets(state).map {
                Waypoint.builder().setLatLng(it.latitude, it.longitude).setTitle(it.name).build()
            }
            value.setDestinations(waypoints, RoutingOptions().travelMode(RoutingOptions.TravelMode.DRIVING),
                DisplayOptions().hideDestinationMarkers(true)) { result ->
                if (!disposed && ticket == generation) {
                    handler.removeCallbacks(watchdog)
                    generation++
                    done(result.routeStatus == Navigator.RouteStatus.OK)
                }
            }
        } catch (_: Exception) {
            handler.removeCallbacks(watchdog)
            if (!disposed && ticket == generation) { generation++; done(false) }
        }
    }

    private fun finishRouteChange() {
        if (plan.destination == null) { navigator?.let(::showMapOnly); return }
        stopDeviceUpdates()
        busy = false
        ready = true
        simulationStarted = false
        if (foreground) resumeGuidance()
        mapUi.onRouteReady()
        show()
    }

    private fun resumeGuidance() {
        if (plan.destination == null) { startDeviceUpdates(); return }
        stopDeviceUpdates()
        try {
            if (!hasPreciseLocation()) { fail("Precise location permission is required. Retry to allow it."); return }
            navigator?.startGuidance()
            if (demo) {
                if (simulationStarted) navigator?.simulator?.resume()
                else {
                    // Advance the SDK vehicle at 5×: one driving minute per twelve on-screen seconds.
                    // Shared by initial routes, Change Stop, and Continue to destination; ETAs stay unscaled.
                    navigator?.simulator?.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(5f))
                    simulationStarted = true
                }
            }
        } catch (_: SecurityException) {
            fail("Location permission was lost. Allow precise location and retry.")
        }
    }

    private fun stopGuidance() {
        navigator?.stopGuidance()
        if (demo) navigator?.simulator?.pause()
    }

    private fun fail(message: String) {
        handler.removeCallbacks(timeout)
        busy = false
        ready = false
        stopGuidance()
        show(message)
    }

    private fun renderProgress() {
        show(when {
            progress.arrived -> "You have arrived at ${plan.destination?.name ?: "your destination"}. Navigation stopped."
            progress.atRestroom -> "Arrived at ${plan.restroom?.name ?: "restroom"}. Tap Continue to destination when you’re ready."
            else -> ""
        })
    }

    private fun show(message: String = "") {
        if (disposed) return
        renderHeader()
        val notice = findViewById<TextView>(R.id.navigation_message)
        notice.text = message
        notice.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        changeButton.isEnabled = ready && !changing && !progress.arrived
        changeButton.text = if (changing) "Changing…" else "Change Stop"
        val reviewTarget = arrivedRestroom(plan, progress, stopId, finalRestroomId)
        if (reviewTarget != null && arrivalReviewId == null) arrivalReviewId = java.util.UUID.randomUUID().toString()
        findViewById<Button>(R.id.navigation_review).isEnabled = !reviewPageOpen
        findViewById<Button>(R.id.navigation_review).visibility =
            if (reviewTarget != null && arrivalReviewId?.let { !ArrivalReviewReceipt.saved(this, it) } == true)
                View.VISIBLE else View.GONE
        action.visibility = if (ready) View.GONE else View.VISIBLE
        action.isEnabled = !busy
        action.text = when {
            progress.arrived -> "Finish trip"
            progress.atRestroom -> "Continue to destination"
            busy -> "Please wait…"
            else -> "Resume guidance"
        }
        if (::mapUi.isInitialized) mapUi.update()
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        reviewPageOpen = false
        if (::mapUi.isInitialized) mapUi.start()
        positionProvider?.addLocationListener(locationListener)
        if (progress.atRestroom || progress.arrived) renderProgress()
        if (ready && !progress.atRestroom && !progress.arrived) {
            resumeGuidance()
            mapUi.onRouteReady()
            if (ready) show()
        }
    }

    override fun onStop() {
        foreground = false
        if (::mapUi.isInitialized) mapUi.stop()
        if (::dropdown.isInitialized) dropdown.close()
        positionProvider?.removeLocationListener(locationListener)
        stopDeviceUpdates()
        lastPosition = null
        if (changing) {
            replacementTransaction.cancel()
            generation++
            changeJob?.cancel()
            changing = false
            ready = false
            busy = false
            show("Stop change interrupted. Resume guidance to restore your previous stop.")
        }
        stopGuidance()
        if (ready) show()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!::plan.isInitialized) { super.onSaveInstanceState(outState); return }
        outState.putBoolean("route_overview", if (::mapUi.isInitialized) mapUi.overview else false)
        outState.putBoolean("final_is_restroom", finalIsRestroom)
        outState.putLong("final_restroom_id", finalRestroomId ?: 0L)
        outState.putString("arrival_review_id", arrivalReviewId)
        outState.putBoolean("active_plan", true)
        plan.destination?.let { outState.putBundle("active_destination", encode(it)) }
        plan.restroom?.let { outState.putBundle("active_stop", encode(it)) }
        lastPosition?.let { outState.putBundle("active_position", encode(it)) }
        outState.putLong("active_stop_id", stopId ?: 0)
        outState.putBoolean("permissionAsked", permissionAsked)
        outState.putBoolean("stopVisited", progress.stopVisited)
        outState.putBoolean("atRestroom", progress.atRestroom)
        outState.putBoolean("arrived", progress.arrived)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        disposed = true
        if (::mapUi.isInitialized) mapUi.close()
        stopDeviceUpdates()
        replacementTransaction.cancel()
        if (::dropdown.isInitialized) dropdown.close()
        changeJob?.cancel()
        positionProvider?.removeLocationListener(locationListener)
        generation++
        handler.removeCallbacksAndMessages(null)
        navigator?.let {
            it.removeArrivalListener(arrivalListener)
            it.stopGuidance()
            it.clearDestinations()
            if (demo) it.simulator.unsetUserLocation()
            it.cleanup()
        }
        navigator = null
        super.onDestroy()
    }

    companion object {
        private fun encode(target: NavigationTarget) = Bundle().apply {
            putString("name", target.name)
            putDouble("latitude", target.latitude)
            putDouble("longitude", target.longitude)
        }
        private fun decode(bundle: Bundle?): NavigationTarget? {
            if (bundle == null || !bundle.containsKey("latitude") || !bundle.containsKey("longitude")) return null
            return NavigationTarget(bundle.getString("name").orEmpty(), bundle.getDouble("latitude"),
                bundle.getDouble("longitude")).takeIf { it.valid }
        }
        internal fun decodePreferences(intent: Intent) = RestroomPreferences(
            access = intent.getStringArrayListExtra("filter_access").orEmpty().mapNotNull {
                runCatching { RestroomAccess.valueOf(it) }.getOrNull()
            }.toSet(),
            minimumStars = intent.getIntExtra("filter_rating", 0).takeIf { it in 1..5 },
            privacy = intent.getStringExtra("filter_privacy")?.let {
                runCatching { PrivacyPreference.valueOf(it) }.getOrNull()
            }
        )
        internal fun decodePlan(intent: Intent): NavigationPlan? {
            val origin = decode(intent.getBundleExtra("origin")) ?: return null
            val destination = decode(intent.getBundleExtra("destination"))
            if (destination == null && (intent.hasExtra("destination") || !intent.getBooleanExtra("map_only", false))) return null
            val stop = if (intent.hasExtra("restroom")) decode(intent.getBundleExtra("restroom")) ?: return null else null
            return NavigationPlan(origin, destination, stop).takeIf { it.valid }
        }
        internal fun intent(context: Context, plan: NavigationPlan, demo: Boolean,
            freeOnly: Boolean, detourMinutes: Int, selectedId: Long? = null,
            preferences: RestroomPreferences = RestroomPreferences()): Intent {
            require(plan.valid)
            return Intent(context, LiveNavigationActivity::class.java).apply {
                putExtra("origin", encode(plan.origin))
                putExtra("map_only", plan.destination == null)
                plan.destination?.let { putExtra("destination", encode(it)) }
                plan.restroom?.let { putExtra("restroom", encode(it)) }
                putExtra("demo", demo)
                putExtra("stop_id", selectedId ?: 0)
                putExtra("final_is_restroom", selectedId != null && plan.restroom == null && plan.destination != null)
                putStringArrayListExtra("filter_access", ArrayList(preferences.access.map { it.name }))
                putExtra("filter_rating", preferences.minimumStars ?: 0)
                putExtra("filter_privacy", preferences.privacy?.name)
                putExtra("free_only", freeOnly)
                putExtra("detour_minutes", detourMinutes)
            }
        }
        fun intent(context: Context, route: DrivingRoute, demo: Boolean, freeOnly: Boolean = false, detourMinutes: Int = route.detourLimitMinutes ?: 10): Intent {
            val plan = NavigationPlan.from(route)
            return Intent(context, LiveNavigationActivity::class.java).apply {
                putExtra("origin", encode(plan.origin))
                plan.destination?.let { putExtra("destination", encode(it)) }
                plan.restroom?.let { putExtra("restroom", encode(it)) }
                putExtra("demo", demo)
                putExtra("stop_id", route.stop?.id ?: 0)
                putExtra("free_only", freeOnly)
                putExtra("detour_minutes", detourMinutes)
            }
        }
    }
}
