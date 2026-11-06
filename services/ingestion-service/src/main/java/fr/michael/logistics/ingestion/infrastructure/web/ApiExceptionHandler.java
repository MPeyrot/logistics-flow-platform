package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.application.ScanPublicationException;
import java.util.stream.Collectors;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /** Requête hors contrat (Bean Validation) : 400 problem+json, champs fautifs dans {@code detail}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
