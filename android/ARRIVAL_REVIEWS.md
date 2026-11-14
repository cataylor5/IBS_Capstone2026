# Arrival reviews and banner

The supplied final I Better Stop! logo.png is packaged unchanged as ibs_wordmark.b64 (2106 × 747, RGBA).
The header removes transparent outer canvas while keeping artwork proportions. The pin's previous
76 × 96 dp Fit scale is retained; empty pin canvas no longer makes the banner unnecessarily tall.
The existing shared appearance state chooses pale aqua/light blue or navy/slate.

## Arrival flow
- A selected midway restroom offers Rate this restroom alongside Continue to destination.
- A restroom used as the trip's final endpoint offers the review and Finish trip.
- Ordinary destinations do not offer restroom reviews.
- Midway and final restroom IDs are tracked separately, including after Change Stop.
- Each arrival gets one stable UUID, saved with navigation state. The invitation is an inline button,
  not a repeated popup. A successful save hides it for that arrival, including after recreation.
- Back to trip keeps the optional draft. Drafts survive rotation/process recreation and reopening the
  form in that arrival; saving failures expose Retry save. Continue/Finish never requires a review.

## Identity and storage
The app currently uses the existing anonymous guest identity (approved for this milestone), not a
sign-in account. The backend issues its signed session cookie; Android retains it privately and the
backend stores sessions in PostgreSQL so restarting the server preserves reviewer identity.
The existing session lifetime is seven days. Private cookies/drafts are excluded from backup/transfer.

GET /api/restrooms/:id/arrival-review returns the exact restroom's listed access/layout and business name.
POST at the same path requires overall (1–5), submissionId (arrival UUID), and demo (boolean), with optional
comment (maximum 4000 characters), accessType, privacyType. Client user IDs are ignored. The guest session
is authoritative. Not sure is sent as null. Questions start unselected; existing listing values are context.

One transaction upserts ratings.overall, inserts a nonblank reviews.comment, upserts explicit
restroom_confirmations answers, and records the retry key. With no explicit confirmations, no new report
is written and an older report is left alone. Explicit Not sure clears that answer from the user’s report;
an unselected question preserves an older answer. All-null reports are removed because the schema disallows them.
Reports never change the restroom's access, layout or verified status. The same arrival/payload
is safe to retry; a conflicting payload with an already-used arrival key returns HTTP 409 rather than
creating another review. Other rating subcategories remain intact.

## Setup
From the repository's backend directory, run:
    npm.cmd run setup:reviews
    npm.cmd start

The first command adds ibs_sessions and arrival_review_submissions in the main database, and creates the
isolated ibs_reviews_test database (requires CREATE DATABASE permission). It does not change the separately
managed public.restroom_confirmations migration. That table must already exist for real submissions.
The supplied migration was present in the local ibs database during implementation.

Demo requests ALWAYS write reports to ibs_reviews_test (or PGREVIEW_TEST_DATABASE ending in _test);
they never fall back to real report storage. Only restroom/business reference IDs and synthetic test
user identity are copied, not existing production reviews or users. The form visibly labels demo saves.
The test database schema is intentionally a minimal review fixture, not a complete backend clone.
Production business-list averages therefore do not change when a simulated review is submitted.

## Automated checks
    npm.cmd test
    npm.cmd run test:reviews:db
    node --env-file=.env verify-arrival-review-api.js

Database checks inject a failed transaction, run concurrent duplicate submissions, update confirmations,
and confirm real report counts stay unchanged. The HTTP check verifies guest-cookie reuse and a real
demo save/retry. Automated review fixtures are removed afterward; manual demo reviews remain in the
test database. Android checks: :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin.
No emulator walkthrough was performed.
