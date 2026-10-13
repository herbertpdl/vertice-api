package com.vertice.api.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Outside {@code local} the application refuses to boot on a JWT secret anyone could know or
 * brute-force: blank, whitespace-padded, below the 256-bit HS256 floor, or one of the publicly
 * known dev/placeholder values. Mirrors vertice-bff's {@code src/config/env.ts}; keep the two in
 * sync. Under {@code local} the shared dev default ({@link #DEV_SECRET}) is allowed so the API
 * verifies tokens minted by a BFF running on its own default. Run by {@link BootGuards} before any
 * bean exists.
 */
public class JwtSecretGuard {

    static final int MIN_SECRET_BYTES = 32;

    /** Shared local-dev secret: the default here, in vertice-bff and in vertice-local. */
    static final String DEV_SECRET = "vertice-dev-jwt-secret-do-not-use-in-production";

    /** Publicly known values (current and past defaults/examples), refused at any length. */
    static final List<String> PLACEHOLDER_SECRETS =
            List.of(DEV_SECRET, "dev-secret-change-me", "change-me-in-production");

    private final String secret;

    public JwtSecretGuard(String secret) {
        this.secret = secret;
    }

    // Messages never echo the secret itself.
    void checkSecret() {
        if (secret == null || jsTrim(secret).isEmpty()) {
            throw new IllegalStateException("JWT_SECRET is required outside the local profile");
        }
        if (!secret.equals(jsTrim(secret))) {
            throw new IllegalStateException("JWT_SECRET must not have leading or trailing whitespace");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        if (isPlaceholder(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is a publicly known placeholder/dev value; set a random secret");
        }
    }

    private static boolean isPlaceholder(String secret) {
        String normalized = jsTrim(secret).toLowerCase(Locale.ROOT);
        return PLACEHOLDER_SECRETS.stream().anyMatch(p -> p.toLowerCase(Locale.ROOT).equals(normalized));
    }

    /**
     * Trims exactly what JavaScript's {@code String.prototype.trim()} does, so this guard and the
     * BFF agree on blank/padded secrets: TAB, LF, VT, FF, CR, every Unicode space/line/paragraph
     * separator (Zs, Zl, Zp, e.g. NBSP) and the BOM (U+FEFF). Java's {@code strip()} differs: it
     * misses NBSP-like spaces and the BOM but strips U+001C–U+001F.
     */
    static String jsTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isJsWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isJsWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isJsWhitespace(char c) {
        return (c >= '\t' && c <= '\r') || Character.isSpaceChar(c) || c == '\uFEFF';
    }
}
