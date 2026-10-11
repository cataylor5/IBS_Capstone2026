package io.github.cataylor5.ibs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

@Composable
fun TripPlanningForm(
    startLatitude: Double?,
    startLongitude: Double?,
    onLocationSelected: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var useCurrentLocation by rememberSaveable {
        mutableStateOf(true)
    }
    // This selects whether to use coordinates or a typed starting point.

    var startingPoint by rememberSaveable {
        mutableStateOf("")
    }

    var destination by rememberSaveable {
        mutableStateOf("")
    }
    // These hold the text entered into the form.

    var attemptedPreview by rememberSaveable {
        mutableStateOf(false)
    }
    // Show missing-input errors after the first preview attempt.

    var showPreview by rememberSaveable {
        mutableStateOf(false)
    }
    // This controls whether the summary popup is visible.

    val focusManager = LocalFocusManager.current
    // This lets us remove focus from a text field when previewing.

    val hasCoordinates =
        startLatitude != null && startLongitude != null
    // Both coordinates are required for a location-based start.

    val startMissing = if (useCurrentLocation) {
        !hasCoordinates
    } else {
        startingPoint.isBlank()
    }
    // Check whichever starting-point option is selected.

    val destinationMissing = destination.isBlank()
    // Spaces alone do not count as a destination.

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
                text = "Where are you starting?",
                style = MaterialTheme.typography.titleMedium
            )

            FilterChip(
                selected = useCurrentLocation,
                onClick = {
                    useCurrentLocation = true
                    attemptedPreview = false
                    showPreview = false
                },
                label = { Text("Use current location") }
            )

            FilterChip(
                selected = !useCurrentLocation,
                onClick = {
                    useCurrentLocation = false
                    attemptedPreview = false
                    showPreview = false
                },
                label = { Text("Enter a starting point") }
            )
            // These choices switch between location and manual entry.

            if (useCurrentLocation) {
                CurrentLocationButton(
                    onLocationSelected = { latitude, longitude ->
                        onLocationSelected(latitude, longitude)
                        showPreview = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // Reuse the location button we already built.

                if (hasCoordinates) {
                    Text(
                        text = "Selected coordinates: " +
                                "$startLatitude, $startLongitude",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (attemptedPreview && startMissing) {
                    Text(
                        text = "Get your location or enter a starting point.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                OutlinedTextField(
                    value = startingPoint,
                    onValueChange = {
                        startingPoint = it
                        showPreview = false
                    },
                    label = { Text("Starting point") },
                    placeholder = { Text("Address, place, or city") },
                    singleLine = true,
                    isError = attemptedPreview && startMissing,
                    supportingText = {
                        if (attemptedPreview && startMissing) {
                            Text("Enter a starting point.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // Manual entry also works when location access is denied.
            }

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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preview trip")
            }
            // Only open the summary when both inputs are present.

            Text(
                text = "Planning preview only. Addresses are not yet " +
                        "verified and no route is calculated.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showPreview && !startMissing && !destinationMissing) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("Your trip preview") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (useCurrentLocation) {
                        Text("From: Selected device location")
                        Text("$startLatitude, $startLongitude")
                    } else {
                        Text("From: ${startingPoint.trim()}")
                    }

                    Text("To: ${destination.trim()}")
                    // Trim removes extra spaces at the beginning and end.

                    Text(
                        "This is a summary of your selections. " +
                                "Route and restroom suggestions come later."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPreview = false }
                ) {
                    Text("Back to planning")
                }
            }
        )
    }
}