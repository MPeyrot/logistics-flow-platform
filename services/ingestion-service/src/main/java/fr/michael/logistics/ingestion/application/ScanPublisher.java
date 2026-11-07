package fr.michael.logistics.ingestion.application;

import fr.michael.logistics.ingestion.domain.ScanEvent;
import java.util.List;

/** Port de sortie : le cas d'usage ignore que le transport est Kafka. */
public interface ScanPublisher {

    /** Retourne quand le transport a accusé réception, sinon lève {@link ScanPublicationException}. */
    void publish(ScanEvent event);

    /**
     * Envoie tout le lot, dans l'ordre, puis attend tous les accusés. Lève {@link ScanPublicationException}
     * si l'un manque : une partie du lot peut alors avoir été publiée.
     */
    void publishAll(List<ScanEvent> events);
}
