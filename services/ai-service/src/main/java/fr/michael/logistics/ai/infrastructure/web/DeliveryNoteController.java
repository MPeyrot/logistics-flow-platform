package fr.michael.logistics.ai.infrastructure.web;

import fr.michael.logistics.ai.application.ExtractDeliveryNoteService;
import fr.michael.logistics.ai.domain.DocumentFormat;
import fr.michael.logistics.ai.domain.SourceDocument;
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

    private final ExtractDeliveryNoteService service;

    DeliveryNoteController(ExtractDeliveryNoteService service) {
        this.service = service;
    }

    @PostMapping(path = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DeliveryNoteResponse extract(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        DocumentFormat format = DocumentFormat.detect(content).orElseThrow(UnsupportedDocumentException::new);
        return DeliveryNoteResponse.from(service.extract(new SourceDocument(content, format)));
    }
}
