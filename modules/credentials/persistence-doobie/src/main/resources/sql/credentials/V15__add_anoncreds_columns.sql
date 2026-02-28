-- issue_credential_records
ALTER TABLE public.issue_credential_records
    ADD COLUMN "credential_format"              VARCHAR(9),
    ADD COLUMN "credential_definition_id"       UUID,
    ADD COLUMN "ac_request_credential_metadata" TEXT;

UPDATE public.issue_credential_records
SET "credential_format" = 'JWT';

ALTER TABLE public.issue_credential_records
    ALTER COLUMN "credential_format" SET NOT NULL;



-- presentation_records
ALTER TABLE public.presentation_records
    ADD COLUMN "credential_format"              VARCHAR(9);

UPDATE public.presentation_records
SET "credential_format" = 'JWT';

ALTER TABLE public.presentation_records
    ALTER COLUMN "credential_format" SET NOT NULL;
