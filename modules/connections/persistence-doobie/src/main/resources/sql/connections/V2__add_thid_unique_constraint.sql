ALTER TABLE public.connection_records
ADD CONSTRAINT unique_thid UNIQUE (thid);