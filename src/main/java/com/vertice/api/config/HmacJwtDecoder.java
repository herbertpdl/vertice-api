package com.vertice.api.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.Map;

/**
 * Verifies the BFF-minted HS256 JWT with the shared {@code JWT_SECRET}.
 *
 * <p>Exists instead of {@code NimbusJwtDecoder.withSecretKey(...)} because Nimbus refuses HMAC
 * secrets shorter than 256 bits, while the local defaults on both sides are shorter (see
 * docs/specs/exercise-starter-catalog/spec.md §0 D1). {@link JwtSecretGuard} makes sure a short
 * secret never reaches a non-local deployment. Nimbus is still used to parse the compact form;
 * only the signature check is done here.
 */
public class HmacJwtDecoder implements JwtDecoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final MappedJwtClaimSetConverter claimSetConverter = MappedJwtClaimSetConverter.withDefaults(Map.of());
    private final OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefault();

    public HmacJwtDecoder(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        SignedJWT signedJwt = parse(token);
        if (!JWSAlgorithm.HS256.equals(signedJwt.getHeader().getAlgorithm())) {
            throw new BadJwtException("Unsupported JWT algorithm; only HS256 is accepted");
        }
        if (!MessageDigest.isEqual(sign(signedJwt.getSigningInput()), signedJwt.getSignature().decode())) {
            throw new BadJwtException("Invalid JWT signature");
        }

        Jwt jwt = Jwt.withTokenValue(token)
                .headers(headers -> headers.putAll(signedJwt.getHeader().toJSONObject()))
                .claims(claims -> claims.putAll(claimSetConverter.convert(claims(signedJwt))))
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        if (result.hasErrors()) {
            throw new JwtValidationException("Invalid JWT", result.getErrors());
        }
        return jwt;
    }

    private static SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException ex) {
            throw new BadJwtException("Malformed JWT", ex);
        }
    }

    private static Map<String, Object> claims(SignedJWT signedJwt) {
        try {
            return signedJwt.getJWTClaimsSet().getClaims();
        } catch (ParseException ex) {
            throw new BadJwtException("Malformed JWT claims", ex);
        }
    }

    private byte[] sign(byte[] signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(signingInput);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }
}
