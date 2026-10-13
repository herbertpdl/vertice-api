package com.vertice.api.user;

/** Length limits for any password a user sets. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** bcrypt only reads the first 72 bytes of a password, so longer ones aren't accepted. */
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }
}
