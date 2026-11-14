const db = require("../../backend/db");
(async () => {
  try {
    const database = (await db.query("SELECT current_database() AS database")).rows;
    const constraints = (await db.query("SELECT conname, pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid='public.restrooms'::regclass AND contype='c'")).rows;
    const businesses = (await db.query("SELECT b.business_id,b.name,COUNT(h.business_id)::int AS schedule_rows FROM public.businesses b LEFT JOIN public.business_hours h ON h.business_id=b.business_id GROUP BY b.business_id ORDER BY b.business_id")).rows;
    const circleHours = (await db.query("SELECT iso_weekday,opens_at,closes_at FROM public.business_hours WHERE business_id=8 ORDER BY iso_weekday")).rows;
    const restrooms = (await db.query("SELECT r.restroom_id,r.business_id,r.access_type,r.restroom_type,AVG(a.overall) AS average_rating,COUNT(a.rating_id)::int AS rating_count FROM public.restrooms r LEFT JOIN public.ratings a ON a.restroom_id=r.restroom_id GROUP BY r.restroom_id ORDER BY r.restroom_id")).rows;
    console.log(JSON.stringify({database,constraints,businesses,circleHours,restrooms}, null, 2));
  } catch (_) { console.error("Database diagnostic failed; no credentials logged."); process.exitCode=1; }
  finally { await db.end(); }
})();
