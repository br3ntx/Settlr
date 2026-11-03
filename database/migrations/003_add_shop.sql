-- Run this ONCE on a database made before the Shop existed.
-- (A fresh database made from schema.sql already has these tables.)
-- Adds what people own from the Shop and the XP they spend there.
USE settlr_db;

CREATE TABLE owned_items (
    user_id      INT         NOT NULL,
    item_code    VARCHAR(30) NOT NULL,
    acquired_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, item_code),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE xp_transactions (
    tx_id       INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    amount      INT          NOT NULL,
    reason      VARCHAR(80)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
