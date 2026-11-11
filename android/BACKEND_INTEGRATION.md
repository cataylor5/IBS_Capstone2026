# Android ↔ IBS API integration

The home screen's Find a Restroom button requests the partner API's list. View details requests the selected record separately. Trip previews still use fictional demo stops.

## Local setup

- PostgreSQL must have the IBS tables in the database selected by backend/.env.
- Configure PGHOST, PGPORT, PGDATABASE, PGUSER and PGPASSWORD in backend/.env. Do not commit this file.
- SESSION_SECRET must be a random local secret. An ignored .env was prepared from the previous local backend settings; its database password must match this PC's PostgreSQL installation.
- In a terminal in backend/, run `npm.cmd start` on Windows. Node.js 20.6+ is required. Restart after editing .env.
- Open `http://127.0.0.1:3000/api/health`. A healthy result is `{"status":"OK","database":"connected"}`.
- On Android 17/API 37, allow the Nearby devices prompt when loading restrooms. The debug build requests ACCESS_LOCAL_NETWORK for its connection to the development computer; release builds do not declare this development-only permission.
- Run the Android debug build on an emulator. It connects to `http://10.0.2.2:3000/`, the emulator alias for the host PC. Only that host is permitted for cleartext HTTP in the debug manifest.
- Release builds have no local API URL and do not include the debug network-security override. Configure a deployed HTTPS service before release.

The app does not connect directly to PostgreSQL. Do not run schema creation or seed scripts against an existing database without reviewing them.

## Current contract

- `GET /api/restrooms`: bare array with restroom_id, business_name, address, coordinates, access_type, requirements and rating aggregates.
- `GET /api/restrooms/:id`: single record in the same shape, or HTTP 404.
- JSON numeric strings (PostgreSQL decimals/averages) are supported.
- Missing fee_required is unknown, not false. The current backend has no fee field; consequently its current records cannot qualify as confirmed Free. Choose Any to view them.
- List and detail requests do not require a guest session. Session cookies and review/rating writes are not integrated yet.
- Loading, empty-list, timeout, HTTP failure and retry states are represented. API failures never silently substitute fictional records.
- Database entries may be seed/test data and are labeled accordingly.
- Manual restroom detours are integrated: Use this stop calls POST /api/routes with the database ID, compares direct and through-stop driving time, enforces the detour/availability preferences and redraws the route. See ROUTING.md. Automatic corridor search and stop ranking remain later milestones.

## Diagnostics

PostgreSQL 28P01 means password authentication failed. Correct the local .env values and restart the server. 42P01 means a required table is absent in the selected database/schema.

Credentials removed from backend source remain in Git history; rotate reused/shared credentials separately. Neither credentials nor raw API/error bodies are logged by the Android client.
