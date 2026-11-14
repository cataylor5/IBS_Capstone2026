-- Support for durable guest sessions and retry-safe, transactional arrival reviews.
-- The separately managed restroom_confirmations table is intentionally not changed here.
CREATE TABLE IF NOT EXISTS public.ibs_sessions (
    sid text PRIMARY KEY,
    sess jsonb NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ibs_sessions_expiry ON public.ibs_sessions (expires_at);
CREATE TABLE IF NOT EXISTS public.arrival_review_submissions (
    user_id integer NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    submission_id uuid NOT NULL,
    restroom_id integer NOT NULL REFERENCES public.restrooms(restroom_id) ON DELETE CASCADE,
    payload_hash text NOT NULL,
    review_id integer REFERENCES public.reviews(review_id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, submission_id)
);
