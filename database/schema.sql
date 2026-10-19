CREATE DATABASE IF NOT EXISTS settlr_db;
USE settlr_db;

CREATE TABLE users (
    user_id        INT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(30)  NOT NULL UNIQUE,
    display_name   VARCHAR(50)  NOT NULL,
    password_hash  CHAR(64)     NOT NULL,  -- SHA-256 as 64 hex characters
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE households (
    household_id      INT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(60) NOT NULL,
    invite_code       CHAR(6)     NOT NULL UNIQUE,
    created_by        INT         NOT NULL,
    hide_shame_ranks  BOOLEAN     NOT NULL DEFAULT FALSE,
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
    FOREIGN KEY (share_id) REFERENCES expense_shares(share_id) ON DELETE CASCADE,
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
