package fr.michael.logistics.shipment.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** Port de sortie : rejeux déjà faits, un au plus par message du DLT. */
public interface DeadLetterReplays {

    /** Date de rejeu des messages déjà rejoués parmi {@code positions} ; les autres sont absents du résultat. */
    Map<DeadLetterPosition, Instant> replayedAt(Collection<DeadLetterPosition> positions);

    /**
     * Enregistre le rejeu, sauf s'il l'est déjà : {@code false} dans ce cas. Un rejeu concurrent du même message,
     * pas encore validé, fait attendre l'appel jusqu'à sa fin.
     */
    boolean record(DeadLetterPosition position, Instant replayedAt, String replayedBy);
}
