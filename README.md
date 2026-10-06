# IBS Capstone 2026

A bathroom break should be part of the route.

IBS is a two-person Android capstone project for finding restrooms near a user or along a planned trip. The concept combines practical restroom information with a playful bathroom theme.

**Planning baseline:** This README reflects the project outline and brainstorming. Features, folders, API examples, and development conventions below are planned or proposed unless the corresponding implementation is available in the repository. Update the setup instructions as the project takes shape.

Repository: [cataylor5/IBS_Capstone2026](https://github.com/cataylor5/IBS_Capstone2026)

## Project goals

- Find public restrooms and restrooms that require payment or a purchase.
- Let users choose a starting point and destination.
- Suggest restroom stops using user preferences and the extra travel required to reach them.
- Show restroom details, user ratings, and reviews.
- Preserve preferences and support a low-friction user experience.
- Develop and demonstrate the Android app using an emulator on a PC.

The outline suggests Greensboro as a possible initial testing area. Start with a small, verified set of restroom locations before expanding coverage.

### Optional theme and stretch ideas

- A toilet-shaped location or vehicle marker.
- A toilet-paper-style route and droppings along the completed portion.
- Toilet-paper collectibles or distance-based achievements.
- Saved preferences and optional travel history.

Theme effects depend on the chosen map/navigation tools and should preserve readable directions and Google attribution. Build the restroom search and review flow before adding game mechanics.

## Planned technology stack

| Part | Technology | Purpose |
| --- | --- | --- |
| Android application | Kotlin in Android Studio | App behavior, permissions, and integration |
| User interface | Jetpack Compose | Screens, buttons, forms, and UI state |
| Map display | Google Maps SDK for Android | Map, markers, and overlays |
| Device location | Android location services | User location with permission |
| Place information | Google Places services | Place lookup and supporting location details |
| Backend | Node.js and Express | HTTP API, validation, sessions, and business logic |
| Database | PostgreSQL | IBS users, businesses, restrooms, ratings, and reviews |
| Authentication | Backend-managed sessions with PostgreSQL storage | Associate requests with an identity |
| Collaboration | Git, GitHub, and GitHub Desktop | Shared source code and change history |

Node.js/Express is the current backend plan. Agree on changes before building features that depend on it.

### Navigation decision still to make

The outline calls for Google navigation inside the app. The Maps SDK displays a map; embedded turn-by-turn guidance requires a separate navigation integration. Google's Navigation SDK **replaces** the Maps SDK rather than running alongside it. Its current policy limits use to commercial applications, so eligibility for this capstone remains unresolved. Confirm suitability before committing to that implementation. Opening the separate Google Maps app for directions is another option if the team revises the embedded-navigation requirement.

The team must also choose how to calculate routes and extra travel time for suggested stops. Nearby distance alone is not a road detour calculation.

## How the components connect

The Kotlin app sends HTTP requests to the Express API. Express validates the request, reads or writes PostgreSQL, and returns JSON that the app displays. The Android app does not connect directly to PostgreSQL.

Google Maps/Places supply mapping services alongside this flow. Decide which Places requests use an Android SDK and which go through the backend before configuring keys.

GitHub stores the code and its history. Google Cloud manages the Google service project, enabled APIs, credentials, permissions, usage, and billing. Committing code to GitHub does not start the backend or create a running database.

## Proposed repository organization

Adopt these folders if the repository does not already have an agreed layout:

| Path | Contents |
| --- | --- |
| `android/` | Android Studio project, Compose screens, and app resources |
| `backend/` | Express API, dependency definitions, and server code |
| `database/` | Versioned schema changes and non-sensitive sample data |
| `docs/` | API contract, design decisions, diagrams, and demo notes |
| `README.md` | Overview and contributor setup |

Keep one Git repository at the project root. Create the Android project inside the agreed folder rather than creating a second nested Git repository. Both contributors should work from their own clone of this shared repository.

## Getting started

### Accounts and tools

Both contributors need a GitHub account and access to this repository. GitHub Desktop provides a graphical Git workflow; a separate command-line Git installation is optional if only using Desktop.

| Work | Install or configure |
| --- | --- |
| Android development | Android Studio, its Android SDK tools, and an Android Virtual Device |
| Backend development | Node.js LTS with npm and a code editor such as VS Code |
| Database development | PostgreSQL; pgAdmin is an optional database interface |
| Full local integration | Android tools plus the backend and database tools |
| Google services | A Google account with access to the team's Google Cloud project |

For a new Android project, choose **Phone and large screens > Empty Activity** and use **Kotlin / Jetpack Compose**. If an Android project is already committed, open that project instead of generating another one. Agree on the package name and minimum Android version before connecting Google services.

### Google Cloud setup

Create one shared development project, invite the other contributor using their own Google account, configure the required billing account, and enable the Google APIs actually used by the app. Maps/Places integration requires this setup; placeholder screens and a local Express/PostgreSQL backend can be developed first.

Restrict Android API keys to the appropriate Android application and APIs. Keep server credentials and database passwords out of Git. Review pricing, usage quotas, and budget alerts when enabling services; ordinary budget alerts do not automatically stop spending.

Google Cloud does not replace GitHub, and using Google Maps does not require hosting the Express server or PostgreSQL there.

### Local development

The exact Node version, database creation commands, environment variable names, migration commands, server port, and npm scripts must be documented when the backend skeleton is committed. Do not assume `npm run dev` works until a `dev` script exists in `backend/package.json`.

Recommended integration order:

1. Start PostgreSQL and prepare the local database using the team's schema and seed scripts.
2. Configure the backend's local environment file from a committed example containing placeholders.
3. Install the backend dependencies and run its documented development command.
4. Configure the Android app with the backend address.
5. Run the app on an emulator and test one request from screen to database and back.

For an Android emulator reaching a backend on the **same PC**, use `10.0.2.2` for the host address. For example, a server configured on port `3000` would use `http://10.0.2.2:3000`. Inside the emulator, `localhost` refers to the emulator itself. Development HTTP may require a debug-only Android network configuration; use HTTPS for a deployed service.

Each contributor can run a local database with the same schema and sample data. A server running only on one person's PC is not automatically available to the other person's emulator. Agree on a shared hosted development API later if needed.

## Daily software checklist

| Tool | When to open or run it |
| --- | --- |
| GitHub Desktop | Fetch/pull before work; review, commit, and push changes afterward |
| Android Studio | While editing, building, or debugging the Android app |
| Android Emulator | While testing the app; it can be closed afterward |
| VS Code or another editor | While editing backend code, SQL, or documentation |
| Node/Express server | Start it in a terminal when testing backend requests; keep that process running |
| PostgreSQL server | Needed for database-backed requests; it may already run as a Windows service, depending on installation |
| pgAdmin | Only when inspecting or administering the database; closing it does not stop the database server |
| GitHub / Google Cloud websites | When managing repository access, pull requests, APIs, credentials, or billing |

Kotlin, Compose, Express, npm, Git, and the Android SDK are languages, libraries, runtimes, or command-line tools, not separate windows that all need to stay open. Installed Node.js does not automatically start the IBS backend.

## Contributing with GitHub Desktop

1. **Clone once:** choose **File > Clone repository > URL** and enter `https://github.com/cataylor5/IBS_Capstone2026.git`.
2. **Before a task:** switch to the default branch, fetch, and pull available changes. Finish or safely save any existing work before switching branches.
3. **Create a branch:** use a short name such as `feature/restroom-list`, `feature/review-api`, or `docs/setup`.
4. **Make and check the change:** edit files in the cloned folder and verify the affected behavior.
5. **Review and commit:** inspect the changed files in Desktop, select the intended files, write a meaningful summary, and commit to the task branch.
6. **Upload:** choose **Publish branch** for a new branch or **Push origin** for later commits.
7. **Open a pull request:** describe what changed, how it was checked, and any setup/API changes. Ask the other contributor to review it.
8. **After merging:** return to the default branch, fetch, and pull before starting the next task.

**Commit** saves a snapshot locally. **Push** uploads commits to GitHub. **Fetch** checks/downloads remote history; **pull** also incorporates remote changes into your current branch. Saving a file in your editor does not upload it.

## Quick Git commands

These are the terminal alternative to GitHub Desktop. Use a terminal with Git installed. The examples use `main`; substitute the repository's actual default branch if different. They assume the repository has an initial commit. For a completely empty repository, make and publish that first commit in Desktop before following the branch workflow.

### Clone once

```bash
git clone https://github.com/cataylor5/IBS_Capstone2026.git
cd IBS_Capstone2026
git status
```

If Git asks for commit identity, configure your own name and email in Desktop or Git. Private-repository authentication can be handled through GitHub Desktop or Git Credential Manager; do not place access tokens in commands or project files.

### Start a task

With a clean working tree:

```bash
git switch main
git pull --ff-only origin main
git switch -c docs/project-readme
```

### Review, save, and upload

After editing `README.md`:

```bash
git status
git diff
git add README.md
git diff --staged
git commit -m "Document IBS project and contributor workflow"
git push -u origin HEAD
```

For other tasks, change the branch name, stage the intended file paths, and write a matching commit message. Then create a pull request on GitHub. After the first push, later commits on the same branch can be uploaded with `git push`.

### Bring current main into your task branch

First commit your current task work. While still on that task branch:

```bash
git fetch origin
git merge origin/main
```

If there are conflicts, review them with your partner, edit the files to the intended result, stage the resolved files, and commit. If the merge finishes automatically, no extra merge commit command is needed. Push the updated branch. Avoid force-pushing shared branches.

### After the pull request is merged

```bash
git switch main
git pull --ff-only origin main
git status
```

If a command reports an error, stop and read it before running later commands. `--ff-only` deliberately stops if histories diverge instead of creating an unexpected merge.

## Frontend and backend responsibilities

| Frontend contributor | Backend contributor | Shared decisions |
| --- | --- | --- |
| Kotlin and Compose screens | JavaScript with Node.js/Express | Feature behavior and acceptance criteria |
| Map display, location permissions, and buttons | API routes, validation, and search/ranking logic | Request and response formats |
| Loading, empty, and error states | PostgreSQL tables, queries, and schema changes | IDs, field names, units, and optional values |
| Sending requests and displaying results | Session management and data persistence | Identity, session expiry, and error handling |
| UI testing on an emulator | API/database testing | Testing the complete user flow |

The two sides meet at the **API contract**: an agreement on the URL, request inputs, response data, and errors for each feature. Record it in `docs/` before both people implement the feature. The frontend can use sample JSON while the backend builds the real endpoint.

### Proposed first integration

Build one small flow: open the restroom list, request nearby restrooms, return sample database records, and show them as cards or map pins.

Example contract for discussion, **not an implemented endpoint**:

```http
GET /api/restrooms?latitude=36.07&longitude=-79.79&radiusMeters=3000
```

Example response using fictional demonstration data:

```json
{
  "restrooms": [
    {
      "id": "demo-restroom-1",
      "name": "Demo restroom",
      "latitude": 36.07,
      "longitude": -79.79,
      "accessType": "public",
      "feeRequired": false,
      "averageRating": null,
      "ratingCount": 0
    }
  ]
}
```

Agree on allowed access types, unknown fee handling, rating scale, error responses, coordinate format, and distance units. This nearby-search example does not yet implement route-based stop suggestions.

Before integrating reviews, decide how users or guests are identified, how the Android client maintains a session, and what happens after expiry or reinstallation. A session-based backend by itself does not define a login-free experience.

## Data and configuration

The outline proposes **Users, Business, Restrooms, Ratings, and Reviews**. Final relationships and names are still to be designed. Decide whether ratings are separate records or part of reviews before both contributors rely on the schema.

Keep IBS-created reviews and independently collected restroom information separate from Google-provided content. Places information has storage and attribution restrictions; place IDs have a storage exception. Google business ratings should not be presented as IBS restroom ratings.

Commit schema migrations and non-sensitive sample data so both PCs can reproduce the same development database. Git does not synchronize live database contents.

Before uploading code, configure `.gitignore` for local environment files, database passwords, Android `local.properties`, local key files, signing keys, dependency folders such as `node_modules/`, and generated build output. Commit placeholder configuration examples, dependency manifests/lockfiles, and the Gradle wrapper. A private repository still needs this separation.

## Suggested milestones

1. Shared repository, Android shell, backend health endpoint, and database schema.
2. One complete restroom-list request from the emulator through Express to PostgreSQL.
3. Map/location display, restroom details, and basic filters.
4. Ratings/reviews and the agreed session flow.
5. Start/destination planning and route-aware restroom suggestions.
6. Approved theme effects, usability checks, and a repeatable capstone demo.

## Official documentation

- [Jetpack Compose setup](https://developer.android.com/develop/ui/compose/setup)
- [Maps SDK setup](https://developers.google.com/maps/documentation/android-sdk/get-api-key)
- [Navigation SDK overview](https://developers.google.com/maps/documentation/navigation/android-sdk/overview) and [policies](https://developers.google.com/maps/documentation/navigation/android-sdk/policies)
- [Places API policies](https://developers.google.com/maps/documentation/places/web-service/policies)
- [Android emulator networking](https://developer.android.com/studio/run/emulator-networking-address)
- [Express getting started](https://expressjs.com/en/starter/installing/)
- [PostgreSQL documentation](https://www.postgresql.org/docs/)
- [GitHub Desktop documentation](https://docs.github.com/en/desktop)
