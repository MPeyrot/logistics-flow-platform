package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.DeliveryNoteData;
import fr.michael.logistics.shipment.application.DeliveryNoteMatch;
import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code shipment} vaut null quand aucune expédition ne porte la référence lue sur le bon. */
record DeliveryNoteMatchResponse(NoteBody deliveryNote, BigDecimal costUsd, ShipmentResponse shipment) {

    static DeliveryNoteMatchResponse from(DeliveryNoteMatch match) {
        return new DeliveryNoteMatchResponse(
                NoteBody.from(match.reading().note()),
                match.reading().costUsd(),
                match.shipment().map(ShipmentResponse::from).orElse(null));
    }

    record NoteBody(
            String reference,
            LocalDate deliveryDate,
            String senderName,
            String recipientName,
            String recipientAddress,
            Integer packageCount,
            BigDecimal totalWeightKg) {

        static NoteBody from(DeliveryNoteData n) {
            return new NoteBody(n.reference(), n.deliveryDate(), n.senderName(), n.recipientName(),
                    n.recipientAddress(), n.packageCount(), n.totalWeightKg());
        }
    }
}
