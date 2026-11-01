-- Run ONCE on a settlr_db that was created before November 1, 2026.
-- Adds net "settle up" support. Existing data is kept; seed.sql still works afterwards.
-- (A brand-new database made from the current schema.sql already has this.)

USE settlr_db;

CREATE TABLE settlements (
    settlement_id  INT AUTO_INCREMENT PRIMARY KEY,
    household_id   INT           NOT NULL,
    from_user      INT           NOT NULL,  -- pays the net amount
    to_user        INT           NOT NULL,  -- receives it and confirms
    amount         DECIMAL(10,2) NOT NULL CHECK (amount >= 0),  -- 0 when the debts just cancel out
    note           VARCHAR(255)  NULL,      -- e.g. "GCash ref 1234"
    created_at     DATETIME      NOT NULL,
    status         ENUM('PENDING','CONFIRMED','REJECTED') NOT NULL DEFAULT 'PENDING',
    responded_at   DATETIME      NULL,
    FOREIGN KEY (household_id) REFERENCES households(household_id),
    FOREIGN KEY (from_user) REFERENCES users(user_id),
    FOREIGN KEY (to_user) REFERENCES users(user_id)
);

ALTER TABLE payments
    ADD COLUMN settlement_id INT NULL,
    ADD CONSTRAINT fk_payments_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(settlement_id);
