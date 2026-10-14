package com.vertice.api.common.exception;

/**
 * A fixed message that never contains the email (PRIV-BFF-002): the error reaches public
 * registration, and echoing the value would confirm which emails are registered.
 */
public class DuplicateEmailException extends RuntimeException {

    public static final String MESSAGE = "Email already in use";

    public DuplicateEmailException() {
        super(MESSAGE);
    }
}
