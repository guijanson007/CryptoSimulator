package org.cryptoBros.fakes;

import org.cryptoBros.business.Crypto;
import org.cryptoBros.persistence.CryptoPersistence;
import org.cryptoBros.persistence.Exceptions.CryptoNotFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-in for {@link CryptoPersistence} (D16): fixed prices per symbol.
 * Methods no test needs throw, so an unexpected call fails loudly.
 */
public class FakeCryptoPersistence implements CryptoPersistence {

    private final Map<String, Double> prices = new HashMap<>();

    /** Sets the current price of a symbol and returns this fake for chaining. */
    public FakeCryptoPersistence withPrice(String symbol, double price) {
        prices.put(symbol, price);
        return this;
    }

    @Override
    public double getCurrentPrice(String symbol) throws CryptoNotFoundException {
        Double price = prices.get(symbol);
        if (price == null) {
            throw new CryptoNotFoundException("No fake price for " + symbol);
        }
        return price;
    }

    @Override
    public Crypto getCrypto(String symbol) throws CryptoNotFoundException {
        double price = getCurrentPrice(symbol);
        return new Crypto(symbol, symbol + " coin", price, price, 1.0);
    }

    @Override
    public List<Crypto> getAllCrypto() {
        throw unexpected();
    }

    @Override
    public Map<Instant, Double> getPriceHistory(String symbol) {
        throw unexpected();
    }

    @Override
    public void changeCryptoName(String oldName, String newName) {
        throw unexpected();
    }

    private static UnsupportedOperationException unexpected() {
        return new UnsupportedOperationException("Not expected in this test");
    }
}
