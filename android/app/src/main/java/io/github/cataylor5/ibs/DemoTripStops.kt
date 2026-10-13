package io.github.cataylor5.ibs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class DemoTripStop(
    val id: String,
    val name: String,
    val detourMinutes: Int,
    val freePublic: Boolean,
    val accessDescription: String
)
// Describes one fictional stop and its sample detour time.

private val demoTripStops = listOf(
    DemoTripStop(
        id = "demo-cafe",
        name = "Demo Café",
        detourMinutes = 3,
        freePublic = false,
        accessDescription = "Customers only; purchase required."
    ),
    DemoTripStop(
        id = "demo-park",
        name = "Demo Community Park",
        detourMinutes = 7,
        freePublic = true,
        accessDescription = "Free public restroom."
    ),
    DemoTripStop(
        id = "demo-station",
        name = "Demo Transit Station",
        detourMinutes = 9,
        freePublic = false,
        accessDescription = "Paid public restroom."
    ),
    DemoTripStop(
        id = "demo-library",
        name = "Demo Library",
        detourMinutes = 12,
        freePublic = true,
        accessDescription = "Free public restroom."
    )
)
// These locations and times are fictional examples, not route results.

@Composable
fun DemoTripStops(
    maxDetourMinutes: Int,
    freePublicOnly: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedStopId by rememberSaveable(
        maxDetourMinutes,
        freePublicOnly
    ) {
        mutableStateOf<String?>(null)
    }
    // Clears the selection when the filters change.

    val matchingStops = demoTripStops
        .filter { stop ->
            stop.detourMinutes <= maxDetourMinutes &&
                    (!freePublicOnly || stop.freePublic)
        }
        .sortedBy { stop -> stop.detourMinutes }
    // Applies both preferences and puts the shortest demo detour first.

    val selectedStop = matchingStops.find {
        it.id == selectedStopId
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Try selecting a demo stop",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Fictional locations and detour times. " +
                    "These examples do not depend on your trip's addresses.",
            style = MaterialTheme.typography.bodySmall
        )
        // Makes it clear that no real route search has happened.

        if (matchingStops.isEmpty()) {
            Text(
                text = "No demo stops match. Try more detour time " +
                        "or change Availability to Any."
            )
        }

        matchingStops.forEach { stop ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(stop.accessDescription)

                    Text(
                        text = "Demo detour: ${stop.detourMinutes} extra minutes"
                    )

                    TextButton(
                        onClick = {
                            selectedStopId =
                                if (selectedStopId == stop.id) {
                                    null
                                } else {
                                    stop.id
                                }
                        }
                    ) {
                        Text(
                            if (selectedStopId == stop.id) {
                                "Remove selection"
                            } else {
                                "Select stop"
                            }
                        )
                    }
                }
            }
        }

        if (selectedStop != null) {
            Text(
                text = "Selected demo stop: ${selectedStop.name}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = "Selection only. No route has been changed.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}