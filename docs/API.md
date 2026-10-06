this is where we should agree on things like "the restroom-list request returns an ID, name, coordinates, and rating"

# IBS API Agreement

Status: Draft for team review. This endpoint is not yet confirmed as implemented.

## First feature: Restroom list

The Android app requests restroom records from the backend and displays them as a list.

### Request

GET /api/restrooms

For the first version, return the project's sample restroom records.
Location filtering and route-based suggestions will be specified later.

### Successful response

HTTP status: 200
Content-Type: application/json

The following is fictional sample data:

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

### Field definitions

- id: String identifying the restroom. Keep it consistent across requests.
- name: String displayed as the restroom's name.
- latitude: Number in decimal degrees.
- longitude: Number in decimal degrees.
- accessType: "public", "customers_only", or "unknown".
- feeRequired: true, false, or null when unknown.
- averageRating: Number from 1 to 5, or null when there are no ratings.
- ratingCount: Integer indicating how many ratings exist.

Public access and payment requirements are separate properties.

### No results

Return HTTP 200 with:

```json
{
  "restrooms": []
}
```

### Server error

Return HTTP 500 with:

```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Unable to load restrooms."
  }
}
```

### Responsibilities

Frontend:
- Request the restroom list.
- Display loading, results, empty-list, and error states.
- Use the example response as temporary sample data during development.

Backend:
- Implement the endpoint.
- Retrieve restroom records from PostgreSQL.
- Return data using the agreed field names and types.

### Decisions still needed

- Confirm this contract together.
- Choose the development server address.
- Decide whether this read-only endpoint requires a session.
- Document any changes here before connecting the two sides.