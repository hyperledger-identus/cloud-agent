CREATE TABLE public.peer_did
(
    "did"        TEXT                                        NOT NULL PRIMARY KEY,
    "created_at" TIMESTAMP WITH TIME ZONE                    NOT NULL,
    "wallet_id"  UUID REFERENCES public.wallet ("wallet_id") NOT NULL
);

ALTER TABLE public.peer_did
    ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.peer_did_rand_key
    ADD CONSTRAINT fk_did FOREIGN KEY ("did") REFERENCES public.peer_did ("did") ON DELETE RESTRICT;

CREATE POLICY peer_did_wallet_isolation
    ON public.peer_did
    USING (wallet_id = current_setting('app.current_wallet_id')::UUID);

DROP POLICY peer_did_rand_key_wallet_isolation ON public.peer_did_rand_key;

ALTER TABLE public.peer_did_rand_key
    DROP COLUMN "wallet_id";

CREATE POLICY peer_did_rand_key_wallet_isolation
    ON public.peer_did_rand_key
    USING (
    EXISTS (SELECT 1
            FROM public.peer_did AS s
            WHERE s.wallet_id = current_setting('app.current_wallet_id')::UUID
              AND s.did = public.peer_did_rand_key.did)
    );
