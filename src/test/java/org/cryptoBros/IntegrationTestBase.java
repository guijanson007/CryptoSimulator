package org.cryptoBros;

import org.cryptoBros.persistence.SQL.DbConnectionSingleton;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for integration tests.
 * One PostgreSQL container is shared by the whole test run (D14); the schema is loaded once from
 * createTables.sql, and every table is truncated before each test (D22).
 */
public abstract class IntegrationTestBase {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
        DbConnectionSingleton.getInstance().configure(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try {
            execute(Files.readString(Path.of("createTables.sql")));
        } catch (IOException | SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @BeforeEach
    void truncateAllTables() throws SQLException {
        execute("TRUNCATE Users, Cryptocurrency, Bots, Portfolio, Crypto_History, Notifications RESTART IDENTITY CASCADE");
    }

    protected static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Opens a connection to another database in the same container, e.g. one a test creates for itself. */
    protected static Connection connect(String database) throws SQLException {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Runs one or more SQL statements with no parameters (the driver handles $$-quoted function bodies). */
    protected static void execute(String sql) throws SQLException {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /** Inserts a plain user with the given balance and returns its id. */
    protected static long insertUser(String username, double balance) throws SQLException {
        return queryLong("INSERT INTO Users (username, email, password, balance) VALUES (?, ?, '', ?) RETURNING user_id",
                username, username + "@test.local", BigDecimal.valueOf(balance));
    }

    /** Inserts a coin whose current and original price are both {@code price}. */
    protected static void insertCoin(String symbol, double price) throws SQLException {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO Cryptocurrency (symbol, name, current_price, original_price, volatility) VALUES (?, ?, ?, ?, 1.0)")) {
            ps.setString(1, symbol);
            ps.setString(2, symbol + " coin");
            ps.setBigDecimal(3, BigDecimal.valueOf(price));
            ps.setBigDecimal(4, BigDecimal.valueOf(price));
            ps.executeUpdate();
        }
    }

    /** Returns the first column of the first row as a double. Fails if there is no row. */
    protected static double queryDouble(String sql, Object... params) throws SQLException {
        return querySingle(sql, params).doubleValue();
    }

    /** Returns the first column of the first row as a long. Fails if there is no row. */
    protected static long queryLong(String sql, Object... params) throws SQLException {
        return querySingle(sql, params).longValue();
    }

    private static BigDecimal querySingle(String sql, Object... params) throws SQLException {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("Query returned no rows: " + sql);
                }
                return rs.getBigDecimal(1);
            }
        }
    }
}
