-- Minimal schema proving SQLite persistence works.
-- Domain-specific tables will be added as modules are migrated.

CREATE TABLE IF NOT EXISTS persistence_metadata (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO persistence_metadata (key, value) VALUES ('provider', 'sqlite');
INSERT INTO persistence_metadata (key, value) VALUES ('schema_version', '1');
