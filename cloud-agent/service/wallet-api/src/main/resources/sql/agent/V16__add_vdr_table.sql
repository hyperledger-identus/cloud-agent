-- VDR lib already takes care of migration, but we want to put this
-- in migration file anyway to ensure table is managed properly
-- in case users skip some version and VDR lib update its migration logic.
CREATE TABLE IF NOT EXISTS public.storage (
    id VARCHAR(64) PRIMARY KEY,
    data BYTEA NOT NULL
);
