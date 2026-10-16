# Testing Decisions

## D1 — Build toolchain: Maven wrapper (2026-10-16)

**Decision:** How to provide Maven, which is not installed and has no wrapper in the repo.

**Options considered:**
- A. Commit Maven wrapper (`mvnw`, `.mvn/`): pinned version for all contributors and CI; ~3 extra files.
- B. System-wide Maven only: nothing added to the repo; contributors and CI can drift on Maven version.
- C. Both: install system Maven once (Fedora `maven` 3.9.11 on OpenJDK 25) to generate the wrapper, then use `./mvnw`.

**Choice:** C. The wrapper is pinned to Maven 3.9.11.

**Objections:** None.

## D2 — Project purpose (2026-10-16)

**Decision:** What the project is for, which sets how much testing is worth.

**Options considered:**
- A. Team coursework (grader/rubric drives tools and coverage).
- B. Portfolio piece (tests must be defensible in an interview; meaningful > high %).
- C. Both: coursework now, portfolio later.
- D. Real users (balance/trade correctness is critical).

**Choice:** C. No rubric was provided; the coursework's testing requirements are unknown, not assumed absent.

**Objections:** None.

## D3 — Risk ranking (2026-10-16)

**Decision:** Which areas get testing effort first.

**Options considered:**
- A. Money correctness (buy/sell/balance/profit).
- B. Startup and login.
- C. Admin coin management and initial seeding.
- D. Live market (bots, chart, threading).

**Choice:** A > C > D > B.
Evidence from git history: money bugs fixed in `c36e28c`, `3129d76`; coin seeding regression fixed twice
(`247dfb6`, `06d8ccf`) plus `ae9c418`, `07ca152`; no D incidents found; B fix was message-only (`43287be`).

**Objections:** User initially ranked A > D > C > B; changed to A > C > D > B after the git-history
evidence and the cost of testing threads.

## D4 — Time budget (2026-10-16)

**Decision:** How much effort to put into building the test suite.

**Options considered:**
- A. ~1 day: pure-logic unit tests + a few DB integration tests on buy/sell/balance.
- B. 2–3 days: A + dependency-injection refactor of managers, AtomicSQL seeding/deletion tests, CI.
- C. A week+: B + bot concurrency tests, reproducible benchmark, coverage reporting.

**Choice:** B. Deadline/submission status unknown; the injection refactor risks merge conflicts
with teammates if the project is still under active development.

**Objections:** None.

## D5 — Test level for money correctness (2026-10-16)

**Decision:** Which test level guards risk A (buy/sell/balance/profit), whose logic is split across
Java, SQL triggers, and controller sequencing.

**Options considered:**
- A. Unit tests with mocked DAOs only: fast, but never executes triggers/upsert/rounding; would pass with the trigger SQL deleted.
- B. Integration tests against real PostgreSQL only: catches DB-side behavior; slower, needs a DB everywhere; Java-only branches awkward.
- C. Both, weighted to integration: real-DB tests for DB-side rules, unit tests (after DI refactor) for Java-only branches.

**Choice:** C.

**Objections:** None.

## D6 — Performance testing scope; revises D4 (2026-10-16)

**Decision:** Whether performance work is in scope, after the user asked for resume-style metrics.

**Options considered:**
- A. Out of scope (keep D4 as is).
- B. Index benchmark only (+½–1 day): fixed-size `Crypto_History`, chart query with vs. without a
  `(crypto_id, time_stamp)` index, repeated `EXPLAIN ANALYZE` runs, spread reported, environment recorded.
- C. B + event-pipeline load test (+2–3 days): likely requires a connection pool; feature work.

**Choice:** B. D4 budget becomes ~2.5–4 days.

**Notes:** The example bullets ("2.5M daily price events", "1.2 s → <50 ms on 500k ledger records")
are not supported by this codebase: current bot rate is ~181k trades/day across 5 coins, there is no
stream parser and no ledger table, and the existing PK already indexes `crypto_id`, so the real
baseline is expected to be far below 1.2 s. Resume numbers will come only from measured results.

