package com.vertice.api.common.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Email already in use: %s".formatted(email));
    }
}
