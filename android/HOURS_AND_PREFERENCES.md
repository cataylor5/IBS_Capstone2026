# Hours and preferences handoff

## Hours trace

Circle K is business_id 8, restroom_id 5. PostgreSQL public.business_hours contains seven ISO weekday rows, each 06:00:00–00:00:00. Existing rows were preserved.

The old running Node process omitted business_hours from its API responses even though server.js already contained the correct correlated join on h.business_id = b.business_id. Restarted that managed process using current source. Running /api/health now confirms databaseName=ibs. GET /api/restrooms and GET /api/restrooms/5 both return the same seven rows as PostgreSQL, with iso_weekday, opens_at and closes_at. Android reads those exact keys.

Home cards, View details, and Change Stop use businessHoursCountdown with Instant.now and America/New_York, refreshed every 15 seconds while displayed. The function checks yesterday's overnight window and the next week's opening. Circle K midnight closing is the following day's midnight. Missing/malformed schedules remain Hours unavailable.

## Filters

- Access chips may be combined (OR within Access); Any removes that dimension. Stored PUBLIC, CUSTOMER_ONLY and MEMBERSHIP_REQUIRED are eligible; EMPLOYEE_ONLY and UNKNOWN values remain in the schema.
- Time is 5, 10 or 15 minutes. With a destination it limits added driving duration relative to the direct route. With no destination, all otherwise eligible businesses appear without a time cap; detour chips are disabled. Home and map-only Change Stop sort by shortest provider driving time from the current position. Selection in map-only mode must not reject a farther business.
- Rating is an optional minimum: 5→4.6, 4→4.0, 3→3.0, 2→2.0, 1→1.0. Backend AVG(overall) is no longer rounded. Android preserves its decimal precision for comparisons; display rounding does not affect filtering. Unrated restrooms pass only when no minimum is selected.
- Private matches SINGLE_STALL or FAMILY. Stalls matches MULTI_STALL. Any removes that dimension.
- All dimensions must match the same restroom before choosing one restroom per business. Changes filter the existing driving estimates automatically. Selected preferences and time limit are passed into Change Stop and rechecked on selection.

## Local migration

The inspected valid_access_type constraint lacked MEMBERSHIP_REQUIRED. Migration tools/migrations/20261112_membership_access.sql extends it while preserving PUBLIC, CUSTOMER_ONLY, EMPLOYEE_ONLY and UNKNOWN. It does not alter rows or hours.

Already applied successfully to the local ibs database. No user database action is needed there. For another copy, run this exact command from the android directory, with backend/.env configured for that ibs database:

    node --env-file=../backend/.env tools/apply-membership-migration.cjs

The runner checks current_database() is ibs and loads credentials from the ignored env file without printing them. Restart the backend after source updates: npm.cmd start from backend (stop the previous local server first).

No existing restroom is currently tagged MEMBERSHIP_REQUIRED; selecting it alone should yield an empty result until a real record is assigned that access value.

## Verification

- :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin passed; 102 unit tests passed.
- npm.cmd test in backend: 20 passed.
- node --test tools/backend-membership.test.cjs from android: 2 passed.
- node --env-file=../backend/.env tools/verify-hours-response.cjs from android: confirmed ibs, identical Circle K DB/list/details hours, and exact database AVG in the API.
- Migration reinspection confirmed original values plus MEMBERSHIP_REQUIRED.
- git diff --check passed.
- No manual emulator testing or instrumentation execution. Preference-intent instrumentation coverage compiles; UI needs the user's check.

On the emulator, use a location/selected destination that puts Circle K inside the selected time limit, then check its Home card, View details, and Change Stop. Expect Opens in/Closes in based on real Eastern time, regardless of simulation. Test Patrons only + Private (Circle K), Public + Stalls, rating 4 versus 5, and narrowing the 15-minute time to 5. Current stored ratings are 3.5 or 4.5, so 5 should return no matches. Clearing the destination disables the detour chips and shows all Access/Rating/Privacy matches, even beyond 15 minutes, sorted by shortest drive. Selecting a business from map-only Change Stop starts guidance to that business without a time-cap rejection.
