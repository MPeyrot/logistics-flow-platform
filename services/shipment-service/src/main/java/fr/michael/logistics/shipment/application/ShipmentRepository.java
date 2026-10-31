package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Shipment;
import java.util.List;

/** Port de sortie : le domaine ne connaît pas la base de données. */
public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    List<Shipment> findAll();

    boolean existsByReference(String reference);
}
