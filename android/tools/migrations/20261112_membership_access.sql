BEGIN;
-- Extend the existing constraint without changing any stored access values.
ALTER TABLE public.restrooms DROP CONSTRAINT IF EXISTS valid_access_type;
ALTER TABLE public.restrooms ADD CONSTRAINT valid_access_type
    CHECK (access_type IN ('PUBLIC', 'CUSTOMER_ONLY', 'MEMBERSHIP_REQUIRED', 'EMPLOYEE_ONLY', 'UNKNOWN'));
COMMIT;
