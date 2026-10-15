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

    /**
     * Whether a normalized email is on the {@code .invalid} TLD (RFC 2606), which can never receive
     * mail. Refused on every write as defence in depth: nothing legitimate uses it, and it is the
     * obvious shape for a "deleted account" placeholder.
     */
    public static boolean isReserved(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1);
        return domain.equals("invalid") || domain.endsWith(".invalid");
    }
}
