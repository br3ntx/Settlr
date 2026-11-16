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
--   ella   She and Ana are even (both paid each other). Ana asks her to swap Mop Floors.
--   gio    Joined 3 days ago and owes Ana P500 (used to show removing a member).
--   fritz  Has no household yet (used to show creating one and the invite code).
--   (zoe is registered live during the demo and joins Fritz's household.)
--
-- The pet, its items and the XP belong to the household, so everyone in Casa Mabini sees the
-- same pet (Mochi) and the same XP. Its mood depends on everyone: it starts sad because Ana
-- has an overdue task; once she ticks it, it is still sad because Dani's Rent and WiFi are past due.
--
-- See docs/demo/Settlr Demo Script.docx for the click-by-click script.

USE settlr_db;

-- ---------------------------------------------------------------- accounts
INSERT INTO users (user_id, username, display_name, password_hash, created_at, pay_method, pay_number, pay_name) VALUES
  (1, 'ana',   'Ana',   'p1$RBhLUuJ-rbNLVvh9$AlksOC77YOHhhSetuzT0XGle3-knuLkGNyGVcp6dxMU', NOW() - INTERVAL 40 DAY, 'GCASH', '0917 555 0142', 'Ana M.'),
  (2, 'ben',   'Ben',   'p1$lfAMn6ZsVRe_wASJ$uFNIyQmUEnbc0A88q2Tln12dtpmCrTOv6eij6Cu6WPI', NOW() - INTERVAL 39 DAY, 'MAYA',  '0918 555 0199', 'Ben T.'),
  (3, 'carlo', 'Carlo', 'p1$30Teyl72ASMsYGE3$dZGHJ7f_dmgXvxaT8W97ZlDdYMEraI4KFMTp02oHOvE', NOW() - INTERVAL 39 DAY, NULL, NULL, NULL),
  (4, 'dani',  'Dani',  'p1$ISSrwBcc1DT6AQJC$SHmYyDe0czVmOd-5ZLoQvSE9tcoMMoizFCaTEH_Q0WI', NOW() - INTERVAL 38 DAY, 'BANK',  'BPI 1234 5678 90', 'Dani C.'),
  (5, 'ella',  'Ella',  'p1$85P8NlwDSDkTUQrG$9Rzc5sZmTMf-t5eNZvaxNAiyKpuxL0zMV3QLT8TY7z0', NOW() - INTERVAL 38 DAY, 'CASH',  NULL, NULL),
  (6, 'gio',   'Gio',   'p1$39atKdtOjg1Eao5i$9eEm1qH-DoPX-lq8J28vtTSqnGJ0lPqF_gA-RnCGx18', NOW() - INTERVAL 3 DAY,  NULL, NULL, NULL),
  (7, 'fritz', 'Fritz', 'p1$GnyTJacN592s5lso$4wyZ0B32qTeWW9w7M1ftlGdQgsKA2JKFq4tHZoNG0b4', NOW() - INTERVAL 1 DAY,  NULL, NULL, NULL);

INSERT INTO households (household_id, name, invite_code, created_by, created_at, active_pet) VALUES
  (1, 'Casa Mabini', 'MABN42', 1, NOW() - INTERVAL 40 DAY, 'PET_DOG');

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
-- Only Rent and WiFi (due on the 1st and 5th) and Cleaning supplies (already paid) are past
-- their due date, so the only past-due debt in the house is Dani's Rent and WiFi.
INSERT INTO expenses (expense_id, household_id, bill_id, title, category, amount, paid_by,
                      due_date, created_by, created_at) VALUES
  (1, 1, 1,    'Rent',             'RENT',        12000.00, 1, DATE_FORMAT(CURDATE(), '%Y-%m-01'), 1, TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '00:05:00')),
  (2, 1, 2,    'WiFi',             'WIFI',         1500.00, 1, DATE_FORMAT(CURDATE(), '%Y-%m-05'), 1, TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '00:05:00')),
  (3, 1, NULL, 'Groceries',        'GROCERIES',    2351.00, 3, CURDATE() + INTERVAL 3 DAY, 3, NOW() - INTERVAL 6 DAY),
  (4, 1, NULL, 'Water bill',       'WATER',        1000.00, 4, CURDATE() + INTERVAL 5 DAY, 4, NOW() - INTERVAL 8 DAY),
  (5, 1, NULL, 'Electricity',      'ELECTRICITY',  3200.00, 5, CURDATE() + INTERVAL 2 DAY, 5, NOW() - INTERVAL 4 DAY),
  (6, 1, NULL, 'LPG refill',       'OTHER',         900.00, 2, CURDATE() + INTERVAL 4 DAY, 2, NOW() - INTERVAL 1 DAY),
  (7, 1, NULL, 'Welcome merienda', 'GROCERIES',    1500.00, 1, CURDATE() + INTERVAL 6 DAY, 1, NOW() - INTERVAL 3 DAY),
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
-- 3. History: Ella settled up with Ana for Rent and WiFi in cash; Ana confirmed it.
INSERT INTO settlements (settlement_id, household_id, from_user, to_user, amount, note, created_at, status, responded_at) VALUES
  (1, 1, 2, 1, 2700.00, 'GCash ref 7781', TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '09:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00')),
  (2, 1, 3, 1, 2729.80, 'GCash ref 4821', NOW() - INTERVAL 1 HOUR, 'PENDING', NULL),
  (3, 1, 5, 1, 2700.00, 'Cash', TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '10:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '13:00:00'));

INSERT INTO payments (share_id, from_user, to_user, amount, paid_at, status, confirmed_at, note, settlement_id) VALUES
  ((SELECT share_id FROM expense_shares WHERE expense_id = 1 AND user_id = 2), 2, 1, 2400.00,
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '09:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00'), 'GCash ref 7781', 1),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 2 AND user_id = 2), 2, 1, 300.00,
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '09:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00'), 'GCash ref 7781', 1),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 1 AND user_id = 5), 5, 1, 2400.00,
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '10:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '13:00:00'), 'Cash', 3),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 2 AND user_id = 5), 5, 1, 300.00,
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '10:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '13:00:00'), 'Cash', 3),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 1 AND user_id = 3), 3, 1, 2400.00,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 2 AND user_id = 3), 3, 1, 300.00,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 7 AND user_id = 3), 3, 1, 500.00,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 3 AND user_id = 1), 1, 3, 470.20,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, 'GCash ref 4821', 2),
  -- Electricity: Ana paid Ella her share in cash, confirmed
  ((SELECT share_id FROM expense_shares WHERE expense_id = 5 AND user_id = 1), 1, 5, 640.00,
        NOW() - INTERVAL 3 DAY, 'CONFIRMED', NOW() - INTERVAL 3 DAY + INTERVAL 1 HOUR, 'Cash', NULL),
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
-- Casa Mabini's things (shared by every member): the Dog (named Mochi, wearing the Party hat)
-- and the Cat, plus a Party hat and Glasses. Mochi is the pet everyone sees on Home.
-- Fritz has no household, so no pet yet.
INSERT INTO owned_items (household_id, item_code, acquired_at, pet_name, hat, glasses) VALUES
  (1, 'PET_DOG',       NOW() - INTERVAL 40 DAY, 'Mochi', 'HAT_PARTY', NULL),
  (1, 'PET_CAT',       NOW() - INTERVAL 12 DAY, NULL, NULL, NULL),
  (1, 'HAT_PARTY',     NOW() - INTERVAL 20 DAY, NULL, NULL, NULL),
  (1, 'GLASSES_ROUND', NOW() - INTERVAL 15 DAY, NULL, NULL, NULL);

