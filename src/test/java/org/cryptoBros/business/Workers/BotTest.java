package org.cryptoBros.business.Workers;

import org.cryptoBros.fakes.FakeBotListener;
import org.cryptoBros.fakes.FakeBotListener.Call;
import org.cryptoBros.persistence.Exceptions.CryptoNotFoundException;
import org.cryptoBros.persistence.Exceptions.DbConnectionException;
import org.cryptoBros.persistence.Exceptions.PurchaseNotAddedException;
import org.cryptoBros.persistence.Exceptions.SaleNotAddedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bot decisions and lifecycle, without timing (D9): run() is called directly; the scheduler is only started
 * to check start/stop safety, and every started bot is stopped before its first 5 s tick.
 */
class BotTest {

    private final FakeBotListener listener = new FakeBotListener();

    private static Random always(boolean value) {
        return new Random() {
            @Override
            public boolean nextBoolean() {
                return value;
            }
        };
    }

    private Bot bot(String symbol, boolean buys) {
        return new Bot(7, symbol, 1.0, listener, always(buys));
    }

    private static long liveThreadsNamed(String name) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().equals(name))
                .count();
    }

    @Test
    @DisplayName("#45 a tick buys one unit when the coin flip says buy")
    void run_randomTrue_buysOneUnit() {
        bot("BTC", true).run();

        assertEquals(List.of(new Call("buy", 7, "BTC", 1.0)), listener.calls());
    }

    @Test
    @DisplayName("#45 a tick sells one unit when the coin flip says sell")
    void run_randomFalse_sellsOneUnit() {
        bot("BTC", false).run();

        assertEquals(List.of(new Call("sell", 7, "BTC", 1.0)), listener.calls());
    }

    @Test
    @DisplayName("#46 each expected buy failure is caught inside run()")
    void run_buyFailure_isSwallowed() {
        assertAll(
                () -> assertDoesNotThrow(new Bot(7, "BTC", 1.0,
                        new FakeBotListener().failBuysWith(new PurchaseNotAddedException("x")), always(true))::run),
                () -> assertDoesNotThrow(new Bot(7, "BTC", 1.0,
                        new FakeBotListener().failBuysWith(new DbConnectionException("x")), always(true))::run),
                () -> assertDoesNotThrow(new Bot(7, "BTC", 1.0,
                        new FakeBotListener().failBuysWith(new CryptoNotFoundException("x")), always(true))::run));
    }

    @Test
    @DisplayName("#46 each expected sell failure is caught inside run()")
    void run_sellFailure_isSwallowed() {
        assertAll(
                () -> assertDoesNotThrow(new Bot(7, "BTC", 1.0,
                        new FakeBotListener().failSellsWith(new DbConnectionException("x")), always(false))::run),
                () -> assertDoesNotThrow(new Bot(7, "BTC", 1.0,
                        new FakeBotListener().failSellsWith(new CryptoNotFoundException("x")), always(false))::run));
    }

    @Test
    @DisplayName("#46 selling more than the bot owns turns the tick into a buy")
    void run_sellMoreThanOwned_buysInstead() {
        listener.failSellsWith(new SaleNotAddedException("x"));

        bot("BTC", false).run();

        assertEquals(List.of(new Call("sell", 7, "BTC", 1.0), new Call("buy", 7, "BTC", 1.0)), listener.calls());
    }

    @Test
    @DisplayName("#47 starting twice keeps a single scheduler thread")
    void start_twice_keepsOneScheduler() {
        Bot bot = bot("T47", true);
        try {
            bot.start();
            bot.start();

            assertEquals(1, liveThreadsNamed("bot-T47"));
        } finally {
            bot.stop();
        }
    }

    @Test
    @DisplayName("#48 stopping before start, and stopping twice, are safe")
    void stop_beforeStart_andStopTwice_areSafe() {
        Bot bot = bot("T48", true);

        assertDoesNotThrow(bot::stop);
        bot.start();
        bot.stop();
        assertDoesNotThrow(bot::stop);
        assertFalse(bot.isRunning());
    }

    @Test
    @DisplayName("#49 a bot cannot be built with volatility outside (0, MAX_VOLATILITY] (D27/D38)")
    void constructor_rejectsInvalidVolatility() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Bot(7, "BTC", 0, listener)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Bot(7, "BTC", -1, listener)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Bot(7, "BTC", 10.01, listener)));
    }

    @Test
    @DisplayName("#67 an unexpected exception in a trade does not escape run() (suspected bug #11)")
    void run_uncheckedFailure_doesNotKillBot() {
        listener.failBuysWith(new IllegalStateException("boom"));

        assertDoesNotThrow(bot("BTC", true)::run);
    }
}
