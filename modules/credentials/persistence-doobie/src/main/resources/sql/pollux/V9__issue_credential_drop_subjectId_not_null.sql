ALTER TABLE public.issue_credential_records ALTER COLUMN "subject_id" DROP NOT NULL;

ALTER TABLE public.issue_credential_records ALTER COLUMN "created_at" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.issue_credential_records ALTER COLUMN "updated_at" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.issue_credential_records ALTER COLUMN "meta_next_retry" TYPE TIMESTAMP WITH TIME ZONE;

ALTER TABLE public.presentation_records ALTER COLUMN "created_at" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.presentation_records ALTER COLUMN "updated_at" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.presentation_records ALTER COLUMN "meta_next_retry" TYPE TIMESTAMP WITH TIME ZONE;