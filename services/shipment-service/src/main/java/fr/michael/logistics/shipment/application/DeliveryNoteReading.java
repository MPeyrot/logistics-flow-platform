package fr.michael.logistics.shipment.application;

import java.math.BigDecimal;

/** Bon de livraison lu, avec le coût en dollars de la lecture. */
public record DeliveryNoteReading(DeliveryNoteData note, BigDecimal costUsd) {
}
