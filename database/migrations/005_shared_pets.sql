-- Run this ONCE on a database made before pets were shared (after 004_add_pet_outfits.sql).
-- (A fresh database made from schema.sql, demo.sql or sandbox.sql already has this.)
-- Makes the pet, the hats and the XP belong to the whole household instead of one person:
--   * owned_items is keyed by household_id instead of user_id
--   * the shown pet moves from users.active_pet to households.active_pet
--   * xp_transactions records the household (XP is pooled) as well as who spent it
-- Each household keeps everything its current members owned. When two members had the same
-- pet, the Admin's copy (name and outfit) is kept.
USE settlr_db;
SET SQL_SAFE_UPDATES = 0;  -- MySQL Workbench blocks UPDATEs without a key in WHERE otherwise

-- 1. The shown pet now belongs to the household: take the Admin's choice.
ALTER TABLE households ADD COLUMN active_pet VARCHAR(30) NULL;
UPDATE households h
SET h.active_pet = (SELECT u.active_pet
                    FROM household_members m JOIN users u ON u.user_id = m.user_id
                    WHERE m.household_id = h.household_id AND m.is_active = TRUE AND u.active_pet IS NOT NULL
                    ORDER BY m.role = 'ADMIN' DESC, m.joined_at
                    LIMIT 1);

-- 2. Items: one list per household, made from the current members' items.
CREATE TABLE household_items (
    household_id INT         NOT NULL,
    item_code    VARCHAR(30) NOT NULL,
    acquired_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pet_name     VARCHAR(20) NULL,
    hat          VARCHAR(30) NULL,
    glasses      VARCHAR(30) NULL,
    neck         VARCHAR(30) NULL,
    PRIMARY KEY (household_id, item_code),
    FOREIGN KEY (household_id) REFERENCES households(household_id)
);
INSERT IGNORE INTO household_items (household_id, item_code, acquired_at, pet_name, hat, glasses, neck)
SELECT m.household_id, o.item_code, o.acquired_at, o.pet_name, o.hat, o.glasses, o.neck
FROM owned_items o
JOIN household_members m ON m.user_id = o.user_id AND m.is_active = TRUE
ORDER BY m.role = 'ADMIN' DESC, o.acquired_at;   -- INSERT IGNORE keeps the first copy: the Admin's
DROP TABLE owned_items;
RENAME TABLE household_items TO owned_items;

-- 3. XP: record which household each transaction belongs to. Spending by people who are no
--    longer in any household can't count towards a household, so it is removed.
ALTER TABLE xp_transactions ADD COLUMN household_id INT NULL AFTER tx_id;
UPDATE xp_transactions x
JOIN household_members m ON m.user_id = x.user_id AND m.is_active = TRUE
SET x.household_id = m.household_id;
DELETE FROM xp_transactions WHERE household_id IS NULL;
ALTER TABLE xp_transactions
    MODIFY household_id INT NOT NULL,
    ADD INDEX (household_id),
    ADD FOREIGN KEY (household_id) REFERENCES households(household_id);

-- 4. The old per-person column is no longer used.
ALTER TABLE users DROP COLUMN active_pet;
SET SQL_SAFE_UPDATES = 1;
