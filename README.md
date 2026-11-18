# Settlr

A desktop app for roommates and dormmates to share **chores** and **expenses** fairly, with a
household **pet** that everyone keeps happy by staying on top of things.

Built with **Java Swing** and **MySQL (JDBC)** as our OOP finals project.

## Features

- **Accounts and households.** Register, log in, and create a household or join one with an
  invite code. Admins can rename it, make new invite codes, manage roles and remove members.
- **Chores that rotate.** One-time and repeating (daily, weekly, monthly) tasks, picked right in
  the New task form. A repeating task can stay with one person or take turns around the
  household, and anyone can ask a housemate to swap a turn.
- **Catch-up on startup.** Overdue turns become MISSED, expired swaps are declined, repeating
  tasks get their next turns, and this month's bills are added, even if the app was closed.
- **Shared expenses.** Add what someone paid and split it equally, down to the centavo (the payer
  gets the leftover centavos). Monthly bills like Rent and WiFi are added automatically.
- **Settling up.** See who owes whom after debts in both directions cancel out, pay in one go,
  and have the receiver confirm it. Nothing counts until they do.
- **XP, Shop and a shared pet.** Finishing tasks and paying on time earns the household XP.
  Spend it on hats and accessories, or hatch a pet egg. The pet is the same for everyone, and it
  turns sad while anyone has an overdue task or a past-due bill.
- **Profile.** Name, photo, password, and "How to pay me" details (GCash, bank, cash).

## Getting started

1. Install **MySQL 8** and **MySQL Workbench**, and **Java 17 or newer**.
2. Copy `resources/config/db.properties.example` to `resources/config/db.properties` and put in
   your own MySQL username and password. This file is never committed.
3. Create the database. Either:
   - double-click `reset-demo.bat` and type `YES` (loads `schema.sql` + `demo.sql`), or
   - in Workbench, run `database/schema.sql` and then `database/seed.sql` for simple sample data.
4. Open the `Settlr` folder in VS Code (with the Extension Pack for Java) and run
   `src/com/settlr/Main.java`.

The libraries (MySQL Connector/J, JUnit 4, Hamcrest) are already in `lib/`.

## Sample data

| File | What it gives you |
| --- | --- |
| `database/demo.sql` | The defense demo: every account starts in a state that shows a feature. Load it with `reset-demo.bat`. |
| `database/sandbox.sql` | The same demo data, plus 10,000 XP and every pet and item for every household. Good for exploring. |
| `database/seed.sql` | A small, plain household for basic testing. |

All three **erase `settlr_db` first**. Every demo account uses the password `settlr123`:

| Account | Role in the demo |
| --- | --- |
| `ana` | Admin of Casa Mabini; has something to show on every screen |
| `ben` | Has a swap request waiting for Ana; Ana owes him ₱300 |
| `carlo` | Sent Ana a settle up that is waiting for her answer |
| `dani` | Owes Ana and settles up live during the demo |
| `ella` | Even with Ana; Ana asks her to swap a turn |
| `gio` | Newest member; owes Ana ₱500 (used to show removing a member) |
| `fritz` | Has no household yet (used to show creating one) |

The click-by-click script is in `docs/demo/Settlr Demo Script.docx`.

## Updating an existing database

If your `settlr_db` was made before a change to `schema.sql`, run the new files in
`database/migrations` once, in order, in Workbench (`001` to `005`). They keep your data.
The latest, `005_shared_pets.sql`, makes pets, Shop items and XP belong to the whole household.

## How it's built

Every feature goes through the same four layers:

```
Screen (ui/screens)  ->  Service (service)  ->  DAO (dao, dao/mysql)  ->  MySQL
```

- **Screens** show data and pass clicks to a service. They never contain SQL.
- **Services** hold the rules: who is allowed to do what, what gets saved, and in what order.
  Anything that saves several rows together runs in one transaction.
- **DAOs** are the only classes that talk to MySQL. Every query uses a `PreparedStatement`
  with `?` placeholders.
