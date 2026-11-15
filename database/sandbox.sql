-- Settlr SANDBOX data: for groupmates to explore the app and learn how it works.
--
-- It is the defense demo data (database/demo.sql) plus, for EVERY account:
--   * 10,000 extra XP to spend in the Shop
--   * every pet (Cat, Dog) and every hat and accessory (Party hat, Bow, Glasses, Fedora, Crown)
--
-- Every account uses the password:  settlr123
--   ana    Admin of Casa Mabini; something to try on every screen (swap requests, payments to confirm...)
--   ben, carlo, dani, ella, gio    housemates in Casa Mabini
--   fritz  has no household yet: try creating one, or join Casa Mabini with invite code MABN42
--
-- How to load it: MySQL Workbench > File > Open SQL Script > this file > click the lightning bolt.
-- WARNING: it ERASES everything in settlr_db first. Run it again any time to start over.
--
-- Use this to practise and explore. For the defense itself use reset-demo.bat instead: the demo
-- script expects the normal demo XP (e.g. the Crown showing "Need ... more XP").
--
-- This file is schema.sql + demo.sql + the extras at the bottom. If either of those changes,
-- rebuild this file the same way so it stays in step.

DROP DATABASE IF EXISTS settlr_db;

-- ================================================================ schema.sql
CREATE DATABASE IF NOT EXISTS settlr_db;
USE settlr_db;

CREATE TABLE users (
    user_id        INT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(30)  NOT NULL UNIQUE,
    display_name   VARCHAR(50)  NOT NULL,
    password_hash  CHAR(64)     NOT NULL,  -- SHA-256 as 64 hex characters
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    avatar_path    VARCHAR(255) NULL,      -- profile picture under uploads/avatars, NULL = initials
    pay_method     ENUM('GCASH','MAYA','BANK','CASH') NULL,  -- "How to pay me", NULL = not set
    pay_number     VARCHAR(40)  NULL,      -- GCash/Maya number or "BPI 1234 5678 90"
    pay_name       VARCHAR(60)  NULL,      -- account name
    active_pet     VARCHAR(30)  NULL       -- the pet shown on Home (an item_code in owned_items), NULL = first pet
);

