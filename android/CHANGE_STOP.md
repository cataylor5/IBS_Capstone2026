# Change Stop during navigation

The compact two-line header shows the final destination and current restroom business above Stop Navigation / Change Stop. The native SDK map and turn instructions remain visible with the IBS ETA card. Change Stop opens a compact anchored searchable popup without pausing guidance.

## Result data

The backend restroom/business join now returns existing business_hours rows by business_id, without multiplying rating rows or altering stored data. Existing average_rating and rating_count remain the source for ratings. The verified schema uses iso_weekday (1 Monday through 7 Sunday), opens_at and closes_at.

The dropdown opts into includeStopTravelTime=true on POST /api/routes. The backend requests routes.legs.duration only for the through-stop route and returns stopTravelSeconds from its first leg. This is current-position-to-stop driving duration, distinct from total duration and extraDrivingSeconds. Missing/malformed first-leg data becomes null, never a guessed time. Existing route consumers retain their original field mask and response shape.

Eligible replacements exclude the current business and apply the selected Access, Rating and Privacy preferences plus route access/fee eligibility. The home time limit is ignored in both routed and map-only navigation, including when selecting a replacement. Results are sorted by unrounded stopTravelSeconds and capped at 10 unique businesses AFTER business-name search. Unknown arrival times sort last and display Travel time unavailable. Unknown detours do not exclude a navigation suggestion; known detours can still be displayed separately. Ratings display e.g. 4.5/5 or No ratings yet.

Search filters all eligible cached businesses by name before taking the first 10 results; businesses beyond the initial 10 can therefore be found without search-time network requests. Position comes from NavigationApi's road-snapped location provider, including moving simulated positions. Route estimates refresh after 100 metres movement and at least 15 seconds. Selection revalidates the chosen stop from the current position.

## Real-time hours

businessHoursCountdown uses Instant.now and America/New_York, independently of simulated driving time. It checks yesterday's overnight interval and searches the following week. Adjacent and overlapping shifts merge; closing before opening rolls into the next date. PostgreSQL 24:00 closing times are supported. Invalid, missing, partially specified or ambiguous equal-opening/closing schedules display Hours unavailable. Completely continuous schedules with no established weekly closing also display Hours unavailable.

The dropdown updates countdowns every 15 seconds while open and cancels its clock task on dismissal. DST comparisons use real instants obtained from New York wall times. Opening/closing boundaries and minute rounding are tested.

## Rerouting and verification

The existing transactional rerouting is preserved: commit the new header only after SDK acceptance, restore previous remaining waypoints on failure, and expose Resume guidance if restoration also fails. Stop Navigation and lifecycle cancellation remain unchanged.

Checks: :app:assembleDebug, :app:testDebugUnitTest (73 passed), backend npm.cmd test (20 passed), and read-only SQL against existing business_hours (five records returned hours arrays). Tests include provider leg extraction and missing-time fallback, JSON mapping, driving-time ranking/search, detour independence, ratings, overnight/week rollover, split shifts, malformed schedules, DST, real clock countdown changes and reroute restoration. No emulator UI launch or traversal was performed.

Before smoke testing, restart the Node backend with npm.cmd start from backend/ and run the updated Android app.

Smoke test: on home compare 5 and 30 minutes, including Drive time with the destination blank. Then enter a simulated trip with a 5-minute home limit and tap Change Stop. A longer-drive/longer-detour eligible business should still appear. Wait for checks and verify the old stop is excluded, rows are shortest-drive first, and each shows driving minutes away (or Travel time unavailable), database rating (or No ratings yet), and a New York real-time opening/closing countdown (or Hours unavailable). Type part of a visible business name in Search business name; results should filter immediately without restarting route checks. Tap a replacement: the popup closes, guidance reroutes to that business and the same final destination, and the header changes only when ready. If a step differs, report its number, exact message, business/preferences and screenshot, without credentials.

Provider reference:
https://developers.google.com/maps/documentation/routes/understand-route-response
