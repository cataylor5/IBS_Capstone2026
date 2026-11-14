package io.github.cataylor5.ibs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomePreferenceControls(trip: TripPlanningViewModel, hasDestination: Boolean) {
    val prefs = trip.restroomPreferences
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Access", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IbsFilterChip(prefs.access.isEmpty(), onClick = { trip.updatePreferences(prefs.copy(access = emptySet())) },
                label = { Text("Any") })
            listOf(RestroomAccess.PUBLIC to "Public access", RestroomAccess.CUSTOMER_ONLY to "Patrons only",
                RestroomAccess.MEMBERSHIP_REQUIRED to "Membership required").forEach { (value, label) ->
                IbsFilterChip(value in prefs.access, onClick = {
                    trip.updatePreferences(prefs.copy(access = if (value in prefs.access) prefs.access - value else prefs.access + value))
                }, label = { Text(label) })
            }
        }
        Text(if (hasDestination) "Detour time" else "Drive time", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetourMinuteOptions.forEach { minute ->
                IbsFilterChip(trip.maxDetourMinutes == minute, onClick = { trip.setDetour(minute) },
                    label = { Text("$minute min") })
            }
        }
        Text("Rating • minimum", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IbsFilterChip(prefs.minimumStars == null, onClick = { trip.updatePreferences(prefs.copy(minimumStars = null)) },
                label = { Text("Any") })
            (5 downTo 1).forEach { stars ->
                IbsFilterChip(prefs.minimumStars == stars,
                    onClick = { trip.updatePreferences(prefs.copy(minimumStars = if (prefs.minimumStars == stars) null else stars)) },
                    label = { Text("$stars ★") })
            }
        }
        Text("Privacy", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IbsFilterChip(prefs.privacy == null, onClick = { trip.updatePreferences(prefs.copy(privacy = null)) },
                label = { Text("Any") })
            listOf(PrivacyPreference.PRIVATE to "Private", PrivacyPreference.STALLS to "Stalls").forEach { (value, label) ->
                IbsFilterChip(prefs.privacy == value,
                    onClick = { trip.updatePreferences(prefs.copy(privacy = if (prefs.privacy == value) null else value)) },
                    label = { Text(label) })
            }
        }
    }
}
