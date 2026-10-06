package io.github.cataylor5.ibs
// This places the file in our app's existing package.

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

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
// These imports let the screen remember and respond to changing values.

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.cataylor5.ibs.ui.theme.IBSTheme
// These imports handle alignment, spacing, previews, and our existing theme.


class MainActivity : ComponentActivity() {
    // This is the Android screen that opens when the app starts.

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android calls this function when it creates the screen.

        super.onCreate(savedInstanceState)
        // This runs the normal Android screen setup.

        enableEdgeToEdge()
        // This lets the app draw behind the system bars.

        setContent {
            // This starts the Compose interface.

            IBSTheme {
                // This applies our existing app colors and text styles.

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // This provides the screen layout and system-bar spacing.

                    IBSHomeScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                    // This displays our home screen within that spacing.
                }
            }
        }
    }
}


@Composable
fun IBSHomeScreen(modifier: Modifier = Modifier) {
    // This function describes what appears on the home screen.

    var showRestrooms by rememberSaveable { mutableStateOf(false) }
    // This starts with the sample list hidden and remembers the selection.

    var freePublicOnly by rememberSaveable { mutableStateOf(false) }
// This remembers whether the free-public-restroom filter is enabled.

    var selectedRestroomId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
// This remembers the selected restroom's ID.
// A null value means no restroom details are open.

    val selectedRestroom = sampleRestrooms.find {
        it.id == selectedRestroomId
    }
// Find the sample record whose ID matches the user's selection.

    if (selectedRestroom != null) {
        RestroomDetailsDialog(
            restroom = selectedRestroom,
            onClose = { selectedRestroomId = null }
        )
        // Clearing the selected ID removes the popup.
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // This uses the available screen space.

            .verticalScroll(rememberScrollState())
            // This lets us scroll when the content exceeds the screen height.

            .padding(24.dp),
        // This adds space around the page's contents.

        verticalArrangement = Arrangement.spacedBy(16.dp)
        // This adds consistent spacing between the items.
    ) {
        Text(
            text = "IBS",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        // This displays the app title using our theme's main color.

        Text(
            text = "A bathroom break should be part of the route.",
            style = MaterialTheme.typography.titleMedium
        )
        // This displays the introductory message.

        Text(
            text = "Demo mode: the restroom examples below are fictional.",
            style = MaterialTheme.typography.bodyMedium
        )
        // This makes it clear that we are displaying practice data.

        Button(
            onClick = { showRestrooms = !showRestrooms },
            // Each tap switches the sample list between visible and hidden.

            modifier = Modifier.fillMaxWidth()
            // This makes the button fill the available width.
        ) {
            Text(
                text = if (showRestrooms) {
                    "Hide sample restrooms"
                } else {
                    "Find a Restroom"
                }
            )
            // This changes the button's wording to match its next action.
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            IBSMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
// This gives the real map a fixed-height space on the home screen.

        if (showRestrooms) {
            // This section appears after the user requests the sample list.

            Text(
                text = "Sample restrooms",
                style = MaterialTheme.typography.titleLarge
            )

            FilterChip(
                selected = freePublicOnly,
                // This controls whether the filter looks selected.

                onClick = { freePublicOnly = !freePublicOnly },
                // Each tap switches the filter on or off.

                label = { Text("Free public restrooms only") }
            )

            val visibleRestrooms = sampleRestrooms.filter { restroom ->
                // This builds a list containing only the records we want to display.

                !freePublicOnly ||
                        (restroom.accessType == "public" && restroom.feeRequired == false)
                // With the filter off, include everything.
                // With it on, require public access and a confirmed lack of fees.
            }

            Text(
                text = "Results: ${visibleRestrooms.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            // This displays how many sample records match the current filter.

            if (visibleRestrooms.isEmpty()) {
                Text(
                    text = "No restrooms match this filter.",
                    style = MaterialTheme.typography.bodyMedium
                )
                // This explains an empty result instead of leaving a blank area.
            }

            visibleRestrooms.forEach { restroom ->
                // This creates one card for each matching restroom.

                val accessLabel = when (restroom.accessType) {
                    "public" -> "Public access"
                    "customers_only" -> "Customers only"
                    else -> "Access unknown"
                }
                // This turns the stored access value into readable wording.

                val feeLabel = when (restroom.feeRequired) {
                    true -> "Fee required"
                    false -> "No restroom fee"
                    null -> "Fee unknown"
                }
                // This describes the fee without treating missing information as free.

                RestroomCard(
                    name = restroom.name,
                    accessDescription = "$accessLabel • $feeLabel",
                    onViewDetails = { selectedRestroomId = restroom.id }
                )
// Pressing this card's details button selects this restroom.
            }
        } else {
            Text(
                text = "Tap Find a Restroom to explore the sample list.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
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
                modifier = Modifier.padding(innerPadding)
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
fun IBSMap(modifier: Modifier = Modifier) {
    val startingLocation = LatLng(36.0726, -79.7920)
    // This sets our starting map view around Greensboro.

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            startingLocation,
            12f
        )
    }
    // This holds the map's viewing position and starting zoom level.

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true
        )
    )
    // This displays the map with plus and minus zoom buttons.
}