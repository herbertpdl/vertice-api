package com.vertice.api.common.exception;

/**
 * A fixed message that never contains the CPF (PRIV-BFF-002): the error reaches public
 * registration, and echoing the value would confirm which CPFs are registered.
 */
public class DuplicateCpfException extends RuntimeException {

    public static final String MESSAGE = "CPF already in use";

    public DuplicateCpfException() {
        super(MESSAGE);
    }
}
