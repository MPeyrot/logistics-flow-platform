package fr.michael.logistics.shipment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import fr.michael.logistics.shipment.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Démarrage refusé, avec un message clair, quand JWT_PRIVATE_KEY manque ou n'est pas une clé RSA. */
class SigningKeyConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(SigningKeyConfig.class);

    @Test
    void startupFailsWithoutPrivateKey() {
        context.run(started -> {
            assertThat(started).hasFailed();
            assertThat(started.getStartupFailure()).rootCause()
                    .hasMessageContaining("JWT_PRIVATE_KEY is not set")
                    .hasMessageContaining("scripts/generate-jwt-key.sh");
        });
    }

    @Test
    void startupFailsWithMalformedPrivateKey() {
        context.withPropertyValues("jwt.private-key=-----BEGIN PRIVATE KEY-----garbage-----END PRIVATE KEY-----")
                .run(started -> {
                    assertThat(started).hasFailed();
                    assertThat(started.getStartupFailure()).rootCause()
                            .hasMessageContaining("JWT_PRIVATE_KEY is not a valid RSA private key");
                });
    }

    @Test
    void pemKeyGivesSigningKeyWithKid() {
        context.withPropertyValues("jwt.private-key=" + TestSecurityConfiguration.generatePrivateKeyPem())
                .run(started -> {
                    RSAKey key = started.getBean(RSAKey.class);
                    assertThat(key.isPrivate()).isTrue();
                    assertThat(key.getKeyID()).isNotBlank();
                });
    }
}
