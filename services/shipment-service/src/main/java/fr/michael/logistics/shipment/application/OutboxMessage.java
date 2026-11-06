package fr.michael.logistics.shipment.application;

/** Message rejoué en attente de publication vers {@code scan-events} : clé et valeur brutes du DLT. */
public record OutboxMessage(long id, String key, String value) {
}