CREATE TABLE households (
    household_id      INT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(60) NOT NULL,
    invite_code       CHAR(6)     NOT NULL UNIQUE,
    created_by        INT         NOT NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE household_members (
    household_id  INT NOT NULL,
    user_id       INT NOT NULL,
    role          ENUM('ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
    joined_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active     BOOLEAN  NOT NULL DEFAULT TRUE,
    PRIMARY KEY (household_id, user_id),
    FOREIGN KEY (household_id) REFERENCES households(household_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE tasks (
    task_id              INT AUTO_INCREMENT PRIMARY KEY,
    household_id         INT         NOT NULL,
    title                VARCHAR(80) NOT NULL,
    description          TEXT        NULL,
    points               TINYINT     NOT NULL CHECK (points BETWEEN 1 AND 5),
    task_type            ENUM('ONE_TIME','RECURRING') NOT NULL,
    recurrence           ENUM('DAILY','WEEKLY','MONTHLY') NULL,  -- NULL for one-time tasks
    first_due_at         DATETIME    NOT NULL,
    default_assignee_id  INT         NOT NULL,  -- first assignee (or the only one if not rotating)
    rotates              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by           INT         NOT NULL,
    is_archived          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (household_id) REFERENCES households(household_id),
    FOREIGN KEY (default_assignee_id) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE task_assignments (
    assignment_id  INT AUTO_INCREMENT PRIMARY KEY,
    task_id        INT      NOT NULL,
    assignee_id    INT      NOT NULL,
    due_at         DATETIME NOT NULL,
    completed_at   DATETIME NULL,
    status         ENUM('PENDING','DONE','DONE_LATE','MISSED') NOT NULL DEFAULT 'PENDING',
    UNIQUE (task_id, due_at),  -- catch-up can never create a duplicate
    FOREIGN KEY (task_id) REFERENCES tasks(task_id),
    FOREIGN KEY (assignee_id) REFERENCES users(user_id)
);

CREATE TABLE swap_requests (
    swap_id        INT AUTO_INCREMENT PRIMARY KEY,
    assignment_id  INT      NOT NULL,
    from_user      INT      NOT NULL,
    to_user        INT      NOT NULL,
    status         ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'PENDING',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at   DATETIME NULL,
    FOREIGN KEY (assignment_id) REFERENCES task_assignments(assignment_id),
    FOREIGN KEY (from_user) REFERENCES users(user_id),
    FOREIGN KEY (to_user) REFERENCES users(user_id)
);

CREATE TABLE recurring_bills (
    bill_id         INT AUTO_INCREMENT PRIMARY KEY,
    household_id    INT          NOT NULL,
    title           VARCHAR(80)  NOT NULL,
    category        ENUM('RENT','ELECTRICITY','WATER','WIFI','GROCERIES','OTHER') NOT NULL,
    amount          DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    paid_by         INT          NOT NULL,
    due_day         TINYINT      NOT NULL CHECK (due_day BETWEEN 1 AND 28),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_generated  DATE         NULL,  -- due date of the last expense created from this bill
    created_by      INT          NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (household_id) REFERENCES households(household_id),
    FOREIGN KEY (paid_by) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE recurring_bill_members (
    bill_id  INT NOT NULL,
    user_id  INT NOT NULL,
    PRIMARY KEY (bill_id, user_id),
    FOREIGN KEY (bill_id) REFERENCES recurring_bills(bill_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE expenses (
    expense_id    INT AUTO_INCREMENT PRIMARY KEY,
    household_id  INT          NOT NULL,
    bill_id       INT          NULL,  -- set when created from a recurring bill
    title         VARCHAR(80)  NOT NULL,
    category      ENUM('RENT','ELECTRICITY','WATER','WIFI','GROCERIES','OTHER') NOT NULL,
    amount        DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    paid_by       INT          NOT NULL,
    due_date      DATE         NOT NULL,
    created_by    INT          NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bill_id, due_date),  -- one expense per bill per month
    FOREIGN KEY (household_id) REFERENCES households(household_id),
    FOREIGN KEY (bill_id) REFERENCES recurring_bills(bill_id),
    FOREIGN KEY (paid_by) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE expense_shares (
    share_id     INT AUTO_INCREMENT PRIMARY KEY,
    expense_id   INT           NOT NULL,
    user_id      INT           NOT NULL,
    amount_owed  DECIMAL(10,2) NOT NULL,
    settled_at   DATETIME      NULL,  -- NULL = still unpaid
    UNIQUE (expense_id, user_id),
    FOREIGN KEY (expense_id) REFERENCES expenses(expense_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- One "settle up" between two people. It pays the NET amount and is linked to one
-- payment row per open share in BOTH directions, so confirming it settles them all.
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

CREATE TABLE payments (
    payment_id    INT AUTO_INCREMENT PRIMARY KEY,
    share_id      INT           NOT NULL,
    from_user     INT           NOT NULL,
    to_user       INT           NOT NULL,
    amount        DECIMAL(10,2) NOT NULL,
    paid_at       DATETIME      NOT NULL,
    status        ENUM('PENDING','CONFIRMED','REJECTED') NOT NULL DEFAULT 'PENDING',
    confirmed_at  DATETIME      NULL,
    note          VARCHAR(255)  NULL,
    settlement_id INT           NULL,  -- set when the payment is part of a settle up
    FOREIGN KEY (share_id) REFERENCES expense_shares(share_id) ON DELETE CASCADE,
    FOREIGN KEY (settlement_id) REFERENCES settlements(settlement_id),
    FOREIGN KEY (from_user) REFERENCES users(user_id),
    FOREIGN KEY (to_user) REFERENCES users(user_id)
);

CREATE TABLE activity_log (
    log_id        INT AUTO_INCREMENT PRIMARY KEY,
    household_id  INT NOT NULL,
    user_id       INT NOT NULL,
    action        ENUM('TASK_DONE','TASK_LATE','TASK_MISSED','EXPENSE_ADDED',
                       'PAYMENT_CONFIRMED','SWAP_ACCEPTED','MEMBER_JOINED') NOT NULL,
    message       VARCHAR(255) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX (household_id, created_at),
    FOREIGN KEY (household_id) REFERENCES households(household_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Shop: hats and accessories bought with XP, and pets from the pet egg.
-- What each item is (name, price, rarity) lives in Java (ShopCatalog); this only records who owns what.
CREATE TABLE owned_items (
    user_id      INT         NOT NULL,
    item_code    VARCHAR(30) NOT NULL,  -- e.g. 'HAT_CROWN' or 'PET_BUNNY'
    acquired_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Only used on pet rows: the pet's name and what it wears (item codes), NULL = default / nothing
    pet_name     VARCHAR(20) NULL,
    hat          VARCHAR(30) NULL,      -- anything on the head: a hat or the bow
    glasses      VARCHAR(30) NULL,
    neck         VARCHAR(30) NULL,      -- no longer used: the Neck items were taken out of the Shop
    PRIMARY KEY (user_id, item_code),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- XP spent (negative) and given back (positive). XP earned is worked out from finished
-- tasks and on-time payments, so balance = earned + SUM(amount).
CREATE TABLE xp_transactions (
    tx_id       INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    amount      INT          NOT NULL,
    reason      VARCHAR(80)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- ================================================================ demo.sql
-- Settlr DEFENSE DEMO data. Loaded by reset-demo.bat (or ResetDemo in VS Code),
-- right after schema.sql on an empty settlr_db. Every date is relative to the moment
-- you load it, so reset shortly before the demo (during the day, not near midnight).
--
-- Every account uses the password:  settlr123
--
--   ana    Admin of Casa Mabini. The main account: something to show on every screen.
--   ben    Asked Ana to take his Take Out Trash turn (pending swap). Ana owes him P300.
--   carlo  Sent Ana a settle up that is waiting (used to show "Not received").
--   dani   Owes Ana; settles up live in the second window. Asked Ana to take Buy Groceries.
--   ella   Owes Ana; Ana asks her to swap Mop Floors during the demo.
--   gio    Joined 3 days ago and owes Ana P500 (used to show removing a member).
--   fritz  Has no household yet (used to show creating one and the invite code).
--   (zoe is registered live during the demo and joins Fritz's household.)
--
-- See docs/demo/Settlr Demo Script.docx for the click-by-click script.

USE settlr_db;

-- ---------------------------------------------------------------- accounts
INSERT INTO users (user_id, username, display_name, password_hash, created_at, pay_method, pay_number, pay_name) VALUES
  (1, 'ana',   'Ana',   SHA2('settlr123', 256), NOW() - INTERVAL 40 DAY, 'GCASH', '0917 555 0142', 'Ana M.'),
  (2, 'ben',   'Ben',   SHA2('settlr123', 256), NOW() - INTERVAL 39 DAY, 'MAYA',  '0918 555 0199', 'Ben T.'),
  (3, 'carlo', 'Carlo', SHA2('settlr123', 256), NOW() - INTERVAL 39 DAY, NULL, NULL, NULL),
  (4, 'dani',  'Dani',  SHA2('settlr123', 256), NOW() - INTERVAL 38 DAY, 'BANK',  'BPI 1234 5678 90', 'Dani C.'),
  (5, 'ella',  'Ella',  SHA2('settlr123', 256), NOW() - INTERVAL 38 DAY, 'CASH',  NULL, NULL),
  (6, 'gio',   'Gio',   SHA2('settlr123', 256), NOW() - INTERVAL 3 DAY,  NULL, NULL, NULL),
  (7, 'fritz', 'Fritz', SHA2('settlr123', 256), NOW() - INTERVAL 1 DAY,  NULL, NULL, NULL);

INSERT INTO households (household_id, name, invite_code, created_by, created_at) VALUES
  (1, 'Casa Mabini', 'MABN42', 1, NOW() - INTERVAL 40 DAY);

-- Join order matters: rotating tasks go Ana > Ben > Carlo > Dani > Ella > Gio.
INSERT INTO household_members (household_id, user_id, role, joined_at) VALUES
  (1, 1, 'ADMIN',  NOW() - INTERVAL 40 DAY),
  (1, 2, 'MEMBER', NOW() - INTERVAL 39 DAY),
  (1, 3, 'MEMBER', NOW() - INTERVAL 39 DAY + INTERVAL 1 HOUR),
  (1, 4, 'MEMBER', NOW() - INTERVAL 38 DAY),
  (1, 5, 'MEMBER', NOW() - INTERVAL 38 DAY + INTERVAL 1 HOUR),
  (1, 6, 'MEMBER', NOW() - INTERVAL 3 DAY);

-- ------------------------------------------------------------------- tasks
-- Each repeating task's first_due_at lines up with its first turn below, and its
-- latest turn is in the future, so the startup catch-up adds nothing unexpected.
INSERT INTO tasks (task_id, household_id, title, description, points, task_type, recurrence,
                   first_due_at, default_assignee_id, rotates, created_by, created_at) VALUES
  (1, 1, 'Wash Dishes',      'After dinner, every night',      2, 'RECURRING', 'DAILY',
       CURDATE() - INTERVAL 9 DAY + INTERVAL 20 HOUR, 3, TRUE,  1, NOW() - INTERVAL 10 DAY),
  (2, 1, 'Take Out Trash',   'Bins go out before 7 PM',        1, 'RECURRING', 'WEEKLY',
       CURDATE() - INTERVAL 12 DAY + INTERVAL 19 HOUR, 4, TRUE,  2, NOW() - INTERVAL 13 DAY),
  (3, 1, 'Clean Bathroom',   'Sink, toilet and shower',        4, 'RECURRING', 'WEEKLY',
       CURDATE() - INTERVAL 11 DAY + INTERVAL 10 HOUR, 3, FALSE, 1, NOW() - INTERVAL 12 DAY),
  (4, 1, 'Mop Floors',       NULL,                             3, 'RECURRING', 'WEEKLY',
       CURDATE() - INTERVAL 10 DAY + INTERVAL 10 HOUR, 2, TRUE,  4, NOW() - INTERVAL 11 DAY),
  (5, 1, 'Water the Plants', 'Balcony and window plants',      1, 'ONE_TIME',  NULL,
       NOW() - INTERVAL 3 HOUR, 1, FALSE, 1, NOW() - INTERVAL 2 DAY),
  (6, 1, 'Buy Groceries',    'Rice, eggs, dish soap',          2, 'ONE_TIME',  NULL,
       CURDATE() + INTERVAL 1 DAY + INTERVAL 18 HOUR, 4, FALSE, 4, NOW() - INTERVAL 1 DAY),
  (7, 1, 'Fix Wifi Router',  'Call the provider if it fails',  5, 'ONE_TIME',  NULL,
       CURDATE() + INTERVAL 3 DAY + INTERVAL 15 HOUR, 2, FALSE, 1, NOW() - INTERVAL 2 DAY),
  (8, 1, 'Defrost the Fridge', NULL,                           3, 'ONE_TIME',  NULL,
       CURDATE() - INTERVAL 6 DAY + INTERVAL 12 HOUR, 3, FALSE, 1, NOW() - INTERVAL 8 DAY),
  (9, 1, 'Sweep the Balcony', NULL,                            1, 'ONE_TIME',  NULL,
       CURDATE() + INTERVAL 2 DAY + INTERVAL 17 HOUR, 6, FALSE, 1, NOW() - INTERVAL 2 DAY),
  (10, 1, 'Deep Clean the Kitchen', 'Oven, fridge shelves, cabinets', 5, 'ONE_TIME', NULL,
       CURDATE() - INTERVAL 16 DAY + INTERVAL 16 HOUR, 1, FALSE, 1, NOW() - INTERVAL 18 DAY),
  (11, 1, 'Organize the Pantry', NULL,                         4, 'ONE_TIME',  NULL,
       CURDATE() - INTERVAL 20 DAY + INTERVAL 16 HOUR, 1, FALSE, 1, NOW() - INTERVAL 22 DAY);

-- Wash Dishes: one turn a day. Today's is Ana's, tomorrow's is Ben's.
-- (Before Gio joined it rotated among five people.)
INSERT INTO task_assignments (task_id, assignee_id, due_at, completed_at, status) VALUES
  (1, 3, CURDATE() - INTERVAL 9 DAY + INTERVAL 20 HOUR, CURDATE() - INTERVAL 9 DAY + INTERVAL 19 HOUR, 'DONE'),
  (1, 4, CURDATE() - INTERVAL 8 DAY + INTERVAL 20 HOUR, CURDATE() - INTERVAL 8 DAY + INTERVAL 19 HOUR, 'DONE'),
  (1, 5, CURDATE() - INTERVAL 7 DAY + INTERVAL 20 HOUR, NULL, 'MISSED'),
  (1, 1, CURDATE() - INTERVAL 6 DAY + INTERVAL 20 HOUR, CURDATE() - INTERVAL 6 DAY + INTERVAL 18 HOUR, 'DONE'),
  (1, 2, CURDATE() - INTERVAL 5 DAY + INTERVAL 20 HOUR, CURDATE() - INTERVAL 5 DAY + INTERVAL 23 HOUR, 'DONE_LATE'),
  (1, 3, CURDATE() - INTERVAL 4 DAY + INTERVAL 20 HOUR, CURDATE() - INTERVAL 4 DAY + INTERVAL 20 HOUR - INTERVAL 30 MINUTE, 'DONE'),
  (1, 4, CURDATE() - INTERVAL 3 DAY + INTERVAL 20 HOUR, NULL, 'MISSED'),
  (1, 5, CURDATE() - INTERVAL 2 DAY + INTERVAL 20 HOUR, CURDATE() - INTERVAL 2 DAY + INTERVAL 19 HOUR, 'DONE'),
  (1, 6, CURDATE() - INTERVAL 1 DAY + INTERVAL 20 HOUR, CURDATE() - INTERVAL 1 DAY + INTERVAL 19 HOUR, 'DONE'),
  (1, 1, CURDATE() + INTERVAL 20 HOUR,                  NULL, 'PENDING'),
  (1, 2, CURDATE() + INTERVAL 1 DAY + INTERVAL 20 HOUR, NULL, 'PENDING');

INSERT INTO task_assignments (task_id, assignee_id, due_at, completed_at, status) VALUES
  -- Take Out Trash (weekly, rotating): Ben's next turn is the one he asked Ana to take
  (2, 4, CURDATE() - INTERVAL 12 DAY + INTERVAL 19 HOUR, CURDATE() - INTERVAL 12 DAY + INTERVAL 18 HOUR, 'DONE'),
  (2, 5, CURDATE() - INTERVAL 5 DAY  + INTERVAL 19 HOUR, CURDATE() - INTERVAL 5 DAY  + INTERVAL 21 HOUR, 'DONE_LATE'),
  (2, 2, CURDATE() + INTERVAL 2 DAY  + INTERVAL 19 HOUR, NULL, 'PENDING'),
  -- Clean Bathroom (weekly, always Carlo; Ben took one turn through a swap)
  (3, 3, CURDATE() - INTERVAL 11 DAY + INTERVAL 10 HOUR, NULL, 'MISSED'),
  (3, 2, CURDATE() - INTERVAL 4 DAY  + INTERVAL 10 HOUR, CURDATE() - INTERVAL 4 DAY + INTERVAL 9 HOUR, 'DONE'),
  (3, 3, CURDATE() + INTERVAL 3 DAY  + INTERVAL 10 HOUR, NULL, 'PENDING'),
  -- Mop Floors (weekly, rotating): Ana's next turn is the one she asks Ella to take
  (4, 2, CURDATE() - INTERVAL 10 DAY + INTERVAL 10 HOUR, CURDATE() - INTERVAL 10 DAY + INTERVAL 8 HOUR, 'DONE'),
  (4, 3, CURDATE() - INTERVAL 3 DAY  + INTERVAL 10 HOUR, CURDATE() - INTERVAL 3 DAY + INTERVAL 11 HOUR, 'DONE_LATE'),
  (4, 1, CURDATE() + INTERVAL 4 DAY  + INTERVAL 10 HOUR, NULL, 'PENDING'),
  -- One-time tasks
  (5, 1, NOW() - INTERVAL 3 HOUR, NULL, 'PENDING'),   -- overdue: shows in red on Ana's Home
  (6, 4, CURDATE() + INTERVAL 1 DAY + INTERVAL 18 HOUR, NULL, 'PENDING'),
  (7, 2, CURDATE() + INTERVAL 3 DAY + INTERVAL 15 HOUR, NULL, 'PENDING'),
  (8, 3, CURDATE() - INTERVAL 6 DAY + INTERVAL 12 HOUR, CURDATE() - INTERVAL 6 DAY + INTERVAL 11 HOUR, 'DONE'),
  (9, 6, CURDATE() + INTERVAL 2 DAY + INTERVAL 17 HOUR, NULL, 'PENDING'),
  (10, 1, CURDATE() - INTERVAL 16 DAY + INTERVAL 16 HOUR, CURDATE() - INTERVAL 16 DAY + INTERVAL 14 HOUR, 'DONE'),
  (11, 1, CURDATE() - INTERVAL 20 DAY + INTERVAL 16 HOUR, CURDATE() - INTERVAL 20 DAY + INTERVAL 18 HOUR, 'DONE_LATE');

-- ------------------------------------------------------------------ swaps
INSERT INTO swap_requests (assignment_id, from_user, to_user, status, created_at, responded_at) VALUES
  -- Waiting for Ana: Ben asks her to take his Take Out Trash turn (accept it on Home)
  ((SELECT assignment_id FROM task_assignments WHERE task_id = 2 AND status = 'PENDING'), 2, 1, 'PENDING',
        NOW() - INTERVAL 2 HOUR, NULL),
  -- Waiting for Ana: Dani asks her to take Buy Groceries (decline it on Tasks)
  ((SELECT assignment_id FROM task_assignments WHERE task_id = 6), 4, 1, 'PENDING',
        NOW() - INTERVAL 1 HOUR, NULL),
  -- History: Ben took one of Carlo's bathroom turns; Dani said no to Ben's mop turn
  ((SELECT assignment_id FROM task_assignments WHERE task_id = 3 AND status = 'DONE'), 3, 2, 'ACCEPTED',
        NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY + INTERVAL 2 HOUR),
  ((SELECT assignment_id FROM task_assignments WHERE task_id = 4 AND assignee_id = 2), 2, 4, 'DECLINED',
        NOW() - INTERVAL 11 DAY, NOW() - INTERVAL 11 DAY + INTERVAL 1 HOUR);

-- --------------------------------------------------------- monthly bills
-- Rent and WiFi already made this month's expense (below), so catch-up adds nothing.
-- Cable TV is paused and never made an expense, so an Admin can delete it.
INSERT INTO recurring_bills (bill_id, household_id, title, category, amount, paid_by, due_day,
                             is_active, last_generated, created_by, created_at) VALUES
  (1, 1, 'Rent',     'RENT', 12000.00, 1, 1, TRUE,  DATE_FORMAT(CURDATE(), '%Y-%m-01'), 1, NOW() - INTERVAL 40 DAY),
  (2, 1, 'WiFi',     'WIFI',  1500.00, 1, 5, TRUE,  DATE_FORMAT(CURDATE(), '%Y-%m-05'), 1, NOW() - INTERVAL 40 DAY),
  (3, 1, 'Cable TV', 'OTHER',  600.00, 2, 15, FALSE, NULL,                              2, NOW() - INTERVAL 6 DAY);

INSERT INTO recurring_bill_members (bill_id, user_id) VALUES
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
  (2, 1), (2, 2), (2, 3), (2, 4), (2, 5),
  (3, 1), (3, 2), (3, 3), (3, 4), (3, 5);

-- ---------------------------------------------------------------- expenses
INSERT INTO expenses (expense_id, household_id, bill_id, title, category, amount, paid_by,
                      due_date, created_by, created_at) VALUES
  (1, 1, 1,    'Rent',             'RENT',        12000.00, 1, DATE_FORMAT(CURDATE(), '%Y-%m-01'), 1, TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '00:05:00')),
  (2, 1, 2,    'WiFi',             'WIFI',         1500.00, 1, DATE_FORMAT(CURDATE(), '%Y-%m-05'), 1, TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '00:05:00')),
  (3, 1, NULL, 'Groceries',        'GROCERIES',    2351.00, 3, CURDATE() - INTERVAL 6 DAY, 3, NOW() - INTERVAL 6 DAY),
  (4, 1, NULL, 'Water bill',       'WATER',        1000.00, 4, CURDATE() - INTERVAL 8 DAY, 4, NOW() - INTERVAL 8 DAY),
  (5, 1, NULL, 'Electricity',      'ELECTRICITY',  3200.00, 5, CURDATE() - INTERVAL 2 DAY, 5, NOW() - INTERVAL 4 DAY),
  (6, 1, NULL, 'LPG refill',       'OTHER',         900.00, 2, CURDATE() + INTERVAL 4 DAY, 2, NOW() - INTERVAL 1 DAY),
  (7, 1, NULL, 'Welcome merienda', 'GROCERIES',    1500.00, 1, CURDATE() - INTERVAL 2 DAY, 1, NOW() - INTERVAL 3 DAY),
  (8, 1, NULL, 'Cleaning supplies','OTHER',         450.00, 5, CURDATE() - INTERVAL 9 DAY, 5, NOW() - INTERVAL 10 DAY);

-- Equal splits to the centavo; the payer's own share is settled right away.
-- Water is split three ways: 333.33 + 333.33, and the leftover centavo goes to Dani (the payer).
INSERT INTO expense_shares (expense_id, user_id, amount_owed, settled_at) VALUES
  (1, 1, 2400.00, (SELECT created_at FROM expenses WHERE expense_id = 1)),
  (1, 2, 2400.00, NULL), (1, 3, 2400.00, NULL), (1, 4, 2400.00, NULL), (1, 5, 2400.00, NULL),
  (2, 1,  300.00, (SELECT created_at FROM expenses WHERE expense_id = 2)),
  (2, 2,  300.00, NULL), (2, 3,  300.00, NULL), (2, 4,  300.00, NULL), (2, 5,  300.00, NULL),
  (3, 1,  470.20, NULL), (3, 2,  470.20, NULL),
  (3, 3,  470.20, (SELECT created_at FROM expenses WHERE expense_id = 3)),
  (3, 4,  470.20, NULL), (3, 5,  470.20, NULL),
  (4, 1,  333.33, NULL), (4, 2,  333.33, NULL),
  (4, 4,  333.34, (SELECT created_at FROM expenses WHERE expense_id = 4)),
  (5, 1,  640.00, NULL), (5, 2,  640.00, NULL), (5, 3,  640.00, NULL), (5, 4,  640.00, NULL),
  (5, 5,  640.00, (SELECT created_at FROM expenses WHERE expense_id = 5)),
  (6, 1,  300.00, NULL), (6, 3,  300.00, NULL),
  (6, 2,  300.00, (SELECT created_at FROM expenses WHERE expense_id = 6)),
  (7, 1,  500.00, (SELECT created_at FROM expenses WHERE expense_id = 7)),
  (7, 3,  500.00, NULL), (7, 6,  500.00, NULL),
  (8, 2,  150.00, NULL), (8, 3,  150.00, NULL),
  (8, 5,  150.00, (SELECT created_at FROM expenses WHERE expense_id = 8));

-- ------------------------------------------------------------ settle ups
-- 1. History: Ben settled up with Ana for Rent and WiFi; Ana confirmed it.
-- 2. Waiting for Ana: Carlo settled up for Rent, WiFi and the merienda, minus the
--    Groceries share Ana owes him (2400 + 300 + 500 - 470.20 = 2,729.80).
INSERT INTO settlements (settlement_id, household_id, from_user, to_user, amount, note, created_at, status, responded_at) VALUES
  (1, 1, 2, 1, 2700.00, 'GCash ref 7781', TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '09:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00')),
  (2, 1, 3, 1, 2729.80, 'GCash ref 4821', NOW() - INTERVAL 1 HOUR, 'PENDING', NULL);

INSERT INTO payments (share_id, from_user, to_user, amount, paid_at, status, confirmed_at, note, settlement_id) VALUES
  ((SELECT share_id FROM expense_shares WHERE expense_id = 1 AND user_id = 2), 2, 1, 2400.00,
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '09:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00'), 'GCash ref 7781', 1),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 2 AND user_id = 2), 2, 1, 300.00,
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '09:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00'), 'GCash ref 7781', 1),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 1 AND user_id = 3), 3, 1, 2400.00,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 2 AND user_id = 3), 3, 1, 300.00,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 7 AND user_id = 3), 3, 1, 500.00,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 3 AND user_id = 1), 1, 3, 470.20,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  -- Groceries: Ben paid Carlo on his own, confirmed
  ((SELECT share_id FROM expense_shares WHERE expense_id = 3 AND user_id = 2), 2, 3, 470.20,
        NOW() - INTERVAL 5 DAY, 'CONFIRMED', NOW() - INTERVAL 5 DAY + INTERVAL 2 HOUR, NULL, NULL),
  -- Cleaning supplies: Ben and Carlo both paid Ella, so it shows under Settled
  ((SELECT share_id FROM expense_shares WHERE expense_id = 8 AND user_id = 2), 2, 5, 150.00,
        NOW() - INTERVAL 9 DAY, 'CONFIRMED', NOW() - INTERVAL 9 DAY + INTERVAL 1 HOUR, 'Cash', NULL),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 8 AND user_id = 3), 3, 5, 150.00,
        NOW() - INTERVAL 8 DAY, 'CONFIRMED', NOW() - INTERVAL 8 DAY + INTERVAL 1 HOUR, 'Cash', NULL);

-- A confirmed payment settles its share at the time it was paid.
UPDATE expense_shares es
JOIN payments p ON p.share_id = es.share_id AND p.status = 'CONFIRMED'
SET es.settled_at = p.paid_at;

-- -------------------------------------------------------------------- shop
-- Ana: the Dog (named Mochi, wearing the Party hat) and the Cat, plus a Party hat and Glasses.
-- Everyone else starts with the Dog. Fritz has no pet yet.
INSERT INTO owned_items (user_id, item_code, acquired_at, pet_name, hat, glasses) VALUES
  (1, 'PET_DOG',       NOW() - INTERVAL 40 DAY, 'Mochi', 'HAT_PARTY', NULL),
  (1, 'PET_CAT',       NOW() - INTERVAL 12 DAY, NULL, NULL, NULL),
  (1, 'HAT_PARTY',     NOW() - INTERVAL 20 DAY, NULL, NULL, NULL),
  (1, 'GLASSES_ROUND', NOW() - INTERVAL 15 DAY, NULL, NULL, NULL),
  (2, 'PET_DOG',       NOW() - INTERVAL 39 DAY, NULL, NULL, NULL),
  (3, 'PET_DOG',       NOW() - INTERVAL 39 DAY, NULL, NULL, NULL),
  (4, 'PET_DOG',       NOW() - INTERVAL 38 DAY, NULL, NULL, NULL),
  (5, 'PET_DOG',       NOW() - INTERVAL 38 DAY, NULL, NULL, NULL),
  (6, 'PET_DOG',       NOW() - INTERVAL 3 DAY,  NULL, NULL, NULL);
UPDATE users SET active_pet = 'PET_DOG' WHERE user_id BETWEEN 1 AND 6;

-- Ana's purchases, and a starting amount so she has 350 XP when the demo starts
-- (90 XP earned from her finished tasks + 570 - 60 - 100 - 150 = 350).
INSERT INTO xp_transactions (user_id, amount, reason, created_at) VALUES
  (1,  570, 'Starting XP for the demo', NOW() - INTERVAL 40 DAY),
  (1,  -60, 'Bought Party hat',         NOW() - INTERVAL 20 DAY),
  (1, -100, 'Bought Glasses',           NOW() - INTERVAL 15 DAY),
  (1, -150, 'Hatched a pet egg',        NOW() - INTERVAL 12 DAY);

-- ------------------------------------------------------------ activity feed
INSERT INTO activity_log (household_id, user_id, action, message, created_at) VALUES
  (1, 1, 'MEMBER_JOINED',     'Ana created Casa Mabini',              NOW() - INTERVAL 40 DAY),
  (1, 2, 'MEMBER_JOINED',     'Ben joined the household',             NOW() - INTERVAL 39 DAY),
  (1, 3, 'MEMBER_JOINED',     'Carlo joined the household',           NOW() - INTERVAL 39 DAY + INTERVAL 1 HOUR),
  (1, 4, 'MEMBER_JOINED',     'Dani joined the household',            NOW() - INTERVAL 38 DAY),
  (1, 5, 'MEMBER_JOINED',     'Ella joined the household',            NOW() - INTERVAL 38 DAY + INTERVAL 1 HOUR),
  (1, 3, 'TASK_MISSED',       'Carlo missed Clean Bathroom',          CURDATE() - INTERVAL 10 DAY + INTERVAL 10 HOUR),
  (1, 1, 'PAYMENT_CONFIRMED', 'Ana confirmed a settle up from Ben',   TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00')),
  (1, 4, 'EXPENSE_ADDED',     'Dani added Water bill',                NOW() - INTERVAL 8 DAY),
  (1, 2, 'SWAP_ACCEPTED',     'Ben took Clean Bathroom from Carlo',   NOW() - INTERVAL 5 DAY + INTERVAL 2 HOUR),
  (1, 2, 'TASK_DONE',         'Ben completed Clean Bathroom',         CURDATE() - INTERVAL 4 DAY + INTERVAL 9 HOUR),
  (1, 5, 'EXPENSE_ADDED',     'Ella added Electricity',               NOW() - INTERVAL 4 DAY),
  (1, 6, 'MEMBER_JOINED',     'Gio joined the household',             NOW() - INTERVAL 3 DAY),
  (1, 1, 'EXPENSE_ADDED',     'Ana added Welcome merienda',           NOW() - INTERVAL 3 DAY),
  (1, 2, 'EXPENSE_ADDED',     'Ben added LPG refill',                 NOW() - INTERVAL 1 DAY);

-- ================================================================ sandbox extras

-- Every pet and every hat/accessory for every account. INSERT IGNORE keeps what they already have
-- (Ana's dog Mochi keeps its name and party hat).
INSERT IGNORE INTO owned_items (user_id, item_code)
SELECT u.user_id, items.code
FROM users u
CROSS JOIN (
    SELECT 'PET_CAT' AS code UNION ALL SELECT 'PET_DOG'
    UNION ALL SELECT 'HAT_PARTY' UNION ALL SELECT 'HAT_BOW' UNION ALL SELECT 'GLASSES_ROUND'
    UNION ALL SELECT 'HAT_FEDORA' UNION ALL SELECT 'HAT_CROWN'
) AS items;

-- XP balance = XP earned from tasks and payments + SUM(xp_transactions), so this adds 10,000.
INSERT INTO xp_transactions (user_id, amount, reason)
SELECT user_id, 10000, 'Sandbox XP' FROM users;

-- Show a pet on Home for anyone who hasn't picked one.
UPDATE users SET active_pet = 'PET_DOG' WHERE active_pet IS NULL;
