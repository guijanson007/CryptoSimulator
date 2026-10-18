package org.cryptoBros.business;

import org.cryptoBros.fakes.FakeAtomicPersistence;
import org.cryptoBros.fakes.FakeCryptoPersistence;
import org.cryptoBros.fakes.FakeUserPersistence;
import org.cryptoBros.fakes.FakeUserPortfolioPersistence;
import org.cryptoBros.fakes.FakeUserPortfolioPersistence.Trade;
import org.cryptoBros.persistence.Exceptions.CryptoNotAddedException;
import org.cryptoBros.persistence.Exceptions.PurchaseNotAddedException;
import org.cryptoBros.persistence.Exceptions.SaleNotAddedException;
import org.cryptoBros.persistence.PortfolioPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Trading rules and coin validation in CryptoManager, without a database (risk A, D5/D16/D27).
 * Only rejection paths of createCrypto are tested: the accepting path starts a real bot thread.
 */
class CryptoManagerTest {

    private static final double EPS = 1e-8;
    private static final long ALICE = 1;

    private final FakeCryptoPersistence cryptos = new FakeCryptoPersistence().withPrice("BTC", 100.00);
    private final FakeUserPortfolioPersistence portfolio = new FakeUserPortfolioPersistence();
    private final FakeUserPersistence users = new FakeUserPersistence().withBalance(ALICE, 1000.00);

    private CryptoManager manager() {
        return new CryptoManager(cryptos, new FakeAtomicPersistence(), portfolio, new UserManager(users, null));
    }

    @Test
    @DisplayName("#1 buying zero units is rejected before any trade")
    void purchase_zeroUnits_rejected() {
        assertThrows(PurchaseNotAddedException.class, () -> manager().purchase(ALICE, "BTC", 0));
        assertTrue(portfolio.trades().isEmpty());
    }

    @Test
    @DisplayName("#2 buying negative units is rejected before any trade")
    void purchase_negativeUnits_rejected() {
        assertThrows(PurchaseNotAddedException.class, () -> manager().purchase(ALICE, "BTC", -1));
        assertTrue(portfolio.trades().isEmpty());
    }

    @Test
    @DisplayName("#6 a sale returns the pre-trade price × units")
    void sell_returnsPreTradePriceTimesUnits() throws Exception {
        assertEquals(200.00, manager().sell(ALICE, "BTC", 2), EPS);
        assertEquals(List.of(new Trade("sell", ALICE, "BTC", 0, 2)), portfolio.trades());
    }

    @Test
    @DisplayName("#7 selling zero or negative units is rejected (suspected bug #6)")
    void sell_nonPositiveUnits_rejected() {
        CryptoManager manager = manager();

        assertAll(
                () -> assertThrows(SaleNotAddedException.class, () -> manager.sell(ALICE, "BTC", 0)),
                () -> assertThrows(SaleNotAddedException.class, () -> manager.sell(ALICE, "BTC", -5)));
        assertTrue(portfolio.trades().isEmpty());
    }

    @Test
    @DisplayName("#8 a bot buying zero or negative units is rejected before any trade")
    void onBotBuy_nonPositiveUnits_rejected() {
        CryptoManager manager = manager();

        assertAll(
                () -> assertThrows(PurchaseNotAddedException.class, () -> manager.onBotBuy(99, "BTC", 0)),
                () -> assertThrows(PurchaseNotAddedException.class, () -> manager.onBotBuy(99, "BTC", -1)));
        assertTrue(portfolio.trades().isEmpty());
    }

    @Test
    @DisplayName("#9 an empty portfolio has zero profit")
    void totalProfit_emptyPortfolio_isZero() {
        assertEquals(0.00, manager().calculateTotalProfit(List.of()), EPS);
    }

    @Test
    @DisplayName("#10 profit sums gains and losses across positions")
    void totalProfit_mixedGainsAndLosses_summed() {
        List<PortfolioPosition> positions = List.of(
                new PortfolioPosition("BTC", 2, 100.00, 110.00),   // +20
                new PortfolioPosition("ETH", 1, 50.00, 40.00));    // -10

        assertEquals(10.00, manager().calculateTotalProfit(positions), EPS);
    }

    @Test
    @DisplayName("#61 a coin with zero or negative volatility is rejected (D27, suspected bug #8)")
    void createCrypto_nonPositiveVolatility_rejected() {
        CryptoManager manager = manager();

        assertAll(
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto("NEW", "New coin", 1.00, 1.00, 0))),
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto("NEW", "New coin", 1.00, 1.00, -1))));
    }

    @Test
    @DisplayName("#62 a coin with volatility above 10 is rejected (D27)")
    void createCrypto_volatilityAboveCap_rejected() {
        assertThrows(CryptoNotAddedException.class,
                () -> manager().createCrypto(new Crypto("NEW", "New coin", 1.00, 1.00, 10.01)));
    }

    @Test
    @DisplayName("#63 a coin with a zero or negative price is rejected (D27)")
    void createCrypto_nonPositivePrice_rejected() {
        CryptoManager manager = manager();

        assertAll(
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto("NEW", "New coin", 0, 1.00, 1.0))),
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto("NEW", "New coin", 1.00, -1.00, 1.0))));
    }

    @Test
    @DisplayName("#64 a coin with a missing or blank symbol or name is rejected (D27)")
    void createCrypto_missingSymbolOrName_rejected() {
        CryptoManager manager = manager();

        assertAll(
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto(null, "New coin", 1.00, 1.00, 1.0))),
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto(" ", "New coin", 1.00, 1.00, 1.0))),
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto("NEW", null, 1.00, 1.00, 1.0))),
                () -> assertThrows(CryptoNotAddedException.class,
                        () -> manager.createCrypto(new Crypto("NEW", "", 1.00, 1.00, 1.0))));
    }
}
