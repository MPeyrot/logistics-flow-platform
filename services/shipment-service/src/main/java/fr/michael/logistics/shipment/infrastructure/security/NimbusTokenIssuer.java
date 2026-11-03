package fr.michael.logistics.shipment.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import fr.michael.logistics.shipment.application.TokenClaims;
import fr.michael.logistics.shipment.application.TokenIssuer;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** Signe les jetons en RS256 avec la clé de SigningKeyConfig. */
@Component
class NimbusTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final String keyId;

    NimbusTokenIssuer(RSAKey signingKey) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        this.keyId = signingKey.getKeyID();
    }

    @Override
    public String sign(TokenClaims claims) {
        JwtClaimsSet.Builder set = JwtClaimsSet.builder()
                .issuer(claims.issuer())
                .subject(claims.subject())
                .audience(List.of(claims.audience()))
                .issuedAt(claims.issuedAt())
                .expiresAt(claims.expiresAt())
                .claim("roles", claims.roles());
        if (claims.site() != null) {
            set.claim("site", claims.site());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, set.build())).getTokenValue();
    }
}
