-- Run this ONCE on a database made before the Profile page existed.
-- (A fresh database made from schema.sql already has these columns.)
-- Adds the profile picture and "How to pay me" to users.
USE settlr_db;

ALTER TABLE users
    ADD COLUMN avatar_path VARCHAR(255) NULL,
    ADD COLUMN pay_method  ENUM('GCASH','MAYA','BANK','CASH') NULL,
    ADD COLUMN pay_number  VARCHAR(40) NULL,
    ADD COLUMN pay_name    VARCHAR(60) NULL;
