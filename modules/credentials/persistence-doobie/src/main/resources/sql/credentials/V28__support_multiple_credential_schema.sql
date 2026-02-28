ALTER TABLE public.issue_credential_records
    ADD COLUMN schema_uris VARCHAR(500)[];

UPDATE public.issue_credential_records
SET schema_uris = ARRAY[schema_uri]
WHERE schema_uri IS NOT NULL;

ALTER TABLE public.issue_credential_records
    DROP COLUMN schema_uri;