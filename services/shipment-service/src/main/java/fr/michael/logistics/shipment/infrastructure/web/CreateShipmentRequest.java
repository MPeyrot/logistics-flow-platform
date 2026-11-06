package fr.michael.logistics.shipment.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;

record CreateShipmentRequest(
        @NotBlank @Size(max = 50) String reference,
        @NotBlank @Size(max = 100) String origin,
        @NotBlank @Size(max = 100) String destination,
        @NotNull @JsonDeserialize(using = IsoInstantDeserializer.class) Instant expectedDeliveryAt) {
}