**Objections:** None.

## D7 — Policy for real bugs exposed by tests (2026-10-16)

**Decision:** What happens when a test reveals a genuine defect.

**Options considered:**
- A. Fix on this branch (approved per fix, logged); merge-conflict risk with teammates.
- B. Report only: test written to correct behavior, kept but `@Disabled("BUG-n: ...")`.
- C. Case by case: stop at each bug, user picks A or B.

**Choice:** C. In every case the test asserts the correct behavior; a test written to expect
the buggy behavior is never acceptable.

**Objections:** None.

## D8 — UI / end-to-end testing (2026-10-16)

**Decision:** How much automated testing above the manager/DB level (controllers or Swing UI).

**Options considered:**
- A. None automated; optional manual pre-demo checklist.
- B. Headless controller tests: reaches bug #1's current location, but needs a second refactor (controllers construct their own views/managers).
- C. GUI automation (AssertJ Swing + Xvfb in CI): catches wiring breaks; slow and flaky.

**Choice:** A. Bug #1 is to be covered below the controller once/if its fix moves buy+payment into one transaction (see D7).
If bug #1 is report-only instead, its test must still reach the controller sequence; revisit then.

**Objections:** None.

## D9 — Bot testing (2026-10-16)

**Decision:** What to test in `Bot` (risk D).

**Options considered:**
- A. Nothing.
- B. Deterministic unit tests: call `run()` directly with a fake `BotListener`; no scheduler/sleeps;
  buy/sell dispatch, failed trade logged without killing the bot, idempotent `start`/`stop`,
  volatility edge cases (0, negative). Requires injecting `Random` via constructor.
- C. Concurrency tests with many bots against real DB: slow/flaky; PostgreSQL row locks already prevent lost price updates.

**Choice:** B.

**Objections:** None.

## D10 — Deliberately untested (2026-10-16)

**Decision:** What is left untested on purpose, so gaps are recorded choices.

**Untested:**
- Swing views, layout, resizing (D8).
- Controllers and navigation wiring (D8; would need a second refactor).
- `GraphPriceWorker` polling loop (sleep-based timing); its query is covered by integration tests and D6 benchmark.
- `UserManager` periodic balance scheduler (timing); `addBalance` itself is covered.
- `DbConnectionSingleton` double-checked locking (standard pattern; fails loudly at startup).
- `config.json` loading/parsing (risk B, ranked last; fails loudly at startup).
- Profile picture, username, and password changes (not in any ranked risk).

**Considered adding:** taken-username case in `UserManager.changeUsername` (one cheap integration test); declined.

**Objections:** None.

## D11 — Test database provider (2026-10-16)

**Decision:** Where integration tests get a real PostgreSQL (none installed; no container runtime).

**Options considered:**
- A. Testcontainers + rootless Podman: fresh pinned `postgres:18` per run; identical locally and in CI (GitHub runners have Docker); one-time Podman socket setup.
- B. Local dnf PostgreSQL 18.6: fastest; state leaks between runs; CI needs separate service config; per-machine setup.
- C. Embedded Postgres (zonky): no containers; less common, lagging PG versions, less support.
- Rejected: H2 PostgreSQL mode (no PL/pgSQL triggers).

**Choice:** A. Fallback to C if Podman socket setup exceeds ~1 hour.
Per-machine config: `~/.testcontainers.properties` with `docker.host` pointing at the rootless Podman socket; Ryuk left enabled unless it fails.

**Objections:** None.

## D12 — Test framework (2026-10-16)

**Decision:** Which test framework.

**Options considered:**
- A. JUnit 6.1.3 (Jupiter API via BOM): current line, Java 17+; API near-identical to JUnit 5.
- B. JUnit 5.x: what most coursework targets; previous line.
- C. TestNG: suite configuration not needed here; weaker Testcontainers integration.

**Choice:** A. Revisit (switch BOM version to 5.x) if the coursework brief names JUnit 5.

