package io.github.cataylor5.ibs

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import io.github.cataylor5.ibs.ui.theme.IbsBrand
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun DestinationTripFlow(modifier: Modifier, trip: TripPlanningViewModel, businesses: RestroomsViewModel) {
    val context = LocalContext.current
    val requestLocation = rememberHomeLocationRequest(trip)
    val access = rememberLocalBackendAccess(businesses)
    val origin = trip.origin
    val destination = trip.destination
    val hasDestination = destination.endpoint.isResolved
    val destinationValid = destination.endpoint.displayText.isBlank() || hasDestination
    var now by remember { mutableStateOf(Instant.now()) }
    HomeLocationUpdates(trip)
    LaunchedEffect(Unit) {
        if (!trip.homeLocationStarted) {
            trip.homeLocationStarted = true
            requestLocation()
        }
    }
    LaunchedEffect(origin.endpoint, origin.isBusy) {
        if (origin.endpoint.isResolved && !origin.isBusy)
            access.request(RestroomRequest.LOAD, null)
    }
    LaunchedEffect(origin.endpoint, origin.isBusy, destination.endpoint, destination.isBusy,
        businesses.restrooms, businesses.loading) {
        if (!origin.isBusy && !destination.isBusy && !businesses.loading)
            businesses.checkDetours(origin.endpoint, destination.endpoint, false)
    }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = Instant.now() } }
    val canStart = origin.endpoint.isResolved && !origin.isBusy && !origin.isError && destinationValid && !destination.isBusy
    val estimatesCurrent = businesses.estimatesMatch(origin.endpoint, destination.endpoint, false)
    val matches = if (canStart && estimatesCurrent && businesses.error == null)
        homeStops(businesses.restrooms, trip.maxDetourMinutes,
            businesses.detourSeconds, businesses.travelSeconds, hasDestination,
            trip.restroomPreferences) else emptyList()
    fun start(business: BackendRestroom? = null) {
        if (!canStart) return
        val plan = NavigationPlan.fromEndpoints(origin.endpoint, destination.endpoint.takeIf { it.isResolved })
        val selected = business?.let { plan.withBusiness(plan.origin, it) } ?: plan
        context.startActivity(LiveNavigationActivity.intent(context, selected, demo = true,
            freeOnly = false, detourMinutes = trip.maxDetourMinutes, selectedId = business?.id,
            preferences = trip.restroomPreferences))
    }

    Column(modifier.fillMaxSize().imePadding()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            IbsBrandHeader()
        }
        item { HomePreferenceControls(trip, hasDestination) }
        if (origin.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Getting your location…") }
        if (origin.isError) item {
            Text(origin.message ?: "Location unavailable.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = requestLocation) { Text("Retry location") }
            LocationSettingsActions()
        }
        access.message?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = { access.request(RestroomRequest.REFRESH, null) }) { Text("Retry") }
                TextButton(onClick = access.openSettings) { Text("App Settings") }
            }
        } }
        businesses.error?.let { error -> item {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { access.request(RestroomRequest.REFRESH, null) }) { Text("Retry businesses") }
        } }
        item {
            Text("Restroom options", style = MaterialTheme.typography.titleMedium)
            Text(if (hasDestination) "Up to ${trip.maxDetourMinutes} extra driving minutes • quickest to reach first"
                else "Within ${trip.maxDetourMinutes} driving minutes • quickest to reach first", style = MaterialTheme.typography.bodySmall)
        }
        if (businesses.loading || businesses.checkingDetours || (canStart && !estimatesCurrent)) item {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Checking driving times…", style = MaterialTheme.typography.bodySmall)
        }
        if (businesses.detourErrors.isNotEmpty()) item {
            Text(businesses.detourErrors.values.firstOrNull() ?: "Some driving times are unavailable.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { businesses.retryDetours(origin.endpoint, destination.endpoint, false) }) {
                Text("Retry estimates")
            }
        }
        if (canStart && estimatesCurrent && businesses.hasLoaded && !businesses.loading && !businesses.checkingDetours &&
            businesses.error == null && matches.isEmpty()) item {
            Text(if (hasDestination) "No matching businesses. Broaden your filters or increase the detour time."
                else "No matching businesses. Broaden your filters or increase the drive time.")
        }
        items(matches, key = { it.restroom.id }) { entry ->
            OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.restroom.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.details, style = MaterialTheme.typography.bodySmall)
                        Text("${entry.restroom.accessLabel} • ${entry.restroom.privacyLabel}", style = MaterialTheme.typography.bodySmall)
                        entry.offRouteLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(businessHoursCountdown(entry.restroom.businessHours, now), style = MaterialTheme.typography.bodySmall)
                        entry.restroom.address?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        TextButton(onClick = { access.request(RestroomRequest.DETAILS, entry.restroom.id) }) { Text("View details") }
                    }
                    OutlinedButton(onClick = { start(entry.restroom) }, border = BorderStroke(1.dp, Color(io.github.cataylor5.ibs.ui.theme.IbsAppearance.palette.actionText)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(io.github.cataylor5.ibs.ui.theme.IbsAppearance.palette.actionText))) { Text("Go Here!") }
                }
            }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DestinationSearchField(trip)
            Button(onClick = { start() }, enabled = canStart,
                colors = ButtonDefaults.buttonColors(containerColor = io.github.cataylor5.ibs.ui.theme.IbsBrand.Cobalt,
                    contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Let’s Go", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    }
    BackendRestroomDetailsDialog(businesses, access, trip, showRouteAction = false)
}

@Composable
internal fun LocationSettingsActions() {
    val context = LocalContext.current
    Row {
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null))) }) { Text("App Settings") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
            Text("Location Settings")
        }
    }
}
