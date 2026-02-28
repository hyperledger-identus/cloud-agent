-- Last used DID Index per wallet (solving race condition)
CREATE TABLE public.last_did_index_per_wallet
(
    "wallet_id"       UUID REFERENCES public.wallet ("wallet_id") NOT NULL PRIMARY KEY,
    "last_used_index" INT                                         NOT NULL
);

ALTER TABLE public.last_did_index_per_wallet
    ENABLE ROW LEVEL SECURITY;

CREATE
POLICY last_did_index_per_wallet_wallet_isolation
ON public.last_did_index_per_wallet
USING (wallet_id = current_setting('app.current_wallet_id')::UUID);

INSERT INTO public.last_did_index_per_wallet(wallet_id, last_used_index)
SELECT wallet_id, MAX(did_index)
FROM public.prism_did_wallet_state
GROUP BY wallet_id;