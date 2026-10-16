package org.cryptoBros.fakes;

import org.cryptoBros.persistence.PortfolioPosition;
import org.cryptoBros.persistence.UserPortfolioPersistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for {@link UserPortfolioPersistence} (D16) that records every trade instead of storing it.
 */
public class FakeUserPortfolioPersistence implements UserPortfolioPersistence {

    /** One recorded call; {@code price} is 0 for sells, which take no price. */
    public record Trade(String side, long userId, String symbol, double price, double units) {}

    private final List<Trade> trades = new ArrayList<>();

    /** Returns every recorded trade, in call order. */
    public List<Trade> trades() {
        return trades;
    }

    @Override
    public void buyCrypto(long userId, String cryptoSymbol, double currentPrice, double units) {
        trades.add(new Trade("buy", userId, cryptoSymbol, currentPrice, units));
    }

    @Override
    public void sellCrypto(long userId, String cryptoSymbol, double units) {
        trades.add(new Trade("sell", userId, cryptoSymbol, 0, units));
    }

    @Override
    public List<PortfolioPosition> getUserPortfolio(long userId) {
        throw new UnsupportedOperationException("Not expected in this test");
    }
}
