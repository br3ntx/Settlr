-- Run this ONCE on a database made before the Home page's pet existed (after 003_add_shop.sql).
-- (A fresh database made from schema.sql already has these columns.)
-- Lets each pet have a name and an outfit, and remembers which pet you show on Home.
USE settlr_db;

ALTER TABLE users
    ADD COLUMN active_pet VARCHAR(30) NULL;

ALTER TABLE owned_items
    ADD COLUMN pet_name VARCHAR(20) NULL,
    ADD COLUMN hat      VARCHAR(30) NULL,
    ADD COLUMN glasses  VARCHAR(30) NULL,
    ADD COLUMN neck     VARCHAR(30) NULL;
