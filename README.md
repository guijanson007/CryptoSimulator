# CryptoBros

A desktop cryptocurrency trading simulator built with Java Swing. Users can register accounts, buy and sell simulated cryptocurrencies, monitor a live price chart, and manage their portfolio. Admins can create and delete coins and rename existing ones. Market prices move automatically via per-coin bots that trade in the background.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Database Setup](#database-setup)
- [Configuration](#configuration)
- [Building and Running](#building-and-running)
- [Running Tests](#running-tests)
- [Benchmark](#benchmark)
- [Project Structure](#project-structure)
- [Architecture Overview](#architecture-overview)
- [Key Design Patterns](#key-design-patterns)

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java (JDK) | 25 |
| Apache Maven | 3.8+ |
| PostgreSQL | 13+ |

---

## Database Setup

1. Start your PostgreSQL server.
2. Create a database (the default configuration uses `postgres`):
   ```sql
   CREATE DATABASE postgres;
   ```
3. Run the schema script to create all tables, triggers, and functions:
   ```bash
   psql -U postgres -d postgres -f createTables.sql
   ```

The script creates the following tables:

| Table | Purpose |
|---|---|
| `Users` | Registered users and their balances |
| `Cryptocurrency` | Listed coins with current price, original price, and volatility |
| `Bots` | One bot user per coin that drives market simulation |
| `Portfolio` | Per-user coin holdings (units and buy price) |
| `Crypto_History` | Price snapshots used for the price chart |
| `Notifications` | Per-user notification messages |

Three PostgreSQL triggers maintain price history and simulate market impact:

- `update_price_history` / `insert_price_history` — record every price change into `Crypto_History`.
- `update_crypto_price_after_buy` — raise the coin price by 1 % on every buy.
- `update_crypto_price_after_sell` — lower the coin price by 1 % on every sell.

---

## Configuration

The application reads connection details from a JSON file at runtime. Edit `src/main/java/org/cryptoBros/config.json` before building:

```json
{
  "port": 5432,
  "ip": "localhost",
  "database": "postgres",
  "username": "postgres",
  "password": "your_db_password",
  "admin_password": "your_admin_password"
}
```

| Field | Description |
|---|---|
| `port` | PostgreSQL port (default `5432`) |
| `ip` | Database host (default `localhost`) |
| `database` | Database name |
| `username` | PostgreSQL user |
| `password` | PostgreSQL password |
| `admin_password` | Password used to log in as admin in the application |

Initial coin data is seeded from `src/main/java/org/cryptoBros/crypto.json`. The file is only read when the `Cryptocurrency` table is empty on first startup. Each entry specifies a `name`, `symbol`, `original_price`, `current_price`, and `volatility`.

---

## Building and Running

Run from the repository root: `config.json`, `crypto.json` and `images/` are read relative to it.
No system Maven is needed; the Maven wrapper (`./mvnw`) downloads the pinned version.

```bash
./mvnw compile exec:java -Dexec.mainClass="org.cryptoBros.Main"
```

`mvn package` builds a jar without its dependencies (PostgreSQL driver, Gson, …), so running it with
`java -cp target/CryptoBros-1.0-SNAPSHOT.jar …` alone fails with missing classes.

The application launches a Swing window. On startup, it loads `config.json`, verifies the database connection, and then shows the Welcome screen.

---

## Running Tests

| Command | Runs | Needs |
|---|---|---|
| `./mvnw test` | Unit tests (`*Test`): business rules, bots, credentials | JDK 25 |
| `./mvnw verify` | Unit tests + integration tests (`*IT`) against a real PostgreSQL, then a coverage report | JDK 25 and Docker or Podman |

Integration tests start a throwaway `postgres:18` container with [Testcontainers](https://testcontainers.com);
your own database and `config.json` are never used.

**Docker** works as is. **Rootless Podman** (e.g. Fedora) needs a one-time setup per machine:

```bash
systemctl --user enable --now podman.socket
```

and this line in `~/.testcontainers.properties` (create the file if it does not exist):

```properties
docker.host=unix:///run/user/<your uid>/podman/podman.sock
```

Run a single class with `./mvnw test -Dtest=BotTest` (unit) or `./mvnw verify -Dit.test=TradingIT` (integration;
the unit tests still run first).
The coverage report is written to `target/site/jacoco/index.html`; it is only generated when every test passes.

A failing test is never retried automatically. A test confirmed to be flaky (it fails, then passes on a re-run with
no code change) is quarantined with `@Disabled("FLAKY: <what was seen>, <date>")`, recorded in
`TESTING_DECISIONS.md`, and then fixed or deleted.

---

## Benchmark

`bench/chart-query.sh` measures the price-chart query (`CryptoSQL.getPriceHistory`) on `Crypto_History`
with and without an index on `(crypto_id, time_stamp)`, at 1 day, 1 week and 1 month of history
(181,440 rows per day: every default coin's bot trading at its real rate).

```bash
bench/chart-query.sh        # about 20-25 minutes (22 min measured, see bench/results/); needs Docker or Podman
```

Each run starts a fresh `postgres:18` container, loads `createTables.sql`, regenerates the history for each
configuration, warms up for 5 s, then measures for 30 s with `pgbench` (prepared statements, one client, inside
the container). The whole benchmark runs 3 times and reports min / median / p95 / max per run, the rows the query
returned, and the query plans. Results include the machine's CPU, RAM, OS, power state and versions.

Limits: warm cache only (cold-cache numbers need root to drop the OS page cache); client and server share a
machine; only the busiest coin (LTC) is queried. Run on AC power with nothing else heavy running, and compare
results only between runs on the same machine.

Test design decisions, the bugs the tests found, and what is deliberately untested are recorded in
[`TESTING_DECISIONS.md`](TESTING_DECISIONS.md).

---

## Project Structure

```
cryptoBros/
├── createTables.sql                  Database schema, triggers, and seed logic
├── pom.xml                           Maven build descriptor
├── images/                           UI screenshot assets
└── src/main/java/org/cryptoBros/
    ├── Main.java                     Entry point
    ├── config.json                   Database + admin credentials
    ├── crypto.json                   Initial coin definitions
    ├── business/                     Domain / business logic layer
    │   ├── AccountManager.java       Registration and login logic
    │   ├── CredentialManager.java    Password hashing and validation (BCrypt + Passay)
    │   ├── Crypto.java               Cryptocurrency domain model
    │   ├── CryptoManager.java        Coin lifecycle, trading, and bot coordination
    │   ├── User.java                 User domain model
    │   ├── UserManager.java          User CRUD operations
    │   ├── Liseners/                 Business-layer event interfaces
    │   │   ├── BalanceListener.java
    │   │   ├── BotListener.java
    │   │   ├── CryptoListener.java
    │   │   └── GraphPriceListener.java
    │   └── Workers/                  Background threads
    │       ├── Bot.java              Scheduled buy/sell agent per coin
    │       └── GraphPriceWorker.java Polling worker that feeds live price data to the chart
    ├── persistence/                  Data-access layer
    │   ├── AtomicPersistence.java    Interface for multi-table atomic operations
    │   ├── Config.java               Config domain model
    │   ├── ConfigJson.java           JSON config file reader
    │   ├── ConfigPersistence.java    Config reader interface
    │   ├── CryptoPersistence.java    Crypto DAO interface
    │   ├── DbCredentials.java        Record holding connection parameters
    │   ├── PortfolioPosition.java    Record for a single portfolio row
    │   ├── UserPersistence.java      User DAO interface
    │   ├── UserPortfolioPersistence.java  Portfolio DAO interface
    │   ├── Deserializers/
    │   │   └── ConfigDeserializer.java    Custom Gson deserializer for config
    │   ├── Exceptions/               Domain-specific checked exceptions
    │   └── SQL/                      JDBC implementations
    │       ├── AtomicSQL.java        Transactional multi-table operations
    │       ├── CryptoSQL.java        Coin queries
    │       ├── DbConnectionSingleton.java  Thread-safe JDBC connection factory
    │       ├── UserPortfolioSQL.java  Portfolio buy/sell queries
    │       └── UserSQL.java          User queries
    └── presentation/                 UI layer (Java Swing)
        ├── BotLogFormatter.java      Custom log formatter for bot output
        ├── JImagePanel.java          Swing panel that renders an image background
        ├── Controllers/              Event-handling and view coordination
        │   ├── AdminController.java
        │   ├── CryptoDetailController.java
        │   ├── CryptoMarketController.java
        │   ├── FrameController.java  Manages the single application JFrame
        │   ├── InitialController.java Startup, config load, and welcome screen
        │   ├── ManageCryptoController.java
        │   ├── NavigatorController.java  Central router between all pages
        │   ├── PortfolioController.java
        │   ├── ProfileController.java
        │   ├── RegistrationController.java
        │   ├── SettingController.java
        │   └── UserController.java
        ├── Enum/
        │   ├── ButtonEnumeration.java  Action-command constants for buttons
        │   └── PagesName.java          Named page identifiers for navigation
        ├── ListenersPersistence/
        │   ├── CryptoSelectedListener.java
        │   └── Navigation.java         Interface implemented by NavigatorController
        └── Views/                    Swing panels (one per screen)
            ├── AbstractTable.java    Base table with inline action buttons
            ├── BaseView.java         Common parent for all views
            ├── CryptoDetailView.java
            ├── CryptoMarketView.java
            ├── DisplayMessage.java   Static error/info dialog helper
            ├── DynamicTable.java
            ├── LoginView.java
            ├── MainFrame.java        Root JFrame
            ├── ManageCryptoTable.java
            ├── ManageCryptoView.java
            ├── Pages.java            Navigation bar panel
            ├── PortfolioTable.java
            ├── PortfolioView.java
            ├── PriceChart.java       Live line chart for coin price history
            ├── ProfilePicture.java
            ├── ProfileView.java
            ├── SettingsView.java
            ├── SignUpView.java
            └── WelcomeView.java
```

---

## Architecture Overview

The application follows a three-layer structure.

**Presentation layer** (`presentation/`) contains all Swing views and controllers. Views are passive panels; controllers hold references to business-layer managers and wire up action listeners. `NavigatorController` acts as the central router and swaps the active view inside `MainFrame` via `FrameController`.

**Business layer** (`business/`) contains domain models (`User`, `Crypto`) and manager classes that enforce business rules — password strength, balance checks, buy/sell price impact — before delegating to the persistence layer. `CryptoManager` implements `BotListener` so it receives buy/sell callbacks from bots without the bots knowing about the UI.

**Persistence layer** (`persistence/`) exposes DAO interfaces (`UserPersistence`, `CryptoPersistence`, `UserPortfolioPersistence`, `AtomicPersistence`) with concrete JDBC implementations under `SQL/`. `DbConnectionSingleton` provides a thread-safe connection factory loaded from `config.json`. `AtomicSQL` wraps multi-table operations (coin + bot creation, coin + bot deletion with user refunds) in a single transaction.

**Background workers** run on daemon threads so they do not prevent JVM shutdown:

- `Bot` — one instance per coin, scheduled at `5 / volatility` second intervals. Each tick it randomly buys or sells 1 unit, which fires the PostgreSQL price triggers and notifies the UI.
- `GraphPriceWorker` — polls price history for the currently viewed coin and pushes data to `PriceChart` on the Swing EDT.

---

## Key Design Patterns

| Pattern | Where used |
|---|---|
| Singleton | `DbConnectionSingleton` — single JDBC connection factory |
| Layered architecture | Presentation layer: Views, Controllers, and business-layer Models |
| Observer / Listener | `CryptoListener`, `BalanceListener`, `BotListener`, `GraphPriceListener` decouple the business layer from the UI |
| DAO | `*Persistence` interfaces with `*SQL` implementations separate SQL from business logic |
| Template Method | `AbstractTable` provides the shared table rendering; subclasses (`ManageCryptoTable`, `PortfolioTable`) supply column definitions and row actions |
