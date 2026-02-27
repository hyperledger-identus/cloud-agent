-- presentation_records
ALTER TABLE public.presentation_records
    ADD COLUMN "sd_jwt_claims_to_use_json_schema_id" VARCHAR(64),
    ADD COLUMN "sd_jwt_claims_to_disclose" JSON;