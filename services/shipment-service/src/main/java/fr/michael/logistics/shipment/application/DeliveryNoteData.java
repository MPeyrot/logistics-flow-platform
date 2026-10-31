package fr.michael.logistics.shipment.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Données lues sur un bon de livraison par le service d'extraction. */
public record DeliveryNoteData(
        String reference,
        LocalDate deliveryDate,
        String senderName,
        String recipientName,
        String recipientAddress,
        Integer packageCount,
        BigDecimal totalWeightKg) {
}
