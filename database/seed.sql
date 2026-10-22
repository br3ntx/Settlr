-- Settlr demo data (M3). Run AFTER database/schema.sql on an EMPTY settlr_db.
--
-- 5 roommates in one household, about 2 weeks of chores, bills and payments.
-- All dates are relative to the day you run this script, so the demo is never
-- stale: some tasks are done, late, missed, overdue or upcoming.
--
-- Every account uses the password:  settlr123
-- (stored as SHA-256 hex, same as PasswordHasher; if login fails, check that
--  PasswordHasher produces lowercase hex, which is what MySQL's SHA2 returns.)
--
-- To reload from scratch, uncomment the reset block below first.

USE settlr_db;

-- SET FOREIGN_KEY_CHECKS = 0;
-- TRUNCATE activity_log; TRUNCATE payments; TRUNCATE expense_shares; TRUNCATE expenses;
-- TRUNCATE recurring_bill_members; TRUNCATE recurring_bills; TRUNCATE swap_requests;
-- TRUNCATE task_assignments; TRUNCATE tasks; TRUNCATE household_members;
-- TRUNCATE households; TRUNCATE users;
-- SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------- accounts
INSERT INTO users (user_id, username, display_name, password_hash, created_at) VALUES
  (1, 'ana',   'Ana',   SHA2('settlr123', 256), NOW() - INTERVAL 31 DAY),
  (2, 'ben',   'Ben',   SHA2('settlr123', 256), NOW() - INTERVAL 30 DAY),
  (3, 'carlo', 'Carlo', SHA2('settlr123', 256), NOW() - INTERVAL 30 DAY),
  (4, 'dani',  'Dani',  SHA2('settlr123', 256), NOW() - INTERVAL 29 DAY),
  (5, 'ella',  'Ella',  SHA2('settlr123', 256), NOW() - INTERVAL 29 DAY);

INSERT INTO households (household_id, name, invite_code, created_by, created_at) VALUES
  (1, 'Casa Mabini', 'MABN42', 1, NOW() - INTERVAL 31 DAY);

-- Join order matters: rotation follows joined_at (Ana, Ben, Carlo, Dani, Ella).
INSERT INTO household_members (household_id, user_id, role, joined_at) VALUES
  (1, 1, 'ADMIN',  NOW() - INTERVAL 31 DAY),
  (1, 2, 'MEMBER', NOW() - INTERVAL 30 DAY),
  (1, 3, 'MEMBER', NOW() - INTERVAL 30 DAY + INTERVAL 1 HOUR),
  (1, 4, 'MEMBER', NOW() - INTERVAL 29 DAY),
  (1, 5, 'MEMBER', NOW() - INTERVAL 29 DAY + INTERVAL 1 HOUR);

-- ------------------------------------------------------------------- tasks
-- first_due_at of each recurring task lines up with its first assignment below,
-- so CatchUpService steps forward on the same schedule.
INSERT INTO tasks (task_id, household_id, title, description, points, task_type, recurrence,
                   first_due_at, default_assignee_id, rotates, created_by, created_at) VALUES
  (1, 1, 'Wash Dishes',     'After dinner, every night',   2, 'RECURRING', 'DAILY',
       CURDATE() - INTERVAL 13 DAY + INTERVAL 20 HOUR, 1, TRUE,  1, NOW() - INTERVAL 14 DAY),
  (2, 1, 'Take Out Trash',  NULL,                          1, 'RECURRING', 'WEEKLY',
       CURDATE() - INTERVAL 12 DAY + INTERVAL 19 HOUR, 1, TRUE,  2, NOW() - INTERVAL 14 DAY),
  (3, 1, 'Clean Bathroom',  'Sink, toilet and shower',     4, 'RECURRING', 'WEEKLY',
       CURDATE() - INTERVAL 10 DAY + INTERVAL 10 HOUR, 3, FALSE, 1, NOW() - INTERVAL 14 DAY),
  (4, 1, 'Mop Floors',      NULL,                          3, 'RECURRING', 'WEEKLY',
       CURDATE() - INTERVAL 9 DAY + INTERVAL 10 HOUR,  4, TRUE,  4, NOW() - INTERVAL 14 DAY),
  (5, 1, 'Buy Groceries',   'Rice, eggs, dish soap',       2, 'ONE_TIME',  NULL,
       CURDATE() - INTERVAL 1 DAY + INTERVAL 17 HOUR,  2, FALSE, 2, NOW() - INTERVAL 3 DAY),
  (6, 1, 'Fix Wifi Router', 'Call the provider if it fails', 5, 'ONE_TIME', NULL,
       CURDATE() + INTERVAL 3 DAY + INTERVAL 15 HOUR,  2, FALSE, 1, NOW() - INTERVAL 2 DAY);

-- Wash Dishes: one assignment per day for 15 days (13 days ago .. tomorrow),
-- rotating Ana > Ben > Carlo > Dani > Ella. Past ones get a mix of outcomes;
-- anything due within the last 24 hours or later stays PENDING.
INSERT INTO task_assignments (task_id, assignee_id, due_at, completed_at, status)
WITH RECURSIVE d (n) AS (
    SELECT 0 UNION ALL SELECT n + 1 FROM d WHERE n < 14
), x AS (
    SELECT n, CURDATE() - INTERVAL (13 - n) DAY + INTERVAL 20 HOUR AS due FROM d
)
SELECT 1,
       ELT(MOD(n, 5) + 1, 1, 2, 3, 4, 5),
       due,
       CASE WHEN due >= NOW() - INTERVAL 1 DAY THEN NULL
            WHEN MOD(n, 7) = 3 THEN NULL
            WHEN MOD(n, 5) = 2 THEN due + INTERVAL 3 HOUR
            ELSE due - INTERVAL 2 HOUR END,
       CASE WHEN due >= NOW() - INTERVAL 1 DAY THEN 'PENDING'
            WHEN MOD(n, 7) = 3 THEN 'MISSED'
            WHEN MOD(n, 5) = 2 THEN 'DONE_LATE'
            ELSE 'DONE' END
FROM x;

INSERT INTO task_assignments (task_id, assignee_id, due_at, completed_at, status) VALUES
  -- Take Out Trash (weekly, rotating)
  (2, 1, CURDATE() - INTERVAL 12 DAY + INTERVAL 19 HOUR, CURDATE() - INTERVAL 12 DAY + INTERVAL 18 HOUR, 'DONE'),
  (2, 2, CURDATE() - INTERVAL 5 DAY  + INTERVAL 19 HOUR, CURDATE() - INTERVAL 5 DAY  + INTERVAL 22 HOUR, 'DONE_LATE'),
  (2, 3, CURDATE() + INTERVAL 2 DAY  + INTERVAL 19 HOUR, NULL, 'PENDING'),
  -- Clean Bathroom (weekly, always Carlo; Ben took last week's through a swap)
  (3, 3, CURDATE() - INTERVAL 10 DAY + INTERVAL 10 HOUR, NULL, 'MISSED'),
  (3, 2, CURDATE() - INTERVAL 3 DAY  + INTERVAL 10 HOUR, CURDATE() - INTERVAL 3 DAY + INTERVAL 9 HOUR, 'DONE'),
  (3, 3, CURDATE() + INTERVAL 4 DAY  + INTERVAL 10 HOUR, NULL, 'PENDING'),
  -- Mop Floors (weekly, rotating; first one finished more than 24 hours early)
  (4, 4, CURDATE() - INTERVAL 9 DAY  + INTERVAL 10 HOUR, CURDATE() - INTERVAL 9 DAY + INTERVAL 10 HOUR - INTERVAL 31 HOUR, 'DONE'),
  (4, 5, CURDATE() - INTERVAL 2 DAY  + INTERVAL 10 HOUR, CURDATE() - INTERVAL 2 DAY + INTERVAL 8 HOUR, 'DONE'),
  (4, 1, CURDATE() + INTERVAL 5 DAY  + INTERVAL 10 HOUR, NULL, 'PENDING'),
  -- One-time tasks
  (5, 2, CURDATE() - INTERVAL 1 DAY  + INTERVAL 17 HOUR, CURDATE() - INTERVAL 1 DAY + INTERVAL 19 HOUR + INTERVAL 30 MINUTE, 'DONE_LATE'),
  (6, 2, CURDATE() + INTERVAL 3 DAY  + INTERVAL 15 HOUR, NULL, 'PENDING');

-- ------------------------------------------------------------------ swaps
INSERT INTO swap_requests (assignment_id, from_user, to_user, status, created_at, responded_at) VALUES
  -- Pending: Ben asks Carlo to take the router fix
  ((SELECT assignment_id FROM task_assignments WHERE task_id = 6), 2, 3, 'PENDING',
        NOW() - INTERVAL 5 HOUR, NULL),
  -- Accepted: Ben took Carlo's bathroom turn
  ((SELECT assignment_id FROM task_assignments WHERE task_id = 3 AND due_at = CURDATE() - INTERVAL 3 DAY + INTERVAL 10 HOUR), 3, 2, 'ACCEPTED',
        NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY + INTERVAL 2 HOUR),
  -- Declined: Ella said no to Ana's mop turn
  ((SELECT assignment_id FROM task_assignments WHERE task_id = 4 AND due_at = CURDATE() + INTERVAL 5 DAY + INTERVAL 10 HOUR), 1, 5, 'DECLINED',
        NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 1 HOUR);

-- ------------------------------------------------- recurring bills (M4's)
-- last_generated and the matching expenses below use this month's due dates,
-- so startup catch-up finds them and does not create duplicates.
INSERT INTO recurring_bills (bill_id, household_id, title, category, amount, paid_by, due_day,
                             last_generated, created_by, created_at) VALUES
  (1, 1, 'Rent', 'RENT', 12000.00, 1, 1, DATE_FORMAT(CURDATE(), '%Y-%m-01'), 1, NOW() - INTERVAL 31 DAY),
  (2, 1, 'WiFi', 'WIFI',  1500.00, 2, 5, DATE_FORMAT(CURDATE(), '%Y-%m-05'), 2, NOW() - INTERVAL 31 DAY);

INSERT INTO recurring_bill_members (bill_id, user_id) VALUES
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
  (2, 1), (2, 2), (2, 3), (2, 4), (2, 5);

-- ---------------------------------------------------------------- expenses
INSERT INTO expenses (expense_id, household_id, bill_id, title, category, amount, paid_by,
                      due_date, created_by, created_at) VALUES
  (1, 1, 1,    'Rent',        'RENT',        12000.00, 1, DATE_FORMAT(CURDATE(), '%Y-%m-01'), 1, TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '00:05:00')),
  (2, 1, 2,    'WiFi',        'WIFI',         1500.00, 2, DATE_FORMAT(CURDATE(), '%Y-%m-05'), 2, TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '00:05:00')),
  (3, 1, NULL, 'Groceries',   'GROCERIES',    2351.00, 3, CURDATE() - INTERVAL 6 DAY, 3, NOW() - INTERVAL 6 DAY),
  (4, 1, NULL, 'Water bill',  'WATER',        1000.00, 4, CURDATE() - INTERVAL 8 DAY, 4, NOW() - INTERVAL 8 DAY),
  (5, 1, NULL, 'Electricity', 'ELECTRICITY',  3200.00, 5, CURDATE() - INTERVAL 3 DAY, 5, NOW() - INTERVAL 3 DAY);

-- Equal splits, rounded down to the centavo. Water is split three ways:
-- 333.33 + 333.33 and the leftover centavo goes to the payer (Dani), 333.34.
-- The payer's own share is settled right away.
INSERT INTO expense_shares (expense_id, user_id, amount_owed, settled_at) VALUES
  (1, 1, 2400.00, (SELECT created_at FROM expenses WHERE expense_id = 1)),
  (1, 2, 2400.00, NULL), (1, 3, 2400.00, NULL), (1, 4, 2400.00, NULL), (1, 5, 2400.00, NULL),
  (2, 1,  300.00, NULL),
  (2, 2,  300.00, (SELECT created_at FROM expenses WHERE expense_id = 2)),
  (2, 3,  300.00, NULL), (2, 4,  300.00, NULL), (2, 5,  300.00, NULL),
  (3, 1,  470.20, NULL), (3, 2,  470.20, NULL),
  (3, 3,  470.20, (SELECT created_at FROM expenses WHERE expense_id = 3)),
  (3, 4,  470.20, NULL), (3, 5,  470.20, NULL),
  (4, 1,  333.33, NULL), (4, 2,  333.33, NULL),
  (4, 4,  333.34, (SELECT created_at FROM expenses WHERE expense_id = 4)),
  (5, 1,  640.00, NULL), (5, 2,  640.00, NULL), (5, 3,  640.00, NULL), (5, 4,  640.00, NULL),
  (5, 5,  640.00, (SELECT created_at FROM expenses WHERE expense_id = 5));

-- ---------------------------------------------------------------- payments
-- Helper idea: each payment points at the debtor's share of that expense.
INSERT INTO payments (share_id, from_user, to_user, amount, paid_at, status, confirmed_at, note) VALUES
  -- Rent: Ben paid on time, confirmed
  ((SELECT share_id FROM expense_shares WHERE expense_id = 1 AND user_id = 2), 2, 1, 2400.00,
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '01:00:00'), 'CONFIRMED',
        TIMESTAMP(DATE_FORMAT(CURDATE(), '%Y-%m-01'), '03:00:00'), 'GCash'),
  -- Rent: Carlo says he paid, Ana has not confirmed yet
  ((SELECT share_id FROM expense_shares WHERE expense_id = 1 AND user_id = 3), 3, 1, 2400.00,
        NOW() - INTERVAL 1 HOUR, 'PENDING', NULL, NULL),
  -- WiFi: Ana paid Ben, still waiting for his confirmation
  ((SELECT share_id FROM expense_shares WHERE expense_id = 2 AND user_id = 1), 1, 2, 300.00,
        NOW() - INTERVAL 2 HOUR, 'PENDING', NULL, NULL),
  -- Groceries: Ben paid Carlo a day late, confirmed
  ((SELECT share_id FROM expense_shares WHERE expense_id = 3 AND user_id = 2), 2, 3, 470.20,
        NOW() - INTERVAL 5 DAY, 'CONFIRMED', NOW() - INTERVAL 5 DAY + INTERVAL 2 HOUR, NULL),
  -- Water: Ana paid Dani on time, confirmed
  ((SELECT share_id FROM expense_shares WHERE expense_id = 4 AND user_id = 1), 1, 4, 333.33,
        TIMESTAMP(CURDATE() - INTERVAL 8 DAY, '18:00:00'), 'CONFIRMED',
        TIMESTAMP(CURDATE() - INTERVAL 8 DAY, '20:00:00'), NULL),
  -- Electricity: Dani paid Ella on time, confirmed; Ana's attempt was rejected
  ((SELECT share_id FROM expense_shares WHERE expense_id = 5 AND user_id = 4), 4, 5, 640.00,
        TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '10:00:00'), 'CONFIRMED',
        TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '12:00:00'), NULL),
  ((SELECT share_id FROM expense_shares WHERE expense_id = 5 AND user_id = 1), 1, 5, 640.00,
        NOW() - INTERVAL 2 DAY, 'REJECTED', NOW() - INTERVAL 2 DAY + INTERVAL 1 HOUR, 'Not received yet');

