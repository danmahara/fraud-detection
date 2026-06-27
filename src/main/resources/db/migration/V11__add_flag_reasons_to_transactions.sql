-- V11: store the context-layer reasons that explain WHY a transaction was
-- flagged (e.g. "new device", "12410 km from home"). JSONB array of strings,
-- consistent with the other JSON arrays in the schema. Empty for clean txns.
ALTER TABLE transactions
    ADD COLUMN flag_reasons JSONB NOT NULL DEFAULT '[]'::jsonb;