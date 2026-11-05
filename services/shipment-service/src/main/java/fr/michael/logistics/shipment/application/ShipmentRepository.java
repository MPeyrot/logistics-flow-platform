package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Shipment;
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

    /** Expéditions non livrées, avec l'instant de leur dernier scan. */
    List<ShipmentActivity> findUndeliveredActivity();

    boolean existsByReference(String reference);
}
