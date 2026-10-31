package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.DeliveryNote;
import fr.michael.logistics.ai.domain.TokenUsage;

/** Ce que renvoie le port : les données validées et les jetons consommés pour les obtenir. */
public record ModelExtraction(DeliveryNote note, TokenUsage usage) {
}
