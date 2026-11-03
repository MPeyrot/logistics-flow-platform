package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.application.ScanPublicationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ScanPublicationException.class)
    ProblemDetail publicationFailed(ScanPublicationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Scan could not be published, retry later");
    }

    @ExceptionHandler(ErrorResponseException.class)
    ProblemDetail errorResponse(ErrorResponseException e) {
        return e.getBody();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
