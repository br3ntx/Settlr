# Settlr

A roommate/dormmate chore divider and expense tracker.
Built with Java Swing + MySQL (JDBC) for our OOP finals.

## Setup

1. Install MySQL 8 and MySQL Workbench.
2. In Workbench, run `database/schema.sql`, then `database/seed.sql` for sample data.
3. Download MySQL Connector/J (Platform Independent .zip) and put the `.jar` in `lib/`.
4. Copy `resources/config/db.properties.example` to `resources/config/db.properties`
   and fill in your own MySQL username and password.
5. Open the `Settlr` folder in VS Code (with the Extension Pack for Java) and run `src/com/settlr/Main.java`.

## Demo for the defense

`database/demo.sql` is a second set of sample data made for presenting: every account starts
in a state that shows off a feature (a swap request waiting, a payment to confirm, XP to spend...).

1. Make sure MySQL is running and `resources/config/db.properties` is filled in.
2. Double-click `reset-demo.bat` and type `YES`. It erases `settlr_db` and loads `schema.sql` + `demo.sql`.
   (If it can't find Java, open `src/com/settlr/tools/ResetDemo.java` in VS Code and click **Run** instead.)
3. Follow `docs/demo/Settlr Demo Script.docx`. All demo accounts use the password `settlr123`.

Reset again before every practice run and right before the defense.

## Updating an existing database

If your `settlr_db` was made before a change to `schema.sql`, run the new files in
`database/migrations` once, in order, in Workbench. The latest is `005_shared_pets.sql`: it makes
the pet, the Shop items and the XP belong to the whole household (everyone sees the same pet)
and keeps what each household's members already had. Resetting with `reset-demo.bat` or loading
`sandbox.sql` also works, but erases everything.

## Sandbox for exploring

`database/sandbox.sql` is the same demo data, but every household also gets 10,000 XP and every
pet, hat and accessory, so you can try everything in the Shop and Edit pet. Pets, items and XP are
shared by the household, so every Casa Mabini account sees the same pet. Load it in Workbench
(File > Open SQL Script, then the lightning bolt). It erases `settlr_db` first, so run it again
any time to start over. Use `reset-demo.bat` instead before practising the defense script.

## Folders

| Folder | What goes there |
| --- | --- |
| `src/com/settlr/model` | Plain data classes and enums |
| `src/com/settlr/dao` | All SQL (one DAO per table group) |
| `src/com/settlr/service` | Rules: auth, tasks, expenses, catch-up |
| `src/com/settlr/split` | SplitStrategy and EqualSplit |
| `src/com/settlr/util` | DBConnection, Session, dates, money, hashing |
| `src/com/settlr/exception` | SettlrException and subclasses |
| `src/com/settlr/ui` | MainFrame, Navigator, BaseScreen, Sidebar |
| `src/com/settlr/ui/theme` | Theme colors, FontManager |
| `src/com/settlr/ui/components` | Reusable styled Swing components |
| `src/com/settlr/ui/screens` | One class per screen or dialog |
| `resources/fonts` | .ttf font files |
| `resources/images` | Logo, icons, illustrations |
| `resources/config` | db.properties (not committed) |
| `database` | schema.sql, seed.sql, erd.png |
| `uploads` | Receipt/proof images (not committed) |
| `lib` | mysql-connector-j .jar |
| `docs` | Data dictionary, design files |

The `.gitkeep` files only exist so Git keeps empty folders. Delete one once its folder has real files.
