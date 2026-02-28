ALTER TABLE public.did_secret_storage
RENAME TO peer_did_rand_key;

-- Introduce DID breaking change given that we do not want to handle
-- migration complextiy and it's not used in production instance yet.
DELETE FROM public.peer_did_rand_key
WHERE true;

DELETE FROM public.prism_did_hd_key
WHERE true;

DELETE FROM public.prism_did_update_lineage
WHERE true;

DELETE FROM public.prism_did_wallet_state
WHERE true;

-- Introduce the concept of wallet
CREATE TABLE public.wallet (
    "wallet_id" UUID PRIMARY KEY,
    "name" VARCHAR(128) NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL,
    "updated_at" TIMESTAMPTZ NOT NULL
);

CREATE TABLE public.wallet_seed (
    "wallet_id" UUID PRIMARY KEY REFERENCES public.wallet ("wallet_id"),
    "seed" BYTEA NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL
);

CREATE TABLE public.wallet_notification (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "wallet_id" UUID REFERENCES public.wallet ("wallet_id") NOT NULL,
    "url" TEXT NOT NULL,
    "custom_headers" TEXT NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL
);

ALTER TABLE public.peer_did_rand_key
ADD COLUMN "wallet_id" UUID REFERENCES public.wallet (
    "wallet_id"
) NOT NULL;

ALTER TABLE public.prism_did_wallet_state
ADD COLUMN "wallet_id" UUID REFERENCES public.wallet (
    "wallet_id"
) NOT NULL;

-- Change contraints scope to only the same wallet
ALTER TABLE public.prism_did_wallet_state
DROP CONSTRAINT prism_did_wallet_state_did_index_key;

ALTER TABLE public.prism_did_wallet_state
ADD CONSTRAINT wallet_id_did_index UNIQUE (wallet_id, did_index);

-- Enforce RLS
ALTER TABLE public.peer_did_rand_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.prism_did_wallet_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_seed ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.prism_did_hd_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.prism_did_update_lineage ENABLE ROW LEVEL SECURITY;

CREATE POLICY peer_did_rand_key_wallet_isolation
ON public.peer_did_rand_key
USING (wallet_id = current_setting('app.current_wallet_id')::UUID);

CREATE POLICY prism_did_wallet_state_wallet_isolation
ON public.prism_did_wallet_state
USING (wallet_id = current_setting('app.current_wallet_id')::UUID);

CREATE POLICY wallet_seed_wallet_isolation
ON public.wallet_seed
USING (wallet_id = current_setting('app.current_wallet_id')::UUID);

CREATE POLICY wallet_notification_isolation
ON public.wallet_notification
USING (wallet_id = current_setting('app.current_wallet_id')::UUID);

CREATE POLICY prism_did_hd_key_wallet_isolation
ON public.prism_did_hd_key
USING (
    EXISTS (
        SELECT 1
        FROM prism_did_wallet_state AS s
        WHERE
            s.wallet_id = current_setting('app.current_wallet_id')::UUID
            AND s.did = public.prism_did_hd_key.did
    )
);

CREATE POLICY prism_did_update_lineage_wallet_isolation
ON public.prism_did_update_lineage
USING (
    EXISTS (
        SELECT 1
        FROM prism_did_wallet_state AS s
        WHERE
            s.wallet_id = current_setting('app.current_wallet_id')::UUID
            AND s.did = public.prism_did_update_lineage.did
    )
);
