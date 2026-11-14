
const express = require("express");
const session = require("express-session");
const crypto = require("crypto");
const db = require("./db");
const { createRouteHandler } = require("./routes");
const { PgSessionStore } = require("./review-session-store");
const { createArrivalReviewHandlers } = require("./arrival-reviews");
const reviewPools = require("./review-pools");

if (!process.env.SESSION_SECRET || process.env.SESSION_SECRET.startsWith("REPLACE_")) {
    throw new Error("Set SESSION_SECRET in backend/.env before starting the API.");
}

const app = express();
app.use(express.json());

app.use(session({
    secret: process.env.SESSION_SECRET,
    store: new PgSessionStore(db),
    resave: false,
    saveUninitialized: false,
    cookie: { maxAge: 7 * 24 * 60 * 60 * 1000 }
}));

// Give each visitor an anonymous user ID
async function guest(req, res, next) {
    try {
        if (!req.session.userId) {
            const name = "guest_" + crypto.randomUUID().replace(/-/g, "");

            const result = await db.query(
                "INSERT INTO users (username, password_hash) VALUES ($1, $2) RETURNING user_id",
                [name, "ANONYMOUS"]
            );

            req.session.userId = result.rows[0].user_id;

            await new Promise((resolve, reject) =>
                req.session.save(err => err ? reject(err) : resolve())
            );
        }

        next();
    } catch (err) {
        next(err);
    }
}

// Shared restroom query
const restroomSQL = `
    SELECT r.*, b.name AS business_name, b.address,
           b.latitude, b.longitude, b.google_place_id,
           AVG(a.overall) AS average_rating,
           COUNT(a.rating_id)::int AS rating_count,
           COALESCE((SELECT jsonb_agg(jsonb_build_object(
               'iso_weekday', h.iso_weekday, 'opens_at', h.opens_at, 'closes_at', h.closes_at
           ) ORDER BY h.iso_weekday, h.opens_at)
               FROM business_hours h WHERE h.business_id = b.business_id), '[]'::jsonb) AS business_hours
    FROM restrooms r
    JOIN businesses b ON b.business_id = r.business_id
    LEFT JOIN ratings a ON a.restroom_id = r.restroom_id
    GROUP BY r.restroom_id, b.business_id
`;

