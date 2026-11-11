// Google Routes stays server-side. Never log coordinates, keys, or upstream payloads.
function validPoint(point) {
    return point && typeof point.latitude === "number" && Number.isFinite(point.latitude) &&
        point.latitude >= -90 && point.latitude <= 90 &&
        typeof point.longitude === "number" && Number.isFinite(point.longitude) &&
        point.longitude >= -180 && point.longitude <= 180;
}

class RouteFailure extends Error {
    constructor(status, code, message, extra = {}) {
        super(message);
        this.status = status;
        this.payload = { code, error: message, ...extra };
    }
}

function createRouteHandler({
    getApiKey = () => process.env.GOOGLE_ROUTES_API_KEY,
    fetchImpl = globalThis.fetch,
    lookupRestroom = async () => null
} = {}) {
    return async (req, res) => {
        const { origin, destination, restroomId, maxDetourMinutes, freePublicOnly } = req.body || {};
        if (!validPoint(origin) || !validPoint(destination)) {
            return res.status(400).json({ code: "INVALID_ENDPOINTS", error: "Two valid coordinate pairs are required." });
        }
        const withStop = restroomId !== undefined;
        if (withStop && (!Number.isSafeInteger(restroomId) || restroomId <= 0 ||
            ![5, 10, 15].includes(maxDetourMinutes) || typeof freePublicOnly !== "boolean")) {
            return res.status(400).json({ code: "INVALID_DETOUR_PREFERENCES", error: "A restroom ID, detour allowance and availability preference are required." });
        }
        const key = getApiKey();
        if (!key || /^(YOUR_|REPLACE_|PLACEHOLDER)/i.test(key)) {
            return res.status(503).json({ code: "ROUTES_NOT_CONFIGURED", error: "The route service is not configured." });
        }
        const waypoint = point => ({ location: { latLng: {
            latitude: point.latitude, longitude: point.longitude
        } } });
        async function compute(stop) {
            const body = {
                origin: waypoint(origin), destination: waypoint(destination),
                travelMode: "DRIVE", routingPreference: "TRAFFIC_UNAWARE",
                computeAlternativeRoutes: false, polylineQuality: "OVERVIEW",
                polylineEncoding: "ENCODED_POLYLINE"
            };
            if (stop) body.intermediates = [waypoint(stop)];
            const response = await fetchImpl("https://routes.googleapis.com/directions/v2:computeRoutes", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Goog-Api-Key": key,
                    "X-Goog-FieldMask": "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"
                },
                signal: AbortSignal.timeout(12_000),
                body: JSON.stringify(body)
            });
            if (!response.ok) {
                const denied = response.status === 401 || response.status === 403;
                throw new RouteFailure(502, denied ? "ROUTES_ACCESS_DENIED" : "ROUTES_UPSTREAM_FAILED",
                    denied ? "The route provider denied access. Check the server key and Routes API configuration."
                        : "The route provider is temporarily unavailable.", { upstreamStatus: response.status });
            }
            const data = await response.json();
            const routes = data && typeof data === "object" && !Array.isArray(data) && Object.keys(data).length === 0
                ? [] : data?.routes;
            if (!Array.isArray(routes)) {
                throw new RouteFailure(502, "INVALID_ROUTE_RESPONSE", "The route provider returned an unexpected response.");
            }
            if (!routes.length) {
                throw new RouteFailure(404, "NO_ROUTE", "No driving route was found for these locations.");
            }
            const route = routes[0];
            const duration = typeof route?.duration === "string" && /^\d+(?:\.\d{1,9})?s$/.test(route.duration)
                ? Number(route.duration.slice(0, -1)) : NaN;
            const encodedPolyline = route?.polyline?.encodedPolyline;
            if (!Number.isSafeInteger(route?.distanceMeters) || route.distanceMeters < 0 ||
                !Number.isFinite(duration) || duration < 0 ||
                typeof encodedPolyline !== "string" || !encodedPolyline.length || encodedPolyline.length > 200000) {
                throw new RouteFailure(502, "INVALID_ROUTE_RESPONSE", "The route provider returned incomplete route data.");
            }
            return { distanceMeters: route.distanceMeters, durationSeconds: duration, encodedPolyline };
        }

        try {
            if (!withStop) return res.json(await compute());
            let row;
            try { row = await lookupRestroom(restroomId); }
            catch (_) { throw new RouteFailure(503, "RESTROOM_LOOKUP_FAILED", "Couldn't look up this restroom. Please retry."); }
            if (!row) throw new RouteFailure(404, "RESTROOM_NOT_FOUND", "This restroom is no longer available.");
            const coordinates = {
                latitude: row.latitude == null ? NaN : Number(row.latitude),
                longitude: row.longitude == null ? NaN : Number(row.longitude)
            };
            if (!validPoint(coordinates) || typeof row.name !== "string" || !row.name.trim()) {
                throw new RouteFailure(422, "RESTROOM_COORDINATES_UNAVAILABLE", "This restroom has no usable location.");
            }
            if (!["PUBLIC", "CUSTOMER_ONLY"].includes(row.accessType) ||
                (freePublicOnly && !(row.accessType === "PUBLIC" && row.feeRequired === false && row.requiresPurchase === false))) {
                throw new RouteFailure(409, "RESTROOM_NOT_ELIGIBLE", "This restroom does not meet the selected access and fee preference.");
            }
            // Re-evaluate the direct baseline with identical routing options. Returning its
            // geometry also lets Android remove a stop without another provider request.
            const [baseline, route] = await Promise.all([compute(), compute(coordinates)]);
            const extraDrivingSeconds = Math.max(0, route.durationSeconds - baseline.durationSeconds);
            if (extraDrivingSeconds > maxDetourMinutes * 60) {
                throw new RouteFailure(409, "DETOUR_LIMIT_EXCEEDED", "This stop exceeds the selected driving detour allowance.",
                    { extraDrivingSeconds, detourLimitMinutes: maxDetourMinutes });
            }
            return res.json({
                ...route,
                stop: { id: restroomId, name: row.name, address: row.address || null, ...coordinates },
                baseline, extraDrivingSeconds, detourLimitMinutes: maxDetourMinutes
            });
        } catch (error) {
            if (error instanceof RouteFailure) return res.status(error.status).json(error.payload);
            const timedOut = error?.name === "TimeoutError" || error?.name === "AbortError";
            return res.status(timedOut ? 504 : 502).json({
                code: timedOut ? "ROUTES_TIMEOUT" : "ROUTES_UNAVAILABLE",
                error: timedOut ? "Route calculation timed out. Please try again." : "Couldn't reach the route provider. Please try again."
            });
        }
    };
}

module.exports = { createRouteHandler, validPoint };
