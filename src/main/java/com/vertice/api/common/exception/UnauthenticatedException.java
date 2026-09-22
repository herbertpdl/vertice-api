package com.vertice.api.common.exception;

/** An RPC that needs to know who is calling was reached without a resolvable caller identity. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Caller identity required");
    }
}
