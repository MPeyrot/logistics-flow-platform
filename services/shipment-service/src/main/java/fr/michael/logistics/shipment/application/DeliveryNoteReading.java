package fr.michael.logistics.shipment.application;

import java.math.BigDecimal;

/** Bon de livraison lu, avec le coût en dollars de la lecture et les jetons qu'elle a consommés. */
public record DeliveryNoteReading(DeliveryNoteData note, BigDecimal costUsd, TokenUsage usage) {
}
