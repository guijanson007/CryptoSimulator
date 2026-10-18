package org.cryptoBros.persistence.SQL;

import org.cryptoBros.IntegrationTestBase;
import org.cryptoBros.persistence.Exceptions.CryptoNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Correctness of the price-chart query (CryptoSQL.getPriceHistory), the query indexed in D53.
 * Times are relative to the database clock and kept well away from the 10-minute boundary; no absolute
 * instants are asserted because the JVM and database time zones may differ (suspected issue #14).
 */
class PriceHistoryIT extends IntegrationTestBase {

    private final CryptoSQL cryptos = new CryptoSQL();

    @BeforeEach
    void seed() throws Exception {
        insertCoin("BTC", 100.00);
        insertCoin("ETH", 50.00);
        execute("DELETE FROM Crypto_History");   // drop the rows the insert trigger just wrote
    }

    private static void history(String symbol, int minutesAgo, double price) throws Exception {
        execute("INSERT INTO Crypto_History (crypto_id, time_stamp, price) VALUES ('" + symbol + "', "
                + "CURRENT_TIMESTAMP - INTERVAL '" + minutesAgo + " minutes', " + price + ")");
    }

    @Test
    @DisplayName("#71 only the requested coin's rows from the last 10 minutes are returned")
    void returnsOnlyThisCoinWithinTenMinutes() throws Exception {
        history("BTC", 1, 101.00);
        history("BTC", 5, 102.00);
        history("BTC", 11, 999.00);   // outside the window
        history("ETH", 2, 51.00);     // other coin

        Map<Instant, Double> result = cryptos.getPriceHistory("BTC");

        assertEquals(2, result.size());
        assertFalse(result.containsValue(999.00));
        assertFalse(result.containsValue(51.00));
    }

    @Test
    @DisplayName("#72 rows are returned oldest first, whatever order they were stored in")
    void returnsOldestFirst() throws Exception {
        history("BTC", 1, 3.00);
        history("BTC", 9, 1.00);
        history("BTC", 5, 2.00);

        List<Double> prices = new ArrayList<>(cryptos.getPriceHistory("BTC").values());

        assertEquals(List.of(1.00, 2.00, 3.00), prices);
    }

    @Test
    @DisplayName("#73 a coin with no rows in the last 10 minutes reports no history")
    void noRecentHistory_throws() throws Exception {
        history("BTC", 11, 101.00);

        assertThrows(CryptoNotFoundException.class, () -> cryptos.getPriceHistory("BTC"));
    }

    @Test
    @DisplayName("#74 an unknown coin reports no history")
    void unknownCoin_throws() {
        assertThrows(CryptoNotFoundException.class, () -> cryptos.getPriceHistory("NOPE"));
    }
}
