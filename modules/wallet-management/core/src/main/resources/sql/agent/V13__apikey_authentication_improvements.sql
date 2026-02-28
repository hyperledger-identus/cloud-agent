ALTER TABLE public.authentication_method
    DROP COLUMN id;

ALTER TABLE public.authentication_method
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE public.authentication_method
    ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE public.authentication_method
    ADD CONSTRAINT unique_type_secret_constraint UNIQUE (type, secret);