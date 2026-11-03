package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.application.ScanPublicationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ScanPublicationException.class)
    ProblemDetail publicationFailed(ScanPublicationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Scan could not be published, retry later");
    }

    /** Garde le corps problem+json (le {@code detail} du 403 de site) et les en-têtes de l'exception. */
    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ProblemDetail> errorResponse(ErrorResponseException e) {
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders()).body(e.getBody());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
