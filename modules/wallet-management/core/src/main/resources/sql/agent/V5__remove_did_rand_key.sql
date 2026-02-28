DROP TABLE public.prism_did_secret_storage;

DELETE FROM public.prism_did_update_lineage
WHERE did in (
  SELECT did
  FROM  public.prism_did_wallet_state
  WHERE key_mode = 'RANDOM'
);

DELETE FROM public.prism_did_wallet_state
WHERE key_mode = 'RANDOM';

ALTER TABLE public.prism_did_wallet_state
  ALTER COLUMN "did_index" SET NOT NULL;