-- What the household bought, and a starting amount so it has 350 XP when the demo starts
-- (470 XP earned from everyone's finished tasks and on-time payments
--  + 190 - 60 - 100 - 150 = 350).
INSERT INTO xp_transactions (household_id, user_id, amount, reason, created_at) VALUES
  (1, 1,  190, 'Starting XP for the demo', NOW() - INTERVAL 40 DAY),
  (1, 1,  -60, 'Bought Party hat',         NOW() - INTERVAL 20 DAY),
  (1, 5, -100, 'Bought Glasses',           NOW() - INTERVAL 15 DAY),
  (1, 2, -150, 'Hatched a pet egg',        NOW() - INTERVAL 12 DAY);

-- ------------------------------------------------------------ activity feed
INSERT INTO activity_log (household_id, user_id, action, message, created_at) VALUES
  (1, 1, 'MEMBER_JOINED',     'Ana created Casa Mabini',              NOW() - INTERVAL 40 DAY),
  (1, 2, 'MEMBER_JOINED',     'Ben joined the household',             NOW() - INTERVAL 39 DAY),
  (1, 3, 'MEMBER_JOINED',     'Carlo joined the household',           NOW() - INTERVAL 39 DAY + INTERVAL 1 HOUR),
  (1, 4, 'MEMBER_JOINED',     'Dani joined the household',            NOW() - INTERVAL 38 DAY),
  (1, 5, 'MEMBER_JOINED',     'Ella joined the household',            NOW() - INTERVAL 38 DAY + INTERVAL 1 HOUR),
  (1, 3, 'TASK_MISSED',       'Carlo missed Clean Bathroom',          CURDATE() - INTERVAL 10 DAY + INTERVAL 10 HOUR),
  (1, 1, 'PAYMENT_CONFIRMED', 'Ana confirmed a settle up from Ben',   TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '12:00:00')),
  (1, 1, 'PAYMENT_CONFIRMED', 'Ana confirmed a settle up from Ella',  TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '13:00:00')),
  (1, 4, 'EXPENSE_ADDED',     'Dani added Water bill',                NOW() - INTERVAL 8 DAY),
  (1, 2, 'SWAP_ACCEPTED',     'Ben took Clean Bathroom from Carlo',   NOW() - INTERVAL 5 DAY + INTERVAL 2 HOUR),
  (1, 2, 'TASK_DONE',         'Ben completed Clean Bathroom',         CURDATE() - INTERVAL 4 DAY + INTERVAL 9 HOUR),
  (1, 5, 'EXPENSE_ADDED',     'Ella added Electricity',               NOW() - INTERVAL 4 DAY),
  (1, 6, 'MEMBER_JOINED',     'Gio joined the household',             NOW() - INTERVAL 3 DAY),
  (1, 1, 'EXPENSE_ADDED',     'Ana added Welcome merienda',           NOW() - INTERVAL 3 DAY),
  (1, 2, 'EXPENSE_ADDED',     'Ben added LPG refill',                 NOW() - INTERVAL 1 DAY);
