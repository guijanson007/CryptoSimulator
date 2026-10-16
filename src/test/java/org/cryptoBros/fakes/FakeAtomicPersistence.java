package org.cryptoBros.fakes;

import org.cryptoBros.business.Crypto;
import org.cryptoBros.business.CryptoManager;
import org.cryptoBros.business.Workers.Bot;
import org.cryptoBros.persistence.AtomicPersistence;

import java.util.List;
import java.util.Map;

/**
 * Stand-in for {@link AtomicPersistence} (D16) where every call fails loudly. Used where the code under test
 * must reject its input before touching the database.
 */
public class FakeAtomicPersistence implements AtomicPersistence {

    @Override
    public long createCryptoWithBot(Crypto crypto) {
        throw unexpected();
    }

    @Override
    public Map<Long, Double> deleteCryptoWithBot(String symbol) {
        throw unexpected();
    }

    @Override
    public List<Bot> loadInitialData(CryptoManager cryptoManager, boolean seedFromJson) {
        throw unexpected();
    }

    @Override
    public List<String> popNotifications(long userId) {
        throw unexpected();
    }

    private static UnsupportedOperationException unexpected() {
        return new UnsupportedOperationException("Not expected in this test");
    }
}
