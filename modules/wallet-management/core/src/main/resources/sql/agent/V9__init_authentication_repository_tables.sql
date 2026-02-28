CREATE TABLE public.authentication_method
(
    id         UUID PRIMARY KEY default gen_random_uuid(),
    type       VARCHAR(128)             NOT NULL,
    entity_id  UUID                     NOT NULL,
    secret     VARCHAR(256)             NOT NULL
);

CREATE INDEX authentication_method_secret_idx ON public.authentication_method(secret);
CREATE INDEX authentication_method_type_idx ON public.authentication_method(type);
CREATE INDEX authentication_method_type_and_secret_idx ON public.authentication_method(type, secret);
