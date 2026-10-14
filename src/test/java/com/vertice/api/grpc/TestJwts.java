package com.vertice.api.grpc;

import com.vertice.api.config.HmacJwtDecoder;
import com.vertice.api.user.Role;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

/**
 * Mints HS256 tokens shaped like the BFF's for tests: scoped ({@code iss}, {@code aud}, {@code exp}),
 * user tokens with {@code sub}, the legacy {@code id} and {@code role}, service tokens with
 * {@code sub: "service:…"} and {@code role: "SERVICE"} (vertice-bff {@code src/lib/jwt.ts}). Signs by
 * hand with {@link Mac} because Nimbus' own signer refuses secrets under 32 bytes, which some tests
 * use on purpose — the same reason {@code HmacJwtDecoder} exists.
 */
public final class TestJwts {

    /** The {@code vertice.jwt.secret} default every {@code local}-profile test context runs with. */
    public static final String LOCAL_SECRET = "vertice-dev-jwt-secret-do-not-use-in-production";

    private static final String HS256_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private TestJwts() {
    }

    public static String token(long userId, Role role) {
        return token(LOCAL_SECRET, userId, role);
    }

    public static String token(String secret, long userId, Role role) {
        return sign(secret, "{\"sub\":\"%d\",\"id\":%d,\"role\":\"%s\",%s}"
                .formatted(userId, userId, role.name(), scopeClaims()));
    }

    public static String serviceToken(ServiceSubject subject) {
        return serviceToken(LOCAL_SECRET, subject);
    }

    public static String serviceToken(String secret, ServiceSubject subject) {
        return sign(secret, "{\"sub\":\"%s\",\"role\":\"SERVICE\",%s}".formatted(subject.subject(), scopeClaims()));
    }

    /** {@code "iss":…,"aud":…,"iat":…,"exp":…} for a token valid for the next hour, to splice into a payload. */
    public static String scopeClaims() {
        long now = Instant.now().getEpochSecond();
        return "\"iss\":\"%s\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d"
                .formatted(HmacJwtDecoder.ISSUER, HmacJwtDecoder.AUDIENCE, now, now + 3600);
    }

    /** Signs {@code payloadJson} under the standard HS256 header. */
    public static String sign(String secret, String payloadJson) {
        return sign(secret, HS256_HEADER, payloadJson);
    }

    public static String sign(String secret, String headerJson, String payloadJson) {
        String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(hmacSha256(secret, signingInput));
    }

    /** Returns {@code stub} sending {@code authorization: Bearer <token>} on every call. */
    public static <S extends AbstractStub<S>> S withBearer(S stub, String token) {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION, "Bearer " + token);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    public static <S extends AbstractStub<S>> S asCaller(S stub, long userId, Role role) {
        return withBearer(stub, token(userId, role));
    }

    public static <S extends AbstractStub<S>> S asService(S stub, ServiceSubject subject) {
        return withBearer(stub, serviceToken(subject));
    }

    private static byte[] hmacSha256(String secret, String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
