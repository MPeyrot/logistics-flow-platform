package fr.michael.logistics.shipment.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Un « tout rejouer » et son avancement. {@code replayed} : messages enregistrés comme rejoués et mis dans l'outbox
 * (leur publication est garantie) ; {@code published} : ceux d'entre eux déjà accusés par le broker ; {@code failed} :
 * messages dont l'enregistrement a échoué, encore à rejouer. {@code finishedAt} : fin du parcours du DLT ;
 * {@code error} : parcours interrompu (broker injoignable).
 */
public record ReplayAllJob(UUID id, String requestedBy, Instant requestedAt, Instant finishedAt, long replayed,
        long published, int failed, String error) {

    static ReplayAllJob started(UUID id, String requestedBy, Instant requestedAt) {
        return new ReplayAllJob(id, requestedBy, requestedAt, null, 0, 0, 0, null);
    }

    public boolean finished() {
        return finishedAt != null;
    }
}
