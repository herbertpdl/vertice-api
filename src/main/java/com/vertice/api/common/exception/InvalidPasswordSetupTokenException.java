package com.vertice.api.common.exception;

/** A password setup token is unknown, expired or already used — deliberately not told apart. */
public class InvalidPasswordSetupTokenException extends RuntimeException {

    public InvalidPasswordSetupTokenException() {
        super("Invalid or expired password setup token");
    }
}
