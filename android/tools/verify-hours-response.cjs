const assert = require("node:assert/strict");
const db = require("../../backend/db");
(async () => {
  try {
    const database = (await db.query("SELECT current_database() AS name")).rows[0].name;
    const health = await (await fetch("http://127.0.0.1:3000/api/health")).json();
    assert.equal(database,"ibs");
    assert.equal(health.databaseName,database);
    const expected = (await db.query("SELECT iso_weekday, opens_at, closes_at FROM public.business_hours WHERE business_id=8 ORDER BY iso_weekday")).rows;
    assert.equal(expected.length,7);
    const list = await (await fetch("http://127.0.0.1:3000/api/restrooms")).json();
    const room = list.find(row => Number(row.business_id) === 8);
    assert.ok(room);
    const detail = await (await fetch("http://127.0.0.1:3000/api/restrooms/"+room.restroom_id)).json();
    assert.deepEqual(room.business_hours,expected);
    assert.deepEqual(detail.business_hours,expected);
    const raw = (await db.query("SELECT AVG(overall) AS average FROM public.ratings WHERE restroom_id=$1",[room.restroom_id])).rows[0].average;
    assert.equal(room.average_rating,raw);
    console.log("Verified running API database ibs; Circle K business 8 / restroom "+room.restroom_id+
      ": seven identical PostgreSQL/list/details schedule rows and unrounded rating average.");
  } catch (_) { console.error("Hours API verification failed; check the local backend and ibs database."); process.exitCode=1; }
  finally { await db.end(); }
})();
