package com.vertice.api.user;

import java.util.Locale;

/**
 * Emails are stored and compared trimmed and lower-cased, so {@code Ana@x.com} and
 * {@code ana@x.com} are one account (V25 backs this with a unique index on {@code lower(email)}).
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
