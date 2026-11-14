# Home browsing and navigation flow

The home screen has scrolling preferences and backend business cards above a fixed footer. The footer contains an optional destination field directly above a full-width Let's Go button. It reserves its own layout space and moves above the keyboard, with outer Scaffold system insets consumed to prevent duplicate bottom padding.

Typing at least two characters requests debounced Places (New) predictions in an upward, field-anchored dropdown. Requests use a session token shared with the coordinate lookup, a timeout, cancellation and revision checks. Selecting a prediction fills the field, closes the dropdown and keyboard, resolves coordinates and automatically recalculates business estimates. Clearing restores destination-free browsing. Search errors have Retry; unresolved typed text cannot start a routed trip. The custom suggestions include Google attribution. [Google's programmatic autocomplete documentation](https://developers.google.com/maps/documentation/places/android-sdk/place-autocomplete#get_place_predictions_programmatically).

The four preference groups are Access, Detour/Drive time, Rating and Privacy. Their full rules and migration are documented in HOURS_AND_PREFERENCES.md. Free/Any has been replaced. The old home map and separate Businesses step are no longer part of the flow. Splash playback and the navigation map remain.

Home requests a fresh precise foreground location automatically on first entry. Only Android's permission prompt is shown; missing permission, disabled location and a failed fix have inline retry and Settings actions. There is no routine Refresh button. Foreground home location updates refresh estimates after about 100 metres of movement; they stop while the Activity is paused. Retry actions are shown for location, backend and estimate failures. Endpoint and preference state remain in lifecycle-aware ViewModels. Destination edits invalidate coordinates, outdated estimates are hidden immediately, and request revisions reject late responses.

Home and Change Stop share `estimateBusinessTrip`, `replacementStops` and `businessHoursCountdown`; `homeStops` applies the home-only time policy:
- Home offers 5/10/15/20/25/30-minute chips in both modes. Without a destination, the control is **Drive time** and caps driving time from the user to the restroom. Access, Rating and Privacy still apply; results are sorted by estimated driving time. Unknown driving times cannot satisfy the home limit.
- With a destination, the home control is **Detour time**. Added driving time relative to the direct trip determines eligibility; the provider's first-leg driving time to the business determines ranking. Changing a chip immediately refilters cached estimates; the six choices persist across recreation.
- Access allows Public, Patrons only and Membership required. Rating uses the unrounded database average; Private means SINGLE_STALL/FAMILY, Stalls means MULTI_STALL. All selected dimensions match one restroom before business deduplication. Preferences persist and are carried to navigation.
- One eligible restroom per business appears. Cards show arrival minutes, database overall rating (or No ratings yet), real America/New_York hours countdown (or Hours unavailable), and Go Here!
- Change Stop ignores the home time limit in both map-only and routed modes, while retaining Access, Rating, Privacy and access/fee eligibility. It excludes the current business and shows up to 10 unique businesses ordered by driving time; unknown times appear last. Business-name search is applied before the 10-result cap and uses cached estimates. Hours refresh every 15 seconds. On home with a final destination, detour eligibility must still be established even if the arrival estimate is unavailable. The estimate-context cache key is observable Compose state, so destination selection/clearing immediately invalidates the home results and new estimates appear without Refresh.

| Action | Navigation plan |
|---|---|
| Let's Go, destination blank | Real device position on map, no route or simulator movement |
| Let's Go, selected destination | Simulated guidance directly to destination |
| Go Here!, destination blank | Selected business is the destination |
| Go Here!, selected destination | Business is a restroom waypoint before the final destination |

Change Stop stays available in map-only mode and uses real foreground fused-location updates. Selecting a business makes it the destination. Once a final destination exists, another selection adds/replaces the restroom waypoint and preserves that destination. Routed sessions use the moving SDK simulated position. Selection rechecks driving eligibility; the route/header commit only after SDK acceptance, with rollback on failure. Restroom arrival still pauses for Continue to destination. Stop Navigation/Back releases guidance and simulation.

Map-only state and a business selected as the new final destination survive recreation. Routed sessions retain the existing explicit Resume guidance recovery after recreation. No keys or precise-location diagnostics are logged.

## Verification for this redesign

- `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` passed; 102 unit tests passed. New fast checks cover prediction cancellation/stale replies, retry, and upward dropdown positioning including RTL and resized windows.
- Added unit coverage for all four endpoint-plan outcomes and separate arrival/detour metrics, including missing arrival estimates. Existing eligibility, sorting, hours, endpoint invalidation and reroute rollback tests pass.
- Instrumentation sources compile, including the explicit map-only intent contract. Device tests were not executed.
- `git diff --check` passed.
- No emulator was launched, installed to, or traversed for this fix. The local backend returned all five businesses; live direct-route checks from a public downtown Greensboro test point returned 16–27 driving minutes, explaining the previous empty list under the 15-minute cap. These diagnostics did not use or log the device's location. Automatic UI updates and simulator behavior still need the user's smoke test.

Earlier runtime verification, before this redesign: a November 12 simulated via-stop trip from Greensboro toward Winston-Salem calculated and moved successfully; Free produced no matches; permission denial was tested. Those results do not verify this new home/map-only flow.

The SDK can choose a different route/time from backend estimates. Simulated movement does not establish real-driving accuracy.
