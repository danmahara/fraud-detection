ALTER TABLE transactions ADD COLUMN channel VARCHAR(20);


ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_channel
        CHECK (channel IS NULL OR channel IN ('APP', 'ATM', 'POS', 'ONLINE'));
