package org.cryptoBros.business;

import org.cryptoBros.IntegrationTestBase;
import org.cryptoBros.persistence.Exceptions.CredentialsErrorFormatException;
import org.cryptoBros.persistence.Exceptions.UserAlreadyExistsException;
import org.cryptoBros.persistence.SQL.AtomicSQL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sign-up and login against a real PostgreSQL (risk B). First in line to be cut under D20.
 */
class AccountIT extends IntegrationTestBase {

    private static final String PASSWORD = "Abcdef1!";

    private final AccountManager accounts = new AccountManager();

    private int signUp(String email, String username) throws Exception {
        return accounts.signUpLogic(email, PASSWORD.toCharArray(), PASSWORD.toCharArray(), username);
    }

    @Test
    @DisplayName("#55 signing up with a taken email or a taken username is rejected")
    void signUp_duplicateEmailOrUsername_rejected() throws Exception {
        signUp("alice@example.com", "alice");

        assertAll(
                () -> assertThrows(UserAlreadyExistsException.class, () -> signUp("alice@example.com", "alice2")),
                () -> assertThrows(UserAlreadyExistsException.class, () -> signUp("other@example.com", "alice")));
        assertEquals(1, queryLong("SELECT COUNT(*) FROM Users"));
    }

    @Test
    @DisplayName("#56 a user can log in with either username or email")
    void login_byUsernameAndByEmail() throws Exception {
        int id = signUp("alice@example.com", "alice");

        assertEquals(id, accounts.logInNormalUser("alice", PASSWORD.toCharArray()));
        assertEquals(id, accounts.logInNormalUser("alice@example.com", PASSWORD.toCharArray()));
    }

    @Test
    @DisplayName("#56 a wrong password is rejected")
    void login_wrongPassword_rejected() throws Exception {
        signUp("alice@example.com", "alice");

        assertThrows(CredentialsErrorFormatException.class,
                () -> accounts.logInNormalUser("alice", "Abcdef1?".toCharArray()));
    }

    @Test
    @DisplayName("#70 logging in with a bot's username is rejected like a wrong password (suspected bug #13)")
    void login_withBotUsername_rejected() throws Exception {
        new AtomicSQL().createCryptoWithBot(new Crypto("DOGE", "Dogecoin", 1.00, 1.00, 1.0));

        assertThrows(CredentialsErrorFormatException.class,
                () -> accounts.logInNormalUser("BOT_DOGE", PASSWORD.toCharArray()));
    }
}
