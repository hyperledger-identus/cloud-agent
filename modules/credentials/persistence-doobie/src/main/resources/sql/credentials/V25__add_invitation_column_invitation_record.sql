-- issue_credential_records
-- Introduce new field invitation for connection-less credential issuance
ALTER TABLE public.issue_credential_records
    ADD COLUMN "invitation" TEXT;