CREATE TABLE public.connection_records(
  "id" VARCHAR(36) NOT NULL PRIMARY KEY,
  "created_at"  TIMESTAMP WITH TIME ZONE NOT NULL,
  "updated_at"  TIMESTAMP WITH TIME ZONE,
  "thid" VARCHAR(36),
  "label" VARCHAR(255),
  "role"  VARCHAR(50) NOT NULL,
  "protocol_state" VARCHAR(50) NOT NULL,
  "invitation" TEXT NOT NULL,
  "connection_request" TEXT,
  "connection_response" TEXT
);