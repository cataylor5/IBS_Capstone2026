const fs = require("node:fs");
const path = require("node:path");
const db = require("../../backend/db");
(async () => {
  try {
    const database = (await db.query("SELECT current_database() AS name")).rows[0].name;
    if (database !== "ibs") throw new Error("Unexpected database");
    await db.query(fs.readFileSync(path.join(__dirname, "migrations/20261112_membership_access.sql"), "utf8"));
    console.log("Membership access migration applied to ibs; existing restroom values preserved.");
  } catch (_) { console.error("Migration failed; verify the local ibs connection configuration. No credentials logged."); process.exitCode=1; }
  finally { await db.end(); }
})();
