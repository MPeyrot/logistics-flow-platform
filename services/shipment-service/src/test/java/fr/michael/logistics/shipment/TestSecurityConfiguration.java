package fr.michael.logistics.shipment;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Clé de signature générée en mémoire (aucune clé dans le dépôt) et premier ADMIN amorcé,
 * pour que les tests obtiennent de vrais jetons par POST /api/auth/token.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    public static final String ADMIN_USERNAME = "test-admin";
    public static final String ADMIN_PASSWORD = "test-admin-password";

    private static final String PRIVATE_KEY_PEM = generatePrivateKeyPem();

    @Bean
    static DynamicPropertyRegistrar securityProperties() {
        return registry -> {
            registry.add("jwt.private-key", () -> PRIVATE_KEY_PEM);
            registry.add("bootstrap.admin.username", () -> ADMIN_USERNAME);
            registry.add("bootstrap.admin.password", () -> ADMIN_PASSWORD);
        };
    }

    public static String generatePrivateKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            byte[] pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs8);
            return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
