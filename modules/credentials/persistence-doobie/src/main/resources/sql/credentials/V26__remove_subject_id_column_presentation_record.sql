-- presentation_records
-- Remove subject_id column
ALTER TABLE public.presentation_records
    DROP COLUMN "subject_id";