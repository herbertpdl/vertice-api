package com.vertice.api.common.exception;

/**
 * An RPC that needs to know who is calling was reached without a caller identity, or with a token
 * whose subject or role isn't one this API knows, or for a user who was since deleted or given
 * another role.
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Caller identity required");
    }

    private UnauthenticatedException(String message) {
        super(message);
    }

    public static UnauthenticatedException unrecognized() {
        return new UnauthenticatedException("Unrecognized caller identity");
    }

    /** The token's user no longer exists or no longer has the token's role. */
    public static UnauthenticatedException sessionNoLongerValid() {
        return new UnauthenticatedException("Session no longer valid");
    }
}
