-- V10: cardholder attributes the ML model needs, added to user_profiles.
-- The model uses date of birth (-> age), gender, and city population, alongside
-- the home_lat/home_lon already on this table. These describe the cardholder
-- (which the bank already knows), so the transaction form never sends them.

ALTER TABLE user_profiles ADD COLUMN dob      DATE;
ALTER TABLE user_profiles ADD COLUMN gender   VARCHAR(1);
ALTER TABLE user_profiles ADD COLUMN city_pop INTEGER;

ALTER TABLE user_profiles
    ADD CONSTRAINT chk_user_profiles_gender
        CHECK (gender IS NULL OR gender IN ('M', 'F'));