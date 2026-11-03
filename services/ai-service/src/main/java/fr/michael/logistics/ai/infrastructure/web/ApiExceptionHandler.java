package fr.michael.logistics.ai.infrastructure.web;

import fr.michael.logistics.ai.application.ExtractionFailedException;
import fr.michael.logistics.ai.application.ExtractionQuotaExceededException;
import fr.michael.logistics.ai.application.ExtractionUnavailableException;
import fr.michael.logistics.ai.application.UnreadableDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UnsupportedDocumentException.class)
    ProblemDetail unsupported(UnsupportedDocumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnreadableDocumentException.class)
    ProblemDetail unreadable(UnreadableDocumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(ExtractionFailedException.class)
    ProblemDetail upstreamFailed(ExtractionFailedException e) {
        log.warn("extraction provider failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The extraction provider failed, retry later");
    }

    @ExceptionHandler(ExtractionUnavailableException.class)
    ProblemDetail unavailable(ExtractionUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Extraction is not configured");
    }

    @ExceptionHandler(ExtractionQuotaExceededException.class)
    ResponseEntity<ProblemDetail> quotaExceeded(ExtractionQuotaExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                        "Extraction limit reached, retry later"));
    }
}
