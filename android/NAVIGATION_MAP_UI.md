# Navigation map presentation

The Navigation SDK 8.1.0 guidance remains responsible for routing, simulated movement, turn banners, road-snapped location, route polylines and arrival events.

## Cobalt ETA card
NavigationMapUi calls NavigationFragment.setEtaCardEnabled(false). The replacement sits at the bottom of the navigation region with 8dp side/bottom margins, 24dp corners, 18dp horizontal padding, a 28sp bold remaining duration and a 16sp distance/arrival row. It uses the same IbsBrand.CobaltArgb as the top banner. The native-style green duration is retained; secondary text is light gray for contrast against cobalt.

The map occupies a separate weighted viewport immediately above the card: Google attribution, compass and incident controls remain inside the SDK viewport instead of underneath an app overlay. No SDK child views are searched or modified.

Navigator.currentTimeAndDistance supplies the next waypoint's live seconds/metres; the app never sums cumulative waypoint estimates or uses backend preview times for this card. Remaining-time/distance callbacks update it, with a 15-second foreground clock refresh. Arrival holds a zero-duration/distance arrival snapshot. Existing Continue to destination recalculates the SDK route with only the final waypoint, so the card then reads the remaining final-destination leg. No estimates are fabricated when data is unavailable.
Distance display follows locale miles/feet or km/metres; arrival uses the device timezone and 12/24-hour setting.

## Overview
The fixed Route Overview button is inside the map at the start edge, 68dp above the bottom (clear of attribution and bottom-right incident controls in the reference phone layout). Resume Follow returns to GoogleMap.followMyLocation(TILTED), without stopping guidance or simulation.

Google's overview handles single-leg routes up to 45 minutes. Multi-leg, longer and unknown-duration routes use all remaining SDK segment coordinates, replacing the first segment with currentRouteSegment's road-snapped remaining points. Restroom and final waypoint coordinates are included. Bounds update on route changes, continuation, restored routes and viewport resizing. Overview preference survives recreation and is reapplied after starting guidance.

SDK overview limitation: https://developers.google.com/maps/documentation/navigation/android-sdk/camera

## Toilet pins
The map reuses the exact pin artwork already extracted from the bundled splash for the home header. Transparent padding is trimmed and the tip is anchored at (0.5, 1.0) at the business coordinates. Markers pan/zoom with the map. Native SDK destination markers are hidden through DisplayOptions.hideDestinationMarkers(true); one app marker per coordinate is rendered, preferring the toilet pin when restroom/final coordinates coincide. Other final destinations receive a standard Google marker.

The home intent records when a business itself is the final destination. Map-only selection and restored navigation retain this identity. Visited restroom locations remain pinned after Continue; remaining-route camera bounds exclude the visited waypoint. Replaced markers are removed on committed stop changes, and all map markers/listeners are released on destruction.

## Verification
- :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin passed.
- 109 unit tests passed, including ETA formatting, separate stop/final estimates, overview selection, remaining waypoints and marker deduplication.
- Business-as-final intent instrumentation coverage compiles but was not executed.
- git diff --check passed.
- No manual emulator launch or traversal. Actual SDK rendering, artwork availability, live ETA transitions, screen-size placement and attribution/incident/compass clearance still require the user smoke test.
