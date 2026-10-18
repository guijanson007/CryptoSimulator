package org.cryptoBros.fakes;

import org.cryptoBros.business.Liseners.BotListener;
import org.cryptoBros.persistence.Exceptions.CryptoNotFoundException;
import org.cryptoBros.persistence.Exceptions.DbConnectionException;
import org.cryptoBros.persistence.Exceptions.PurchaseNotAddedException;
import org.cryptoBros.persistence.Exceptions.SaleNotAddedException;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every bot trade callback and can be told to fail buys or sells with a given exception (D16).
 */
public class FakeBotListener implements BotListener {

    /** One recorded callback. */
    public record Call(String side, long botUserId, String symbol, double units) {}

    private final List<Call> calls = new ArrayList<>();
    private Exception buyFailure;
    private Exception sellFailure;

    /** Makes every subsequent onBotBuy throw {@code failure} after recording the call. */
    public FakeBotListener failBuysWith(Exception failure) {
        this.buyFailure = failure;
        return this;
    }

    /** Makes every subsequent onBotSell throw {@code failure} after recording the call. */
    public FakeBotListener failSellsWith(Exception failure) {
        this.sellFailure = failure;
        return this;
    }

    /** Returns every recorded callback, in call order. */
    public List<Call> calls() {
        return calls;
    }

    @Override
    public void onBotBuy(long botUserId, String cryptoSymbol, double units)
            throws PurchaseNotAddedException, DbConnectionException, CryptoNotFoundException {
        calls.add(new Call("buy", botUserId, cryptoSymbol, units));
        switch (buyFailure) {
            case null -> { }
            case RuntimeException e -> throw e;
            case PurchaseNotAddedException e -> throw e;
            case DbConnectionException e -> throw e;
            case CryptoNotFoundException e -> throw e;
            default -> throw new IllegalArgumentException("onBotBuy cannot throw " + buyFailure);
        }
    }

    @Override
    public void onBotSell(long botUserId, String cryptoSymbol, double units)
            throws DbConnectionException, SaleNotAddedException, CryptoNotFoundException {
        calls.add(new Call("sell", botUserId, cryptoSymbol, units));
        switch (sellFailure) {
            case null -> { }
            case RuntimeException e -> throw e;
            case DbConnectionException e -> throw e;
            case SaleNotAddedException e -> throw e;
            case CryptoNotFoundException e -> throw e;
            default -> throw new IllegalArgumentException("onBotSell cannot throw " + sellFailure);
        }
    }
}
