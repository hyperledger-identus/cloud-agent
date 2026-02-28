ALTER TABLE public.did_secret_storage
    ADD COLUMN "schema_id" VARCHAR(50) DEFAULT 'jwk' NOT NULL;