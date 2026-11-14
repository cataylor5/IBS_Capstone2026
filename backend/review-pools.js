const { Pool } = require("pg");
const main = require("./db");
const database = process.env.PGREVIEW_TEST_DATABASE || "ibs_reviews_test";
if (!/^[a-zA-Z0-9_]+_test$/.test(database) || database === process.env.PGDATABASE)
    throw new Error("Demo reviews require a separate database ending in _test.");
const demo = new Pool({ database, connectionTimeoutMillis: 5000, query_timeout: 10000 });
module.exports = { main, demo };
