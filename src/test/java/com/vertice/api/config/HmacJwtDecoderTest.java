package com.vertice.api.config;

import com.vertice.api.grpc.ServiceSubject;
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
    private static final long IN_ONE_HOUR = Instant.now().plusSeconds(3600).getEpochSecond();
    private static final String USER = "\"sub\":\"42\",\"id\":42,\"role\":\"TRAINER\"";
    private static final String ISS = "\"iss\":\"vertice-bff\"";
    private static final String AUD = "\"aud\":\"vertice-api\"";
    private static final String EXP = "\"exp\":" + IN_ONE_HOUR;

    private final HmacJwtDecoder decoder = new HmacJwtDecoder(SECRET);

    @Test
    void decode_scopedUserToken_returnsClaims() {
        Jwt jwt = decoder.decode(TestJwts.token(SECRET, 42L, Role.TRAINER));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.<Number>getClaim("id").longValue()).isEqualTo(42L);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TRAINER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("vertice-bff");
        assertThat(jwt.getAudience()).containsExactly("vertice-api");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void decode_serviceToken_isAccepted() {
        Jwt jwt = decoder.decode(TestJwts.serviceToken(SECRET, ServiceSubject.BFF));

        assertThat(jwt.getSubject()).isEqualTo("service:bff");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("SERVICE");
    }

    @Test
    void decode_audienceArrayContainingThisApi_isAccepted() {
        Jwt jwt = decoder.decode(sign(USER, ISS, "\"aud\":[\"something-else\",\"vertice-api\"]", EXP));

        assertThat(jwt.getAudience()).contains("vertice-api");
    }

    @Test
    void decode_missingIss_throwsBadJwt() {
        assertRejected(sign(USER, AUD, EXP));
    }

    @Test
    void decode_wrongIss_throwsBadJwt() {
        assertRejected(sign(USER, "\"iss\":\"someone-else\"", AUD, EXP));
    }

    @Test
    void decode_missingAud_throwsBadJwt() {
        assertRejected(sign(USER, ISS, EXP));
    }

    @Test
    void decode_wrongAud_throwsBadJwt() {
        assertRejected(sign(USER, ISS, "\"aud\":\"vertice-web\"", EXP));
    }

    @Test
    void decode_missingExp_throwsBadJwt() {
        assertRejected(sign(USER, ISS, AUD));
    }

    @Test
    void decode_expiredToken_throwsBadJwt() {
        long oneHourAgo = Instant.now().minusSeconds(3600).getEpochSecond();

        assertRejected(sign(USER, ISS, AUD, "\"exp\":" + oneHourAgo));
    }

    @Test
    void decode_tokenWithFutureExp_exposesInstant() {
        Jwt jwt = decoder.decode(sign(USER, ISS, AUD, EXP));

        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.ofEpochSecond(IN_ONE_HOUR));
    }

    @Test
    void decode_wrongSecret_throwsBadJwt() {
        assertRejected(TestJwts.token("another-secret-that-is-also-32-bytes-long", 42L, Role.TRAINER));
    }

    @Test
    void decode_algNone_throwsBadJwt() {
        String signed = TestJwts.sign(SECRET, "{\"alg\":\"none\"}", payload(USER, ISS, AUD, EXP));
        String unsecured = signed.substring(0, signed.lastIndexOf('.') + 1);

        assertRejected(unsecured);
        assertRejected(signed);
    }

    @Test
    void decode_otherHmacAlgorithm_throwsBadJwt() {
        assertRejected(TestJwts.sign(SECRET, "{\"alg\":\"HS384\",\"typ\":\"JWT\"}", payload(USER, ISS, AUD, EXP)));
    }

    @Test
    void decode_headerWithoutTyp_isAccepted() {
        Jwt jwt = decoder.decode(TestJwts.sign(SECRET, "{\"alg\":\"HS256\"}", payload(USER, ISS, AUD, EXP)));

        assertThat(jwt.getSubject()).isEqualTo("42");
    }

    @Test
    void decode_typOtherThanJwt_throwsBadJwt() {
        assertRejected(TestJwts.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"at+jwt\"}", payload(USER, ISS, AUD, EXP)));
        assertRejected(TestJwts.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"JOSE\"}", payload(USER, ISS, AUD, EXP)));
    }

    @Test
    void decode_lowercaseJwtTyp_isAccepted() {
        assertThat(decoder.decode(TestJwts.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"jwt\"}", payload(USER, ISS, AUD, EXP))))
                .isNotNull();
    }

    /** The default validator allows 60 s of clock skew between the BFF and the API, and no more. */
    @Test
    void decode_expiredWithinClockSkew_isAccepted_beyondItRejected() {
        long now = Instant.now().getEpochSecond();

        assertThat(decoder.decode(sign(USER, ISS, AUD, "\"exp\":" + (now - 30))).getSubject()).isEqualTo("42");
        assertRejected(sign(USER, ISS, AUD, "\"exp\":" + (now - 90)));
    }

    @Test
    void decode_notBeforeWithinClockSkew_isAccepted_beyondItRejected() {
        long now = Instant.now().getEpochSecond();

        assertThat(decoder.decode(sign(USER, ISS, AUD, EXP, "\"nbf\":" + (now + 30))).getSubject()).isEqualTo("42");
        assertRejected(sign(USER, ISS, AUD, EXP, "\"nbf\":" + (now + 90)));
    }

    @Test
    void decode_shortSecret_stillVerifies() {
        String shortSecret = "short-local-secret";
        HmacJwtDecoder shortSecretDecoder = new HmacJwtDecoder(shortSecret);

        Jwt jwt = shortSecretDecoder.decode(TestJwts.token(shortSecret, 1L, Role.ADMIN));

        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void decode_malformedToken_throwsBadJwt() {
        assertRejected("not-a-jwt");
    }

    private void assertRejected(String token) {
        // JwtValidationException (claims) and the signature/algorithm failures are all BadJwtException,
        // which the resource server turns into UNAUTHENTICATED.
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    private static String sign(String... claims) {
        return TestJwts.sign(SECRET, payload(claims));
    }

    private static String payload(String... claims) {
        return "{" + String.join(",", claims) + "}";
    }
}
