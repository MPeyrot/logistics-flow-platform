package fr.michael.logistics.ai.infrastructure.claude;

import fr.michael.logistics.ai.domain.DeliveryNote;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Forme brute de la réponse du modèle. C'est une donnée externe : elle est validée (Bean Validation)
 * avant d'être convertie en {@link DeliveryNote} et de passer à la couche application.
 */
record DeliveryNoteDraft(
        @NotBlank @Size(max = 50) String reference,
        LocalDate deliveryDate,
        @Size(max = 200) String senderName,
        @Size(max = 200) String recipientName,
        @Size(max = 500) String recipientAddress,
        @Min(1) @Max(100_000) Integer packageCount,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") BigDecimal totalWeightKg) {

    DeliveryNote toDomain() {
        return new DeliveryNote(reference.strip(), deliveryDate, blankToNull(senderName),
                blankToNull(recipientName), blankToNull(recipientAddress), packageCount, totalWeightKg);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
