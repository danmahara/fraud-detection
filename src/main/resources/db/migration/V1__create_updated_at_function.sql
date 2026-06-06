-- V1: Reusable trigger function.
-- Sets updated_at = now() automatically on every UPDATE.
-- Created first so later tables can attach a trigger that uses it.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
