package fr.michael.logistics.ai.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Données extraites d'un bon de livraison. Seule la référence est obligatoire. */
public record DeliveryNote(
        String reference,
        LocalDate deliveryDate,
        String senderName,
        String recipientName,
        String recipientAddress,
        Integer packageCount,
        BigDecimal totalWeightKg) {

    public DeliveryNote {
        Objects.requireNonNull(reference, "reference");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
    }
}
