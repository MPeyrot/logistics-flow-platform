package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.application.CsvStructureException;
import fr.michael.logistics.ingestion.application.ImportAbortedException;
import fr.michael.logistics.ingestion.application.ImportBusyException;
import fr.michael.logistics.ingestion.application.ImportInterruptedException;
import fr.michael.logistics.ingestion.application.ScanPublicationException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    /** Le fichier CSV est inutilisable ou trop long : rien n'a été publié. */
    @ExceptionHandler(CsvStructureException.class)
    ProblemDetail invalidCsv(CsvStructureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Kafka en panne en cours d'import : {@code published} lignes sont déjà parties (le réimport ne double rien). */
    @ExceptionHandler(ImportInterruptedException.class)
    ProblemDetail importInterrupted(ImportInterruptedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Import interrupted: Kafka is unavailable, retry later");
        problem.setProperty("published", e.published());
        return problem;
    }

    /** Arrêt de l'instance pendant l'attente du tour : rien n'est parti, le client peut réessayer. */
    @ExceptionHandler(ImportAbortedException.class)
    ProblemDetail importAborted(ImportAbortedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Import aborted: the service is stopping, retry later");
    }

    @ExceptionHandler(ImportBusyException.class)
    ResponseEntity<ProblemDetail> importBusy(ImportBusyException e) {
        long seconds = Math.max(1, e.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                        "Another import is running, retry later"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail missingPart(MissingServletRequestPartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getRequestPartName() + ": required part is missing");
    }

    /** Corps JSON, vide ou multipart illisible : il n'y a pas de partie {@code file}. */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail notMultipart(MultipartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "file: required part is missing");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The uploaded file is too large");
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

    /** JSON illisible ou valeur impossible à convertir (date, type inconnu…) : on nomme le champ, sans le message de Jackson. */
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
}
