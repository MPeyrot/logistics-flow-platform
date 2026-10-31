package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.ReadDeliveryNoteService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/delivery-notes")
class DeliveryNoteController {

    private final ReadDeliveryNoteService service;

    DeliveryNoteController(ReadDeliveryNoteService service) {
        this.service = service;
    }

    /** Lit un bon de livraison (PDF ou image) et retrouve l'expédition qui porte la même référence. */
    @PostMapping(path = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DeliveryNoteMatchResponse extract(@RequestParam("file") MultipartFile file) throws IOException {
        return DeliveryNoteMatchResponse.from(service.read(file.getBytes(), file.getOriginalFilename()));
    }
}
