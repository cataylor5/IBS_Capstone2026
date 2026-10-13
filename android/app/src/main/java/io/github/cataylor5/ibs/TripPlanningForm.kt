package io.github.cataylor5.ibs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun TripPlanningForm(
    startLatitude: Double?,
    startLongitude: Double?,
    onLocationSelected: (Double, Double) -> Unit,
    freePublicOnly: Boolean,
    onFreePublicOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var useCurrentLocation by rememberSaveable {
        mutableStateOf(false)
    }
    // Begin with an empty, editable starting-location field.

    var startingPoint by rememberSaveable {
        mutableStateOf("")
    }

    var destination by rememberSaveable {
        mutableStateOf("")
    }
    // Remember the text entered into both fields.

    var maxDetourMinutes by rememberSaveable {
        mutableStateOf(10)
    }
    // Default to 10 extra driving minutes.

    var attemptedPreview by rememberSaveable {
        mutableStateOf(false)
    }

    var showPreview by rememberSaveable {
        mutableStateOf(false)
    }
    // Control validation messages and the preview popup.

    var locationStatus by remember {
        mutableStateOf("")
    }

    var isLocating by remember {
        mutableStateOf(false)
    }
    // Track feedback from the location button.

    var selectedStopId by rememberSaveable(
        startingPoint,
        destination,
        useCurrentLocation,
        startLatitude,
        startLongitude,
        maxDetourMinutes,
        freePublicOnly
    ) {
        mutableStateOf<String?>(null)
    }
// Keeps the selection outside the popup.
// Resets it when the trip details or preferences change.

    val selectedStop = demoTripStops.find {
        it.id == selectedStopId
    }
// Looks up the full details of the selected stop.

    val focusManager = LocalFocusManager.current

    val hasCoordinates =
        startLatitude != null && startLongitude != null

    val startMissing = if (useCurrentLocation) {
        !hasCoordinates
    } else {
        startingPoint.isBlank()
    }

    val destinationMissing = destination.isBlank()
    // Check that a starting point and destination are available.

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Plan your trip",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Out of way by:",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15).forEach { minutes ->
                    FilterChip(
                        selected = maxDetourMinutes == minutes,
                        onClick = {
                            maxDetourMinutes = minutes
                            showPreview = false
                        },
                        label = { Text("$minutes") },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
            // Arrange the rounded detour choices side by side.

            Text(
                text = "Extra driving minutes per stop.",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Availability:",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = freePublicOnly,
                    onClick = {
                        onFreePublicOnlyChange(true)
                        showPreview = false
                    },
                    label = { Text("Free") },
                    shape = RoundedCornerShape(50)
                )

                FilterChip(
                    selected = !freePublicOnly,
                    onClick = {
                        onFreePublicOnlyChange(false)
                        showPreview = false
                    },
                    label = { Text("Any") },
                    shape = RoundedCornerShape(50)
                )
            }
            // Free and Any share the existing restroom-list preference.

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrentLocationButton(
                    onLocationSelected = { latitude, longitude ->
                        onLocationSelected(latitude, longitude)
                        useCurrentLocation = true
                        attemptedPreview = false
                        showPreview = false
                        focusManager.clearFocus()
                    },
                    compact = true,
                    onStatusChanged = { message, loading ->
                        locationStatus = message
                        isLocating = loading
                    },
                    modifier = Modifier.width(56.dp)
                )
                // Request a location directly from the small GPS button.

                OutlinedTextField(
                    value = if (useCurrentLocation && hasCoordinates) {
                        "Using current location"
                    } else {
                        startingPoint
                    },
                    onValueChange = {
                        startingPoint = it
                        showPreview = false
                    },
                    label = { Text("Starting location") },
                    singleLine = true,
                    readOnly = useCurrentLocation,
                    enabled = !isLocating,
                    isError = attemptedPreview && startMissing,
                    trailingIcon = {
                        if (useCurrentLocation) {
                            TextButton(
                                onClick = {
                                    useCurrentLocation = false
                                    startingPoint = ""
                                    locationStatus = ""
                                    showPreview = false
                                }
                            ) {
                                Text("Edit")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // Always display the field beside the GPS button.
                // Edit switches from coordinates to a typed starting point.
            }

            if (attemptedPreview && startMissing) {
                Text(
                    text = "Enter a starting location or tap GPS.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (
                locationStatus.isNotBlank() &&
                locationStatus !=
                "Use your location as the trip's starting point."
            ) {
                Text(
                    text = locationStatus,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Show location progress or errors below the field.

            OutlinedTextField(
                value = destination,
                onValueChange = {
                    destination = it
                    showPreview = false
                },
                label = { Text("Destination") },
                placeholder = { Text("Where are you going?") },
                singleLine = true,
                isError = attemptedPreview && destinationMissing,
                supportingText = {
                    if (attemptedPreview && destinationMissing) {
                        Text("Enter a destination.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    attemptedPreview = true

                    if (!startMissing && !destinationMissing) {
                        focusManager.clearFocus()
                        showPreview = true
                    }
                },
                enabled = !isLocating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preview trip")
            }
            // Wait for an active location request before allowing preview.

            Text(
                text = "Preview only. Addresses and routes aren't verified yet.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (selectedStop != null) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Your planned stop",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = selectedStop.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(selectedStop.accessDescription)

                Text(
                    text = "Demo detour: " +
                            "${selectedStop.detourMinutes} extra minutes"
                )

                Text(
                    text = "Fictional demo stop. No route has been calculated.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            showPreview = true
                        },
                        enabled = !isLocating &&
                                !startMissing &&
                                !destinationMissing
                    ) {
                        Text("Change")
                    }
                    // Reopens the preview with the current selection.

                    TextButton(
                        onClick = {
                            selectedStopId = null
                        }
                    ) {
                        Text("Remove")
                    }
                    // Clears the selection and hides this card.
                }
            }
        }
    }

    if (showPreview && !startMissing && !destinationMissing) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("Your trip preview") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (useCurrentLocation) {
                        Text("From: Current location")
                        Text("$startLatitude, $startLongitude")
                    } else {
                        Text("From: ${startingPoint.trim()}")
                    }

                    Text("To: ${destination.trim()}")

                    Text(
                        "Maximum detour: $maxDetourMinutes " +
                                "extra driving minutes per stop."
                    )

                    Text(
                        if (freePublicOnly) {
                            "Availability: Free public restrooms."
                        } else {
                            "Availability: Any, including paid " +
                                    "and customer-only restrooms."
                        }
                    )

                    Text(
                        "Detour time includes returning to the route, " +
                                "but not time spent at the restroom."
                    )

                    Text("No route has been calculated yet.")
                    DemoTripStops(
                        maxDetourMinutes = maxDetourMinutes,
                        freePublicOnly = freePublicOnly,
                        selectedStopId = selectedStopId,
                        onStopSelected = { stopId ->
                            selectedStopId = stopId
                        }
                    )
// Shares one selection between the popup and the planned-stop card.
// Passes the form's preferences into the demo results.
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreview = false }) {
                    Text("Back to planning")
                }
            }
        )
    }
}