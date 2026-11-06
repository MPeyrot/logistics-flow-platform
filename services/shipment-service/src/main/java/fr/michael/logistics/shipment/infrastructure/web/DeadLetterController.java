package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.DeadLetterPosition;
import fr.michael.logistics.shipment.application.DeadLetterService;
import fr.michael.logistics.shipment.application.ReplayAllJob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

    /**
     * Enregistre le rejeu du message ; il est republié vers scan-events peu après (outbox). 404 si l'offset
     * n'existe pas, 409 s'il est déjà rejoué.
     */
    @PostMapping("/{partition}/{offset}/replay")
    DeadLetterResponse replay(@PathVariable int partition, @PathVariable long offset, Authentication authentication) {
        return DeadLetterResponse.from(
                service.replay(new DeadLetterPosition(partition, offset), authentication.getName()));
    }

    /**
     * Lance en tâche de fond le rejeu de tous les messages pas encore rejoués, dans l'ordre de chaque partition :
     * 202, avancement à l'adresse de {@code Location}.
     */
    @PostMapping("/replay")
    ResponseEntity<ReplayAllResponse> replayAll(Authentication authentication) {
        ReplayAllJob job = service.replayAll(authentication.getName());
        return ResponseEntity.accepted()
                .location(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(job.id()))
                .body(ReplayAllResponse.from(job));
    }

    /** Avancement d'un « tout rejouer » ; 404 si l'identifiant est inconnu. */
    @GetMapping("/replay/{id}")
    ReplayAllResponse replayAllJob(@PathVariable UUID id) {
        return ReplayAllResponse.from(service.replayAllJob(id));
    }

    /**
     * {@code replayed} : messages enregistrés comme rejoués (publication garantie) ; {@code published} : déjà
     * accusés par le broker ; {@code failed} : enregistrement en échec, encore à rejouer ; {@code finished} : DLT
     * parcouru ; {@code error} : parcours interrompu.
     */
    record ReplayAllResponse(UUID id, String requestedBy, Instant requestedAt, boolean finished, Instant finishedAt,
            long replayed, long published, int failed, String error) {

        static ReplayAllResponse from(ReplayAllJob job) {
            return new ReplayAllResponse(job.id(), job.requestedBy(), job.requestedAt(), job.finished(),
                    job.finishedAt(), job.replayed(), job.published(), job.failed(), job.error());
        }
    }
}
