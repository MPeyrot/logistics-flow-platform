package fr.michael.logistics.ai.infrastructure.security;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Simule shipment-service : une clé de test, son JWKS servi par WireMock, et des jetons signés. */
public final class TestJwt {

    private static final RSAKey KEY = generate("test-key");
    private static final RSAKey OTHER_KEY = generate("test-key");
    private static final WireMockServer SHIPMENT_SERVICE = start();

    private TestJwt() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("shipment-service.url", SHIPMENT_SERVICE::baseUrl);
    }

    /** Jeton de service valide (shipment-service vers ai-service) ; à personnaliser avec {@link Builder}. */
    public static Builder service() {
        return new Builder();
    }

    public static final class Builder {
        private String issuer = "shipment-service";
        private String audience = "ai-service";
        private String subject = "shipment-service";
        private Instant expiresAt = Instant.now().plusSeconds(3600);
        private RSAKey signingKey = KEY;

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder signedWithOtherKey() {
            this.signingKey = OTHER_KEY;
            return this;
        }

        public String build() {
            try {
                JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                        .issuer(issuer).subject(subject).audience(audience)
                        .issueTime(Date.from(Instant.now().minusSeconds(10)))
                        .expirationTime(Date.from(expiresAt));
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID())
                                .type(JOSEObjectType.JWT).build(),
                        claims.build());
                jwt.sign(new RSASSASigner(signingKey));
                return jwt.serialize();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(get(urlEqualTo("/.well-known/jwks.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JWKSet(KEY.toPublicJWK()).toString())));
        return server;
    }
}
