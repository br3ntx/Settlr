# Settlr

A roommate/dormmate chore divider and expense tracker with weekly leaderboards.
Built with Java Swing + MySQL (JDBC) for our OOP finals.

## Setup

1. Install MySQL 8 and MySQL Workbench.
2. In Workbench, run `database/schema.sql`, then `database/seed.sql` for sample data.
3. Download MySQL Connector/J (Platform Independent .zip) and put the `.jar` in `lib/`.
4. Copy `resources/config/db.properties.example` to `resources/config/db.properties`
   and fill in your own MySQL username and password.
5. Open the `Settlr` folder in VS Code (with the Extension Pack for Java) and run `src/com/settlr/Main.java`.

## Folders

| Folder | What goes there |
| --- | --- |
| `src/com/settlr/model` | Plain data classes and enums |
| `src/com/settlr/dao` | All SQL (one DAO per table group) |
| `src/com/settlr/service` | Rules: auth, tasks, expenses, catch-up |
| `src/com/settlr/split` | SplitStrategy and EqualSplit |
| `src/com/settlr/ranking` | Leaderboard categories |
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
