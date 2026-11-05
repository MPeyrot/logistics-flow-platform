package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.AccountNotFoundException;
import fr.michael.logistics.shipment.application.DeadLetterAlreadyReplayedException;
import fr.michael.logistics.shipment.application.DeadLetterNotFoundException;
import fr.michael.logistics.shipment.application.DuplicateReferenceException;
import fr.michael.logistics.shipment.application.LastActiveAdminException;
import fr.michael.logistics.shipment.application.LoginBlockedException;
import fr.michael.logistics.shipment.application.DuplicateUsernameException;
import fr.michael.logistics.shipment.application.ExtractionServiceUnavailableException;
import fr.michael.logistics.shipment.application.ExtractionRateLimitedException;
import fr.michael.logistics.shipment.application.InvalidCredentialsException;
import fr.michael.logistics.shipment.application.UnreadableDeliveryNoteException;
import fr.michael.logistics.shipment.application.UnsupportedDocumentException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DuplicateReferenceException.class)
    ProblemDetail duplicate(DuplicateReferenceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    ProblemDetail duplicateUsername(DuplicateUsernameException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(LastActiveAdminException.class)
    ProblemDetail lastActiveAdmin(LastActiveAdminException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DeadLetterNotFoundException.class)
    ProblemDetail deadLetterNotFound(DeadLetterNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DeadLetterAlreadyReplayedException.class)
    ProblemDetail deadLetterAlreadyReplayed(DeadLetterAlreadyReplayedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Même réponse pour un identifiant inconnu, un mauvais mot de passe ou un compte désactivé. */
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(LoginBlockedException.class)
    ResponseEntity<ProblemDetail> loginBlocked(LoginBlockedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    @ExceptionHandler(ExtractionRateLimitedException.class)
    ResponseEntity<ProblemDetail> extractionRateLimited(ExtractionRateLimitedException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (e.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, e.retryAfterSeconds().toString());
        }
        return response.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    /** Corps refusé par Bean Validation : un champ par erreur, par exemple {@code expectedDeliveryAt: must not be null}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Invalid request body" : detail);
    }

    /** JSON illisible ou valeur impossible à convertir (date mal formée…) : on nomme le champ, sans le message de Jackson. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpMessageNotReadableException e) {
        String field = e.getCause() instanceof JacksonException json
                ? json.getPath().stream()
                        .map(JacksonException.Reference::getPropertyName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("."))
                : "";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                field.isEmpty() ? "Malformed request body" : "Invalid value for " + field);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnsupportedDocumentException.class)
    ProblemDetail unsupportedDocument(UnsupportedDocumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(UnreadableDeliveryNoteException.class)
    ProblemDetail unreadable(UnreadableDeliveryNoteException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(ExtractionServiceUnavailableException.class)
    ProblemDetail extractionUnavailable(ExtractionServiceUnavailableException e) {
        log.warn("extraction service failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Delivery note extraction is unavailable, retry later");
    }
}
