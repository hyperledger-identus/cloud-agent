-- Extend prism DID key usage enum with VDR signing key (internal, secp256k1-only).
ALTER TYPE public.prism_did_key_usage ADD VALUE IF NOT EXISTS 'VDR_SIGNING';
