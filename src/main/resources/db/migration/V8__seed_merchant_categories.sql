-- V8: seed merchant_categories
-- Demo reference data so the context-aware layer works out of the box.
-- Risk scores follow the spec: LOW merchants pull the score down,
-- MEDIUM are neutral, HIGH push it up.

INSERT INTO merchant_categories (merchant_name, category, risk_level, risk_score) VALUES
    -- LOW risk
    ('City General Hospital',     'hospital',   'LOW',    0.100),
    ('Apollo Pharmacy',           'pharmacy',   'LOW',    0.100),
    ('National Electricity Board','utility',    'LOW',    0.100),
    ('Govt Tax Portal',           'government', 'LOW',    0.100),
    ('State University',          'education',  'LOW',    0.120),
    ('LifeSecure Insurance',      'insurance',  'LOW',    0.120),
    ('GreenView Apartments Rent', 'rent',       'LOW',    0.150),
    -- MEDIUM risk
    ('FreshMart Grocery',         'grocery',    'MEDIUM', 0.450),
    ('Spice Garden Restaurant',   'restaurant', 'MEDIUM', 0.450),
    ('Highway Fuel Station',      'fuel',       'MEDIUM', 0.450),
    ('TrendKart Retail',          'retail',     'MEDIUM', 0.500),
    ('TechWorld Electronics',     'electronics','MEDIUM', 0.550),
    -- HIGH risk
    ('LuckySpin Online Casino',   'gambling',   'HIGH',   0.880),
    ('CoinExchange Crypto',       'crypto',     'HIGH',   0.850),
    ('GlobalWire Transfer',       'wire_transfer','HIGH', 0.900),
    ('Unknown Intl Merchant',     'international','HIGH',  0.820);
