CREATE TABLE public.presentation_definition (
    id UUID PRIMARY KEY,
    input_descriptors json NOT NULL,
    name VARCHAR(300),
    purpose VARCHAR(100),
    "format" json,
    wallet_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE public.presentation_definition
    ENABLE ROW LEVEL SECURITY;

CREATE POLICY presentation_definition_wallet_isolation
    ON public.presentation_definition
    USING (wallet_id = current_setting('app.current_wallet_id')::UUID);
