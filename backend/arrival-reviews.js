const crypto = require("crypto");
const allowedAccess = new Set(["PUBLIC", "CUSTOMER_ONLY", "MEMBERSHIP_REQUIRED"]);
const allowedPrivacy = new Set(["PRIVATE", "STALLS"]);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function validateReview(body) {
    if (!body || !Number.isInteger(body.overall) || body.overall < 1 || body.overall > 5 ||
        typeof body.demo !== "boolean" || !uuid.test(body.submissionId || "") ||
        (body.comment != null && (typeof body.comment !== "string" || body.comment.length > 4000)) ||
        (body.accessType != null && !allowedAccess.has(body.accessType)) ||
        (body.privacyType != null && !allowedPrivacy.has(body.privacyType)) ||
        (body.accessAnswered !== undefined && typeof body.accessAnswered !== "boolean") ||
        (body.privacyAnswered !== undefined && typeof body.privacyAnswered !== "boolean") ||
        (body.accessAnswered === false && body.accessType != null) ||
        (body.privacyAnswered === false && body.privacyType != null)) return null;
    return { overall: body.overall, comment: body.comment?.trim() || null,
        accessType: body.accessType ?? null, privacyType: body.privacyType ?? null,
        accessAnswered: body.accessAnswered ?? (body.accessType != null),
        privacyAnswered: body.privacyAnswered ?? (body.privacyType != null),
        submissionId: body.submissionId, demo: body.demo };
}

const sourceSql = `SELECT r.restroom_id,r.business_id,r.access_type,r.restroom_type,r.verified,b.name AS business_name
    FROM public.restrooms r JOIN public.businesses b ON b.business_id=r.business_id WHERE r.restroom_id=$1`;

function createArrivalReviewHandlers({ main, demo }) {
    async function context(req, res, next) {
        const id = Number(req.params.id);
        if (!Number.isSafeInteger(id) || id <= 0) return res.status(400).json({ error: "Invalid restroom ID" });
        try {
            const result = await main.query(sourceSql, [id]);
            if (!result.rows.length) return res.status(404).json({ error: "Restroom not found" });
            res.json(result.rows[0]);
        } catch (error) { next(error); }
    }

    async function submit(req, res, next) {
        const id = Number(req.params.id), input = validateReview(req.body);
        if (!Number.isSafeInteger(id) || id <= 0 || !input)
            return res.status(400).json({ error: "Choose an overall rating from 1 to 5 and valid optional answers." });
        // The session is the authority. Never accept a client-supplied user ID.
        const userId = req.session?.userId;
        if (!Number.isSafeInteger(userId) || userId <= 0)
            return res.status(401).json({ error: "Guest session required. Retry to reconnect." });
        let client;
        try {
            const source = (await main.query(sourceSql, [id])).rows[0];
            if (!source) return res.status(404).json({ error: "Restroom not found" });
            client = await (input.demo ? demo : main).connect();
            await client.query("BEGIN");
            if (input.demo) {
                // Only reference IDs and test user identity are copied; production reports are never copied.
                await client.query(`INSERT INTO public.users(user_id,username,password_hash) VALUES($1,$2,'ANONYMOUS')
                    ON CONFLICT(user_id) DO NOTHING`, [userId, "demo_guest_" + userId]);
                await client.query(`INSERT INTO public.businesses(business_id,name) VALUES($1,$2)
                    ON CONFLICT(business_id) DO UPDATE SET name=EXCLUDED.name`, [source.business_id, source.business_name]);
                await client.query(`INSERT INTO public.restrooms(restroom_id,business_id,access_type,restroom_type,verified)
                    VALUES($1,$2,$3,$4,$5) ON CONFLICT(restroom_id) DO UPDATE SET business_id=EXCLUDED.business_id`,
                    [id, source.business_id, source.access_type, source.restroom_type, source.verified]);
            }
            // Serialize repeat taps, retries and different arrivals by the same reviewer at the same restroom.
            await client.query("SELECT pg_advisory_xact_lock($1::int,$2::int)", [userId, id]);
            const payloadHash = crypto.createHash("sha256").update(JSON.stringify([id, input.overall,
                input.comment, input.accessType, input.privacyType, input.accessAnswered, input.privacyAnswered, input.demo])).digest("hex");
            const previous = (await client.query(`SELECT restroom_id,payload_hash FROM public.arrival_review_submissions
                WHERE user_id=$1 AND submission_id=$2`, [userId, input.submissionId])).rows[0];
            if (previous) {
                await client.query("ROLLBACK");
                if (previous.restroom_id !== id || previous.payload_hash !== payloadHash)
                    return res.status(409).json({ error: "This arrival was already submitted with different answers. Return to your trip." });
                return res.json({ saved: true, demo: input.demo, replayed: true });
            }
            await client.query(`INSERT INTO public.ratings(user_id,restroom_id,overall) VALUES($1,$2,$3)
                ON CONFLICT(user_id,restroom_id) DO UPDATE SET overall=EXCLUDED.overall`,
                [userId, id, input.overall]);
            let reviewId = null;
            if (input.comment) {
                reviewId = (await client.query(`INSERT INTO public.reviews(user_id,restroom_id,comment)
                    VALUES($1,$2,$3) RETURNING review_id`, [userId, id, input.comment])).rows[0].review_id;
            }
            if (input.accessAnswered || input.privacyAnswered) {
                const existing = (await client.query(`SELECT access_type,privacy_type FROM public.restroom_confirmations
                    WHERE user_id=$1 AND restroom_id=$2`, [userId,id])).rows[0];
                // Unselected leaves an older answer intact; explicit Not sure clears that user's answer.
                const access = input.accessAnswered ? input.accessType : existing?.access_type ?? null;
                const privacy = input.privacyAnswered ? input.privacyType : existing?.privacy_type ?? null;
                if (access != null || privacy != null) {
                    await client.query(`INSERT INTO public.restroom_confirmations(user_id,restroom_id,access_type,privacy_type)
                        VALUES($1,$2,$3,$4) ON CONFLICT(user_id,restroom_id) DO UPDATE SET
                        access_type=EXCLUDED.access_type,privacy_type=EXCLUDED.privacy_type,confirmed_at=now()`,
                        [userId, id, access, privacy]);
                } else {
                    // The schema disallows an all-null report, so fully unanswered means no report row.
                    await client.query("DELETE FROM public.restroom_confirmations WHERE user_id=$1 AND restroom_id=$2", [userId,id]);
                }
            }
            await client.query(`INSERT INTO public.arrival_review_submissions(user_id,submission_id,restroom_id,payload_hash,review_id)
                VALUES($1,$2,$3,$4,$5)`, [userId, input.submissionId, id, payloadHash, reviewId]);
            await client.query("COMMIT");
            res.json({ saved: true, demo: input.demo, replayed: false });
        } catch (error) {
            if (client) await client.query("ROLLBACK").catch(() => {});
            if (error.code === "42P01" || error.code === "3D000" || error.code === "ECONNREFUSED")
                return res.status(503).json({ error: input.demo
                    ? "Demo review storage is unavailable. Run the review database setup and retry."
                    : "Review storage is unavailable. Apply the review and confirmation migrations, then retry." });
            next(error);
        } finally { client?.release(); }
    }
    return { context, submit };
}
module.exports = { createArrivalReviewHandlers, validateReview };
