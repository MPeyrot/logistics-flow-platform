package fr.michael.logistics.shipment.application;

/** Signe un jeton d'accès. Les claims sont décidés par l'application, l'adaptateur ne fait que signer. */
public interface TokenIssuer {

    String sign(TokenClaims claims);
}
