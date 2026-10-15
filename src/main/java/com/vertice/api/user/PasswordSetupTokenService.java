package com.vertice.api.user;

import com.vertice.api.common.exception.InvalidPasswordSetupTokenException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.time.DbInstants;
import com.vertice.api.generated.grpc.user.v1.CreatePasswordSetupTokenResponse;
import com.vertice.api.grpc.ProtoInstants;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * First-access passwords: a client created by a trainer has no password, so the BFF issues them a
 * setup token and they choose one. Tokens are 32 random bytes; only their SHA-256 is stored (no salt
 * or slow hash needed at that entropy), so a database leak doesn't hand out usable tokens.
 */
@Service
@RequiredArgsConstructor
@Transactional
@EnableConfigurationProperties(PasswordSetupProperties.class)
public class PasswordSetupTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordSetupTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordSetupProperties properties;

    /**
     * Replaces any unused token the user already has, so only the newest link works. A deleted
     * account (P1-05) is NOT_FOUND: it has no password, but must never get one back.
     */
    public CreatePasswordSetupTokenResponse createToken(Long userId) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        tokenRepository.deleteUnusedByUserId(userId);

        String rawToken = newRawToken();
        Instant now = DbInstants.now();
        PasswordSetupToken token = new PasswordSetupToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(properties.tokenTtl()));
        tokenRepository.save(token);

        return CreatePasswordSetupTokenResponse.newBuilder()
                .setToken(rawToken)
                .setExpiresAt(ProtoInstants.instantToString(token.getExpiresAt()))
                .build();
    }

    /** Consuming the token and setting the password share this transaction: both happen or neither. */
    public void setPasswordWithToken(String rawToken, String rawPassword) {
        String tokenHash = sha256Hex(rawToken);
        if (tokenRepository.markUsed(tokenHash, DbInstants.now()) == 0) {
            throw new InvalidPasswordSetupTokenException();
        }
        User user = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidPasswordSetupTokenException::new)
                .getUser();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    private static String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every JVM", e);
        }
    }
}
