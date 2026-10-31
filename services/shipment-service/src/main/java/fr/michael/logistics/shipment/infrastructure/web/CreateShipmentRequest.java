package fr.michael.logistics.shipment.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateShipmentRequest(
        @NotBlank @Size(max = 50) String reference,
        @NotBlank @Size(max = 100) String origin,
        @NotBlank @Size(max = 100) String destination) {
}
