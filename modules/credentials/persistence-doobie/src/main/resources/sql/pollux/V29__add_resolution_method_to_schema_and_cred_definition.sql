-- Create the enum type
CREATE TYPE resolution_method_enum AS ENUM ('http', 'did');

-- Add the column to credential_definition table
ALTER TABLE public.credential_definition
    ADD COLUMN resolution_method resolution_method_enum NOT NULL DEFAULT 'http';

-- Add the column to credential_schema table
ALTER TABLE public.credential_schema
    ADD COLUMN resolution_method resolution_method_enum NOT NULL DEFAULT 'http';
