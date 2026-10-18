package org.cryptoBros.persistence.SQL;

import org.cryptoBros.IntegrationTestBase;
import org.cryptoBros.persistence.Exceptions.CryptoNotFoundException;
import org.cryptoBros.persistence.Exceptions.PurchaseNotAddedException;
import org.cryptoBros.persistence.Exceptions.SaleNotAddedException;
import org.cryptoBros.persistence.Exceptions.UserNotFoundException;
import org.cryptoBros.persistence.PortfolioPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Buy/sell/balance behavior against a real PostgreSQL, including the ±1% price triggers (risk A, D5).
 */
class TradingIT extends IntegrationTestBase {

    private static final double EPS = 1e-8;

    private final UserPortfolioSQL portfolio = new UserPortfolioSQL();
    private final UserSQL users = new UserSQL();

    private long alice;

    @BeforeEach
    void seed() throws Exception {
        alice = insertUser("alice", 1000.00);
        insertCoin("BTC", 100.00);
    }

    private double price() throws Exception {
        return queryDouble("SELECT current_price FROM Cryptocurrency WHERE symbol = 'BTC'");
    }

    private double units(long userId) throws Exception {
        return queryDouble("SELECT units FROM Portfolio WHERE user_id = ? AND crypto_id = 'BTC'", userId);
    }

    private long positionCount(long userId) throws Exception {
        return queryLong("SELECT COUNT(*) FROM Portfolio WHERE user_id = ? AND crypto_id = 'BTC'", userId);
    }

    @Test
    @DisplayName("#16 buying a new position inserts a row at the given buy price")
    void buy_newPosition_insertsRowAtBuyPrice() throws Exception {
        portfolio.buyCrypto(alice, "BTC", 100.00, 2.0);

        assertEquals(2.0, units(alice), EPS);
        assertEquals(100.00, queryDouble("SELECT buy_price FROM Portfolio WHERE user_id = ?", alice), EPS);
    }

    @Test
    @DisplayName("#17 a buy raises the price by 1% and records it in the history")
    void buy_raisesPriceOnePercent_andRecordsHistory() throws Exception {
        long historyBefore = queryLong("SELECT COUNT(*) FROM Crypto_History WHERE crypto_id = 'BTC'");

        portfolio.buyCrypto(alice, "BTC", 100.00, 1.0);

        assertEquals(101.00, price(), EPS);
        assertEquals(historyBefore + 1, queryLong("SELECT COUNT(*) FROM Crypto_History WHERE crypto_id = 'BTC'"));
        assertEquals(101.00, queryDouble(
                "SELECT price FROM Crypto_History WHERE crypto_id = 'BTC' ORDER BY event_id DESC LIMIT 1"), EPS);
    }

    @Test
    @DisplayName("#18 buying more of a held coin accumulates units and raises the price again")
    void buy_existingPosition_accumulatesUnits_andRaisesPriceAgain() throws Exception {
        portfolio.buyCrypto(alice, "BTC", 100.00, 1.0);
        portfolio.buyCrypto(alice, "BTC", 101.00, 1.0);

        assertEquals(2.0, units(alice), EPS);
        assertEquals(102.01, price(), EPS);
    }

    @Test
    @DisplayName("#19 buying more of a held coin sets buy_price to the weighted average (D23, suspected bug #3)")
    void buy_existingPosition_updatesBuyPrice() throws Exception {
        portfolio.buyCrypto(alice, "BTC", 100.00, 1.0);
        portfolio.buyCrypto(alice, "BTC", 101.00, 1.0);

        assertEquals(100.50, queryDouble("SELECT buy_price FROM Portfolio WHERE user_id = ?", alice), EPS);
    }

    @Test
    @DisplayName("#20 a partial sell decrements units and lowers the price by 1%")
    void sell_partial_decrementsUnits_lowersPriceOnePercent() throws Exception {
        portfolio.buyCrypto(alice, "BTC", 100.00, 2.0);   // price -> 101.00

        portfolio.sellCrypto(alice, "BTC", 1.0);

        assertEquals(1.0, units(alice), EPS);
        assertEquals(99.99, price(), EPS);
    }

