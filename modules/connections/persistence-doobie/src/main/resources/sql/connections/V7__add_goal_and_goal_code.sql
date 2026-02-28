
-- Introduce a goal and goal code  for connection
ALTER TABLE public.connection_records
    ADD COLUMN "goal_code" TEXT,
    ADD COLUMN "goal" TEXT;
