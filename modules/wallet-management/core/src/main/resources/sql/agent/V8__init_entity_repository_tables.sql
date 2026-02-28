CREATE TABLE public.entity
(
    id         UUID PRIMARY KEY default gen_random_uuid(),
    name       VARCHAR(128)                                NOT NULL,
    wallet_id  UUID REFERENCES public.wallet ("wallet_id") NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE                    NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE                    NOT NULL
);

CREATE INDEX entity_wallet_id_index ON public.entity (wallet_id);
CREATE INDEX entity_name_index ON public.entity (name);
