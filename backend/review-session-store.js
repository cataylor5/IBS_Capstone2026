const session = require("express-session");

/** Durable guest identity; a backend restart must not create a different reviewer. */
class PgSessionStore extends session.Store {
    constructor(pool) { super(); this.pool = pool; }
    get(sid, callback) {
        this.pool.query("SELECT sess FROM public.ibs_sessions WHERE sid=$1 AND expires_at>now()", [sid])
            .then(result => callback(null, result.rows[0]?.sess || null), callback);
    }
    set(sid, value, callback = () => {}) {
        const expires = value.cookie?.expires || new Date(Date.now() + 7 * 86400000);
        this.pool.query(`INSERT INTO public.ibs_sessions(sid,sess,expires_at) VALUES($1,$2,$3)
            ON CONFLICT(sid) DO UPDATE SET sess=EXCLUDED.sess,expires_at=EXCLUDED.expires_at`,
            [sid, JSON.stringify(value), expires]).then(() => callback(null), callback);
    }
    destroy(sid, callback = () => {}) {
        this.pool.query("DELETE FROM public.ibs_sessions WHERE sid=$1", [sid])
            .then(() => callback(null), callback);
    }
    touch(sid, value, callback = () => {}) {
        this.pool.query("UPDATE public.ibs_sessions SET expires_at=$2 WHERE sid=$1",
            [sid, value.cookie?.expires || new Date(Date.now() + 7 * 86400000)])
            .then(() => callback(null), callback);
    }
}
module.exports = { PgSessionStore };
