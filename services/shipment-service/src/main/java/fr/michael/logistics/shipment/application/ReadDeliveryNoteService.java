package fr.michael.logistics.shipment.application;

import org.springframework.stereotype.Service;

@Service
public class ReadDeliveryNoteService {

    private final DeliveryNoteReader reader;
    private final ShipmentRepository shipments;

    public ReadDeliveryNoteService(DeliveryNoteReader reader, ShipmentRepository shipments) {
        this.reader = reader;
        this.shipments = shipments;
    }

    /** Pas de transaction : l'appel au service d'extraction peut durer, la base n'est lue qu'ensuite. */
    public DeliveryNoteMatch read(byte[] content, String filename) {
        DeliveryNoteReading reading = reader.read(content, filename);
        return new DeliveryNoteMatch(reading, shipments.findByReference(reading.note().reference()));
    }
}
