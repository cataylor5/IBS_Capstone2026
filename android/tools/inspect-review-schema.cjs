const db = require("../../backend/db");
(async () => {
    for (const query of [
        "SELECT current_database() AS database",
        "SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('restroom_confirmations','ratings','reviews','users') ORDER BY table_name,ordinal_position",
        "SELECT conrelid::regclass::text AS table_name,pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid IN (to_regclass('public.restroom_confirmations'),to_regclass('public.reviews'))",
        "SELECT datname FROM pg_database WHERE datname LIKE 'ibs%'"
    ]) console.log(JSON.stringify((await db.query(query)).rows));
    await db.end();
})().catch(async () => { console.error("Schema diagnostic failed"); await db.end(); process.exitCode = 1; });
