package io.github.cataylor5.ibs
// This places the file in our app's existing package.

import android.content.pm.PackageManager
import androidx.compose.material3.FilterChip
// This provides a small selectable control for filtering results.

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
// this will expand our button options

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// These imports handle starting the Android screen and displaying Compose content.

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// These imports arrange items on the screen and let the page scroll.

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// These imports provide the buttons, cards, text, and screen layout.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
// These imports let the screen remember and respond to changing values.

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.rememberCameraPositionState

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.places.api.Places
import io.github.cataylor5.ibs.ui.theme.IBSTheme
// These imports handle alignment, spacing, previews, and our existing theme.


class MainActivity : ComponentActivity() {
    // This is the Android screen that opens when the app starts.

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android calls this function when it creates the screen.

        super.onCreate(savedInstanceState)
        // This runs the normal Android screen setup.

        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = applicationInfo.metaData?.getString("com.google.android.geo.API_KEY")
            if (apiKey != null && apiKey.isNotBlank() && !Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
            }
        } catch (e: Exception) {
            android.util.Log.e("Places", "Places initialization failed. Check local configuration.")
        }
        // Initialize Places SDK safely with the key from AndroidManifest.xml.

        enableEdgeToEdge()
        // This lets the app draw behind the system bars.

        setContent {
            IBSTheme {
                IbsLaunchScreen(
                    lifecycle = this@MainActivity.lifecycle
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        IBSHomeScreen(
                            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun IBSHomeScreen(
    modifier: Modifier = Modifier,
    tripViewModel: TripPlanningViewModel = viewModel(),
    restroomsViewModel: RestroomsViewModel = viewModel()
) {
    DestinationTripFlow(modifier, tripViewModel, restroomsViewModel)
}


@Composable
fun RestroomCard(
    name: String,
    accessDescription: String,
    onViewDetails: () -> Unit
    // This is the action to perform when View details is pressed.
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = accessDescription,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "No ratings yet",
                style = MaterialTheme.typography.bodySmall
            )

            TextButton(onClick = onViewDetails) {
                Text("View details")
            }
            // This runs the action supplied by the home screen.
        }
    }
}


@Preview(showBackground = true)
@Composable
fun IBSHomeScreenPreview() {
    // This provides an Android Studio preview of the home screen.

    IBSTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            IBSHomeScreen(
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            )
        }
    }
}

@Composable
fun RestroomDetailsDialog(
    restroom: Restroom,
    onClose: () -> Unit
) {
    val accessDescription = when (restroom.accessType) {
        "public" -> "Open to the public."
        "customers_only" -> "Customers only. A purchase may be required."
        else -> "Access requirements have not been confirmed."
    }
    // This explains the access rules in readable language.

    val feeDescription = when (restroom.feeRequired) {
        true -> "A restroom fee is required. Amount not provided."
        false -> "No separate restroom fee."
        null -> "Restroom fee information is unknown."
    }
    // A separate restroom fee is different from a required purchase.

    AlertDialog(
        onDismissRequest = onClose,
        // Close when the user taps outside or presses Back.

        title = {
            Text(restroom.name)
        },

        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(accessDescription)
                Text(feeDescription)
                Text("No ratings yet.")
                Text("Fictional demo location.")
            }
        },

        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@Composable
fun IBSMap(modifier: Modifier = Modifier, route: DrivingRoute? = null) {
    val startingLocation = LatLng(36.0726, -79.7920)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startingLocation, 12f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    val points = remember(route) {
        route?.points?.map { LatLng(it.latitude, it.longitude) } ?: emptyList()
    }
    LaunchedEffect(route, mapLoaded) {
        if (route != null && mapLoaded && points.isNotEmpty()) {
            val bounds = LatLngBounds.builder().apply {
                points.forEach { include(it) }
                include(LatLng(route.origin.latitude!!, route.origin.longitude!!))
                include(LatLng(route.destination.latitude!!, route.destination.longitude!!))
                route.stop?.let { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            val update = if (bounds.northeast == bounds.southwest)
                CameraUpdateFactory.newLatLngZoom(bounds.center, 16f)
            else CameraUpdateFactory.newLatLngBounds(bounds, 64)
            cameraPositionState.animate(update)
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = true),
        onMapLoaded = { mapLoaded = true }
    ) {
        if (route != null) {
            Polyline(points = points, color = MaterialTheme.colorScheme.primary, width = 12f)
            route.stop?.let { stop ->
                val stopIcon = remember { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) }
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(stop.latitude, stop.longitude)),
                    title = "Restroom: ${stop.name}",
                    snippet = stop.address,
                    icon = stopIcon
                )
            }
            Marker(
                state = rememberUpdatedMarkerState(position = LatLng(route.origin.latitude!!, route.origin.longitude!!)),
                title = "Start",
                snippet = route.origin.displayText
            )
            Marker(
                state = rememberUpdatedMarkerState(position = LatLng(route.destination.latitude!!, route.destination.longitude!!)),
                title = "Destination",
                snippet = route.destination.displayText
            )
        }
    }
}
