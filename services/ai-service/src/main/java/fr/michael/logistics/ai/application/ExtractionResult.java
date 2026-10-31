package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.DeliveryNote;
import fr.michael.logistics.ai.domain.TokenUsage;
import java.math.BigDecimal;

public record ExtractionResult(DeliveryNote note, TokenUsage usage, BigDecimal costUsd) {
}
