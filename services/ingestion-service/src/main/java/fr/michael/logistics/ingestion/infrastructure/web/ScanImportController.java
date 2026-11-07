package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.application.ImportReport;
import fr.michael.logistics.ingestion.application.ImportScansService;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/scans/imports")
class ScanImportController {

    private final ImportScansService importer;

    ScanImportController(ImportScansService importer) {
        this.importer = importer;
    }

    /** 200 même si toutes les lignes sont rejetées : le bilan dit lesquelles. Chaque ligne doit être du site du jeton. */
    @PostMapping
    ImportReport importScans(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt token) {
        try {
            return importer.importScans(file.getBytes(), token.getClaimAsString("site"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