- **Models** are plain objects that check their own values (e.g. a task's points must be 1-5).

Each service receives its DAOs and the `Session` through its constructor, so the tests can pass
in-memory versions instead of MySQL.

## Design patterns

Search the code for `DESIGN PATTERN` (Ctrl+Shift+F in VS Code) to jump to every place below.

| Pattern | Where | What it does |
| --- | --- | --- |
| Singleton | `Session`, `DBConnection`, `Navigator` | One logged-in session, one database setup, one window |
| Factory Method | `TaskCreator` and its two subclasses | Builds a `OneTimeTask` or `RecurringTask` from a database row |
| Abstract Factory | `DaoFactory`, `MySqlDaoFactory`, `InMemoryDaoFactory` | Hands out a whole set of DAOs: MySQL for the app, in-memory for the tests |
| Builder | `Expense.builder()` | Builds an expense step by step and checks it once in `build()` |
| Prototype | `OwnedPet.copy()` | Edit pet works on copies, so Cancel changes nothing |
| Adapter | `SettleUpAdapter`, `SinglePaymentAdapter` -> `PendingPayment` | Lets one row and one popup handle both kinds of payment |
| Decorator | `PlainPet` + `HatDecorator`, `GlassesDecorator` | Draws a pet, then layers each item on top |
| Facade | `HomeService` | One class behind everything the Home page shows |
| Strategy | `SplitStrategy` -> `EqualSplit` | The split rule is swappable; a new rule is a new class |
| Template Method | `AbstractDao` | The JDBC steps are written once; each DAO only fills in `mapRow` |

## Tests

103 JUnit 4 tests in `test/` cover money splitting (including 2,000 random splits that must add up
exactly), balances, tasks and rotation, swaps, Admin rules, the startup catch-up, XP and the Shop,
the shared pet and its mood, the New task form's repeat options, and passwords. They run **without
MySQL**: `InMemoryDaoFactory` keeps the data in Java lists, and `TestHousehold` sets up Ana, Ben
and Carlo.

Run them from the **Testing** panel in VS Code, or right-click the `test` folder and choose
**Run Tests**.

## Folders

| Folder | What's in it |
| --- | --- |
| `src/com/settlr/model` | Data classes and enums (`User`, `Task`, `Expense`, `OwnedPet`...) |
| `src/com/settlr/service` | The rules of every feature (`TaskService`, `SettlementService`...) |
| `src/com/settlr/dao` | DAO interfaces and `DaoFactory` (no SQL) |
| `src/com/settlr/dao/mysql` | The MySQL DAOs with all the SQL, and `AbstractDao` |
| `src/com/settlr/factory` | `TaskCreator` (Factory Method) |
| `src/com/settlr/split` | `SplitStrategy` and `EqualSplit` |
| `src/com/settlr/exception` | `SettlrException` and its subclasses |
| `src/com/settlr/util` | `Session`, `DBConnection`, `Transaction`, `PasswordHasher`, money and date helpers |
| `src/com/settlr/ui` | `MainFrame`, `Navigator`, `BaseScreen`, `Background`, sidebar and top bar |
| `src/com/settlr/ui/screens` | Every page, popup and list row |
| `src/com/settlr/ui/components` | Reusable styled Swing components |
| `src/com/settlr/ui/pet` | Pet drawing (Decorator) |
| `src/com/settlr/ui/theme` | Colors and fonts |
| `src/com/settlr/tools` | `ResetDemo`, used by `reset-demo.bat` |
| `test/com/settlr` | JUnit tests; `dao/memory` holds the in-memory DAOs |
| `database` | `schema.sql`, sample data, `migrations/`, and the ERD |
| `resources/config` | `db.properties` (yours, not committed) and its example |
| `resources/images` | Logo, pets and cosmetics |
| `lib` | MySQL Connector/J, JUnit, Hamcrest |
| `uploads` | Profile photos saved by the app (not committed) |
| `docs` | The demo script |

## Security notes

- Passwords are stored as salted **PBKDF2** hashes (120,000 rounds), never as typed. Accounts
  made with the older SHA-256 hashing still log in and are upgraded on that login.
- All SQL uses `PreparedStatement`, so user input can't change a query.
- Money uses `BigDecimal` in Java and `DECIMAL` in MySQL, so centavos are always exact.

## Known limits

- The New task form doesn't set points or a description yet: every task made there is worth
  1 point. The services already support both.
- There is no password recovery, since the app has no email server.
- Screens refresh when you open them, not live, so a second window needs a click to update.
- Saving still runs on the Swing thread; only loading runs in the background.
