CREATE TABLE public.credential_definition
(
    guid               UUID PRIMARY KEY default gen_random_uuid(),
    id                 UUID                     NOT NULL,
    name               VARCHAR(128)             NOT NULL,
    version            VARCHAR(64)              NOT NULL,
    tags               VARCHAR(64) ARRAY NULL,
    description        TEXT NULL,
    definition_json_schema_id            VARCHAR(64) NOT NULL,
    definition         json                     NOT NULL,
    key_correctness_proof_json_schema_id VARCHAR(64) NOT NULL,
    key_correctness_proof         json                     NOT NULL,
    author             VARCHAR(255)             NOT NULL,
    authored           TIMESTAMP WITH TIME ZONE NOT NULL,
    proof_id           UUID NULL,
    schema_id          VARCHAR(255)             NOT NULL,
    signature_type     VARCHAR(64)              NOT NULL,
    support_revocation BOOLEAN                  NOT NULL,
    UNIQUE (name, version, author)
);

CREATE INDEX credential_definition_name_index ON public.credential_definition (name);
CREATE INDEX credential_definition_version_index ON public.credential_definition (version);
CREATE INDEX credential_definition_tags_index ON public.credential_definition (tags);
CREATE INDEX credential_definition_author_index ON public.credential_definition (author);
CREATE INDEX credential_definition_authored_index ON public.credential_definition (authored);



