-- CREATE TYPE public.operation_type AS ENUM(
--   'CREATE',
--   'UPDATE',
--   'RECOVER',
--   'DEACTIVATE'
-- );
--
-- CREATE DOMAIN public.did_suffix_type AS TEXT CHECK(VALUE ~ '^[0-9a-f]{64}$');
--
-- CREATE TABLE public.jwt_credentials(
--   "batch_id" VARCHAR(36) NOT NULL,
--   "credential_id" VARCHAR(36) NOT NULL,
--   "content" TEXT NOT NULL
-- );

CREATE TABLE public.issue_credential_records(
  "id" VARCHAR(64) NOT NULL PRIMARY KEY,-- must be <=32 bytes string consisting entirely of unreserved URI characters
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP,
  "thid" VARCHAR(64) NOT NULL,
  "schema_id" VARCHAR(36),
  "role"  VARCHAR(50) NOT NULL,
  "subject_id" TEXT NOT NULL,
  "validity_period" INTEGER,
  "automatic_issuance" BOOLEAN,
  "await_confirmation" BOOLEAN,
  "protocol_state" VARCHAR(50) NOT NULL,
  "publication_state" VARCHAR(50),
  "offer_credential_data" TEXT,
  "request_credential_data" TEXT,
  "issue_credential_data" TEXT,
  "issued_credential_raw" TEXT,
  "issuing_did" TEXT,
  "meta_retries" BIGINT NOT NULL,
  "meta_next_retry" TIMESTAMP,
  "meta_last_failure" TEXT,
  CONSTRAINT unique_thid UNIQUE (thid)
);

CREATE TABLE public.presentation_records(
  "id" VARCHAR(64) NOT NULL PRIMARY KEY,
  "created_at" TIMESTAMP NOT NULL,
  "updated_at" TIMESTAMP,
  "thid" VARCHAR(64) NOT NULL,
  "schema_id" VARCHAR(36),
  "connection_id" VARCHAR(36),
  "role"  VARCHAR(50) NOT NULL,
  "subject_id" TEXT NOT NULL,
  "protocol_state" VARCHAR(50) NOT NULL,
  "request_presentation_data" TEXT,
  "propose_presentation_data" TEXT,
  "presentation_data" TEXT,
  "credentials_to_use" TEXT[] NULL,
  "meta_retries" BIGINT NOT NULL,
  "meta_next_retry" TIMESTAMP,
  "meta_last_failure" TEXT
);