**Objections:** None.

## D13 — Separating unit and integration runs (2026-10-16)

**Decision:** How fast unit tests and container-backed integration tests are kept apart.

**Options considered:**
- A. Surefire (`*Test`, `test` phase) + Failsafe (`*IT`, `verify` phase): standard Maven layout; `./mvnw test` needs no Podman; post-integration cleanup runs before failure is reported.
- B. Surefire + JUnit `@Tag("integration")` + profile: one plugin; a forgotten tag misplaces a test.
- C. No separation: every run needs Podman and pays container startup.

**Choice:** A. Plugins pinned at 3.6.0.

**Objections:** None.

## D14 — Testcontainers modules and container lifecycle (2026-10-16)

**Decision:** How many PostgreSQL containers a test run starts.

**Options considered:**
- A. One shared container per run (static base class), `testcontainers-postgresql` 2.0.5 only; ~3 s per `verify`.
- B. Fresh container per class via `testcontainers-junit-jupiter`: ~10–20 s total; JUnit 6 compatibility unverified.
- C. Reuse mode across runs: fastest locally; state leaks between runs; not CI-compatible.

**Choice:** A. Per-test data cleanup is required (to be designed in Phase 4).

**Objections:** None.

## D15 — Assertion library (2026-10-16)

**Decision:** What tests use to check results.

**Options considered:**
- A. JUnit built-in assertions: no dependency; `assertEquals(expected, actual, 1e-8)` for money; sparser failure messages on collections.
- B. AssertJ 3.27.7: fluent, field-level failure diffs; one more dependency and API.
- C. Hamcrest: superseded; no advantage over B.

**Choice:** A. AssertJ can be added later without rewriting existing tests.

**Objections:** None.

## D16 — Test doubles for unit tests (2026-10-16)

**Decision:** What stands in for persistence classes in DB-free unit tests.

**Options considered:**
- A. Hand-written fakes: return fixed values for used methods, throw `UnsupportedOperationException` otherwise; no dependency; ~30–60 lines.
- B. Mockito 5.24.0: stubbing and `verify`; needs `-javaagent` Surefire config on JDK 21+; over-mocking risk.
- C. No manager unit tests (contradicts D5).

**Choice:** A. Requires constructor injection in `UserManager` (`UserPersistence`) and `CryptoManager`
(its persistence classes and `UserManager`). Note: `CryptoManager` currently builds its own separate
`UserManager` instance (`CryptoManager.java:44`).

**Objections:** None.

## D17 — Coverage tool (2026-10-16)

**Decision:** Whether and how to measure coverage.

**Options considered:**
- A. JaCoCo 0.8.15, merged unit + integration report, no threshold.
- B. JaCoCo with a failing threshold: a global threshold conflicts with the deliberately untested UI (~2/3 of the code); a package-scoped one needs more config.
- C. No coverage tool.

**Choice:** A. Phase 8 reports coverage per package (`business`, `persistence`) as well as overall;
overall is expected to be low because of D8/D10.

**Objections:** None.

## D18 — Static analysis (2026-10-16)

**Decision:** Whether to add a static analysis tool.

**Options considered:**
- A. None for now.
- B. SpotBugs, report-only, scoped to `business/` and `persistence/`; Java 25 support unverified; ~1–2 h triage.
- C. Error Prone: compiler plugin; needs `--add-exports` flags on JDK 25; fragile across JDK updates.
- Rejected: Checkstyle (style, not bugs).

**Choice:** A. Revisit in Phase 8. None of risks A–D are well suited to static analysis.

**Objections:** None.

## D19 — Test DB configuration hook (2026-10-16)

**Decision:** How integration tests point `DbConnectionSingleton` at the Testcontainers PostgreSQL (random port).

**Options considered:**
- A. `configure(jdbcUrl, user, password)` on `DbConnectionSingleton`, called by the IT base class; ~8 lines; production `loadConfig()` path unchanged.
- B. System-property override of the `config.json` path + generated temp config: exercises real config loading; more moving parts.
- C. Inject a connection provider into all `*SQL` classes: cleanest; largest change and merge-conflict risk.

