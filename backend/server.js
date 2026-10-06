const express = require("express");
const pool = require("./db");

const app = express();
const PORT = 3000;

app.use(express.json());


// Home page
app.get("/", (req, res) => {
    res.send("IBS Restroom Finder API is running!");
});


// Get all restrooms
app.get("/api/restrooms", async (req, res) => {
    try {

        const result = await pool.query(`
            SELECT
                r.restroom_id,
                b.name AS business_name,
                b.address,
                b.latitude,
                b.longitude,
                r.access_type,
                r.restroom_type,
                r.accessible,
                r.male_changing_table,
                r.female_changing_table,
                r.requires_purchase,
                r.requires_code,
                r.verified,
                ROUND(AVG(ra.overall), 1) AS average_rating

            FROM restrooms r

            JOIN businesses b
                ON r.business_id = b.business_id

            LEFT JOIN ratings ra
                ON r.restroom_id = ra.restroom_id

            GROUP BY
                r.restroom_id,
                b.business_id

            ORDER BY
                r.restroom_id;
        `);

        res.json(result.rows);

    } catch (error) {

        console.error(error);

        res.status(500).json({
            error: "Failed to retrieve restrooms"
        });
    }
});

// Get one restroom by ID
app.get("/api/restrooms/:id", async (req, res) => {
    try {
        const restroomId = req.params.id;

        const result = await pool.query(`
            SELECT
                r.restroom_id,
                b.business_id,
                b.name AS business_name,
                b.address,
                b.latitude,
                b.longitude,
                r.access_type,
                r.restroom_type,
                r.accessible,
                r.male_changing_table,
                r.female_changing_table,
                r.requires_purchase,
                r.requires_code,
                r.verified,
                ROUND(AVG(ra.overall), 1) AS average_rating

            FROM restrooms r

            JOIN businesses b
                ON r.business_id = b.business_id

            LEFT JOIN ratings ra
                ON r.restroom_id = ra.restroom_id

            WHERE r.restroom_id = $1

            GROUP BY
                r.restroom_id,
                b.business_id;
        `, [restroomId]);

        // Restroom doesn't exist
        if (result.rows.length === 0) {
            return res.status(404).json({
                error: "Restroom not found"
            });
        }

        res.json(result.rows[0]);

    } catch (error) {
        console.error(error);

        res.status(500).json({
            error: "Failed to retrieve restroom"
        });
    }
});

// Get all reviews for one restroom
app.get("/api/restrooms/:id/reviews", async (req, res) => {
    try {
        const restroomId = req.params.id;

        const result = await pool.query(`
            SELECT
                rev.review_id,
                rev.comment,
                rev.created_at,
                u.username

            FROM reviews rev

            JOIN users u
                ON rev.user_id = u.user_id

            WHERE rev.restroom_id = $1

            ORDER BY rev.created_at DESC;
        `, [restroomId]);

        res.json(result.rows);

    } catch (error) {
        console.error(error);

        res.status(500).json({
            error: "Failed to retrieve reviews"
        });
    }
});

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});