-- Add the fields: meta_retries; meta_last_failure
ALTER TABLE public.connection_records
ADD meta_retries BIGINT NOT NULL,
ADD meta_last_failure TEXT;