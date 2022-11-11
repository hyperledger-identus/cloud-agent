CREATE TYPE public.operation_type AS ENUM(
  'CREATE',
  'UPDATE',
  'RECOVER',
  'DEACTIVATE'
);

CREATE DOMAIN public.did_suffix_type AS TEXT CHECK(VALUE ~ '^[0-9a-f]{64}$');

CREATE TABLE public.confirmed_published_did_operations(
  "ledger_name" VARCHAR(32) NOT NULL,
  "did_suffix" public.did_suffix_type NOT NULL,
  "operation_type" public.operation_type NOT NULL,
  "operation_content" BYTEA NOT NULL,
  "anchored_at" TIMESTAMPTZ NOT NULL,
  "block_number" INTEGER NOT NULL,
  "block_index" INTEGER NOT NULL
);