package org.cryptoBros.fakes;

import org.cryptoBros.business.User;
import org.cryptoBros.persistence.Exceptions.UserNotFoundException;
import org.cryptoBros.persistence.UserPersistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-in for {@link UserPersistence} (D16). Holds one balance per user id and records every
 * balance adjustment. Methods no test needs throw, so an unexpected call fails loudly.
 */
public class FakeUserPersistence implements UserPersistence {

    private final Map<Long, Double> balances = new HashMap<>();
    private final List<Double> adjustments = new ArrayList<>();

    /** Sets the stored balance for a user and returns this fake for chaining. */
    public FakeUserPersistence withBalance(long userId, double balance) {
        balances.put(userId, balance);
        return this;
    }

    /** Returns every amount passed to {@link #adjustUserBalance}, in call order. */
    public List<Double> adjustments() {
        return adjustments;
    }

    @Override
    public double getUserBalance(long id) throws UserNotFoundException {
        Double balance = balances.get(id);
        if (balance == null) {
            throw new UserNotFoundException("No fake balance for user " + id);
        }
        return balance;
    }

    @Override
    public double adjustUserBalance(long userId, double amount) throws UserNotFoundException {
        double newBalance = getUserBalance(userId) + amount;
        balances.put(userId, newBalance);
        adjustments.add(amount);
        return newBalance;
    }

    @Override
    public User addUser(User user) {
        throw unexpected();
    }

    @Override
    public void removeUser(int id) {
        throw unexpected();
    }

    @Override
    public User getUser(String username, String email) {
        throw unexpected();
    }

    @Override
    public void changeUsername(String newUsername, int currentUserId) {
        throw unexpected();
    }

    @Override
    public void changePassword(String password, int currentUserId) {
        throw unexpected();
    }

    private static UnsupportedOperationException unexpected() {
        return new UnsupportedOperationException("Not expected in this test");
    }
}
