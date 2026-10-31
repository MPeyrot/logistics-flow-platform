package fr.michael.logistics.ai.infrastructure.web;

import fr.michael.logistics.ai.application.ExtractionResult;
import fr.michael.logistics.ai.domain.DeliveryNote;
import fr.michael.logistics.ai.domain.TokenUsage;
import java.math.BigDecimal;
import java.time.LocalDate;

record DeliveryNoteResponse(DeliveryNoteBody deliveryNote, UsageBody usage, BigDecimal costUsd) {

    static DeliveryNoteResponse from(ExtractionResult result) {
        return new DeliveryNoteResponse(DeliveryNoteBody.from(result.note()), UsageBody.from(result.usage()),
                result.costUsd());
    }

    record DeliveryNoteBody(
            String reference,
            LocalDate deliveryDate,
            String senderName,
            String recipientName,
            String recipientAddress,
            Integer packageCount,
            BigDecimal totalWeightKg) {

        static DeliveryNoteBody from(DeliveryNote n) {
            return new DeliveryNoteBody(n.reference(), n.deliveryDate(), n.senderName(), n.recipientName(),
                    n.recipientAddress(), n.packageCount(), n.totalWeightKg());
        }
    }

    record UsageBody(long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens) {

        static UsageBody from(TokenUsage u) {
            return new UsageBody(u.inputTokens(), u.outputTokens(), u.cacheWriteTokens(), u.cacheReadTokens());
        }
    }
}
