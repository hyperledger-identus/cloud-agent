-- presentation_records
ALTER TABLE public.presentation_records
    ADD COLUMN "sd_jwt_disclosed_claims" JSON;