# Start now: driving routes

Select a resolved origin (GPS or Places search) and destination, tap Preview trip, then Start now. The backend calls Google Routes Compute Routes and the app draws the returned road geometry, adds endpoint markers, fits the map, and shows provider-supplied distance and estimated driving time.

To include a restroom, select both endpoints, open Find a Restroom, and choose Use this stop on a database record (or in its details). The backend resolves that record's coordinates and access information, computes direct and through-stop driving routes with identical options, and returns the extra driving time. A direct route does not need to be calculated beforehand.

The 5/10/15-minute allowance is enforced against unrounded added driving seconds. Free requires confirmed public access, no fee, and no required purchase; missing fee information is not false. Employee-only or unconfirmed-access records cannot be used as stops. Database entries are currently seeded test records and remain labeled accordingly.

The accepted route includes one restroom marker and its name, estimated total time and added driving time. Restroom visit time is excluded. Remove restroom stop restores the returned direct-route geometry without another provider call. If lookup, eligibility, allowance or network checks fail, the current route is preserved.

Automatic corridor search/ranking remains a future milestone. The fictional demo-stop picker remains separate and never supplies route waypoints.

## In-app navigation

After calculating a route, tap Navigate and confirm Start live guidance. Navigation SDK for Android 8.1.0 supplies the navigation map, spoken instructions, remaining time/distance and live rerouting. It replaces the standalone Maps SDK dependency while the existing home map remains in Compose.

Enable Navigation SDK for Android in the billing-enabled Cloud project and allow that API on the existing Android-restricted Maps/Places key. Keep the package and signing SHA-1 restrictions. The manifest loads the same local MAPS_API_KEY; the separate backend Routes key is unchanged. The Google navigation terms must be accepted by the person using the app.

Live navigation starts at the device location, not a manual preview origin. The selected database restroom is the first destination and the final endpoint is second. At the restroom, guidance pauses until Continue to destination is tapped. Final arrival stops guidance. Navigation recalculates its own route, so preview geometry/time and the 5/10/15-minute comparison are not guarantees during live rerouting; automatic live detour-limit enforcement is not implemented.

The new navigation screen requests precise foreground location with denial/rationale/settings recovery. No background-location permission is requested. Guidance pauses when the screen is not visible and resumes on return; Stop navigation or Back closes the session and clears destinations, simulation and listeners. Screen recreation preserves restroom/final-arrival progress and shows an explicit Resume guidance action, which recalculates from the current location. It never restores a loading spinner.

Debug builds also provide Demo navigation (simulated), explicitly separate from live GPS. This uses the SDK simulator at 2x speed from the preview origin. After recreation, resume recalculates the simulation from the original origin (or the restroom after its visit). Simulation is unset when the session closes. Demo movement is not evidence of real GPS driving accuracy.

### Verification on November 10, 2026

The debug APK built, installed and launched on the Android 17 emulator. Unit and device tests passed, including waypoint ordering, stop continuation, validation and private Activity/Intent contracts. Home GPS selection, Places destination selection and backend route preview still succeeded. The navigation screen opened; error/retry, Stop navigation and rotation recovery were exercised.

Both live guidance and debug simulation returned Navigator.RouteStatus.NETWORK_ERROR during setDestinations, including after a fresh process start following the Cloud update. Basic emulator DNS/connectivity to Google succeeded. This does not verify the SDK endpoint's connectivity or authorization. No successful turn-by-turn route, simulated movement, spoken instructions or real driving has yet been verified.

If NETWORK_ERROR persists, check the Cloud project that owns the actual Android key: Navigation SDK API enabled, allowed API restrictions include Navigation SDK, billing active, navigation quotas available, Android package io.github.cataylor5.ibs and debug signing SHA-1 match. Check Navigation SDK request metrics/errors in that project at the test time. Allow time for restriction changes to apply and retry. Check emulator/device network, VPN/proxy/firewall access to Google SDK services. Do not remove key restrictions or paste keys into logs/chat.

Official navigation references:
- https://developers.google.com/maps/documentation/navigation/android-sdk/android-studio-setup
- https://developers.google.com/maps/documentation/navigation/android-sdk/route
- https://developers.google.com/maps/documentation/navigation/android-sdk/events

## Backend configuration

Enable Routes API in a billing-enabled Google Cloud project. Use a separate server key restricted to Routes API and the backend server's public egress IP. Set GOOGLE_ROUTES_API_KEY in the ignored backend/.env and restart the backend with npm.cmd start. Do not reuse the Android-restricted Maps/Places key or put the server key in Android source, BuildConfig, or Git.

The example environment file has a placeholder. Release builds still require a deployed HTTPS backend URL; the existing debug build uses the emulator host alias and Android 17 local-network permission flow.

Official references:
- https://developers.google.com/maps/documentation/routes/get-api-key
- https://developers.google.com/maps/documentation/routes/compute_route_directions
- https://developers.google.com/maps/documentation/routes/intermed_waypoints

## API contract

POST /api/routes

Request:
```json
{
  "origin": { "latitude": 36.0726, "longitude": -79.7920 },
  "destination": { "latitude": 36.0999, "longitude": -80.2442 }
}
```

Success includes distanceMeters, durationSeconds and encodedPolyline. The app decodes Google's five-decimal polyline format; it never substitutes a straight line or fictional travel time.

For a restroom detour, add restroomId (positive integer), maxDetourMinutes (5, 10 or 15), and freePublicOnly (boolean) to the same request. No client-provided stop coordinates or name are accepted. The server performs a parameterized restroom/business lookup.

A successful detour also returns stop (id, name, address, latitude, longitude), baseline (the complete direct-route response), extraDrivingSeconds = max(0, through-stop duration - direct duration), and detourLimitMinutes. The intermediate waypoint is the database location; routing uses the normal stopover behavior.

HTTP 409 DETOUR_LIMIT_EXCEEDED returns extraDrivingSeconds and detourLimitMinutes without geometry. RESTROOM_NOT_ELIGIBLE, RESTROOM_NOT_FOUND, RESTROOM_COORDINATES_UNAVAILABLE and RESTROOM_LOOKUP_FAILED have explicit sanitized responses.

The request uses DRIVE and TRAFFIC_UNAWARE, no alternative routes, and an overview polyline. Driving time is an estimate without current traffic. Only distance, duration and encoded geometry are requested from Google.

Errors use fixed sanitized codes for missing configuration, denied provider access, timeouts, no route and malformed responses. Keys, raw upstream errors and endpoint coordinates are not logged.

## State and checks

- Both endpoint coordinates are required; GPS endpoints need no place ID.
- ViewModel requests survive rotation. Endpoint changes invalidate old geometry and stale completions.
- Picker cancellation preserves an already calculated route when endpoint selection is unchanged.
- Large route geometry is not stored in the saved-state Bundle. After process recreation, endpoint values and the last accepted restroom ID/name remain available; Start now revalidates the record and recalculates the route. No orphaned spinner is restored.
- Android checks: :app:assembleDebug, :app:testDebugUnitTest and :app:connectedDebugAndroidTest.
- Backend checks: npm.cmd test (or node --test routes.test.js) from backend/.
