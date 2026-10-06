const { Pool } = require("pg");

const pool = new Pool({
    user: "postgres",
    host: "localhost",
    database: "postgres",
    password: "Sethismy1bro.!5",
    port: 5432
});

module.exports = pool;