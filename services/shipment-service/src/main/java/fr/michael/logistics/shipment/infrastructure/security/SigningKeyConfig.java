package fr.michael.logistics.shipment.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clé RSA de signature des jetons, lue dans JWT_PRIVATE_KEY (PEM PKCS#8). Obligatoire : sans elle, le
 * service refuse de démarrer. La clé publique en est dérivée et son kid est l'empreinte RFC 7638.
 */
@Configuration
class SigningKeyConfig {

    static final String MISSING_KEY = "JWT_PRIVATE_KEY is not set: shipment-service needs an RSA private key "
            + "in PEM (PKCS#8) to sign tokens. Locally, run scripts/generate-jwt-key.sh then source scripts/env.sh.";

    @Bean
    RSAKey signingKey(@Value("${jwt.private-key:}") String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException(MISSING_KEY);
        }
        return fromPem(pem);
    }

    static RSAKey fromPem(String pem) {
        try {
            String base64 = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            RSAKey key = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException | JOSEException e) {
            // Pas le contenu de la clé dans le message : seulement ce qui est attendu.
            throw new IllegalStateException("JWT_PRIVATE_KEY is not a valid RSA private key in PEM (PKCS#8, "
                    + "\"-----BEGIN PRIVATE KEY-----\"). Generate one with scripts/generate-jwt-key.sh.");
        }
    }
}
