CREATE TABLE public.issuer_metadata (
    id UUID PRIMARY KEY,
    authorization_server VARCHAR(500) NOT NULL,
    authorization_server_client_id VARCHAR(100) NOT NULL,
    authorization_server_client_secret VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    wallet_id UUID NOT NULL
);

CREATE TABLE public.issuer_credential_configuration (
    configuration_id VARCHAR(100) NOT NULL,
    issuer_id UUID NOT NULL,
    format VARCHAR(9) NOT NULL,
    schema_id VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (configuration_id, issuer_id),
    CONSTRAINT fk_issuer FOREIGN KEY (issuer_id) REFERENCES public.issuer_metadata(id) ON DELETE CASCADE
);

ALTER TABLE public.issuer_metadata
    ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.issuer_credential_configuration
    ENABLE ROW LEVEL SECURITY;

CREATE POLICY issuer_metadata_wallet_isolation
    ON public.issuer_metadata
    USING (wallet_id = current_setting('app.current_wallet_id')::UUID);

CREATE POLICY issuer_credential_configuration_wallet_isolation
    ON public.issuer_credential_configuration
    USING (
    EXISTS (SELECT 1
            FROM public.issuer_metadata AS im
            WHERE im.wallet_id = current_setting('app.current_wallet_id')::UUID
              AND im.id = public.issuer_credential_configuration.issuer_id)
    );
