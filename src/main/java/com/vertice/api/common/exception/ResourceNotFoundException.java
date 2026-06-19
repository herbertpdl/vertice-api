package com.vertice.api.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super("%s with id %d not found".formatted(resource, id));
    }
}
