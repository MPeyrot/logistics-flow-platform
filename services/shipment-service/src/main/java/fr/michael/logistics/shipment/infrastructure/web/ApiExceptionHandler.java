package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.AccountNotFoundException;
import fr.michael.logistics.shipment.application.DuplicateReferenceException;
import fr.michael.logistics.shipment.application.LastActiveAdminException;
import fr.michael.logistics.shipment.application.LoginBlockedException;
import fr.michael.logistics.shipment.application.DuplicateUsernameException;
import fr.michael.logistics.shipment.application.ExtractionServiceUnavailableException;
import fr.michael.logistics.shipment.application.InvalidCredentialsException;
import fr.michael.logistics.shipment.application.UnreadableDeliveryNoteException;
import fr.michael.logistics.shipment.application.UnsupportedDocumentException;
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
