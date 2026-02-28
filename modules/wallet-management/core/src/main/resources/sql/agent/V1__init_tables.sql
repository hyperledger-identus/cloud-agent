CREATE TABLE public.did_secret_storage(
  "did" TEXT NOT NULL,
  "created_at" BIGINT NOT NULL,
  "updated_at" BIGINT,
  "key_id" TEXT NOT NULL,
  "key_pair" TEXT NOT NULL,
  PRIMARY KEY("did", "key_id")
);