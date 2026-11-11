package io.github.cataylor5.ibs

import android.Manifest
import android.app.AlertDialog
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
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
    private var progress = NavigationProgress()
    private var busy = false
    private var ready = false
    private var foreground = false
    private var disposed = false
    private var generation = 0
    private var demo = false
    private var permissionAsked = false
    private var simulationStarted = false
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
            progress = progress.arrive(event.isFinalDestination)
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
        demo = BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)
        permissionAsked = savedInstanceState?.getBoolean("permissionAsked") ?: false
        progress = NavigationProgress(
            savedInstanceState?.getBoolean("stopVisited") ?: false,
            savedInstanceState?.getBoolean("atRestroom") ?: false,
            savedInstanceState?.getBoolean("arrived") ?: false
        )
        setContentView(R.layout.activity_live_navigation)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navigation_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = findViewById(R.id.navigation_status)
        action = findViewById(R.id.navigation_action)
        findViewById<Button>(R.id.navigation_stop).setOnClickListener { finish() }
        action.setOnClickListener {
            if (progress.atRestroom) {
                progress = progress.continueTrip()
                initialize()
            } else requestLocation()
        }
        if (progress.atRestroom || progress.arrived) renderProgress()
        else if (savedInstanceState == null) requestLocation()
        else show("Navigation paused after screen recreation. Tap Resume guidance when ready.")
    }

    private fun hasPreciseLocation() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (busy || progress.arrived) return
        if (hasPreciseLocation()) { initialize(); return }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(this).setTitle("Precise location for navigation")
                .setMessage("IBS uses your device location to show your position and give turn-by-turn guidance while this screen is open.")
                .setPositiveButton("Continue") { _, _ -> launchPermission() }
                .setNegativeButton("Cancel", null).show()
        } else if (permissionAsked) {
            AlertDialog.Builder(this).setTitle("Allow precise location")
                .setMessage("Enable precise location for IBS in app settings, then return and tap Resume guidance.")
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${packageName}")))
                }.setNegativeButton("Cancel", null).show()
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
                    value.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE)
                    value.removeArrivalListener(arrivalListener)
                    value.addArrivalListener(arrivalListener)
                    calculate(value, ticket)
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
            show("Calculating guidance from ${if (demo) "the simulated position" else "your live location"}…")
            value.setDestinations(waypoints, RoutingOptions().travelMode(RoutingOptions.TravelMode.DRIVING)) { description ->
                if (disposed || ticket != generation) return@setDestinations
                handler.removeCallbacks(timeout)
                busy = false
                if (description.routeStatus == Navigator.RouteStatus.OK) {
                    ready = true
                    value.setAudioGuidanceSettings(AudioGuidanceSettings.builder()
                        .setGuidanceMode(AudioGuidanceSettings.GuidanceMode.VOICE_ALERTS_AND_GUIDANCE).build())
                    val fragment = supportFragmentManager.findFragmentById(R.id.navigation_map) as NavigationFragment
                    fragment.getMapAsync { map ->
                        if (!disposed) map.followMyLocation(GoogleMap.CameraPerspective.TILTED)
                    }
                    if (foreground) resumeGuidance()
                    if (ready) show(if (foreground) "Guidance active. ${tripDescription()}" else "Guidance paused while IBS is in the background.")
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

    private fun tripDescription(): String =
        (if (demo) "DEMO SIMULATION • " else "") +
        (if (plan.restroom != null && !progress.stopVisited) "Via ${plan.restroom!!.name} (development database stop). " else "") +
        "To ${plan.destination.name}. Route and detour can differ from the preview."

    private fun resumeGuidance() {
        try {
            if (!hasPreciseLocation()) { fail("Precise location permission is required. Retry to allow it."); return }
            navigator?.startGuidance()
            if (demo) {
                if (simulationStarted) navigator?.simulator?.resume()
                else {
                    navigator?.simulator?.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(2f))
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
            progress.arrived -> "You have arrived at ${plan.destination.name}. Navigation stopped."
            progress.atRestroom -> "Arrived at ${plan.restroom?.name ?: "restroom"}. Guidance paused for your visit."
            else -> tripDescription()
        })
    }

    private fun show(message: String) {
        if (disposed) return
        status.text = (if (demo && !message.startsWith("DEMO")) "DEMO SIMULATION • " else "") + message
        action.visibility = if (progress.arrived || ready) View.GONE else View.VISIBLE
        action.isEnabled = !busy
        action.text = when {
            progress.atRestroom -> "Continue to destination"
            busy -> "Please wait…"
            else -> "Resume guidance"
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        if (ready && !progress.atRestroom && !progress.arrived) {
            resumeGuidance()
            if (ready) show("Guidance active. ${tripDescription()}")
        }
    }

    override fun onStop() {
        foreground = false
        stopGuidance()
        if (ready) show("Guidance paused while IBS is in the background.")
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionAsked", permissionAsked)
        outState.putBoolean("stopVisited", progress.stopVisited)
        outState.putBoolean("atRestroom", progress.atRestroom)
        outState.putBoolean("arrived", progress.arrived)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        disposed = true
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
        internal fun decodePlan(intent: Intent): NavigationPlan? {
            val origin = decode(intent.getBundleExtra("origin")) ?: return null
            val destination = decode(intent.getBundleExtra("destination")) ?: return null
            val stop = if (intent.hasExtra("restroom")) decode(intent.getBundleExtra("restroom")) ?: return null else null
            return NavigationPlan(origin, destination, stop).takeIf { it.valid }
        }
        fun intent(context: Context, route: DrivingRoute, demo: Boolean): Intent {
            val plan = NavigationPlan.from(route)
            return Intent(context, LiveNavigationActivity::class.java).apply {
                putExtra("origin", encode(plan.origin))
                putExtra("destination", encode(plan.destination))
                plan.restroom?.let { putExtra("restroom", encode(it)) }
                putExtra("demo", BuildConfig.DEBUG && demo)
            }
        }
    }
}
