package fr.michael.logistics.shipment.application;

import java.time.Instant;
import java.util.List;

/**
 * Contenu d'un jeton d'accès. {@code audience} liste les services visés (claim {@code aud}) ; {@code site} n'est
 * renseigné que pour un SCANNER.
 */
public record TokenClaims(
        String issuer,
        String subject,
        List<String> audience,
        List<String> roles,
        String site,
        Instant issuedAt,
        Instant expiresAt) {

    public TokenClaims {
        audience = List.copyOf(audience);
    }
}