**Choice:** A. Documented as a test seam; C remains a possible later refactor.

**Objections:** None.

## D20 — Test case list (2026-10-16)

**Decision:** Approve the Phase 4 test case list.

**Choice:** All 56 proposed cases approved as listed (risk order A, C, D, B). Open fixture/behavior
flags F1–F6 to be decided individually. New suspected bugs found during design: #6 (`sell` accepts
non-positive units; not UI-reachable), #7 (`addBalance` accepts negative amounts; UI-reachable).
If the integration work overruns, cut #55–56 then #53 first; never cut #30, #41, #42.

**Objections:** None.

## D21 — Commit size rule (2026-10-16)

**Decision:** Constraint on how work is committed.

**Choice:** No large commits; one logical change per commit, ~200 changed lines guideline excluding
generated files; refactors and tests in separate commits; every commit compiles. Recorded in
`CLAUDE.md` (git-ignored, so local to this machine).

**Objections:** None.

## D22 — Integration test isolation (F1) (2026-10-16)

**Decision:** How the shared test database is reset between integration tests.

**Options considered:**
- A. `TRUNCATE` all tables `RESTART IDENTITY CASCADE` in `@BeforeEach`; schema loaded once per run; ms per reset; deterministic ids; row triggers not fired.
- B. Re-run `createTables.sql` per test: exact schema each time, but couples every test to suspected bug #4; ~50–100 ms per test.
- C. Per-test transaction rollback: impossible, since code under test opens and commits its own connections.
- D. Fresh database per test from a template: strongest isolation; ~100–300 ms per test; more plumbing.

**Choice:** A. The truncate list must be updated when the schema gains a table.

**Objections:** None.

## D23 — Correct buy_price on repeat buys (F2, bug #3) (2026-10-16)

**Decision:** What `Portfolio.buy_price` should be after buying more of a coin already held.

**Options considered:**
- A. Weighted average cost: `(u1·p1 + u2·p2) / (u1 + u2)`; standard accounting; one-line upsert fix.
- B. Keep first price (current behavior), declared intended; profit overstated after repeat buys.
- C. Last price wins; profit understated.

**Choice:** A. Case #19 asserts 100.50 after buys at 100 and 101. Fix vs. report decided per D7.

**Objections:** None.

## D24 — Balance rounding spec (F3, bug #5) (2026-10-16)

**Decision:** The specified rounding of money stored in `Users.balance` (`DECIMAL(15,2)`), which currently receives Java `double`s.

**Options considered:**
- A. Half-up to cents (10.005 → 10.01); likely fails today because `double` 10.005 is 10.00499…; fix: `BigDecimal.setScale(2, HALF_UP)` + `setBigDecimal`.
- B. Within one cent of exact: passes today; also passes a consistent bias against the user.
- C. Drop the case; record sub-cent drift as a known limitation.

**Choice:** A. Case #28 asserts 10.01 after crediting 10.005. Expected-failure reasoning (PostgreSQL computing `numeric + float8` in floating point) is unverified until the test runs.

**Objections:** None.

## D25 — Buy atomicity and stale price (F4, bugs #1 and #2) (2026-10-16)

**Decision:** How to test, and whether to fix, the split buy/pay sequence (`UserController:321-322`) and the
price read before the trade is recorded.

**Options considered:**
- A. Fix: new `AtomicSQL.purchaseAtomically(userId, symbol, units)`: one transaction, `SELECT … FOR UPDATE`
  on coin and user rows, insert trade, deduct `price × units`, commit; `UserController.buyCrypto` calls it.
  ~50–70 production lines + 2-line controller change; 3–4 new IT cases.
- B. Report only: no correct-behavior test is expressible against the split API; document as known defects.
- C. Controller-level test: needs the rejected controller refactor; still cannot express atomicity without a fix.

