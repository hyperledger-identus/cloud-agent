ALTER TYPE public.did_publication_status
RENAME TO prism_did_wallet_status;

ALTER TABLE public.did_publication_state
RENAME TO prism_did_wallet_state;

-- add "created_at" column with default value for existing records
ALTER TABLE public.prism_did_wallet_state
ADD COLUMN "created_at" TIMESTAMPTZ,
ADD COLUMN "updated_at" TIMESTAMPTZ;

UPDATE public.prism_did_wallet_state
SET "created_at" = '1970-01-01 00:00:00.000+0000',
    "updated_at" = '1970-01-01 00:00:00.000+0000'
WHERE "created_at" IS NULL;

ALTER TABLE public.prism_did_wallet_state
ALTER COLUMN "created_at" SET NOT NULL,
ALTER COLUMN "updated_at" SET NOT NULL;

CREATE TYPE public.prism_did_operation_status AS ENUM(
  'PENDING_SUBMISSION',
  'AWAIT_CONFIRMATION',
  'CONFIRMED_AND_APPLIED',
  'CONFIRMED_AND_REJECTED'
);

CREATE TABLE public.prism_did_secret_storage(
  "did" TEXT NOT NULL,
  "created_at" TIMESTAMPTZ NOT NULL,
  "key_id" TEXT NOT NULL,
  "key_pair" TEXT NOT NULL,
  "operation_hash" BYTEA NOT NULL,
  PRIMARY KEY("did", "key_id", "operation_hash")
);

CREATE TABLE public.prism_did_update_lineage(
  "did" TEXT NOT NULL,
  "operation_hash" BYTEA NOT NULL,
  "previous_operation_hash" BYTEA NOT NULL,
  "status" PRISM_DID_OPERATION_STATUS NOT NULL,
  "operation_id" BYTEA NOT NULL,
  "created_at" TIMESTAMPTZ NOT NULL,
  "updated_at" TIMESTAMPTZ NOT NULL,
  PRIMARY KEY ("operation_id"),
  CONSTRAINT fk_did FOREIGN KEY(
    "did"
  ) REFERENCES public.prism_did_wallet_state("did") ON DELETE RESTRICT
);

-- move did:prism keys to a new table instead of sharing with did:peer
INSERT INTO public.prism_did_secret_storage(
  "did",
  "created_at",
  "key_id",
  "key_pair",
  "operation_hash"
)
SELECT
  public.did_secret_storage."did",
  to_timestamp(public.did_secret_storage."created_at"),
  public.did_secret_storage."key_id",
  public.did_secret_storage."key_pair",
  sha256(public.prism_did_wallet_state."atala_operation_content")
FROM public.did_secret_storage
LEFT JOIN
  public.prism_did_wallet_state ON
    public.did_secret_storage."did" = public.prism_did_wallet_state."did"
WHERE public.did_secret_storage."did" LIKE 'did:prism:%';

DELETE FROM public.did_secret_storage
WHERE "did" LIKE 'did:prism:%';
