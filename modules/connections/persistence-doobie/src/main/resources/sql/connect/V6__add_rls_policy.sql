-- Introduce breaking change given that we do not want to handle
-- migration complexity and it's not used in production instance yet.
DELETE
FROM public.connection_records
WHERE true;

-- Introduce a concept of wallet
ALTER TABLE public.connection_records
    ADD COLUMN "wallet_id" UUID NOT NULL;

-- Alter unique constraint on 'pthid'
ALTER TABLE public.connection_records
    DROP CONSTRAINT unique_thid;

ALTER TABLE public.connection_records
    ADD CONSTRAINT connection_records_unique_thid_per_wallet UNIQUE (wallet_id, thid);

-- Enforce RLS
ALTER TABLE public.connection_records
    ENABLE ROW LEVEL SECURITY;

CREATE POLICY connection_records_wallet_isolation
    ON public.connection_records
    USING (wallet_id = current_setting('app.current_wallet_id')::UUID);
