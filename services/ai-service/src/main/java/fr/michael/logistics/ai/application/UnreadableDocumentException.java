package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.TokenUsage;

/**
 * Le document ne contient pas de bon de livraison exploitable (ou la réponse du modèle est invalide).
 * Les jetons ont quand même été facturés : on les garde pour tracer le coût.
 */
public class UnreadableDocumentException extends RuntimeException {

    private final transient TokenUsage usage;

    public UnreadableDocumentException(String message, TokenUsage usage) {
        super(message);
        this.usage = usage;
    }

    public UnreadableDocumentException(String message, TokenUsage usage, Throwable cause) {
        super(message, cause);
        this.usage = usage;
    }

    /** Jetons consommés par la requête, ou {@code null} si inconnus. */
    public TokenUsage usage() {
        return usage;
    }
}
