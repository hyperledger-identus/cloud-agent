ALTER TABLE public.peer_did_rand_key DROP COLUMN "schema_id";

CREATE TABLE public.generic_secret (
    "key" TEXT NOT NULL,
    "payload" TEXT NOT NULL,
    "created_at" TIMESTAMP WITH TIME ZONE NOT NULL,
    "wallet_id" UUID REFERENCES public.wallet ("wallet_id") NOT NULL,
    CONSTRAINT unique_key_wallet UNIQUE ("key", "wallet_id")
);

ALTER TABLE public.generic_secret ENABLE ROW LEVEL SECURITY;

CREATE POLICY generic_secret_wallet_isolation
ON public.generic_secret
USING (wallet_id = current_setting('app.current_wallet_id')::UUID);
