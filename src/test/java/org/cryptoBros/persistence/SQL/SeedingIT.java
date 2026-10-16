package org.cryptoBros.persistence.SQL;

import org.cryptoBros.IntegrationTestBase;
import org.cryptoBros.business.CryptoManager;
import org.cryptoBros.business.Workers.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Initial coin seeding from crypto.json (risk C). #42 guards the regressions fixed in 247dfb6
 * ("always loading initial cryptos again") and 06d8ccf ("deleted cryptos being created again on new login");
 * verified by mutation in D30.
 * Bots returned by loadInitialData are constructed but never started here.
 */
class SeedingIT extends IntegrationTestBase {

    private static final List<String> DEFAULT_NAMES = List.of("Bitcoin", "Ethereum", "Litecoin", "Ripple", "Cardano");

    private final AtomicSQL atomic = new AtomicSQL();
    private final CryptoManager listener = new CryptoManager();

    private static long coinCount() throws Exception {
        return queryLong("SELECT COUNT(*) FROM Cryptocurrency");
    }

    @Test
    @DisplayName("#40 seeding an empty database inserts the five default coins and returns one bot each")
    void seed_emptyDb_insertsAllFiveCoins_andReturnsFiveBots() throws Exception {
        List<Bot> bots = atomic.loadInitialData(listener, true);

        assertEquals(5, coinCount());
        assertEquals(5, queryLong("SELECT COUNT(*) FROM Cryptocurrency WHERE symbol IN ('BTC','ETH','LTC','XRP','ADA')"));
        assertEquals(5, queryLong("SELECT COUNT(*) FROM Bots"));
        assertEquals(5, bots.size());
    }

    @Test
    @DisplayName("#41 seeding twice does not duplicate coins or bots")
    void seed_secondCall_doesNotDuplicate() throws Exception {
        atomic.loadInitialData(listener, true);

        List<Bot> bots = atomic.loadInitialData(listener, true);

        assertEquals(5, coinCount());
        assertEquals(5, queryLong("SELECT COUNT(*) FROM Bots"));
        assertEquals(5, bots.size());
    }

    @Test
    @DisplayName("#42 a deleted default coin is not recreated on the next load (regressions 247dfb6, 06d8ccf)")
    void seed_afterDeletingOneCoin_doesNotRecreateIt() throws Exception {
        atomic.loadInitialData(listener, true);
        atomic.deleteCryptoWithBot("Bitcoin");

        List<Bot> bots = atomic.loadInitialData(listener, true);

        assertEquals(0, queryLong("SELECT COUNT(*) FROM Cryptocurrency WHERE symbol = 'BTC'"));
        assertEquals(4, coinCount());
        assertEquals(4, bots.size());
    }

    @Test
    @DisplayName("#43 when every coin has been deleted, the defaults are restored (intended, D26)")
    void seed_afterDeletingAllCoins_restoresDefaults() throws Exception {
        atomic.loadInitialData(listener, true);
        for (String name : DEFAULT_NAMES) {
            atomic.deleteCryptoWithBot(name);
        }
        assertEquals(0, coinCount());

        List<Bot> bots = atomic.loadInitialData(listener, true);

        assertEquals(5, coinCount());
        assertEquals(5, bots.size());
    }
}
