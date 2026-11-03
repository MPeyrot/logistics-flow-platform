package fr.michael.logistics.shipment.application;

import org.springframework.stereotype.Service;

@Service
public class ReadDeliveryNoteService {

    private final DeliveryNoteReader reader;
    private final ShipmentRepository shipments;
    private final ExtractionRateLimiter rateLimiter;

    public ReadDeliveryNoteService(DeliveryNoteReader reader, ShipmentRepository shipments, ExtractionRateLimiter rateLimiter) {
        this.reader = reader;
        this.shipments = shipments;
        this.rateLimiter = rateLimiter;
    }

    /** Pas de transaction : l'appel au service d'extraction peut durer, la base n'est lue qu'ensuite. */
    public DeliveryNoteMatch read(String user, byte[] content, String filename) {
        rateLimiter.acquire(user);
        DeliveryNoteReading reading;
        try {
            reading = reader.read(content, filename);
        } catch (UnsupportedDocumentException | ExtractionRateLimitedException e) {
            // 415 et 429 d'ai-service : aucun appel Claude facturé. 422 et 502 restent comptés (un appel a pu avoir lieu).
            rateLimiter.release(user);
            throw e;
        }
        return new DeliveryNoteMatch(reading, shipments.findByReference(reading.note().reference()));
    }
}
