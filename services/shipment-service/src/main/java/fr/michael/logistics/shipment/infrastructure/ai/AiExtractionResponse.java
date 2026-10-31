package fr.michael.logistics.shipment.infrastructure.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Réponse de l'ai-service : donnée externe, validée avant d'entrer dans l'application. */
record AiExtractionResponse(@NotNull @Valid Note deliveryNote, @NotNull @PositiveOrZero BigDecimal costUsd) {

    record Note(
            @NotBlank String reference,
            LocalDate deliveryDate,
            String senderName,
            String recipientName,
            String recipientAddress,
            Integer packageCount,
            BigDecimal totalWeightKg) {
    }
}
