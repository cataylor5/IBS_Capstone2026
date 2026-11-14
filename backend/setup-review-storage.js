const { Pool } = require("pg");
const fs = require("node:fs");
const path = require("node:path");
const support = fs.readFileSync(path.join(__dirname, "migrations/001_arrival_reviews.sql"), "utf8");
const database = process.env.PGREVIEW_TEST_DATABASE || "ibs_reviews_test";
const main = new Pool({ connectionTimeoutMillis: 5000, query_timeout: 10000 });
let demo;
(async () => {
    if (!/^[a-zA-Z0-9_]+_test$/.test(database)) throw new Error("Invalid test database name");
    const source = (await main.query("SELECT current_database() AS name")).rows[0].name;
    if (database === source) throw new Error("Test and main databases must differ");
    await main.query(support);
    const exists = (await main.query("SELECT 1 FROM pg_database WHERE datname=$1", [database])).rowCount;
    if (!exists) await main.query('CREATE DATABASE "' + database + '"');
    demo = new Pool({ database, connectionTimeoutMillis: 5000, query_timeout: 10000 });
    const tables = await demo.query("SELECT to_regclass('public.users') AS users,to_regclass('public.ibs_review_test_metadata') AS marker");
    if (tables.rows[0].users && !tables.rows[0].marker) throw new Error("Existing test database is not managed by IBS review setup");
    await demo.query(`
        CREATE TABLE IF NOT EXISTS public.ibs_review_test_metadata (test_data_only boolean NOT NULL CHECK(test_data_only));
        CREATE TABLE IF NOT EXISTS public.users(user_id integer PRIMARY KEY,username text UNIQUE NOT NULL,password_hash text NOT NULL);
        CREATE TABLE IF NOT EXISTS public.businesses(business_id integer PRIMARY KEY,name text NOT NULL);
        CREATE TABLE IF NOT EXISTS public.restrooms(restroom_id integer PRIMARY KEY,business_id integer NOT NULL REFERENCES businesses,
            access_type text,restroom_type text,verified boolean NOT NULL DEFAULT false);
        CREATE TABLE IF NOT EXISTS public.ratings(rating_id serial PRIMARY KEY,user_id integer NOT NULL REFERENCES users,
            restroom_id integer NOT NULL REFERENCES restrooms,overall integer NOT NULL CHECK(overall BETWEEN 1 AND 5),
            cleanliness integer,privacy integer,accessibility integer,created_at timestamptz DEFAULT now(),
            UNIQUE(user_id,restroom_id));
        CREATE TABLE IF NOT EXISTS public.reviews(review_id serial PRIMARY KEY,user_id integer NOT NULL REFERENCES users,
            restroom_id integer NOT NULL REFERENCES restrooms,comment text NOT NULL,created_at timestamptz DEFAULT now());
        CREATE TABLE IF NOT EXISTS public.restroom_confirmations(
            user_id integer NOT NULL REFERENCES users,restroom_id integer NOT NULL REFERENCES restrooms,
            access_type text CHECK(access_type IN ('PUBLIC','CUSTOMER_ONLY','MEMBERSHIP_REQUIRED')),
            privacy_type text CHECK(privacy_type IN ('PRIVATE','STALLS')),confirmed_at timestamptz NOT NULL DEFAULT now(),
            PRIMARY KEY(user_id,restroom_id),CHECK(access_type IS NOT NULL OR privacy_type IS NOT NULL));
    `);
    await demo.query(support);
    const confirmation = (await main.query("SELECT to_regclass('public.restroom_confirmations') AS name")).rows[0].name;
    console.log("Review support tables ready; isolated demo review database ready.");
    console.log(confirmation ? "Main confirmation table is present." : "Main confirmation migration still required.");
})().catch(() => { console.error("Review database setup failed; check database permissions and schema."); process.exitCode = 1; })
.finally(async () => { await main.end(); if (demo) await demo.end(); });
