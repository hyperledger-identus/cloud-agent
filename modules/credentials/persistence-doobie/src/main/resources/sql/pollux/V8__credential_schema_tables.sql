CREATE TABLE public.credential_schema
(
    guid        UUID PRIMARY KEY default gen_random_uuid(),
    id          UUID                     NOT NULL,
    type        varchar(128)             NOT NULL,
    name        VARCHAR(128)             NOT NULL,
    version     VARCHAR(64)              NOT NULL,
    tags        VARCHAR(64) ARRAY        NULL,
    description TEXT                     NULL,
    schema      json                     NOT NULL,
    author      VARCHAR(255)             NOT NULL,
    authored    TIMESTAMP WITH TIME ZONE NOT NULL,
    proof_id    UUID                     NULL,
    UNIQUE (name, version, author)
);

CREATE INDEX credential_schema_name_index ON public.credential_schema (name);
CREATE INDEX credential_schema_type_index ON public.credential_schema (type);
CREATE INDEX credential_schema_version_index ON public.credential_schema (version);
CREATE INDEX credential_schema_tags_index ON public.credential_schema (tags);
CREATE INDEX credential_schema_author_index ON public.credential_schema (author);
CREATE INDEX credential_schema_authored_index ON public.credential_schema (authored);



