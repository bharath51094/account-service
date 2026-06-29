package com.ledger.accountservice.exception;

/**
 * Raised when a transaction's currency does not match the currency an account was created with.
 * One account is restricted to a single currency.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String message) {
        super(message);
    }
}
