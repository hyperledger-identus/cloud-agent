CREATE TABLE public.verifiable_credential_schema
(
    id          UUID PRIMARY KEY default gen_random_uuid(),
    name        VARCHAR(128)             NOT NULL,
    version     VARCHAR(64)              NOT NULL,
    tags        VARCHAR(64) ARRAY        NULL,
    description TEXT                     NULL,
    attributes  VARCHAR(64) ARRAY        NOT NULL,
    author      VARCHAR(255)             NOT NULL,
    authored    TIMESTAMP WITH TIME ZONE NOT NULL,
    proof_id    UUID                     NULL,
    UNIQUE (name, version, author)
);

CREATE INDEX name_index ON public.verifiable_credential_schema (name);
CREATE INDEX version_index ON public.verifiable_credential_schema (version);
CREATE INDEX tags_index ON public.verifiable_credential_schema (tags);
CREATE INDEX attributes_index ON public.verifiable_credential_schema (attributes);
CREATE INDEX author_index ON public.verifiable_credential_schema (author);
CREATE INDEX authored_index ON public.verifiable_credential_schema (authored);

CREATE TABLE public.proof
(
    id                 UUID primary key default gen_random_uuid(),
    type               VARCHAR(128)             NOT NULL,
    created            TIMESTAMP WITH TIME ZONE NOT NULL,
    verification_method VARCHAR(128)             NOT NULL,
    proof_purpose       VARCHAR(128)             NOT NULL,
    proof_value         TEXT                     NOT NULL,
    domain             VARCHAR(255)             NULL
);



CREATE INDEX type_index ON public.proof (type);
CREATE INDEX verification_method_index ON public.proof (verification_method);
CREATE INDEX proof_purpose_index ON public.proof (proof_purpose)