function distance(lat1, lon1, lat2, lon2) {
    const rad = x => x * Math.PI / 180;
    const a = Math.sin(rad(lat2 - lat1) / 2) ** 2 +
        Math.cos(rad(lat1)) * Math.cos(rad(lat2)) *
        Math.sin(rad(lon2 - lon1) / 2) ** 2;

    return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

app.get("/", (req, res) => res.send("IBS Restroom Finder API"));

app.get("/api/health", async (req, res, next) => {
    try {
        const result = await db.query("SELECT current_database() AS name");
        res.json({ status: "OK", database: "connected", databaseName: result.rows[0].name });
    } catch (err) { next(err); }
});

// Resolve a selected database restroom on the server; never trust client stop coordinates.
app.post("/api/routes", createRouteHandler({
    lookupRestroom: async id => {
        const result = await db.query(
            `SELECT r.*, b.name AS business_name, b.address, b.latitude, b.longitude
             FROM restrooms r JOIN businesses b ON b.business_id = r.business_id
             WHERE r.restroom_id = $1`, [id]
        );
        const row = result.rows[0];
        return row && {
            id: row.restroom_id, name: row.business_name, address: row.address,
            latitude: row.latitude, longitude: row.longitude,
            accessType: row.access_type, requiresPurchase: row.requires_purchase,
            feeRequired: row.fee_required ?? null
        };
    }
}));

app.get("/api/session", guest, (req, res) => {
    res.json({ user_id: req.session.userId });
});

// List and filter restrooms
app.get("/api/restrooms", async (req, res, next) => {
    try {
        const { accessible, access_type, restroom_type } = req.query;
        let rows = (await db.query(restroomSQL)).rows;

        if (accessible !== undefined)
            rows = rows.filter(r => r.accessible === (accessible === "true"));
        if (access_type)
            rows = rows.filter(r => r.access_type === access_type);
        if (restroom_type)
            rows = rows.filter(r => r.restroom_type === restroom_type);

        res.json(rows);
    } catch (err) { next(err); }
});

// Nearby and emergency search
async function search(req, res, next) {
    try {
        const { lat, lng } = req.query;
        const radius = Number(req.query.radius_km || 10);

        if (lat === undefined || lng === undefined ||
            !Number.isFinite(Number(lat)) || !Number.isFinite(Number(lng)) ||
            Number(lat) < -90 || Number(lat) > 90 ||
            Number(lng) < -180 || Number(lng) > 180 ||
            !Number.isFinite(radius) || radius <= 0) {
            return res.status(400).json({ error: "Invalid coordinates or radius" });
        }

        let rows = (await db.query(restroomSQL)).rows
            .map(r => ({
                ...r,
                distance_km: +distance(
                    Number(lat), Number(lng),
                    Number(r.latitude), Number(r.longitude)
                ).toFixed(2)
            }))
            .filter(r => r.distance_km <= radius);

        if (req.path.endsWith("/emergency")) {
            rows = rows
                .filter(r => r.access_type !== "EMPLOYEE_ONLY")
                .map(r => ({
                    ...r,
                    emergency_score: +(
                        100 - r.distance_km * 12 +
                        (r.access_type === "PUBLIC" ? 25 : 0) -
                        (r.access_type === "CUSTOMER_ONLY" ? 10 : 0) -
                        (r.requires_purchase ? 20 : 0) -
                        (r.requires_code ? 15 : 0) +
                        (r.verified ? 10 : 0) +
                        Number(r.average_rating || 0) * 3
                    ).toFixed(1)
                }))
                .sort((a, b) => b.emergency_score - a.emergency_score);
        } else {
            rows.sort((a, b) => a.distance_km - b.distance_km);
        }

        res.json(rows);
    } catch (err) { next(err); }
}

app.get("/api/restrooms/nearby", search);
app.get("/api/restrooms/emergency", search);

// Single restroom
app.get("/api/restrooms/:id", async (req, res, next) => {
    try {
        const result = await db.query(
            restroomSQL.replace(
                "GROUP BY",
                "WHERE r.restroom_id = $1 GROUP BY"
            ),
            [req.params.id]
        );

        if (!result.rows.length)
            return res.status(404).json({ error: "Restroom not found" });

        res.json(result.rows[0]);
    } catch (err) { next(err); }
});

// Add restroom
app.post("/api/restrooms", guest, async (req, res, next) => {
    try {
        const r = req.body;

        const result = await db.query(`
            INSERT INTO restrooms (
                business_id, access_type, restroom_type,
                accessible, male_changing_table, female_changing_table,
                requires_purchase, requires_code
            )
            VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
            RETURNING *
        `, [
            r.business_id,
            r.access_type || "UNKNOWN",
            r.restroom_type || "UNKNOWN",
            r.accessible || false,
            r.male_changing_table || false,
            r.female_changing_table || false,
            r.requires_purchase || false,
            r.requires_code || false
        ]);

        res.status(201).json(result.rows[0]);
    } catch (err) { next(err); }
});

// Read reviews
app.get("/api/restrooms/:id/reviews", async (req, res, next) => {
    try {
        const result = await db.query(`
            SELECT v.*, u.username
            FROM reviews v
            JOIN users u ON u.user_id = v.user_id
            WHERE v.restroom_id = $1
            ORDER BY v.created_at DESC
        `, [req.params.id]);

        res.json(result.rows);
    } catch (err) { next(err); }
});

const arrivalReviews = createArrivalReviewHandlers(reviewPools);
app.get("/api/restrooms/:id/arrival-review", guest, arrivalReviews.context);
app.post("/api/restrooms/:id/arrival-review", guest, arrivalReviews.submit);

// Add review
app.post("/api/restrooms/:id/reviews", guest, async (req, res, next) => {
    try {
        const comment = req.body.comment;

        if (typeof comment !== "string" || !comment.trim())
            return res.status(400).json({ error: "Comment required" });

        const result = await db.query(`
            INSERT INTO reviews (user_id, restroom_id, comment)
            VALUES ($1,$2,$3) RETURNING *
        `, [req.session.userId, req.params.id, comment.trim()]);

        res.status(201).json(result.rows[0]);
    } catch (err) { next(err); }
});

// Read ratings
app.get("/api/restrooms/:id/ratings", async (req, res, next) => {
    try {
        const result = await db.query(
            "SELECT * FROM ratings WHERE restroom_id = $1",
            [req.params.id]
        );
        res.json(result.rows);
    } catch (err) { next(err); }
});

// Add or update rating
app.put("/api/restrooms/:id/ratings", guest, async (req, res, next) => {
    try {
        const { cleanliness, privacy, accessibility, overall } = req.body;

        if (!Number.isInteger(overall) || overall < 1 || overall > 5 ||
            ![cleanliness, privacy, accessibility].every(
                v => v == null || (Number.isInteger(v) && v >= 1 && v <= 5)
            )) {
            return res.status(400).json({ error: "Ratings must be 1-5" });
        }

        const result = await db.query(`
            INSERT INTO ratings
                (user_id, restroom_id, cleanliness, privacy, accessibility, overall)
            VALUES ($1,$2,$3,$4,$5,$6)
            ON CONFLICT (user_id, restroom_id)
            DO UPDATE SET
                cleanliness = EXCLUDED.cleanliness,
                privacy = EXCLUDED.privacy,
                accessibility = EXCLUDED.accessibility,
                overall = EXCLUDED.overall
            RETURNING *
        `, [
            req.session.userId, req.params.id,
            cleanliness ?? null, privacy ?? null,
            accessibility ?? null, overall
        ]);

        res.json(result.rows[0]);
    } catch (err) { next(err); }
});

// Businesses
app.get("/api/businesses", async (req, res, next) => {
    try {
        res.json((await db.query(
            "SELECT * FROM businesses ORDER BY name"
        )).rows);
    } catch (err) { next(err); }
});

app.post("/api/businesses", guest, async (req, res, next) => {
    try {
        const { google_place_id, name, address, latitude, longitude } = req.body;

        if (!name || latitude == null || longitude == null)
            return res.status(400).json({ error: "Name and coordinates required" });

        const result = await db.query(`
            INSERT INTO businesses
                (google_place_id, name, address, latitude, longitude)
            VALUES ($1,$2,$3,$4,$5) RETURNING *
        `, [google_place_id || null, name, address || null, latitude, longitude]);

        res.status(201).json(result.rows[0]);
    } catch (err) { next(err); }
});

// Google Places (only works after API key is configured)
app.get("/api/places/search", async (req, res, next) => {
    try {
        const key = process.env.GOOGLE_MAPS_API_KEY;
        if (!key)
            return res.status(503).json({ error: "Google Places not configured" });

        const { lat, lng } = req.query;
        if (lat == null || lng == null)
            return res.status(400).json({ error: "Coordinates required" });

        const response = await fetch(
            "https://places.googleapis.com/v1/places:searchNearby",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Goog-Api-Key": key,
                    "X-Goog-FieldMask":
                        "places.id,places.displayName,places.formattedAddress,places.location"
                },
                body: JSON.stringify({
                    includedTypes: [
                        "restaurant", "gas_station",
                        "shopping_mall", "supermarket"
                    ],
                    maxResultCount: 20,
                    locationRestriction: {
                        circle: {
                            center: {
                                latitude: Number(lat),
                                longitude: Number(lng)
                            },
                            radius: 3000
                        }
                    }
                })
            }
        );

        if (!response.ok)
            return res.status(502).json({ error: "Google Places request failed" });

        const data = await response.json();
        res.json((data.places || []).map(p => ({
            google_place_id: p.id,
            name: p.displayName?.text || "",
            address: p.formattedAddress || "",
            latitude: p.location?.latitude,
            longitude: p.location?.longitude
        })));
    } catch (err) { next(err); }
});

// Error handler
app.use((err, req, res, next) => {
    // Error payloads can contain SQL and personal data; log only a diagnostic code.
    const code = typeof err.code === "string" && /^[A-Z0-9_]+$/.test(err.code) ? err.code : "UNKNOWN";
    console.error("IBS API request failed:", code);
    res.status(500).json({ error: "Server error" });
});

const port = Number(process.env.PORT || 3000);
const host = process.env.HOST || "127.0.0.1";
app.listen(port, host, () => {
    console.log("IBS API listening on configured host and port.");
});
