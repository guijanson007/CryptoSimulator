package org.cryptoBros.persistence.Exceptions;

/**
 * Thrown when a monetary amount is not valid for the requested operation, such as a non-positive deposit.
 */
public class InvalidAmountException extends Exception {
    /**
     * Creates a new InvalidAmountException with the given detail message.
     *
     * @param message the detail message
     */
    public InvalidAmountException(String message) {
        super(message);
    }
}
