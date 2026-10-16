package org.cryptoBros.business;

import org.cryptoBros.fakes.FakeUserPersistence;
import org.cryptoBros.persistence.Exceptions.InsufficientBalanceException;
import org.cryptoBros.persistence.Exceptions.InvalidAmountException;
import org.cryptoBros.persistence.Exceptions.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Balance rules in UserManager, without a database (risk A, D5/D16).
 * AtomicPersistence is passed as null: the balance methods never use it, so any use fails immediately.
 */
class UserManagerTest {

    private static final double EPS = 1e-8;
    private static final int ALICE = 1;

    private final FakeUserPersistence users = new FakeUserPersistence().withBalance(ALICE, 50.00);

    private UserManager loggedInAs(int userId) {
        UserManager manager = new UserManager(users, null);
        manager.setCurrentUserId(userId);
        return manager;
    }

    private UserManager loggedOut() {
        UserManager manager = new UserManager(users, null);
        manager.clearCurrentUser();
        return manager;
    }

    @Test
    @DisplayName("#65 a new UserManager starts with no user logged in (suspected bug #10)")
    void freshManager_hasNoUserLoggedIn() {
        assertEquals(-1, new UserManager(users, null).getCurrentUserId());
    }

    @Test
    @DisplayName("#11 deducting with no user logged in is rejected")
    void deductBalance_noUserLoggedIn_throws() {
        assertThrows(UserNotFoundException.class, () -> loggedOut().deductBalance(10.00));
        assertTrue(users.adjustments().isEmpty());
    }

    @Test
    @DisplayName("#12 deducting more than the balance is rejected and the balance is untouched")
    void deductBalance_insufficient_throwsAndDoesNotAdjust() throws Exception {
        assertThrows(InsufficientBalanceException.class, () -> loggedInAs(ALICE).deductBalance(50.01));
        assertTrue(users.adjustments().isEmpty());
        assertEquals(50.00, users.getUserBalance(ALICE), EPS);
    }

    @Test
    @DisplayName("#13 deducting exactly the whole balance is allowed")
    void deductBalance_exactBalance_allowed() throws Exception {
        loggedInAs(ALICE).deductBalance(50.00);

        assertEquals(List.of(-50.00), users.adjustments());
        assertEquals(0.00, users.getUserBalance(ALICE), EPS);
    }

    @Test
    @DisplayName("#14 adding funds with no user logged in is rejected")
    void addBalance_noUserLoggedIn_throws() {
        assertThrows(UserNotFoundException.class, () -> loggedOut().addBalance(10.00));
        assertTrue(users.adjustments().isEmpty());
    }

    @Test
    @DisplayName("#15 adding a zero or negative amount is rejected (D33, suspected bug #7)")
    void addBalance_nonPositiveAmount_rejected() throws Exception {
        UserManager manager = loggedInAs(ALICE);

        assertAll(
                () -> assertThrows(InvalidAmountException.class, () -> manager.addBalance(0.00)),
                () -> assertThrows(InvalidAmountException.class, () -> manager.addBalance(-500.00)));
        assertTrue(users.adjustments().isEmpty());
        assertEquals(50.00, users.getUserBalance(ALICE), EPS);
    }
}
