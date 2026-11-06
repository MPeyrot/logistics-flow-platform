package fr.michael.logistics.shipment.application;

import org.springframework.stereotype.Service;

@Service
public class ReadDeliveryNoteService {

    private final DeliveryNoteReader reader;
    private final ShipmentRepository shipments;
    private final AnomalyRepository anomalies;
    private final ExtractionRateLimiter rateLimiter;

    public ReadDeliveryNoteService(DeliveryNoteReader reader, ShipmentRepository shipments,
            AnomalyRepository anomalies, ExtractionRateLimiter rateLimiter) {
        this.reader = reader;
        this.shipments = shipments;
        this.anomalies = anomalies;
        this.rateLimiter = rateLimiter;
    }

    /** Pas de transaction : l'appel au service d'extraction peut durer, la base n'est lue qu'ensuite. */
    public DeliveryNoteMatch read(String user, byte[] content, String filename) {
        rateLimiter.acquire(user);
        DeliveryNoteReading reading;
        try {
            reading = reader.read(content, filename);
        } catch (UnsupportedDocumentException | ExtractionRateLimitedException | ExtractionServiceUnreachableException e) {
            // 415 et 429 d'ai-service, ou connexion impossible : aucun appel Claude facturé. 422, autres 5xx et
            // expiration de lecture restent comptés (un appel a pu avoir lieu).
            rateLimiter.release(user);
            throw e;
        }
        return new DeliveryNoteMatch(reading, shipments.findByReference(reading.note().reference())
                .map(shipment -> new ShipmentWithAnomalies(shipment, anomalies.findOpenByShipmentId(shipment.id()))));
    }
}
