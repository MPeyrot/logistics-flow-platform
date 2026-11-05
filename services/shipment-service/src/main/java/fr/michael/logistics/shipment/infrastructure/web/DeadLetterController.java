package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.DeadLetterPosition;
import fr.michael.logistics.shipment.application.DeadLetterService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Outil ADMIN de lecture et de rejeu de scan-events.DLT. */
@RestController
@RequestMapping("/api/admin/dlt/scan-events")
class DeadLetterController {

    private final DeadLetterService service;

    DeadLetterController(DeadLetterService service) {
        this.service = service;
    }

    /** Les messages les plus récents d'abord ; {@code limit} de 1 à 500, sinon 400. */
    @GetMapping
    List<DeadLetterResponse> list(@RequestParam(defaultValue = "100") int limit) {
        return service.list(limit).stream().map(DeadLetterResponse::from).toList();
    }

    /** Republie le message vers scan-events ; 404 si l'offset n'existe pas, 409 s'il est déjà rejoué. */
    @PostMapping("/{partition}/{offset}/replay")
    DeadLetterResponse replay(@PathVariable int partition, @PathVariable long offset, Authentication authentication) {
        return DeadLetterResponse.from(
                service.replay(new DeadLetterPosition(partition, offset), authentication.getName()));
    }

    /** Rejoue tous les messages pas encore rejoués, dans l'ordre de chaque partition. */
    @PostMapping("/replay")
    ReplayAllResponse replayAll(Authentication authentication) {
        return new ReplayAllResponse(service.replayAll(authentication.getName()));
    }

    record ReplayAllResponse(int replayed) {
    }
}
