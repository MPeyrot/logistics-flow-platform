package fr.michael.logistics.shipment.domain;

/** LATE : non livrée après sa date prévue. STALLED : aucun scan depuis le seuil (48 h par défaut). */
public enum AnomalyType {
    LATE,
    STALLED
}
