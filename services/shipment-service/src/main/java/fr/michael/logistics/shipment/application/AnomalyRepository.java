package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Port de sortie : anomalies des expéditions, au plus une ouverte par expédition et par type. */
public interface AnomalyRepository {

    /**
     * Enregistre l'anomalie si aucune du même type n'est ouverte pour cette expédition.
     * Une anomalie déjà ouverte, même par une détection concurrente, n'est pas une erreur.
     *
     * @return true si l'anomalie a été enregistrée, false si une autre était déjà ouverte
     */
    boolean openIfAbsent(Anomaly anomaly);

    /** Ferme l'anomalie ouverte de ce type, s'il y en a une. */
    void resolveOpen(UUID shipmentId, AnomalyType type, Instant resolvedAt);

    List<Anomaly> findOpen();

    /** Anomalies avec la référence de leur expédition, détectées en dernier d'abord. open : null = toutes. */
    List<AnomalyView> findViews(Boolean open);
}
