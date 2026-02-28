-- Clear content of meta_last_failure
UPDATE public.issue_credential_records SET meta_last_failure=NULL;
UPDATE public.presentation_records SET meta_last_failure=NULL;