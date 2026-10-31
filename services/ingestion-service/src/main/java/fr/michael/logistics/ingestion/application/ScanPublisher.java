package fr.michael.logistics.ingestion.application;

import fr.michael.logistics.ingestion.domain.ScanEvent;

/** Port de sortie : le cas d'usage ignore que le transport est Kafka. */
public interface ScanPublisher {

    /** Retourne quand le transport a accusé réception, sinon lève {@link ScanPublicationException}. */
    void publish(ScanEvent event);
}
