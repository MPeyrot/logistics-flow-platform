package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.AnomalyQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
class AnomalyController {

    private final AnomalyQueryService service;

    AnomalyController(AnomalyQueryService service) {
        this.service = service;
    }

    @GetMapping
    List<AnomalyResponse> list(@RequestParam(required = false) Boolean open) {
        return service.list(open).stream().map(AnomalyResponse::from).toList();
    }
}
