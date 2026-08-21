package com.vertice.api.common.exception;

public class DuplicateCpfException extends RuntimeException {

    public DuplicateCpfException(String cpf) {
        super("CPF already in use: %s".formatted(cpf));
    }
}
