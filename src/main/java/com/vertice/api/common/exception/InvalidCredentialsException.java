package com.vertice.api.common.exception;

/**
 * An email/password pair didn't check out. One fixed message for a wrong password, an unknown email
 * and a user with no password, so the response never reveals whether an account exists.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
