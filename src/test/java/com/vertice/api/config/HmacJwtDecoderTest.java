package com.vertice.api.config;

import com.vertice.api.grpc.TestJwts;
import com.vertice.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacJwtDecoderTest {

    private static final String SECRET = "a-secret-that-is-at-least-32-bytes-long";

    private final HmacJwtDecoder decoder = new HmacJwtDecoder(SECRET);

    @Test
    void decode_validHs256Token_returnsClaims() {
        Jwt jwt = decoder.decode(TestJwts.token(SECRET, 42L, Role.TRAINER));

        assertThat(jwt.<Number>getClaim("id").longValue()).isEqualTo(42L);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TRAINER");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void decode_wrongSecret_throwsBadJwt() {
        String token = TestJwts.token("another-secret-that-is-also-32-bytes-long", 42L, Role.TRAINER);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void decode_algNone_throwsBadJwt() {
        String signed = TestJwts.sign(SECRET, "{\"alg\":\"none\"}", "{\"id\":42,\"role\":\"TRAINER\"}");
        String unsecured = signed.substring(0, signed.lastIndexOf('.') + 1);

        assertThatThrownBy(() -> decoder.decode(unsecured)).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(signed)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void decode_expiredToken_throwsBadJwt() {
        long oneHourAgo = Instant.now().minusSeconds(3600).getEpochSecond();
        String token = TestJwts.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"id\":42,\"role\":\"TRAINER\",\"exp\":%d}".formatted(oneHourAgo));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void decode_tokenWithoutExp_isAccepted() {
        Jwt jwt = decoder.decode(TestJwts.token(SECRET, 7L, Role.CLIENT));

        assertThat(jwt.getExpiresAt()).isNull();
    }

    @Test
    void decode_tokenWithFutureExp_exposesInstant() {
        long inOneHour = Instant.now().plusSeconds(3600).getEpochSecond();
        Jwt jwt = decoder.decode(TestJwts.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"id\":42,\"role\":\"TRAINER\",\"exp\":%d}".formatted(inOneHour)));

        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.ofEpochSecond(inOneHour));
    }

    @Test
    void decode_shortSecret_stillVerifies() {
        HmacJwtDecoder shortSecretDecoder = new HmacJwtDecoder(TestJwts.LOCAL_SECRET);

        Jwt jwt = shortSecretDecoder.decode(TestJwts.token(1L, Role.ADMIN));

        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void decode_malformedToken_throwsBadJwt() {
        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(BadJwtException.class);
    }
}
