-- generalize prism DID key table to contain any curve and non-deterministic key
ALTER TABLE public.prism_did_hd_key
RENAME TO prism_did_key;

CREATE TYPE public.curve_name AS ENUM (
    'secp256k1',
    'Ed25519',
    'X25519'
);

-- enforce more strict constraints on key table
UPDATE public.prism_did_key
SET key_usage = 'MASTER'
WHERE key_usage IS NULL;

ALTER TABLE public.prism_did_key
ALTER COLUMN key_usage SET NOT NULL,
ADD COLUMN key_mode PRISM_DID_KEY_MODE NOT NULL DEFAULT 'HD',
ADD COLUMN curve_name CURVE_NAME NOT NULL DEFAULT 'secp256k1';

ALTER TABLE public.prism_did_key
ALTER COLUMN key_mode DROP DEFAULT,
ALTER COLUMN key_usage DROP DEFAULT,
ALTER COLUMN curve_name DROP DEFAULT;

ALTER TABLE public.prism_did_wallet_state
DROP COLUMN key_mode;

-- secretStorage of prism DID key pair
CREATE TABLE public.prism_did_rand_key (
    "did" TEXT NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL,
    "key_id" TEXT NOT NULL,
    "operation_hash" BYTEA NOT NULL,
    "key_pair" TEXT NOT NULL,
    PRIMARY KEY ("did", "key_id", "operation_hash")
);

ALTER TABLE public.prism_did_rand_key
ENABLE ROW LEVEL SECURITY;

CREATE POLICY prism_did_rand_key_wallet_isolation
ON public.prism_did_rand_key
USING (
    EXISTS (
        SELECT 1
        FROM prism_did_wallet_state AS s
        WHERE
            s.wallet_id = current_setting('app.current_wallet_id')::UUID
            AND s.did = public.prism_did_rand_key.did
    )
);
