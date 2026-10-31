package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.application.IngestScanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scans")
class ScanController {

    private final IngestScanService service;

    ScanController(IngestScanService service) {
        this.service = service;
    }

    /** 202 : l'événement est accepté et publié ; la mise à jour de l'expédition est asynchrone. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    ScanAccepted ingest(@Valid @RequestBody ScanRequest request) {
        return new ScanAccepted(service.ingest(
                request.eventId(), request.shipmentReference(), request.type(), request.site(), request.occurredAt()));
    }
}
