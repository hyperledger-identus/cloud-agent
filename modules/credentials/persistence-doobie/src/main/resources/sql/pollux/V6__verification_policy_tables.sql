CREATE TABLE public.verification_policy
(
    id          UUID PRIMARY KEY                  default gen_random_uuid(),
    name        VARCHAR(255)             NOT NULL,
    nonce       INTEGER                  NOT NULL,
    description TEXT                     NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL default now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL default now()
);

CREATE INDEX verification_policy_name_index ON public.verification_policy (name);
CREATE INDEX verification_policy_created_at_index ON public.verification_policy (created_at);
CREATE INDEX verification_policy_updated_at_index ON public.verification_policy (updated_at);

CREATE TABLE public.verification_policy_constraint
(
    fk_id           UUID         NOT NULL,
    index           INT          NOT NULL,
    type            VARCHAR(128) NOT NULL,
    schema_id       VARCHAR(255),
    trusted_issuers VARCHAR(255) ARRAY,

    UNIQUE (fk_id, index),

    CONSTRAINT fk_verification_policy
        FOREIGN KEY (fk_id)
            REFERENCES public.verification_policy (id)
            ON DELETE CASCADE
);

CREATE INDEX verification_policy_constraint_fk_id_index ON public.verification_policy_constraint (fk_id);
CREATE INDEX verification_policy_constraint_type_index ON public.verification_policy_constraint (type);


