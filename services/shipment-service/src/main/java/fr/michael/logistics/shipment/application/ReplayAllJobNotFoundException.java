package fr.michael.logistics.shipment.application;

import java.util.UUID;

public class ReplayAllJobNotFoundException extends RuntimeException {

    public ReplayAllJobNotFoundException(UUID id) {
        super("No replay of the dead letter topic with id " + id);
    }
}
