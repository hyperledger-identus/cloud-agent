ALTER TABLE public.issue_credential_records RENAME COLUMN schema_id TO schema_uri;
ALTER TABLE public.issue_credential_records ADD COLUMN credential_definition_uri VARCHAR(500);