-- A confirmed payment settles its share at the time it was paid (3.6).
UPDATE expense_shares es
JOIN payments p ON p.share_id = es.share_id AND p.status = 'CONFIRMED'
SET es.settled_at = p.paid_at;

-- ------------------------------------------------------------ activity feed
INSERT INTO activity_log (household_id, user_id, action, message, created_at) VALUES
  (1, 1, 'MEMBER_JOINED',     'Ana created Casa Mabini',                          NOW() - INTERVAL 31 DAY),
  (1, 2, 'MEMBER_JOINED',     'Ben joined the household',                         NOW() - INTERVAL 30 DAY),
  (1, 3, 'MEMBER_JOINED',     'Carlo joined the household',                       NOW() - INTERVAL 30 DAY + INTERVAL 1 HOUR),
  (1, 4, 'MEMBER_JOINED',     'Dani joined the household',                        NOW() - INTERVAL 29 DAY),
  (1, 5, 'MEMBER_JOINED',     'Ella joined the household',                        NOW() - INTERVAL 29 DAY + INTERVAL 1 HOUR),
  (1, 3, 'TASK_MISSED',       'Carlo missed Clean Bathroom',                      CURDATE() - INTERVAL 9 DAY + INTERVAL 10 HOUR),
  (1, 4, 'EXPENSE_ADDED',     'Dani added Water bill',                            NOW() - INTERVAL 8 DAY),
  (1, 2, 'TASK_LATE',         'Ben completed Take Out Trash (3 hours late)',      CURDATE() - INTERVAL 5 DAY + INTERVAL 22 HOUR),
  (1, 2, 'SWAP_ACCEPTED',     'Ben took Clean Bathroom from Carlo',               NOW() - INTERVAL 4 DAY + INTERVAL 2 HOUR),
  (1, 2, 'TASK_DONE',         'Ben completed Clean Bathroom',                     CURDATE() - INTERVAL 3 DAY + INTERVAL 9 HOUR),
  (1, 4, 'PAYMENT_CONFIRMED', 'Dani confirmed a payment from Ana',                NOW() - INTERVAL 8 DAY + INTERVAL 2 HOUR),
  (1, 2, 'TASK_LATE',         'Ben completed Buy Groceries (2 hours late)',       CURDATE() - INTERVAL 1 DAY + INTERVAL 19 HOUR + INTERVAL 30 MINUTE);

-- Quick checks (run by hand):
--   SELECT status, COUNT(*) FROM task_assignments GROUP BY status;
--   SELECT u.display_name, SUM(es.amount_owed) AS still_owes
--     FROM expense_shares es JOIN users u ON u.user_id = es.user_id
--     WHERE es.settled_at IS NULL GROUP BY u.display_name;
--   SELECT * FROM swap_requests;
