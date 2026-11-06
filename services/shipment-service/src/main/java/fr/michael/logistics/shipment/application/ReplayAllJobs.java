package fr.michael.logistics.shipment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port de sortie : les « tout rejouer » lancés, et leur avancement. */
public interface ReplayAllJobs {

    void create(UUID id, String requestedBy, Instant requestedAt);

    void recordFailure(UUID id);

    /** {@code error} : {@code null} si le parcours du DLT est allé au bout. */
    void finish(UUID id, Instant finishedAt, String error);

    /** Avec les comptes de l'outbox ({@code replayed}, {@code published}). */
    Optional<ReplayAllJob> find(UUID id);
}
