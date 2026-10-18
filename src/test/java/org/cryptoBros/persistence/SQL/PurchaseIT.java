package org.cryptoBros.persistence.SQL;

import org.cryptoBros.IntegrationTestBase;
import org.cryptoBros.persistence.Exceptions.CryptoNotFoundException;
import org.cryptoBros.persistence.Exceptions.InsufficientBalanceException;
import org.cryptoBros.persistence.Exceptions.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The transactional buy that replaces the split purchase + deductBalance sequence (D25, bugs #1 and #2).
 * Row locking against concurrent bots is verified by design, not by a test (D9).
 */
class PurchaseIT extends IntegrationTestBase {

    private static final double EPS = 1e-8;

    private final AtomicSQL atomic = new AtomicSQL();

    private long alice;

    @BeforeEach
    void seed() throws Exception {
        alice = insertUser("alice", 100.00);
        insertCoin("BTC", 40.00);
    }

    private double price() throws Exception {
        return queryDouble("SELECT current_price FROM Cryptocurrency WHERE symbol = 'BTC'");
    }

    private double balance(long userId) throws Exception {
        return queryDouble("SELECT balance FROM Users WHERE user_id = ?", userId);
    }

    private long positions() throws Exception {
        return queryLong("SELECT COUNT(*) FROM Portfolio");
    }

    @Test
    @DisplayName("#58 a purchase charges exactly the locked price × units and records the position")
    void purchase_chargesLockedPriceTimesUnits() throws Exception {
        double cost = atomic.purchaseAtomically(alice, "BTC", 2.0);

        assertEquals(80.00, cost, EPS);
        assertEquals(20.00, balance(alice), EPS);
        assertEquals(2.0, queryDouble("SELECT units FROM Portfolio WHERE user_id = ?", alice), EPS);
        assertEquals(40.00, queryDouble("SELECT buy_price FROM Portfolio WHERE user_id = ?", alice), EPS);
        assertEquals(40.40, price(), EPS);
    }

    @Test
    @DisplayName("#68 a purchase costing exactly the balance is allowed and leaves 0.00")
    void purchase_costEqualsBalance_allowed() throws Exception {
        atomic.purchaseAtomically(alice, "BTC", 2.5);

        assertEquals(0.00, balance(alice), EPS);
        assertEquals(1, positions());
    }

    @Test
    @DisplayName("#57 an unaffordable purchase is rejected and changes nothing")
    void purchase_insufficientBalance_changesNothing() throws Exception {
        long historyBefore = queryLong("SELECT COUNT(*) FROM Crypto_History");

        assertThrows(InsufficientBalanceException.class, () -> atomic.purchaseAtomically(alice, "BTC", 3.0));

        assertEquals(100.00, balance(alice), EPS);
        assertEquals(0, positions());
        assertEquals(40.00, price(), EPS);
        assertEquals(historyBefore, queryLong("SELECT COUNT(*) FROM Crypto_History"));
    }

    @Test
    @DisplayName("#59 a purchase by an unknown user is rejected and changes nothing")
    void purchase_unknownUser_changesNothing() throws Exception {
        assertThrows(UserNotFoundException.class, () -> atomic.purchaseAtomically(9_999, "BTC", 1.0));

        assertEquals(0, positions());
        assertEquals(40.00, price(), EPS);
    }

    @Test
    @DisplayName("#60 a purchase of an unknown coin is rejected and changes nothing")
    void purchase_unknownCoin_changesNothing() throws Exception {
        assertThrows(CryptoNotFoundException.class, () -> atomic.purchaseAtomically(alice, "NOPE", 1.0));

        assertEquals(100.00, balance(alice), EPS);
        assertEquals(0, positions());
    }
}
