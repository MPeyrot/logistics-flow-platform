package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de sortie : le domaine ne connaît pas la base de données. */
public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    List<Shipment> findAll();

    Optional<Shipment> findByReference(String reference);

    /**
     * Comme {@link #findByReference}, en verrouillant l'expédition jusqu'à la fin de la transaction en cours :
     * un scan et une détection d'anomalies sur la même expédition passent l'un après l'autre.
     */
    Optional<Shipment> lockByReference(String reference);

    /** Verrouille l'expédition jusqu'à la fin de la transaction en cours (voir {@link #lockByReference}). */
    Optional<Shipment> lockById(UUID id);

    /**
     * Identifiants des expéditions non livrées qui ont une anomalie à ouvrir : échéance {@code expectedDeliveryAt}
     * dépassée à {@code now} sans LATE ouverte, ou dernier scan reçu (sinon création) au plus tard à
     * {@code stalledBefore} sans STALLED ouverte. Le filtre est fait par la base ; {@link
     * fr.michael.logistics.shipment.domain.AnomalyRules#detect} reste la règle une fois l'expédition verrouillée.
     */
    List<UUID> findAnomalyCandidateIds(Instant now, Instant stalledBefore);

    boolean existsByReference(String reference);
}
