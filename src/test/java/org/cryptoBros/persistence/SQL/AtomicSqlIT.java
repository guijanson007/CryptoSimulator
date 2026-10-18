package org.cryptoBros.persistence.SQL;

import org.cryptoBros.IntegrationTestBase;
import org.cryptoBros.business.Crypto;
import org.cryptoBros.business.CryptoManager;
import org.cryptoBros.persistence.Exceptions.CryptoNameAlreadyExists;
import org.cryptoBros.persistence.Exceptions.CryptoNotAddedException;
import org.cryptoBros.persistence.Exceptions.CryptoNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coin creation and deletion transactions, refunds and notifications (risk C).
 * Note: deletion and rename look coins up by name, not symbol.
 */
class AtomicSqlIT extends IntegrationTestBase {

    private static final double EPS = 1e-8;

    private final AtomicSQL atomic = new AtomicSQL();
    private final UserPortfolioSQL portfolio = new UserPortfolioSQL();

    private static Crypto coin(String symbol, String name, double price) {
        return new Crypto(symbol, name, price, price, 1.0);
    }

    private static long count(String sql, Object... params) throws Exception {
        return queryLong("SELECT COUNT(*) FROM " + sql, params);
    }

    @Test
    @DisplayName("#29 creating a coin inserts the coin, its bot user and its bot row atomically")
    void createCrypto_insertsCoinBotUserAndBotRow() throws Exception {
        long botId = atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 0.25));

        assertEquals(0.25, queryDouble("SELECT current_price FROM Cryptocurrency WHERE symbol = 'DOGE'"), EPS);
        assertEquals(1, count("Users WHERE user_id = ? AND username = 'BOT_DOGE'", botId));
        assertEquals(1, count("Bots WHERE bot_id = ? AND crypto_id = 'DOGE'", botId));
        assertEquals(1, count("Crypto_History WHERE crypto_id = 'DOGE'"));   // insert trigger
    }

    @Test
    @DisplayName("#30 creating a duplicate symbol is rejected and leaves no orphaned bot user")
    void createCrypto_duplicateSymbol_rollsBackBotUser() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 0.25));
        long usersBefore = count("Users");

        assertThrows(CryptoNotAddedException.class,
                () -> atomic.createCryptoWithBot(coin("DOGE", "Dogecoin Two", 0.30)));

        assertEquals(usersBefore, count("Users"));
        assertEquals(1, count("Bots"));
        assertEquals(0.25, queryDouble("SELECT current_price FROM Cryptocurrency WHERE symbol = 'DOGE'"), EPS);
    }

    @Test
    @DisplayName("#31 deleting a coin refunds each holder at the current price")
    void deleteCrypto_refundsHoldersAtCurrentPrice() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));
        long alice = insertUser("alice", 0.00);
        long bob = insertUser("bob", 0.00);
        portfolio.buyCrypto(alice, "DOGE", 100.00, 2.0);   // price -> 101.00
        portfolio.buyCrypto(bob, "DOGE", 101.00, 1.0);     // price -> 102.01

        Map<Long, Double> refunds = atomic.deleteCryptoWithBot("Dogecoin");

        assertEquals(2, refunds.size());
        assertEquals(204.02, refunds.get(alice), EPS);
        assertEquals(102.01, refunds.get(bob), EPS);
        assertEquals(204.02, queryDouble("SELECT balance FROM Users WHERE user_id = ?", alice), EPS);
        assertEquals(102.01, queryDouble("SELECT balance FROM Users WHERE user_id = ?", bob), EPS);
    }

    @Test
    @DisplayName("#32 deleting a coin does not refund its own bot")
    void deleteCrypto_doesNotRefundBot() throws Exception {
        long botId = atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));
        portfolio.buyCrypto(botId, "DOGE", 100.00, 1.0);

        Map<Long, Double> refunds = atomic.deleteCryptoWithBot("Dogecoin");

        assertFalse(refunds.containsKey(botId));
        assertTrue(refunds.isEmpty());
    }

    @Test
    @DisplayName("#33 deleting a coin notifies each holder exactly once, and only holders")
    void deleteCrypto_notifiesEachHolderOnce() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));
        long alice = insertUser("alice", 0.00);
        long bystander = insertUser("bystander", 0.00);
        portfolio.buyCrypto(alice, "DOGE", 100.00, 1.0);

        atomic.deleteCryptoWithBot("Dogecoin");

        assertEquals(1, count("Notifications WHERE user_id = ?", alice));
        assertEquals(0, count("Notifications WHERE user_id = ?", bystander));
    }

    @Test
    @DisplayName("#34 deleting a coin removes the coin, bot, positions, history and bot user")
    void deleteCrypto_removesCoinBotsPortfolioHistoryAndBotUser() throws Exception {
        long botId = atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));
        long alice = insertUser("alice", 0.00);
        portfolio.buyCrypto(alice, "DOGE", 100.00, 1.0);

        atomic.deleteCryptoWithBot("Dogecoin");

        assertAll(
                () -> assertEquals(0, count("Cryptocurrency WHERE symbol = 'DOGE'")),
                () -> assertEquals(0, count("Bots WHERE crypto_id = 'DOGE'")),
                () -> assertEquals(0, count("Portfolio WHERE crypto_id = 'DOGE'")),
                () -> assertEquals(0, count("Crypto_History WHERE crypto_id = 'DOGE'")),
                () -> assertEquals(0, count("Users WHERE user_id = ?", botId)),
                () -> assertEquals(1, count("Users WHERE user_id = ?", alice)));
    }

    @Test
    @DisplayName("#35 deleting an unknown coin throws and changes nothing")
    void deleteCrypto_unknownName_throws_andNothingChanges() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));

        assertThrows(CryptoNotFoundException.class, () -> atomic.deleteCryptoWithBot("No Such Coin"));

        assertEquals(1, count("Cryptocurrency"));
        assertEquals(1, count("Bots"));
    }

    @Test
    @DisplayName("#36 deleting a coin nobody holds returns no refunds")
    void deleteCrypto_noHolders_returnsEmptyRefunds() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));

        assertTrue(atomic.deleteCryptoWithBot("Dogecoin").isEmpty());
        assertEquals(0, count("Cryptocurrency"));
    }

    @Test
    @DisplayName("#37 popping notifications returns them once, then nothing")
    void popNotifications_returnsThenEmpties() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));
        long alice = insertUser("alice", 0.00);
        portfolio.buyCrypto(alice, "DOGE", 100.00, 1.0);
        atomic.deleteCryptoWithBot("Dogecoin");

        List<String> first = atomic.popNotifications(alice);

        assertEquals(1, first.size());
        assertTrue(first.getFirst().contains("Dogecoin"));
        assertTrue(atomic.popNotifications(alice).isEmpty());
    }

    @Test
    @DisplayName("#38 renaming a coin to a name already in use is rejected")
    void renameCrypto_toExistingName_rejected() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));
        atomic.createCryptoWithBot(coin("SHIB", "Shiba", 1.00));

        assertThrows(CryptoNameAlreadyExists.class, () -> new CryptoManager().changeCryptoName("Shiba", "Dogecoin"));

        assertEquals(1, count("Cryptocurrency WHERE symbol = 'SHIB' AND name = 'Shiba'"));
    }

    @Test
    @DisplayName("#39 a renamed coin can still be deleted and refunded by its new name")
    void renameCrypto_thenDeleteByNewName_refunds() throws Exception {
        atomic.createCryptoWithBot(coin("DOGE", "Dogecoin", 100.00));
        long alice = insertUser("alice", 0.00);
        portfolio.buyCrypto(alice, "DOGE", 100.00, 1.0);   // price -> 101.00

        new CryptoManager().changeCryptoName("Dogecoin", "Doge");
        Map<Long, Double> refunds = atomic.deleteCryptoWithBot("Doge");

        assertEquals(101.00, refunds.get(alice), EPS);
        assertEquals(0, count("Cryptocurrency"));
    }
}
