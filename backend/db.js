const { Pool } = require("pg");

// pg reads PGHOST, PGPORT, PGDATABASE, PGUSER and PGPASSWORD from the environment.
// npm start loads the ignored .env file; credentials never belong in this source.
const pool = new Pool({
    connectionTimeoutMillis: 5000,
    query_timeout: 10000
});

module.exports = pool;
