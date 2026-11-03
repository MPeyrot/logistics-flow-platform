package fr.michael.logistics.shipment.domain;

/** Rôle d'un compte. Un SCANNER est rattaché à un site, les autres non. */
public enum Role {
    ADMIN,
    OPERATOR,
    SCANNER
}
