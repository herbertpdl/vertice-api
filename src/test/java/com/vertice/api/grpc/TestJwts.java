package com.vertice.api.grpc;

import com.vertice.api.user.Role;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Mints HS256 tokens shaped like the BFF's ({@code id}, {@code role} claims) for tests. Signs by
 * hand with {@link Mac} because Nimbus' own signer refuses the short local default secret — the
 * same reason {@code HmacJwtDecoder} exists.
 */
public final class TestJwts {

    /** The {@code vertice.jwt.secret} default every {@code local}-profile test context runs with. */
    public static final String LOCAL_SECRET = "dev-secret-change-me";

    private static final String HS256_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private TestJwts() {
    }

    public static String token(long userId, Role role) {
        return token(LOCAL_SECRET, userId, role);
    }

    public static String token(String secret, long userId, Role role) {
        return sign(secret, HS256_HEADER, "{\"id\":%d,\"role\":\"%s\"}".formatted(userId, role.name()));
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
