package org.cryptoBros.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sign-up validation and password hashing (risk B). No database or config file is touched:
 * the constructor only creates a ConfigJson, which reads the file lazily.
 */
class CredentialManagerTest {

    private static final String VALID_EMAIL = "alice@example.com";
    private static final String VALID_PASSWORD = "Abcdef1!";

    private final CredentialManager credentials = new CredentialManager();

    private String check(String email, String password) {
        return credentials.checkCredentials(email, password.toCharArray(), password.toCharArray());
    }

    @Test
    @DisplayName("#50 a valid email and matching strong password are accepted")
    void validCredentials_returnOk() {
        assertEquals("ok", check(VALID_EMAIL, VALID_PASSWORD));
    }

    @Test
    @DisplayName("#51 mismatched password confirmation is reported")
    void mismatchedPasswords_reported() {
        String result = credentials.checkCredentials(VALID_EMAIL, "Abcdef1!".toCharArray(), "Abcdef1?".toCharArray());

        assertTrue(result.contains("The password doesn't match"), result);
    }

    @ParameterizedTest(name = "#52 accepted: \"{0}\"")
    @ValueSource(strings = {
            "Abc12!",                  // exactly 6 characters
            "Abcdefghijklmnop12!x"     // exactly 20 characters
    })
    void passwordRules_accepted(String password) {
        assertEquals("ok", check(VALID_EMAIL, password));
    }

    @ParameterizedTest(name = "#52 rejected: \"{0}\"")
    @ValueSource(strings = {
            "Ab1!c",                   // 5 characters
            "Abcdefghijklmnopq12!x",   // 21 characters
            "abcdef1!",                // no uppercase
            "ABCDEF1!",                // no lowercase
            "Abcdefg!",                // no digit
            "Abcdefg1",                // no special character
            "Abc def1!"                // contains a space
    })
    void passwordRules_rejected(String password) {
        String result = check(VALID_EMAIL, password);

        assertTrue(result.contains("Password must be"), result);
    }

    @ParameterizedTest(name = "#53 valid email: \"{0}\"")
    @ValueSource(strings = {"plain@example.com", "a+b@x.io"})
    void emailFormat_accepted(String email) {
        assertEquals("ok", check(email, VALID_PASSWORD));
    }

    @ParameterizedTest(name = "#53 invalid email: \"{0}\"")
    @ValueSource(strings = {".lead@example.com", "user@example", "a..b@x.io", "no-at-sign.com"})
    void emailFormat_rejected(String email) {
        String result = check(email, VALID_PASSWORD);

        assertTrue(result.contains("Error email format"), result);
    }

    @Test
    @DisplayName("#54 a hashed password verifies against itself")
    void hash_thenCheck_matches() {
        String hash = credentials.hashPassword(VALID_PASSWORD.toCharArray());

        assertTrue(credentials.checkHashedPassword(VALID_PASSWORD.toCharArray(), hash));
    }

    @Test
    @DisplayName("#54 a wrong password does not verify")
    void wrongPassword_doesNotMatch() {
        String hash = credentials.hashPassword(VALID_PASSWORD.toCharArray());

        assertFalse(credentials.checkHashedPassword("Abcdef1?".toCharArray(), hash));
    }

    @Test
    @DisplayName("#54 hashing the same password twice gives different hashes (salted)")
    void hashIsSalted() {
        assertNotEquals(credentials.hashPassword(VALID_PASSWORD.toCharArray()),
                credentials.hashPassword(VALID_PASSWORD.toCharArray()));
    }
}
