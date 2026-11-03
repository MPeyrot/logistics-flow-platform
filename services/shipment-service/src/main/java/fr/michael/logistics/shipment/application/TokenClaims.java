package fr.michael.logistics.shipment.application;

import java.time.Instant;
import java.util.List;

/** Contenu d'un jeton d'accès. {@code site} n'est renseigné que pour un SCANNER. */
public record TokenClaims(
        String issuer,
        String subject,
        String audience,
        List<String> roles,
        String site,
        Instant issuedAt,
        Instant expiresAt) {
}