**Choice:** A (decided now, overriding D7's case-by-case process for these two bugs). New cases:
#57 insufficient balance → no position/price change/charge; #58 success → charged exactly locked price × units;
#59 unknown user → nothing changes; #60 unknown coin → nothing changes.
Bug #2's race is fixed by row locking and verified by design, not by a test (concurrency tests rejected in D9).
Fix and tests go in separate commits (D21). Check teammate ownership of `UserController` before editing.

**Objections:** None.

## D26 — Re-seed after all coins deleted (F5) (2026-10-16)

**Decision:** Whether `loadInitialData` re-seeding defaults when `Cryptocurrency` is empty is intended.

**Options considered:**
- A. Seed only on first-ever startup (persistent seed-done marker; schema change).
- B. Declare current behavior intended: an empty market is restored to defaults.
- C. Move seeding into `createTables.sql`; remove JSON seeding.

**Choice:** B. Case #43 asserts all 5 defaults return after deleting every coin.
Side finding: `loadInitialData`'s `seedFromJson` parameter is unused.

**Objections:** None.

## D27 — Invalid coin data (F6, bug #8) (2026-10-16)

**Decision:** What happens to coins with invalid volatility, price, symbol or name (reachable via admin JSON import,
`AdminController.addCryptoFromFile`, where Gson defaults missing numbers to 0).

**Options considered:**
- A. Validate in `CryptoManager.createCrypto` before any DB write (`CryptoNotAddedException`); `Bot` constructor
  rejects volatility ≤ 0 (`IllegalArgumentException`) as a backstop.
- B. Guard only in `Bot` (≤ 0 means never trades); bad data still stored.
- C. Schema `CHECK` constraints; generic error message; best combined with A.

**Choice:** A, with valid volatility range 0 < v ≤ 10 (defaults are 0.5–3.5; 999.99 would mean a trade every 5 ms).
C deferred. Suspected bug #8 (unverified): negative volatility throws after commit, and on next login aborts
`loadInitialCrypto`'s loop, so later bots never start. Case #49 becomes `bot_rejectsNonPositiveVolatility`;
new cases #61–64: `createCrypto` rejects volatility ≤ 0, volatility > 10, price ≤ 0, missing symbol/name.

**Objections:** None.

## D28 — Bug #3: fix (2026-10-16)

**Decision:** Fix or report-only for bug #3, confirmed by TradingIT #19 (`expected: <100.5> but was: <100.0>`).

**Options considered:**
- A. Fix: weighted-average `buy_price` in the `buyCrypto` upsert; existing rows not backfilled.
- B. Report only: `@Disabled("BUG-3: ...")`.

**Choice:** A. Commit order base → TradingIT (red on #19) → fix, so history shows the test found the bug;
the red commit compiles (D21 requires compile, not pass).

**Also recorded:** #28 passed, contrary to the prediction in D24; suspected bug #5 did not reproduce at 10.005
(likely because PostgreSQL's float8→numeric conversion goes through a ~15-significant-digit decimal form; unverified).

**Objections:** None.

## D29 — Bug #9: duplicate-symbol error (2026-10-16)

**Decision:** Fix or report-only for bug #9, confirmed by AtomicSqlIT #30: a duplicate symbol throws
`DbConnectionException("... users_email_key")` instead of `CryptoNotAddedException`, because the bot user
(unique email `bot.<SYMBOL>@...`) is inserted before the coin.

**Options considered:**
- A. Check `cryptoExists(conn, symbol)` first and throw `CryptoNotAddedException` (~3 lines, reuses existing helper).
- B. Reorder inserts so `ON CONFLICT DO NOTHING` catches it.
- C. Report only (`@Disabled`); leaves #30's rollback assertions unverified.

**Choice:** A. Commit order AtomicSqlIT (red on #30) → fix. #30's rollback assertions were not executed in the
red run (assertThrows failed first) and are verified only after the fix.

**Objections:** None.

## D30 — SeedingIT mutation check (2026-10-16)

**Decision:** Verify the seeding regression tests can fail (Q25).

**Method:** Temporarily replaced `if (!hasCryptos(conn))` with `if (true)` in `AtomicSQL.loadInitialData`, ran SeedingIT,
reverted with `git checkout` (never committed).

**Result:** #42 failed (`expected: <0> but was: <1>`; deleted BTC re-created), #40/#41/#43 passed.
Prediction that #41 would fail was wrong: a second guard (`cryptoExists` per coin) independently prevents
duplicates. `247dfb6` introduced the `hasCryptos` guard, so #42, not #41, guards both 247dfb6 and 06d8ccf;
display names corrected accordingly.

**Objections:** None.

## D31 — Bug #4: schema script not re-runnable (2026-10-16)

**Decision:** Fix or report-only for bug #4, confirmed by SchemaIT #44 (`relation "notifications" already exists`
on second application; first application on a fresh database succeeds).

**Options considered:**
- A. Fix: add `DROP TABLE IF EXISTS Notifications CASCADE;` to the top of `createTables.sql`.
- B. Report only (`@Disabled`).

**Choice:** A. Re-running the script now resets all tables including pending notifications, consistent with
the existing DROP statements. SchemaIT runs in a throwaway database (`schema_check`), so the shared test
database is unaffected either way.

**Objections:** None.

## D32 — Covering bug #10 (2026-10-16)

**Decision:** How "no user logged in" is tested, given suspected bug #10 (`UserManager.currentUserId` has no
initializer, so a fresh manager starts at 0, not -1, and the `== -1` guards don't fire until `clearCurrentUser()`).

**Options considered:**
- A. #11/#14 call `clearCurrentUser()` and test the guard; new case #65 asserts a fresh manager reports -1.
- B. #11/#14 use a fresh manager (bug #10 reported twice; guard logic untested).
- C. Both states inside #11/#14 via `assertAll`.

**Choice:** A. Test list grows to 65 cases.

**Objections:** None.

## D33 — Exception type for rejected deposits (Q29, bug #7) (2026-10-16)

**Decision:** Which exception `UserManager.addBalance` must throw for zero/negative amounts (case #15).

**Options considered:**
- A. `IllegalArgumentException`: no new class; unchecked, so `UserController.addBalance` would let it escape to the EDT with no user message.
- B. New checked `InvalidAmountException` (persistence/Exceptions), matching the project's checked domain-exception convention; the compiler forces UI handling; the fix needs a 2-line catch in `UserController`.
- C. Reuse `InsufficientBalanceException`: wrong meaning shown to the user.

**Choice:** B. The class is added in its own commit before the test so the test compiles.

**Objections:** None.

## D34 — Bug #10: initial user id (2026-10-16)

**Decision:** Fix or report-only for bug #10, confirmed by UserManagerTest #65 (`expected: <-1> but was: <0>`).

**Options considered:**
- A. Fix: initialize `currentUserId = -1` (the existing comment's intent).
- B. Report only (`@Disabled`).

**Choice:** A. All 11 reads of the current user id in `UserController` pass it to DB queries where 0 and -1 both
match no user (identity ids start at 1), and login always sets it first, so no query result changes; the
`== -1` guards now also hold before the first login.

**Objections:** None.

## D35 — Bug #7: non-positive deposits (2026-10-16)

**Decision:** Fix or report-only for bug #7, confirmed by UserManagerTest #15 (0.00 and -500.00 both accepted).

**Options considered:**
- A. Fix: `UserManager.addBalance` throws `InvalidAmountException` for amount ≤ 0; `UserController.addBalance` and
  `sellCrypto` catch it and show the message (2 catch clauses; same commit so every commit compiles).
- B. Report only (`@Disabled`).

**Choice:** A. First edit to `UserController` (last changed 2026-06-16 by teammates). Side effect: sell proceeds ≤ 0
(reachable only via bug #6) are now rejected with a message instead of silently debiting.

**Objections:** None.
