-- presentation_records
ALTER TABLE public.presentation_records
    ADD COLUMN "anoncred_credentials_to_use_json_schema_id" VARCHAR(64),
    ADD COLUMN "anoncred_credentials_to_use" JSON;