    @Test
    @DisplayName("#21 selling everything deletes the row and lowers the price exactly once")
    void sell_all_deletesRow_lowersPriceExactlyOnce() throws Exception {
        portfolio.buyCrypto(alice, "BTC", 100.00, 1.0);   // price -> 101.00

        portfolio.sellCrypto(alice, "BTC", 1.0);

        assertEquals(0, positionCount(alice));
        assertEquals(99.99, price(), EPS);
    }

    @Test
    @DisplayName("#22 selling more than owned is rejected and changes nothing")
    void sell_moreThanOwned_throws_andNothingChanges() throws Exception {
        portfolio.buyCrypto(alice, "BTC", 100.00, 1.0);   // price -> 101.00

        assertThrows(SaleNotAddedException.class, () -> portfolio.sellCrypto(alice, "BTC", 2.0));

        assertEquals(1.0, units(alice), EPS);
        assertEquals(101.00, price(), EPS);
    }

    @Test
    @DisplayName("#23 selling a coin the user does not hold throws CryptoNotFoundException")
    void sell_noPosition_throwsCryptoNotFound() {
        assertThrows(CryptoNotFoundException.class, () -> portfolio.sellCrypto(alice, "BTC", 1.0));
    }

    @Test
    @DisplayName("#24 selling all units after a fractional buy deletes the row")
    void sell_allAfterFractionalBuy_deletesRow() throws Exception {
        portfolio.buyCrypto(alice, "BTC", 100.00, 0.33333333);
        double held = portfolio.getUserPortfolio(alice).getFirst().units();   // what the Sell button would send

        portfolio.sellCrypto(alice, "BTC", held);

        assertEquals(0, positionCount(alice));
    }

    @Test
    @DisplayName("#25 the DAO rejects non-positive units or price")
    void buy_nonPositivePriceOrUnits_rejectedByDao() throws Exception {
        assertAll(
                () -> assertThrows(PurchaseNotAddedException.class, () -> portfolio.buyCrypto(alice, "BTC", 100.00, 0)),
                () -> assertThrows(PurchaseNotAddedException.class, () -> portfolio.buyCrypto(alice, "BTC", 100.00, -1)),
                () -> assertThrows(PurchaseNotAddedException.class, () -> portfolio.buyCrypto(alice, "BTC", 0, 1.0)));
        assertEquals(0, positionCount(alice));
        assertEquals(100.00, price(), EPS);
    }

    @Test
    @DisplayName("#26 the portfolio reports the live price after someone else trades")
    void portfolio_reflectsCurrentPrice() throws Exception {
        long bob = insertUser("bob", 1000.00);
        portfolio.buyCrypto(alice, "BTC", 100.00, 1.0);   // price -> 101.00
        portfolio.buyCrypto(bob, "BTC", 101.00, 1.0);     // price -> 102.01

        List<PortfolioPosition> positions = portfolio.getUserPortfolio(alice);

        assertEquals(1, positions.size());
        assertEquals("BTC", positions.getFirst().cryptoSymbol());
        assertEquals(100.00, positions.getFirst().buyPrice(), EPS);
        assertEquals(102.01, positions.getFirst().currentPrice(), EPS);
    }

    @Test
    @DisplayName("#27 adjusting a balance returns the new balance; an unknown user is rejected")
    void adjustBalance_returnsNewBalance_unknownUserThrows() throws Exception {
        assertEquals(1250.00, users.adjustUserBalance(alice, 250.00), EPS);
        assertEquals(1250.00, queryDouble("SELECT balance FROM Users WHERE user_id = ?", alice), EPS);
        assertThrows(UserNotFoundException.class, () -> users.adjustUserBalance(9_999, 1.00));
    }

    @Test
    @DisplayName("#28 balances are stored rounded half-up to cents (D24, suspected bug #5)")
    void balance_storedToCents() throws Exception {
        long carol = insertUser("carol", 0.00);

        users.adjustUserBalance(carol, 10.005);

        assertEquals(10.01, queryDouble("SELECT balance FROM Users WHERE user_id = ?", carol), EPS);
    }
}
